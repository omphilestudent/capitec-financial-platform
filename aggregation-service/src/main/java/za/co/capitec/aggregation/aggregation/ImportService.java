package za.co.capitec.aggregation.aggregation;

import org.springframework.stereotype.Service;
import za.co.capitec.aggregation.account.AccountClient;
import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.SourceSystem;
import za.co.capitec.aggregation.source.FetchRequest;
import za.co.capitec.aggregation.source.SourceAdapter;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportService {

    public record ImportResult(String source, UUID accountId, int received, int counted, int notCounted,
                               int duplicatesSkipped, int failed, List<String> errors) {
    }

    private static final int MAX_COUNT = 2_000;

    private final Map<SourceSystem, SourceAdapter> adapters = new EnumMap<>(SourceSystem.class);
    private final AccountClient accounts;
    private final AggregationService aggregation;

    public ImportService(List<SourceAdapter> adapters, AccountClient accounts, AggregationService aggregation) {
        for (SourceAdapter adapter : adapters) {
            this.adapters.put(adapter.source(), adapter);
        }
        this.accounts = accounts;
        this.aggregation = aggregation;
    }

    public List<SourceSystem> sources() {
        return adapters.keySet().stream().filter(s -> s != SourceSystem.TRANSACTION_SERVICE).toList();
    }

    public ImportResult importFrom(SourceSystem source, UUID accountId, LocalDate from, LocalDate to, int count, long seed) {
        SourceAdapter adapter = adapters.get(source);
        if (adapter == null || source == SourceSystem.TRANSACTION_SERVICE) {
            throw new za.co.capitec.aggregation.common.exception.InvalidRequestException(
                    "Unsupported import source: " + source);
        }
        if (count < 1 || count > MAX_COUNT) {
            throw new za.co.capitec.aggregation.common.exception.InvalidRequestException(
                    "count must be between 1 and " + MAX_COUNT);
        }
        var account = accounts.require(accountId);
        List<Map<String, String>> raw = adapter.fetch(new FetchRequest(account.customerId(), accountId, from, to, count, seed));

        int counted = 0;
        int notCounted = 0;
        int duplicates = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();
        for (Map<String, String> record : raw) {
            try {
                NormalizedTransaction tx = adapter.normalize(record, account.customerId(), accountId);
                switch (aggregation.ingest(tx)) {
                    case COUNTED -> counted++;
                    case NOT_COUNTED -> notCounted++;
                    case DUPLICATE_SOURCE, DUPLICATE_CROSS_SOURCE -> duplicates++;
                }
            } catch (RuntimeException e) {
                failed++;
                if (errors.size() < 10) {
                    errors.add(e.getMessage());
                }
            }
        }
        return new ImportResult(source.name(), accountId, raw.size(), counted, notCounted, duplicates, failed, errors);
    }
}
