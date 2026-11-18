package za.co.capitec.statementservice.statement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.statementservice.common.exception.AccessDeniedStatementException;
import za.co.capitec.statementservice.common.exception.DownloadLinkException;
import za.co.capitec.statementservice.common.exception.InvalidStatementRequestException;
import za.co.capitec.statementservice.common.exception.StatementNotFoundException;
import za.co.capitec.statementservice.common.messaging.OutboxWriter;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.domain.StatementDownloadToken;
import za.co.capitec.statementservice.statement.domain.StatementStatus;
import za.co.capitec.statementservice.statement.events.StatementEvents;
import za.co.capitec.statementservice.statement.events.StatementEvents.StatementDownloadRequestedEvent;
import za.co.capitec.statementservice.statement.events.StatementEvents.StatementDownloadedEvent;
import za.co.capitec.statementservice.statement.repository.StatementDownloadTokenRepository;
import za.co.capitec.statementservice.statement.repository.StatementRepository;
import za.co.capitec.statementservice.statement.storage.StatementStorage;
import za.co.capitec.statementservice.statement.web.dto.DownloadLinkResponse;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(noRollbackFor = {AccessDeniedStatementException.class, DownloadLinkException.class,
        InvalidStatementRequestException.class})
public class DownloadService {

    public record Download(String filename, byte[] content) {
    }

    private final StatementRepository statements;
    private final StatementDownloadTokenRepository tokens;
    private final StatementStorage storage;
    private final StatementAccess access;
    private final DownloadTokens tokenFactory;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final long ttlMinutes;

    public DownloadService(StatementRepository statements,
                           StatementDownloadTokenRepository tokens,
                           StatementStorage storage,
                           StatementAccess access,
                           DownloadTokens tokenFactory,
                           OutboxWriter outboxWriter,
                           @Value("${statement.download.token-ttl-minutes:15}") long ttlMinutes) {
        this.statements = statements;
        this.tokens = tokens;
        this.storage = storage;
        this.access = access;
        this.tokenFactory = tokenFactory;
        this.outboxWriter = outboxWriter;
        this.clock = Clock.systemDefaultZone();
        this.ttlMinutes = ttlMinutes;
    }

    public DownloadLinkResponse createLink(CurrentUser user, UUID statementId) {
        Statement statement = statements.findById(statementId)
                .orElseThrow(() -> new StatementNotFoundException("Statement not found: " + statementId));
        user.requireCustomer(statement.getCustomerId());
        if (statement.getStatus() != StatementStatus.GENERATED) {
            throw new InvalidStatementRequestException("Only generated statements can be downloaded");
        }

        String raw = tokenFactory.newRawToken();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = now.plusMinutes(ttlMinutes);
        tokens.save(new StatementDownloadToken(UUID.randomUUID(), statement.getId(), tokenFactory.hash(raw),
                expiresAt, now));

        access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.LINK_CREATED, true, null);
        outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                StatementDownloadRequestedEvent.EVENT_TYPE,
                new StatementDownloadRequestedEvent(UUID.randomUUID(), statement.getId(), statement.getCustomerId(),
                        expiresAt, now));

        return new DownloadLinkResponse(statement.getId(), raw, expiresAt,
                "/api/v1/statements/download/" + raw);
    }

    public int revokeLinks(CurrentUser user, UUID statementId) {
        Statement statement = statements.findById(statementId)
                .orElseThrow(() -> new StatementNotFoundException("Statement not found: " + statementId));
        user.requireCustomer(statement.getCustomerId());

        LocalDateTime now = LocalDateTime.now(clock);
        List<StatementDownloadToken> active = tokens.findByStatementIdAndRevokedAtIsNull(statement.getId());
        active.forEach(t -> t.revoke(now));
        tokens.saveAll(active);
        access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.LINK_REVOKED, true,
                "revoked " + active.size());
        return active.size();
    }

    public Download download(CurrentUser user, String rawToken) {
        LocalDateTime now = LocalDateTime.now(clock);
        var tokenRow = tokens.findByTokenHash(tokenFactory.hash(rawToken));
        if (tokenRow.isEmpty()) {
            access.record(null, null, user.subject(), AccessType.DOWNLOAD_ATTEMPT, false, "unknown token");
            throw new DownloadLinkException(DownloadLinkException.Reason.INVALID, "The download link is not valid");
        }

        StatementDownloadToken token = tokenRow.get();
        Statement statement = statements.findById(token.getStatementId())
                .orElseThrow(() -> new StatementNotFoundException("Statement not found"));

        if (!user.canAccessCustomer(statement.getCustomerId())) {
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOAD_ATTEMPT,
                    false, "not the statement owner");
            throw new DownloadLinkException(DownloadLinkException.Reason.FORBIDDEN,
                    "You are not authorised to download this statement");
        }
        if (token.isRevoked()) {
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOAD_ATTEMPT,
                    false, "link revoked");
            throw new DownloadLinkException(DownloadLinkException.Reason.REVOKED, "This download link has been revoked");
        }
        if (token.isExpired(now)) {
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOAD_ATTEMPT,
                    false, "link expired");
            throw new DownloadLinkException(DownloadLinkException.Reason.EXPIRED, "This download link has expired");
        }
        if (statement.getStatus() != StatementStatus.GENERATED || statement.getStorageKey() == null) {
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOAD_ATTEMPT,
                    false, "statement not available");
            throw new DownloadLinkException(DownloadLinkException.Reason.INVALID, "The statement is not available");
        }

        byte[] content;
        try {
            content = storage.read(statement.getStorageKey());
        } catch (IOException e) {
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOAD_ATTEMPT,
                    false, "storage read failed");
            throw new DownloadLinkException(DownloadLinkException.Reason.INVALID, "The statement file is unavailable");
        }

        access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.DOWNLOADED, true, null);
        outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                StatementDownloadedEvent.EVENT_TYPE,
                new StatementDownloadedEvent(UUID.randomUUID(), statement.getId(), statement.getCustomerId(), now));

        return new Download(statement.getStatementReference() + ".pdf", content);
    }
}
