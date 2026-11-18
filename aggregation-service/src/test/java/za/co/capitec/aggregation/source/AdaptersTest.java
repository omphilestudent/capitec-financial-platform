package za.co.capitec.aggregation.source;

import org.junit.jupiter.api.Test;
import za.co.capitec.aggregation.categorisation.Categoriser;
import za.co.capitec.aggregation.domain.Category;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;
import za.co.capitec.aggregation.domain.SourceSystem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdaptersTest {

    private final MockTransactionGenerator generator = new MockTransactionGenerator();
    private final Categoriser categoriser = new Categoriser();
    private final BankFeedAdapter bank = new BankFeedAdapter(generator, categoriser);
    private final CardNetworkAdapter card = new CardNetworkAdapter(generator, categoriser);
    private final OpenBankingAdapter openBanking = new OpenBankingAdapter(generator, categoriser);
    private final UUID customer = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();

    private FetchRequest request(int count, long seed) {
        return new FetchRequest(customer, account, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), count, seed);
    }

    @Test
    void generatorIsDeterministicForTheSameSeedAndAccount() {
        List<MockSpec> a = generator.generate("BF", request(50, 7));
        List<MockSpec> b = generator.generate("BF", request(50, 7));
        List<MockSpec> c = generator.generate("BF", request(50, 8));
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void generatedDatesSpreadAcrossThePeriodAndStayInside() {
        List<MockSpec> specs = generator.generate("BF", request(300, 42));
        long distinctDays = specs.stream().map(s -> s.occurredAt().toLocalDate()).distinct().count();
        assertTrue(distinctDays > 20, "expected transactions across most of October, got " + distinctDays);
        assertTrue(specs.stream().allMatch(s -> !s.occurredAt().toLocalDate().isBefore(LocalDate.of(2026, 10, 1))
                && !s.occurredAt().toLocalDate().isAfter(LocalDate.of(2026, 10, 31))));
    }

    @Test
    void bankFeedRoundTripPreservesEverySpecField() {
        MockSpec spec = new MockSpec("BF-1", LocalDateTime.of(2026, 10, 5, 9, 30, 15), Direction.DEBIT,
                new BigDecimal("1250.50"), "Checkers Sixty60 groceries", "5411", "COMPLETED");
        NormalizedTransaction tx = bank.normalize(BankFeedAdapter.encode(spec), customer, account);

        assertEquals(SourceSystem.BANK_FEED, tx.source());
        assertEquals("BF-1", tx.externalId());
        assertEquals(Direction.DEBIT, tx.direction());
        assertEquals(new BigDecimal("1250.50"), tx.amount());
        assertEquals(LocalDateTime.of(2026, 10, 5, 9, 30, 15), tx.occurredAt());
        assertEquals(Category.GROCERIES, tx.category());
        assertEquals("COMPLETED", tx.status());
    }

    @Test
    void cardNetworkRoundTripUsesMccAndSouthAfricanTime() {
        MockSpec spec = new MockSpec("CN-1", LocalDateTime.of(2026, 10, 12, 18, 0, 0), Direction.DEBIT,
                new BigDecimal("640.00"), "Engen fuel", "5541", "PENDING");
        NormalizedTransaction tx = card.normalize(CardNetworkAdapter.encode(spec), customer, account);

        assertEquals(new BigDecimal("640.00"), tx.amount());
        assertEquals(LocalDateTime.of(2026, 10, 12, 18, 0, 0), tx.occurredAt());
        assertEquals(Category.FUEL, tx.category());
        assertEquals("PENDING", tx.status());
    }

    @Test
    void openBankingRoundTripMapsCreditAndBookingStatus() {
        MockSpec spec = new MockSpec("OB-1", LocalDateTime.of(2026, 10, 25, 7, 0, 0), Direction.CREDIT,
                new BigDecimal("38000.00"), "Salary payment ACME PAYROLL", null, "COMPLETED");
        NormalizedTransaction tx = openBanking.normalize(OpenBankingAdapter.encode(spec), customer, account);

        assertEquals(Direction.CREDIT, tx.direction());
        assertEquals(Category.SALARY, tx.category());
        assertEquals("COMPLETED", tx.status());
    }

    @Test
    void failedAndRejectedRecordsAreNormalisedAsFailed() {
        MockSpec spec = new MockSpec("OB-2", LocalDateTime.of(2026, 10, 2, 1, 0, 0), Direction.DEBIT,
                new BigDecimal("10.00"), "Card purchase Takealot", null, "FAILED");
        assertEquals("FAILED", openBanking.normalize(OpenBankingAdapter.encode(spec), customer, account).status());
    }

    @Test
    void missingRequiredFieldIsRejected() {
        Map<String, String> raw = new java.util.HashMap<>(BankFeedAdapter.encode(new MockSpec("BF-9",
                LocalDateTime.of(2026, 10, 1, 8, 0), Direction.CREDIT, BigDecimal.ONE, "x", null, "COMPLETED")));
        raw.remove("txnId");
        assertThrows(IllegalArgumentException.class, () -> bank.normalize(raw, customer, account));
    }

    @Test
    void categoriserUsesMccBeforeDescriptionAndDefaultsByDirection() {
        assertEquals(Category.ATM_WITHDRAWAL, categoriser.categorise("Anything", "6011", Direction.DEBIT));
        assertEquals(Category.LOAN_REPAYMENT, categoriser.categorise("Home loan repayment", null, Direction.DEBIT));
        assertEquals(Category.INCOME_OTHER, categoriser.categorise("Unknown credit", null, Direction.CREDIT));
        assertEquals(Category.OTHER, categoriser.categorise("Unknown debit", null, Direction.DEBIT));
    }
}
