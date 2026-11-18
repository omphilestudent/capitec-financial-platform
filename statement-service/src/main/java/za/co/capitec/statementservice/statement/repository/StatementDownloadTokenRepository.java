package za.co.capitec.statementservice.statement.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.statementservice.statement.domain.StatementDownloadToken;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StatementDownloadTokenRepository extends JpaRepository<StatementDownloadToken, UUID> {

    Optional<StatementDownloadToken> findByTokenHash(String tokenHash);

    List<StatementDownloadToken> findByStatementIdAndRevokedAtIsNull(UUID statementId);
}
