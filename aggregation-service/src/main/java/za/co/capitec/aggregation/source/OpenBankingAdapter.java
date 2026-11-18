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
public class OpenBankingAdapter implements SourceAdapter {

    private static final DateTimeFormatter BOOKED = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final MockTransactionGenerator generator;
    private final Categoriser categoriser;

    public OpenBankingAdapter(MockTransactionGenerator generator, Categoriser categoriser) {
        this.generator = generator;
        this.categoriser = categoriser;
    }

    @Override
    public SourceSystem source() {
        return SourceSystem.OPEN_BANKING;
    }

    @Override
    public List<Map<String, String>> fetch(FetchRequest request) {
        return generator.generate("OB", request).stream().map(OpenBankingAdapter::encode).toList();
    }

    static Map<String, String> encode(MockSpec spec) {
        return Map.of(
                "transaction_id", spec.externalId(),
                "booking_date_time", spec.occurredAt().format(BOOKED),
                "amount_value", spec.amount().toPlainString(),
                "amount_currency", "ZAR",
                "credit_debit_indicator", spec.direction() == Direction.CREDIT ? "Credit" : "Debit",
                "remittance_information", spec.description(),
                "status", spec.status().equals("COMPLETED") ? "Booked" : spec.status().equals("PENDING") ? "Pending" : "Rejected");
    }

    @Override
    public NormalizedTransaction normalize(Map<String, String> raw, UUID customerId, UUID accountId) {
        Direction direction = switch (raw.getOrDefault("credit_debit_indicator", "")) {
            case "Credit" -> Direction.CREDIT;
            case "Debit" -> Direction.DEBIT;
            default -> throw new IllegalArgumentException("Unknown credit_debit_indicator");
        };
        String description = raw.get("remittance_information");
        String status = switch (raw.getOrDefault("status", "")) {
            case "Booked" -> "COMPLETED";
            case "Pending" -> "PENDING";
            default -> "FAILED";
        };
        return new NormalizedTransaction(
                SourceSystem.OPEN_BANKING,
                require(raw, "transaction_id"),
                customerId,
                accountId,
                direction,
                new BigDecimal(require(raw, "amount_value")),
                require(raw, "amount_currency"),
                description,
                status,
                LocalDateTime.parse(require(raw, "booking_date_time"), BOOKED),
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
