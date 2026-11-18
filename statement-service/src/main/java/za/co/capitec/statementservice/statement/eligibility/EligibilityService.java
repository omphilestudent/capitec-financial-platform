package za.co.capitec.statementservice.statement.eligibility;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.statementservice.statement.client.TransactionView;
import za.co.capitec.statementservice.statement.integration.LedgerRepository;
import za.co.capitec.statementservice.statement.integration.LedgerRepository.LedgerRow;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EligibilityService {

    public enum Status { ELIGIBLE, FLAGGED, PENDING_REVIEW }

    public record Entry(LedgerRow row, Status status) {
    }

    public record Summary(int eligibleInPeriod, int excludedInPeriod) {
    }

    private final LedgerRepository ledger;

    public EligibilityService(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    public List<Entry> load(UUID customerId, UUID accountId, LocalDate upToInclusive) {
        List<LedgerRow> rows = ledger.entriesForAccount(customerId, accountId,
                upToInclusive.plusDays(1).atStartOfDay());
        List<Entry> entries = new ArrayList<>(rows.size());
        for (LedgerRow row : rows) {
            entries.add(new Entry(row, classify(row)));
        }
        return entries;
    }

    public static Status classify(LedgerRow row) {
        if (row.flagged() || "DETECTED".equals(row.decisionOutcome())) {
            return Status.FLAGGED;
        }
        if ("NOT_DETECTED".equals(row.decisionOutcome())) {
            return Status.ELIGIBLE;
        }
        return Status.PENDING_REVIEW;
    }

    public static Summary summarise(List<Entry> entries, LocalDate start, LocalDate end) {
        int eligible = 0;
        int excluded = 0;
        for (Entry entry : entries) {
            LocalDate day = entry.row().occurredAt().toLocalDate();
            if (day.isBefore(start) || day.isAfter(end)) {
                continue;
            }
            if (entry.status() == Status.ELIGIBLE) {
                eligible++;
            } else {
                excluded++;
            }
        }
        return new Summary(eligible, excluded);
    }

    public static List<TransactionView> toLedgerViews(List<Entry> entries) {
        return entries.stream()
                .filter(e -> e.status() == Status.ELIGIBLE)
                .map(e -> new TransactionView(e.row().aggregatedTransactionId(), e.row().accountId(),
                        e.row().externalId(), e.row().direction(), e.row().amount(), e.row().currency(),
                        e.row().description(), e.row().status(), e.row().occurredAt()))
                .toList();
    }
}
