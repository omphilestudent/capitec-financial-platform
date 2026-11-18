
package za.co.capitec.statementservice.statement.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.common.exception.DownloadLinkException;
import za.co.capitec.statementservice.common.messaging.OutboxWriter;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.domain.StatementDownloadToken;
import za.co.capitec.statementservice.statement.events.StatementEvents;
import za.co.capitec.statementservice.statement.repository.StatementDownloadTokenRepository;
import za.co.capitec.statementservice.statement.repository.StatementRepository;
import za.co.capitec.statementservice.statement.storage.StatementStorage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DownloadServiceTest {

    private final UUID customerId = UUID.randomUUID();
    private final UUID statementId = UUID.randomUUID();
    private final CurrentUser owner = new CurrentUser("customer-owner", false, customerId);
    private final CurrentUser stranger = new CurrentUser("customer-other", false, UUID.randomUUID());
    private final CurrentUser staff = new CurrentUser("staff", true, null);

    private StatementRepository statements;
    private StatementDownloadTokenRepository tokens;
    private StatementStorage storage;
    private StatementAccess access;
    private OutboxWriter outbox;
    private DownloadTokens factory;
    private DownloadService service;
    private Statement statement;

    @BeforeEach
    void setUp() throws Exception {
        statements = mock(StatementRepository.class);
        tokens = mock(StatementDownloadTokenRepository.class);
        storage = mock(StatementStorage.class);
        access = mock(StatementAccess.class);
        outbox = mock(OutboxWriter.class);
        factory = new DownloadTokens();

        statement = new Statement(statementId, "STM-TEST", customerId, UUID.randomUUID(), "29390331863", "SAVINGS",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "ZAR");
        statement.markGenerated(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ZERO,
                "statements/2026/10/" + statementId + ".pdf", LocalDateTime.now());

        when(statements.findById(statementId)).thenReturn(Optional.of(statement));
        when(storage.read(anyString())).thenReturn("%PDF-bytes".getBytes());
        when(tokens.save(any(StatementDownloadToken.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new DownloadService(statements, tokens, storage, access, factory, outbox, 15);
    }

    private StatementDownloadToken storedToken(String raw, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        StatementDownloadToken token = new StatementDownloadToken(UUID.randomUUID(), statementId, factory.hash(raw),
                expiresAt, LocalDateTime.now().minusMinutes(1));
        if (revokedAt != null) {
            token.revoke(revokedAt);
        }
        when(tokens.findByTokenHash(factory.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    @Test
    void createdLinkExpiresInTheConfiguredWindowAndStoresOnlyTheHash() {
        var link = service.createLink(owner, statementId);

        assertEquals(statementId, link.statementId());
        assertTrue(link.expiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));
        assertTrue(link.expiresAt().isBefore(LocalDateTime.now().plusMinutes(16)));
        verify(tokens).save(argThat(t -> !t.getTokenHash().equals(link.token())
                && t.getTokenHash().equals(factory.hash(link.token()))));
        verify(outbox).write(any(), eq(StatementEvents.TOPIC), anyString(),
                eq(StatementEvents.StatementDownloadRequestedEvent.EVENT_TYPE), any());
    }

    @Test
    void validTokenDownloadsThePdfAndPublishesDownloadedEvent() throws Exception {
        String raw = "valid-token";
        storedToken(raw, LocalDateTime.now().plusMinutes(5), null);

        DownloadService.Download download = service.download(owner, raw);

        assertArrayEquals("%PDF-bytes".getBytes(), download.content());
        assertEquals("STM-TEST.pdf", download.filename());
        verify(access).record(eq(statementId), eq(customerId), anyString(), eq(AccessType.DOWNLOADED), eq(true), any());
        verify(outbox).write(any(), eq(StatementEvents.TOPIC), anyString(),
                eq(StatementEvents.StatementDownloadedEvent.EVENT_TYPE), any());
    }

    @Test
    void expiredTokenIsRejectedBeforeAnyFileIsRead() throws Exception {
        String raw = "expired-token";
        storedToken(raw, LocalDateTime.now().minusSeconds(1), null);

        DownloadLinkException ex = assertThrows(DownloadLinkException.class, () -> service.download(owner, raw));

        assertEquals(DownloadLinkException.Reason.EXPIRED, ex.getReason());
        verify(storage, never()).read(anyString());
        verify(access).record(eq(statementId), eq(customerId), anyString(), eq(AccessType.DOWNLOAD_ATTEMPT), eq(false), eq("link expired"));
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        String raw = "revoked-token";
        storedToken(raw, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());

        DownloadLinkException ex = assertThrows(DownloadLinkException.class, () -> service.download(owner, raw));

        assertEquals(DownloadLinkException.Reason.REVOKED, ex.getReason());
        verify(storage, never()).read(anyString());
    }

    @Test
    void unknownTokenIsRejectedAndLogged() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        DownloadLinkException ex = assertThrows(DownloadLinkException.class, () -> service.download(owner, "nope"));

        assertEquals(DownloadLinkException.Reason.INVALID, ex.getReason());
        verify(access).record(any(), any(), anyString(), eq(AccessType.DOWNLOAD_ATTEMPT), eq(false), eq("unknown token"));
    }

    @Test
    void validTokenDoesNotGrantAccessToAnotherCustomersStatement() throws Exception {
        String raw = "owner-token";
        storedToken(raw, LocalDateTime.now().plusMinutes(5), null);

        DownloadLinkException ex = assertThrows(DownloadLinkException.class, () -> service.download(stranger, raw));

        assertEquals(DownloadLinkException.Reason.FORBIDDEN, ex.getReason());
        verify(storage, never()).read(anyString());
    }

    @Test
    void staffCanDownloadWithAValidToken() throws Exception {
        String raw = "staff-token";
        storedToken(raw, LocalDateTime.now().plusMinutes(5), null);

        assertNotNull(service.download(staff, raw).content());
    }

    @Test
    void revokingLinksMarksEveryActiveTokenRevoked() {
        StatementDownloadToken a = new StatementDownloadToken(UUID.randomUUID(), statementId, "a".repeat(64),
                LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        StatementDownloadToken b = new StatementDownloadToken(UUID.randomUUID(), statementId, "b".repeat(64),
                LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        when(tokens.findByStatementIdAndRevokedAtIsNull(statementId)).thenReturn(List.of(a, b));

        int revoked = service.revokeLinks(owner, statementId);

        assertEquals(2, revoked);
        assertTrue(a.isRevoked());
        assertTrue(b.isRevoked());
    }

    @Test
    void customerCannotRevokeAnotherCustomersLinks() {
        assertThrows(za.co.capitec.statementservice.common.exception.AccessDeniedStatementException.class,
                () -> service.revokeLinks(stranger, statementId));
        verify(tokens, never()).saveAll(any());
    }
}
