package za.co.capitec.frauddetection.detection.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A transaction this service has evaluated. Its presence makes re-processing a no-op. */
@Entity
@Table(name = "processed_transactions")
public class ProcessedTransaction {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "transaction_created_at", nullable = false)
    private LocalDateTime transactionCreatedAt;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false, length = 30)
    private String outcome;

    protected ProcessedTransaction() {
    }

    public ProcessedTransaction(UUID transactionId, UUID accountId, BigDecimal amount, String currency,
                                LocalDateTime transactionCreatedAt, String outcome) {
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amount = amount;
        this.currency = currency;
        this.transactionCreatedAt = transactionCreatedAt;
        this.processedAt = LocalDateTime.now();
        this.outcome = outcome;
    }

    public UUID getTransactionId() { return transactionId; }
    public UUID getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public LocalDateTime getTransactionCreatedAt() { return transactionCreatedAt; }
    public String getOutcome() { return outcome; }
}
