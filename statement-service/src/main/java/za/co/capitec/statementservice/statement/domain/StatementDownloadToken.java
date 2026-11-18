package za.co.capitec.statementservice.statement.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "statement_download_tokens")
public class StatementDownloadToken {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "statement_id", nullable = false, updatable = false)
    private UUID statementId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected StatementDownloadToken() {
    }

    public StatementDownloadToken(UUID id, UUID statementId, String tokenHash, LocalDateTime expiresAt,
                                  LocalDateTime createdAt) {
        this.id = id;
        this.statementId = statementId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void extendTo(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void revoke(LocalDateTime when) {
        if (revokedAt == null) {
            revokedAt = when;
        }
    }

    public UUID getId() { return id; }
    public UUID getStatementId() { return statementId; }
    public String getTokenHash() { return tokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
