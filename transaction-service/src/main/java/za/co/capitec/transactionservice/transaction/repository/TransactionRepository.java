package za.co.capitec.transactionservice.transaction.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.transactionservice.transaction.entity.Transaction;

import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    boolean existsByTransactionReference(String transactionReference);
}
