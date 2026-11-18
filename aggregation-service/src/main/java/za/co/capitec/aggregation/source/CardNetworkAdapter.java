package za.co.capitec.aggregation.source;

import org.springframework.stereotype.Component;
import za.co.capitec.aggregation.categorisation.Categoriser;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.domain.SourceSystem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class CardNetworkAdapter implements SourceAdapter {

    private static final ZoneId SAST = ZoneId.of("Africa/Johannesburg");

    private final MockTransactionGenerator generator;
    private final Categoriser categoriser;

    public CardNetworkAdapter(MockTransactionGenerator generator, Categoriser categoriser) {
        this.generator = generator;
        this.categoriser = categoriser;
    }

    @Override
    public SourceSystem source() {
        return SourceSystem.CARD_NETWORK;
    }

    @Override
    public List<Map<String, String>> fetch(FetchRequest request) {
        return generator.generate("CN", request).stream().map(CardNetworkAdapter::encode).toList();
    }

    static Map<String, String> encode(MockSpec spec) {
        long epoch = spec.occurredAt().atZone(SAST).toEpochSecond();
        long cents = spec.amount().movePointRight(2).longValueExact();
        return Map.of(
                "authCode", spec.externalId(),
                "mcc", spec.mcc() == null ? "0000" : spec.mcc(),
                "merchantName", spec.description(),
                "amountCents", Long.toString(cents),
                "direction", spec.direction().name(),
                "epochSeconds", Long.toString(epoch),
                "currencyCode", "ZAR",
                "authStatus", spec.status());
    }

    @Override
    public NormalizedTransaction normalize(Map<String, String> raw, UUID customerId, UUID accountId) {
        Direction direction = Direction.valueOf(require(raw, "direction"));
        String mcc = raw.get("mcc");
        String merchant = require(raw, "merchantName");
        BigDecimal amount = new BigDecimal(require(raw, "amountCents")).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime occurredAt = LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(Long.parseLong(require(raw, "epochSeconds"))), SAST);
        String status = switch (raw.getOrDefault("authStatus", "")) {
            case "COMPLETED" -> "COMPLETED";
            case "PENDING" -> "PENDING";
            default -> "FAILED";
        };
        return new NormalizedTransaction(
                SourceSystem.CARD_NETWORK,
                require(raw, "authCode"),
                customerId,
                accountId,
                direction,
                amount,
                require(raw, "currencyCode"),
                merchant,
                status,
                occurredAt,
                categoriser.categorise(merchant, "0000".equals(mcc) ? null : mcc, direction));
    }

    private static String require(Map<String, String> raw, String key) {
        String value = raw.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }
}
