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
}
