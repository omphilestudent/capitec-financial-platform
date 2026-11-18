package za.co.capitec.statementservice.statement.pdf;

import org.springframework.stereotype.Component;
import za.co.capitec.statementservice.statement.client.TransactionView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class LedgerCalculator {

    public static final String FAILED = "FAILED";
    public static final String CREDIT = "CREDIT";
    public static final String DEBIT = "DEBIT";

    public Ledger calculate(List<TransactionView> ledgerEntries, LocalDate periodStart, LocalDate periodEnd) {
        if (periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("periodStart must be on or before periodEnd");
        }

        int excludedFailed = 0;
        BigDecimal opening = BigDecimal.ZERO;
        List<TransactionView> inPeriod = new java.util.ArrayList<>();

        for (TransactionView tx : ledgerEntries) {
            if (FAILED.equals(tx.status())) {
                excludedFailed++;
                continue;
            }
            LocalDate day = tx.createdAt().toLocalDate();
            if (day.isBefore(periodStart)) {
                opening = opening.add(signed(tx));
            } else if (!day.isAfter(periodEnd)) {
                inPeriod.add(tx);
            }
        }

        inPeriod.sort(Comparator.comparing(TransactionView::createdAt)
                .thenComparing(TransactionView::transactionReference));

        BigDecimal running = opening;
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        List<LedgerRow> rows = new java.util.ArrayList<>(inPeriod.size());

        for (TransactionView tx : inPeriod) {
            BigDecimal amount = tx.amount();
            BigDecimal credit = null;
            BigDecimal debit = null;
            if (CREDIT.equals(tx.type())) {
                credits = credits.add(amount);
                credit = amount;
                running = running.add(amount);
            } else if (DEBIT.equals(tx.type())) {
                debits = debits.add(amount);
                debit = amount;
                running = running.subtract(amount);
            } else {
                throw new IllegalArgumentException("Unknown transaction type: " + tx.type());
            }
            rows.add(new LedgerRow(tx.createdAt(), tx.transactionReference(), tx.description(), tx.type(),
                    debit, credit, running));
        }

        BigDecimal closing = running;
        if (opening.add(credits).subtract(debits).compareTo(closing) != 0) {
            throw new IllegalStateException("Statement does not reconcile: opening + credits - debits != closing");
        }

        return new Ledger(opening, closing, credits, debits, rows, excludedFailed);
    }

    private static BigDecimal signed(TransactionView tx) {
        return CREDIT.equals(tx.type()) ? tx.amount() : tx.amount().negate();
    }
}
