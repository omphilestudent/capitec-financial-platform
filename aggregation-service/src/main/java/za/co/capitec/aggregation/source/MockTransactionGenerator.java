package za.co.capitec.aggregation.source;

import org.springframework.stereotype.Component;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class MockTransactionGenerator {

    private record Template(String description, String mcc, Direction direction, int minCents, int maxCents, int weight) {
    }

    private static final List<Template> TEMPLATES = List.of(
            new Template("Salary payment ACME PAYROLL", null, Direction.CREDIT, 900_000, 3_800_000, 2),
            new Template("EFT transfer from savings", null, Direction.CREDIT, 50_000, 500_000, 4),
            new Template("Interest credit", null, Direction.CREDIT, 500, 15_000, 2),
            new Template("Refund online order", null, Direction.CREDIT, 10_000, 90_000, 2),
            new Template("Checkers Sixty60 groceries", "5411", Direction.DEBIT, 15_000, 140_000, 9),
            new Template("Engen fuel", "5541", Direction.DEBIT, 40_000, 110_000, 6),
            new Template("City Power electricity", "4900", Direction.DEBIT, 30_000, 240_000, 3),
            new Template("Home loan repayment", null, Direction.DEBIT, 250_000, 900_000, 2),
            new Template("ATM cash withdrawal", "6011", Direction.DEBIT, 20_000, 200_000, 4),
            new Template("Card purchase Takealot", "5999", Direction.DEBIT, 15_000, 300_000, 7),
            new Template("Supplier invoice payment", null, Direction.DEBIT, 100_000, 2_000_000, 2),
            new Template("EFT to landlord", null, Direction.DEBIT, 400_000, 900_000, 2)
    );

    private static final int TOTAL_WEIGHT = TEMPLATES.stream().mapToInt(Template::weight).sum();

    public List<MockSpec> generate(String sourcePrefix, FetchRequest request) {
        if (request.to().isBefore(request.from())) {
            throw new IllegalArgumentException("'from' must be on or before 'to'");
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(request.from(), request.to()) + 1;
        long seconds = days * 86_400L;
        Random random = new Random(request.seed() ^ request.accountId().getMostSignificantBits());
        List<MockSpec> specs = new ArrayList<>(request.count());

        for (int index = 0; index < request.count(); index++) {
            Template template = pick(random);
            LocalDateTime occurredAt = request.from().atStartOfDay()
                    .plus(Duration.ofSeconds((long) (random.nextDouble() * seconds)));
            BigDecimal amount = BigDecimal.valueOf(
                    template.minCents() + random.nextInt(template.maxCents() - template.minCents() + 1), 2)
                    .setScale(2, RoundingMode.HALF_UP);
            int roll = random.nextInt(100);
            String status = roll < 90 ? "COMPLETED" : roll < 95 ? "PENDING" : "FAILED";
            String externalId = sourcePrefix + "-" + Long.toHexString(request.seed()) + "-"
                    + request.accountId().toString().substring(0, 8) + "-" + index;
            specs.add(new MockSpec(externalId, occurredAt, template.direction(), amount, template.description(),
                    template.mcc(), status));
        }
        return specs;
    }

    private static Template pick(Random random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Template template : TEMPLATES) {
            roll -= template.weight();
            if (roll < 0) {
                return template;
            }
        }
        return TEMPLATES.get(0);
    }

    static LocalDate day(LocalDateTime value) {
        return value.toLocalDate();
    }
}
