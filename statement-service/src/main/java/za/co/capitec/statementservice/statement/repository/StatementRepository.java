package za.co.capitec.statementservice.statement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.statementservice.statement.domain.Statement;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface StatementRepository extends JpaRepository<Statement, UUID> {

    Optional<Statement> findByAccountIdAndPeriodStartAndPeriodEnd(UUID accountId, LocalDate periodStart, LocalDate periodEnd);

    Page<Statement> findByAccountIdOrderByPeriodEndDescCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<Statement> findByAccountIdAndCustomerIdOrderByPeriodEndDescCreatedAtDesc(UUID accountId, UUID customerId, Pageable pageable);

    Page<Statement> findByCustomerIdOrderByPeriodEndDescCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Statement> findByCustomerIdAndAccountTypeOrderByPeriodEndDescCreatedAtDesc(UUID customerId, String accountType,
                                                                                    Pageable pageable);
}
