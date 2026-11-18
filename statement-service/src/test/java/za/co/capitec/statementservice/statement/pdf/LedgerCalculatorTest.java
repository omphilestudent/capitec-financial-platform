package za.co.capitec.statementservice.statement.pdf;

import org.junit.jupiter.api.Test;
import za.co.capitec.statementservice.statement.client.TransactionView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LedgerCalculatorTest {

    private final LedgerCalculator calculator = new LedgerCalculator();
    private final UUID account = UUID.randomUUID();

    private TransactionView tx(String type, String amount, String status, String when, String ref) {
        return new TransactionView(UUID.randomUUID(), account, ref, type, new BigDecimal(amount), "ZAR",
                "desc " + ref, status, LocalDateTime.parse(when));
    }

    @Test
    void openingBalanceIsEverythingPostedBeforeThePeriodStart() {
        List<TransactionView> entries = List.of(
                tx("CREDIT", "5000.00", "COMPLETED", "2026-09-01T09:00:00", "A"),
                tx("DEBIT", "1200.00", "PENDING", "2026-09-20T09:00:00", "B"),
                tx("CREDIT", "300.00", "COMPLETED", "2026-10-05T09:00:00", "C"),
                tx("DEBIT", "50.00", "COMPLETED", "2026-10-30T23:59:59", "D"));

        Ledger ledger = calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(new BigDecimal("3800.00"), ledger.openingBalance());
        assertEquals(new BigDecimal("300.00"), ledger.totalCredits());
        assertEquals(new BigDecimal("50.00"), ledger.totalDebits());
        assertEquals(new BigDecimal("4050.00"), ledger.closingBalance());
    }

    @Test
    void runningBalanceIsOrderedAndReconcilesWithClosing() {
        List<TransactionView> entries = List.of(
                tx("DEBIT", "100.00", "COMPLETED", "2026-10-03T10:00:00", "Z"),
                tx("CREDIT", "250.00", "COMPLETED", "2026-10-02T10:00:00", "Y"),
                tx("DEBIT", "25.50", "COMPLETED", "2026-10-03T08:00:00", "X"));

        Ledger ledger = calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(3, ledger.rows().size());
        assertEquals("Y", ledger.rows().get(0).reference());
        assertEquals("X", ledger.rows().get(1).reference());
        assertEquals("Z", ledger.rows().get(2).reference());
        assertEquals(new BigDecimal("250.00"), ledger.rows().get(0).runningBalance());
        assertEquals(new BigDecimal("224.50"), ledger.rows().get(1).runningBalance());
        assertEquals(new BigDecimal("124.50"), ledger.rows().get(2).runningBalance());
        assertEquals(ledger.closingBalance(), ledger.rows().get(2).runningBalance());
    }

    @Test
    void failedTransactionsAreExcludedAndCounted() {
        List<TransactionView> entries = List.of(
                tx("CREDIT", "1000.00", "COMPLETED", "2026-10-02T10:00:00", "A"),
                tx("DEBIT", "999.00", "FAILED", "2026-10-03T10:00:00", "B"));

        Ledger ledger = calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(1, ledger.excludedFailedCount());
        assertEquals(1, ledger.rows().size());
        assertEquals(new BigDecimal("1000.00"), ledger.closingBalance());
    }

    @Test
    void periodBoundariesAreInclusiveOfWholeDays() {
        List<TransactionView> entries = List.of(
                tx("CREDIT", "10.00", "COMPLETED", "2026-10-01T00:00:00", "START"),
                tx("CREDIT", "20.00", "COMPLETED", "2026-10-31T23:59:59", "END"),
                tx("CREDIT", "40.00", "COMPLETED", "2026-11-01T00:00:00", "AFTER"));

        Ledger ledger = calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertEquals(BigDecimal.ZERO.setScale(2), ledger.openingBalance().setScale(2));
        assertEquals(2, ledger.rows().size());
        assertEquals(new BigDecimal("30.00"), ledger.closingBalance());
    }

    @Test
    void emptyPeriodYieldsOpeningEqualToClosing() {
        List<TransactionView> entries = List.of(tx("CREDIT", "700.00", "COMPLETED", "2026-08-01T10:00:00", "OLD"));

        Ledger ledger = calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));

        assertTrue(ledger.rows().isEmpty());
        assertEquals(new BigDecimal("700.00"), ledger.openingBalance());
        assertEquals(new BigDecimal("700.00"), ledger.closingBalance());
    }

    @Test
    void invertedPeriodIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(List.of(), LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1)));
    }

    @Test
    void unknownTransactionTypeIsRejected() {
        List<TransactionView> entries = List.of(tx("TRANSFER", "5.00", "COMPLETED", "2026-10-02T10:00:00", "T"));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(entries, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));
    }
}
