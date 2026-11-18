
package za.co.capitec.statementservice.statement.eligibility;

import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.statement.integration.LedgerRepository.LedgerRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EligibilityServiceTest {

    private LedgerRow row(String outcome, boolean flagged, String when) {
        return new LedgerRow(UUID.randomUUID(), UUID.randomUUID(), "EXT", "BANK_FEED", UUID.randomUUID(), UUID.randomUUID(),
                "DEBIT", new BigDecimal("10.00"), "ZAR", "COMPLETED", "OTHER", "d", LocalDateTime.parse(when), flagged, outcome);
    }

    @Test
    void notDetectedIsEligibleAndAnyFlagOrDetectionIsFlagged() {
        assertEquals(EligibilityService.Status.ELIGIBLE, EligibilityService.classify(row("NOT_DETECTED", false, "2026-10-01T10:00:00")));
        assertEquals(EligibilityService.Status.FLAGGED, EligibilityService.classify(row("NOT_DETECTED", true, "2026-10-01T10:00:00")));
        assertEquals(EligibilityService.Status.FLAGGED, EligibilityService.classify(row("DETECTED", false, "2026-10-01T10:00:00")));
    }

    @Test
    void missingOrUnknownDecisionIsNeverTreatedAsSafe() {
        assertEquals(EligibilityService.Status.PENDING_REVIEW, EligibilityService.classify(row(null, false, "2026-10-01T10:00:00")));
        assertEquals(EligibilityService.Status.PENDING_REVIEW, EligibilityService.classify(row("ACCOUNT_NOT_FOUND", false, "2026-10-01T10:00:00")));
    }

    @Test
    void summaryCountsOnlyRowsInsideThePeriod() {
        var entries = List.of(
                new EligibilityService.Entry(row("NOT_DETECTED", false, "2026-10-01T10:00:00"), EligibilityService.Status.ELIGIBLE),
                new EligibilityService.Entry(row("DETECTED", true, "2026-10-02T10:00:00"), EligibilityService.Status.FLAGGED),
                new EligibilityService.Entry(row(null, false, "2026-09-30T10:00:00"), EligibilityService.Status.PENDING_REVIEW));

        var summary = EligibilityService.summarise(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(1, summary.eligibleInPeriod());
        assertEquals(1, summary.excludedInPeriod());
    }

    @Test
    void ledgerViewsNeverIncludeFlaggedTransactions() {
        var entries = List.of(
                new EligibilityService.Entry(row("NOT_DETECTED", false, "2026-10-01T10:00:00"), EligibilityService.Status.ELIGIBLE),
                new EligibilityService.Entry(row("DETECTED", true, "2026-10-02T10:00:00"), EligibilityService.Status.FLAGGED));

        assertEquals(1, EligibilityService.toLedgerViews(entries).size());
    }
}
