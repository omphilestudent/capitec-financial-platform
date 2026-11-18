package za.co.capitec.aggregation.aggregation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.aggregation.aggregation.AggregationQueries.Scope;
import za.co.capitec.aggregation.aggregation.AggregationQueries.Totals;
import za.co.capitec.aggregation.aggregation.AggregationQueries.Velocity;
import za.co.capitec.aggregation.common.exception.InvalidRequestException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AggregationQueryService {

    private static final int VELOCITY_SAMPLE_LIMIT = 200_000;

    private final AggregationQueries queries;
    private final VelocityCalculator velocity;

    public AggregationQueryService(AggregationQueries queries, VelocityCalculator velocity) {
        this.queries = queries;
        this.velocity = velocity;
    }

    public static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > 731) {
            throw new InvalidRequestException("date range may not exceed 731 days");
        }
        return new LocalDate[]{start, end};
    }

    public Totals totals(Scope scope, LocalDate from, LocalDate to) {
        LocalDate[] r = range(from, to);
        return queries.totals(scope, r[0], r[1]);
    }

    public List<AggregationQueries.TrendPoint> trends(Scope scope, LocalDate from, LocalDate to, String granularity) {
        LocalDate[] r = range(from, to);
        boolean monthly = switch (granularity == null ? "DAY" : granularity.toUpperCase(java.util.Locale.ROOT)) {
            case "DAY" -> false;
            case "MONTH" -> true;
            default -> throw new InvalidRequestException("granularity must be DAY or MONTH");
        };
        return queries.trends(scope, r[0], r[1], monthly);
    }

    public List<AggregationQueries.CategoryRow> categories(Scope scope, LocalDate from, LocalDate to) {
        LocalDate[] r = range(from, to);
        return queries.categories(scope, r[0], r[1]);
    }

    public Velocity velocity(Scope scope, LocalDate from, LocalDate to) {
        LocalDate[] r = range(from, to);
        List<java.time.LocalDateTime> timestamps = queries.countedTimestamps(scope, r[0], r[1], VELOCITY_SAMPLE_LIMIT);
        long days = ChronoUnit.DAYS.between(r[0], r[1]) + 1;
        VelocityCalculator.Peaks peaks = velocity.peaks(timestamps);
        return new Velocity(timestamps.size(), (double) timestamps.size() / days,
                peaks.peakIn10Minutes(), peaks.peakInHour(), r[0], r[1]);
    }

    public AggregationQueries.Filter filter(java.util.UUID accountId, java.util.UUID customerId, String category,
                                            String direction, String source, String status, LocalDate from,
                                            LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'");
        }
        if (direction != null && !List.of("CREDIT", "DEBIT").contains(direction)) {
            throw new InvalidRequestException("direction must be CREDIT or DEBIT");
        }
        return new AggregationQueries.Filter(accountId, customerId, category, direction, source, status, from, to);
    }

    public AggregationQueries.TransactionRow transaction(java.util.UUID id) {
        return queries.findTransaction(id);
    }

    public long count(AggregationQueries.Filter filter) {
        return queries.countTransactions(filter);
    }

    public List<AggregationQueries.TransactionRow> page(AggregationQueries.Filter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new InvalidRequestException("page must be >= 0 and size between 1 and 200");
        }
        return queries.listTransactions(filter, page, size);
    }
}
