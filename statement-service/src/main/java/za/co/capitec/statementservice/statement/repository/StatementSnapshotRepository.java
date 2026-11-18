package za.co.capitec.statementservice.statement.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.capitec.statementservice.statement.pdf.LedgerRow;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public class StatementSnapshotRepository {

    private final JdbcTemplate jdbc;

    public StatementSnapshotRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void save(UUID statementId, List<LedgerRow> rows) {
        int sequence = 1;
        for (LedgerRow row : rows) {
            BigDecimal debit = row.debit();
            BigDecimal credit = row.credit();
            jdbc.update(
                    "INSERT INTO statement_transaction_snapshots (statement_id, sequence_no, transaction_reference, "
                            + "occurred_at, description, direction, debit, credit, running_balance) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    statementId, sequence++, row.reference(), row.createdAt(), row.description(), row.type(),
                    debit, credit, row.runningBalance());
        }
    }

    public int count(UUID statementId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM statement_transaction_snapshots WHERE statement_id = ?", Integer.class, statementId);
        return count == null ? 0 : count;
    }
}
