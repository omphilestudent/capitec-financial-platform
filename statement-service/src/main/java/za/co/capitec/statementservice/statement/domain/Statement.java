package za.co.capitec.statementservice.statement.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "statements")
public class Statement {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "statement_reference", nullable = false, unique = true, length = 40)
    private String statementReference;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Column(name = "account_type", nullable = false, length = 20)
    private String accountType;

    @Column(name = "statement_period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "statement_period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "closing_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "total_credits", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCredits;

    @Column(name = "total_debits", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalDebits;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "storage_key", length = 255)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatementStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "eligible_transaction_count", nullable = false)
    private int eligibleTransactionCount;

    @Column(name = "excluded_transaction_count", nullable = false)
    private int excludedTransactionCount;

    @Column(name = "generated_at")
    private LocalDateTime generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Statement() {
    }

    public Statement(UUID id, String statementReference, UUID customerId, UUID accountId, String accountNumber,
                     String accountType, LocalDate periodStart, LocalDate periodEnd, String currency) {
        this.id = id;
        this.statementReference = statementReference;
        this.customerId = customerId;
        this.accountId = accountId;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.currency = currency;
        this.openingBalance = BigDecimal.ZERO;
        this.closingBalance = BigDecimal.ZERO;
        this.totalCredits = BigDecimal.ZERO;
        this.totalDebits = BigDecimal.ZERO;
        this.status = StatementStatus.FAILED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void markGenerated(BigDecimal opening, BigDecimal closing, BigDecimal credits, BigDecimal debits,
                              String storageKey, LocalDateTime when) {
        this.openingBalance = opening;
        this.closingBalance = closing;
        this.totalCredits = credits;
        this.totalDebits = debits;
        this.storageKey = storageKey;
        this.status = StatementStatus.GENERATED;
        this.failureReason = null;
        this.generatedAt = when;
    }

    public void markWithdrawn(String reason) {
        this.status = StatementStatus.WITHDRAWN;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public void setEligibilityCounts(int eligible, int excluded) {
        this.eligibleTransactionCount = eligible;
        this.excludedTransactionCount = excluded;
    }

    public int getEligibleTransactionCount() { return eligibleTransactionCount; }
    public int getExcludedTransactionCount() { return excludedTransactionCount; }

    public void markFailed(String reason) {
        this.status = StatementStatus.FAILED;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public UUID getId() { return id; }
    public String getStatementReference() { return statementReference; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAccountId() { return accountId; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountType() { return accountType; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public BigDecimal getOpeningBalance() { return openingBalance; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public BigDecimal getTotalDebits() { return totalDebits; }
    public String getCurrency() { return currency; }
    public String getStorageKey() { return storageKey; }
    public StatementStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
