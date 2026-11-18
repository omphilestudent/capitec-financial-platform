package za.co.capitec.aggregation.source;

import org.springframework.stereotype.Component;
import za.co.capitec.aggregation.categorisation.Categoriser;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.domain.SourceSystem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BankFeedAdapter implements SourceAdapter {

    private static final DateTimeFormatter POSTED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MockTransactionGenerator generator;
    private final Categoriser categoriser;

    public BankFeedAdapter(MockTransactionGenerator generator, Categoriser categoriser) {
        this.generator = generator;
        this.categoriser = categoriser;
    }

    @Override
    public SourceSystem source() {
        return SourceSystem.BANK_FEED;
    }

    @Override
    public List<Map<String, String>> fetch(FetchRequest request) {
        return generator.generate("BF", request).stream().map(BankFeedAdapter::encode).toList();
    }

    static Map<String, String> encode(MockSpec spec) {
        return Map.of(
                "txnId", spec.externalId(),
                "postedDate", spec.occurredAt().format(POSTED),
                "valueAmount", spec.amount().toPlainString(),
                "drCr", spec.direction() == Direction.CREDIT ? "CR" : "DR",
                "narrative", spec.description(),
                "ccy", "ZAR",
                "bookingStatus", spec.status());
    }

    @Override
    public NormalizedTransaction normalize(Map<String, String> raw, UUID customerId, UUID accountId) {
        Direction direction = switch (raw.getOrDefault("drCr", "")) {
            case "CR" -> Direction.CREDIT;
            case "DR" -> Direction.DEBIT;
            default -> throw new IllegalArgumentException("Unknown drCr: " + raw.get("drCr"));
        };
        String description = raw.get("narrative");
        return new NormalizedTransaction(
                SourceSystem.BANK_FEED,
                require(raw, "txnId"),
                customerId,
                accountId,
                direction,
                new BigDecimal(require(raw, "valueAmount")),
                require(raw, "ccy"),
                description,
                "COMPLETED".equals(raw.get("bookingStatus")) ? "COMPLETED"
                        : "PENDING".equals(raw.get("bookingStatus")) ? "PENDING" : "FAILED",
                LocalDateTime.parse(require(raw, "postedDate"), POSTED),
                categoriser.categorise(description, null, direction));
    }

    private static String require(Map<String, String> raw, String key) {
        String value = raw.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }
}
