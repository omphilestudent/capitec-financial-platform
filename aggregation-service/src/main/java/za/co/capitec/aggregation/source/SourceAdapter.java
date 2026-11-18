package za.co.capitec.aggregation.source;

import za.co.capitec.aggregation.domain.NormalizedTransaction;
import za.co.capitec.aggregation.domain.SourceSystem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface SourceAdapter {

    SourceSystem source();

    List<Map<String, String>> fetch(FetchRequest request);

    NormalizedTransaction normalize(Map<String, String> raw, UUID customerId, UUID accountId);
}
