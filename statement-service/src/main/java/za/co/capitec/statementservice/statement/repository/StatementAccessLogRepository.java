package za.co.capitec.statementservice.statement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.statementservice.statement.domain.StatementAccessLog;

import java.util.List;
import java.util.UUID;

public interface StatementAccessLogRepository extends JpaRepository<StatementAccessLog, UUID> {

    List<StatementAccessLog> findByStatementIdOrderByAccessedAtDesc(UUID statementId);
}
