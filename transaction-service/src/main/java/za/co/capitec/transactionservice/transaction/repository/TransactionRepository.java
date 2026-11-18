package za.co.capitec.transactionservice.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<Transaction> findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID accountId, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    List<Transaction> findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID accountId, TransactionType type, LocalDateTime fromInclusive, LocalDateTime toExclusive);

    List<Transaction> findTop100ByOrderByCreatedAtDesc();

    boolean existsByTransactionReference(String transactionReference);

    java.util.Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @org.springframework.data.jpa.repository.Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    Object lockAccountForPosting(@org.springframework.data.repository.query.Param("key") String key);

    boolean existsByAccountIdAndEntryKind(UUID accountId, za.co.capitec.transactionservice.transaction.entity.EntryKind entryKind);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(t.amount), 0) from Transaction t "
                    + "where t.accountId = :accountId and t.type = :type and t.status = :status")
    java.math.BigDecimal sumAmount(
            @org.springframework.data.repository.query.Param("accountId") UUID accountId,
            @org.springframework.data.repository.query.Param("type") za.co.capitec.transactionservice.transaction.entity.TransactionType type,
            @org.springframework.data.repository.query.Param("status") za.co.capitec.transactionservice.transaction.entity.TransactionStatus status);
}
