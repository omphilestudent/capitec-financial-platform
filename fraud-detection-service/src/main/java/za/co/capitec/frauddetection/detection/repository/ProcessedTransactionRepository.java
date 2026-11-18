package za.co.capitec.frauddetection.detection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.frauddetection.detection.entity.ProcessedTransaction;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransaction, UUID> {

    /** Transactions for an account created at or after the window start, including the one under review. */
    long countByAccountIdAndTransactionCreatedAtBetween(UUID accountId, LocalDateTime from, LocalDateTime to);
}
