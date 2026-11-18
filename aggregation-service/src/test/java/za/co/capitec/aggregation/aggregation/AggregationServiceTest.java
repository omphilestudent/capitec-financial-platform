package za.co.capitec.aggregation.aggregation;

import org.junit.jupiter.api.Test;
import za.co.capitec.aggregation.domain.Category;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.domain.SourceSystem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AggregationServiceTest {

    private NormalizedTransaction tx(SourceSystem source, String externalId, String description, String amount) {
        return new NormalizedTransaction(source, externalId, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"), Direction.DEBIT, new BigDecimal(amount),
                "ZAR", description, "COMPLETED", LocalDateTime.of(2026, 10, 3, 10, 15, 30), Category.GROCERIES);
    }

    @Test
    void fingerprintIgnoresSourceAndIdButMatchesTheSameRealWorldTransaction() {
        String fromBank = AggregationService.fingerprint(tx(SourceSystem.BANK_FEED, "A", "Checkers  ", "100.00"));
        String fromCard = AggregationService.fingerprint(tx(SourceSystem.CARD_NETWORK, "B", "checkers", "100.0"));
        assertEquals(fromBank, fromCard);
    }

    @Test
    void fingerprintChangesWhenAmountOrMinuteOrDirectionChanges() {
        String base = AggregationService.fingerprint(tx(SourceSystem.BANK_FEED, "A", "Shop", "100.00"));
        assertNotEquals(base, AggregationService.fingerprint(tx(SourceSystem.BANK_FEED, "A", "Shop", "100.01")));
        assertEquals(64, base.length());
        assertTrue(base.matches("[0-9a-f]{64}"));
    }

    @Test
    void rangeDefaultsToLast30DaysAndRejectsInvertedOrOversizedRanges() {
        var defaults = AggregationQueryService.range(null, null);
        assertEquals(30, java.time.temporal.ChronoUnit.DAYS.between(defaults[0], defaults[1]) + 1);
        assertThrows(za.co.capitec.aggregation.common.exception.InvalidRequestException.class,
                () -> AggregationQueryService.range(java.time.LocalDate.of(2026, 10, 2), java.time.LocalDate.of(2026, 10, 1)));
        assertThrows(za.co.capitec.aggregation.common.exception.InvalidRequestException.class,
                () -> AggregationQueryService.range(java.time.LocalDate.of(2020, 1, 1), java.time.LocalDate.of(2026, 10, 1)));
    }
}
