package za.co.capitec.transactionservice.transaction.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 50)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "ZAR";

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_kind", nullable = false, length = 30)
    private EntryKind entryKind = EntryKind.STANDARD;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @Column(name = "synthetic_seed", nullable = false)
    private boolean syntheticSeed;

    @jakarta.persistence.Transient
    private LocalDateTime requestedOccurredAt;

    protected Transaction() {
    }

    public Transaction(
            UUID accountId,
            String transactionReference,
            TransactionType type,
            BigDecimal amount,
            String currency,
            String description) {
        this.accountId = accountId;
        this.transactionReference = transactionReference;
        this.type = type;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
        this.status = TransactionStatus.PENDING;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = requestedOccurredAt != null ? requestedOccurredAt : LocalDateTime.now();
        if (status == TransactionStatus.COMPLETED && postedAt == null) {
            postedAt = createdAt;
        }
    }

    public static Transaction funding(UUID accountId, String reference, BigDecimal amount, String currency,
                                      String description, EntryKind kind, String idempotencyKey) {
        Transaction tx = new Transaction(accountId, reference, TransactionType.CREDIT, amount, currency, description);
        tx.entryKind = kind;
        tx.idempotencyKey = idempotencyKey;
        tx.setStatus(TransactionStatus.COMPLETED);
        return tx;
    }

    public void markSynthetic(LocalDateTime occurredAt) {
        this.syntheticSeed = true;
        this.requestedOccurredAt = occurredAt;
    }

    public boolean isSyntheticSeed() {
        return syntheticSeed;
    }

    public void markPosted() {
        this.status = TransactionStatus.COMPLETED;
        this.postedAt = LocalDateTime.now();
    }

    public EntryKind getEntryKind() {
        return entryKind;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public TransactionType getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
