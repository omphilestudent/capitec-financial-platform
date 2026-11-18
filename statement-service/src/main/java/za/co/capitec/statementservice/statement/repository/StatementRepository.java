package za.co.capitec.statementservice.statement.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.statementservice.statement.domain.Statement;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface StatementRepository extends JpaRepository<Statement, UUID> {

    Optional<Statement> findByAccountIdAndPeriodStartAndPeriodEndAndVersionStatus(UUID accountId, LocalDate periodStart,
                                                                                  LocalDate periodEnd, String versionStatus);

    java.util.List<Statement> findByAccountIdAndPeriodStartAndPeriodEndOrderByVersionNumberDesc(UUID accountId,
                                                                                               LocalDate periodStart,
                                                                                               LocalDate periodEnd);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(max(s.versionNumber), 0) from Statement s where s.accountId = :accountId "
                    + "and s.periodStart = :periodStart and s.periodEnd = :periodEnd")
    int maxVersion(@org.springframework.data.repository.query.Param("accountId") UUID accountId,
                   @org.springframework.data.repository.query.Param("periodStart") LocalDate periodStart,
                   @org.springframework.data.repository.query.Param("periodEnd") LocalDate periodEnd);

    Page<Statement> findByAccountIdOrderByPeriodEndDescCreatedAtDesc(UUID accountId, Pageable pageable);

    Page<Statement> findByAccountIdAndCustomerIdOrderByPeriodEndDescCreatedAtDesc(UUID accountId, UUID customerId, Pageable pageable);

    Page<Statement> findByCustomerIdOrderByPeriodEndDescCreatedAtDesc(UUID customerId, Pageable pageable);

    Page<Statement> findByCustomerIdAndAccountTypeOrderByPeriodEndDescCreatedAtDesc(UUID customerId, String accountType,
                                                                                    Pageable pageable);
}
