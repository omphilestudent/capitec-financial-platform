package za.co.capitec.aggregation.aggregation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class AggregationQueries {

    public record Totals(long transactionCount, long creditCount, long debitCount, BigDecimal creditTotal,
                         BigDecimal debitTotal, BigDecimal netTotal) {
    }

    public record TrendPoint(String period, long creditCount, long debitCount, BigDecimal creditTotal,
                             BigDecimal debitTotal, BigDecimal netTotal) {
    }

    public record CategoryRow(String category, String direction, long count, BigDecimal total) {
    }

    public record TransactionRow(UUID id, String source, String externalId, UUID customerId, UUID accountId,
                                 String direction, BigDecimal amount, String currency, String category,
                                 String status, LocalDateTime occurredAt, boolean counted, UUID duplicateOf) {
    }

    public record Velocity(long transactionCount, double averagePerDay, long peakIn10Minutes, long peakInHour,
                           LocalDate from, LocalDate to) {
    }

    public record Scope(UUID accountId, UUID customerId) {

        public static Scope account(UUID accountId) {
            return new Scope(accountId, null);
        }

        public static Scope customer(UUID customerId) {
            return new Scope(null, customerId);
        }

        public static Scope all() {
            return new Scope(null, null);
        }

        String column() {
            return accountId != null ? "account_id" : "customer_id";
        }

        UUID value() {
            return accountId != null ? accountId : customerId;
        }
    }

    private final JdbcTemplate jdbc;

    public AggregationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(Scope scope, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(SUM(credit_count),0) cc, COALESCE(SUM(debit_count),0) dc, "
                        + "COALESCE(SUM(credit_total),0) ct, COALESCE(SUM(debit_total),0) dt "
                        + "FROM daily_account_summaries WHERE summary_date BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(from, to));
        applyScope(sql, args, scope, "");
        return jdbc.queryForObject(sql.toString(), (rs, n) -> {
            long credits = rs.getLong("cc");
            long debits = rs.getLong("dc");
            BigDecimal creditTotal = rs.getBigDecimal("ct");
            BigDecimal debitTotal = rs.getBigDecimal("dt");
            return new Totals(credits + debits, credits, debits, creditTotal, debitTotal, creditTotal.subtract(debitTotal));
        }, args.toArray());
    }

    public List<TrendPoint> trends(Scope scope, LocalDate from, LocalDate to, boolean monthly) {
        String period = monthly ? "to_char(date_trunc('month', summary_date), 'YYYY-MM')" : "to_char(summary_date, 'YYYY-MM-DD')";
        StringBuilder sql = new StringBuilder(
                "SELECT " + period + " AS period, SUM(credit_count) cc, SUM(debit_count) dc, "
                        + "SUM(credit_total) ct, SUM(debit_total) dt FROM daily_account_summaries "
                        + "WHERE summary_date BETWEEN ? AND ?");
        List<Object> args = new ArrayList<>(List.of(from, to));
        applyScope(sql, args, scope, "");
        sql.append(" GROUP BY period ORDER BY period");
        return jdbc.query(sql.toString(), (rs, n) -> {
            BigDecimal creditTotal = rs.getBigDecimal("ct");
            BigDecimal debitTotal = rs.getBigDecimal("dt");
            return new TrendPoint(rs.getString("period"), rs.getLong("cc"), rs.getLong("dc"),
                    creditTotal, debitTotal, creditTotal.subtract(debitTotal));
        }, args.toArray());
    }

    public List<CategoryRow> categories(Scope scope, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT category, direction, COUNT(*) cnt, SUM(amount) total FROM aggregated_transactions "
                        + "WHERE counted = TRUE AND occurred_at >= ? AND occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        applyScope(sql, args, scope, "");
        sql.append(" GROUP BY category, direction ORDER BY total DESC");
        return jdbc.query(sql.toString(), (rs, n) -> new CategoryRow(rs.getString("category"),
                rs.getString("direction"), rs.getLong("cnt"), rs.getBigDecimal("total")), args.toArray());
    }

    public List<LocalDateTime> countedTimestamps(Scope scope, LocalDate from, LocalDate to, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT occurred_at FROM aggregated_transactions WHERE counted = TRUE "
                        + "AND occurred_at >= ? AND occurred_at < ?");
        List<Object> args = new ArrayList<>(List.of(from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
        applyScope(sql, args, scope, "");
        sql.append(" ORDER BY occurred_at LIMIT ").append(limit);
        return jdbc.query(sql.toString(), (rs, n) -> rs.getObject("occurred_at", LocalDateTime.class), args.toArray());
    }

    public long countTransactions(Filter filter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM aggregated_transactions WHERE 1=1");
        List<Object> args = new ArrayList<>();
        filter.apply(sql, args);
        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0 : count;
    }

    public TransactionRow findTransaction(UUID id) {
        List<TransactionRow> rows = jdbc.query(
                "SELECT id, source, external_id, customer_id, account_id, direction, amount, currency, category, "
                        + "status, occurred_at, counted, duplicate_of FROM aggregated_transactions WHERE id = ?",
                (rs, n) -> new TransactionRow(
                        rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("external_id"),
                        rs.getObject("customer_id", UUID.class), rs.getObject("account_id", UUID.class),
                        rs.getString("direction"), rs.getBigDecimal("amount"), rs.getString("currency"),
                        rs.getString("category"), rs.getString("status"),
                        rs.getObject("occurred_at", LocalDateTime.class), rs.getBoolean("counted"),
                        rs.getObject("duplicate_of", UUID.class)), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public List<TransactionRow> listTransactions(Filter filter, int page, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, source, external_id, customer_id, account_id, direction, amount, currency, category, "
                        + "status, occurred_at, counted, duplicate_of FROM aggregated_transactions WHERE 1=1");
        List<Object> args = new ArrayList<>();
        filter.apply(sql, args);
        sql.append(" ORDER BY occurred_at DESC, id LIMIT ? OFFSET ?");
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(sql.toString(), (rs, n) -> new TransactionRow(
                rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("external_id"),
                rs.getObject("customer_id", UUID.class), rs.getObject("account_id", UUID.class),
                rs.getString("direction"), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getString("category"), rs.getString("status"),
                rs.getObject("occurred_at", LocalDateTime.class), rs.getBoolean("counted"),
                rs.getObject("duplicate_of", UUID.class)), args.toArray());
    }

    public record Filter(UUID accountId, UUID customerId, String category, String direction, String source,
                         String status, LocalDate from, LocalDate to) {

        void apply(StringBuilder sql, List<Object> args) {
            if (accountId != null) {
                sql.append(" AND account_id = ?");
                args.add(accountId);
            }
            if (customerId != null) {
                sql.append(" AND customer_id = ?");
                args.add(customerId);
            }
            if (category != null) {
                sql.append(" AND category = ?");
                args.add(category);
            }
            if (direction != null) {
                sql.append(" AND direction = ?");
                args.add(direction);
            }
            if (source != null) {
                sql.append(" AND source = ?");
                args.add(source);
            }
            if (status != null) {
                sql.append(" AND status = ?");
                args.add(status);
            }
            if (from != null) {
                sql.append(" AND occurred_at >= ?");
                args.add(from.atStartOfDay());
            }
            if (to != null) {
                sql.append(" AND occurred_at < ?");
                args.add(to.plusDays(1).atStartOfDay());
            }
        }
    }

    private static void applyScope(StringBuilder sql, List<Object> args, Scope scope, String prefix) {
        if (scope.accountId() != null || scope.customerId() != null) {
            sql.append(" AND ").append(prefix).append(scope.column()).append(" = ?");
            args.add(scope.value());
        }
    }
}
