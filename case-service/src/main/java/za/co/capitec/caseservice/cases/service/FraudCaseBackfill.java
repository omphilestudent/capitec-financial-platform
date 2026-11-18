package za.co.capitec.caseservice.cases.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import za.co.capitec.caseservice.messaging.event.FraudDetectedEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class FraudCaseBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FraudCaseBackfill.class);
    private static final int PAGE_SIZE = 100;

    public record FraudRow(UUID id, String fraudReference, UUID caseId, UUID customerId, UUID accountId,
                           UUID transactionId, int riskScore, String riskLevel, String detectionRule,
                           String detectionDescription, LocalDateTime detectedAt) {
    }

    public record FraudPage(List<FraudRow> content, int page, int totalPages) {
    }

    private final CaseService caseService;
    private final RestClient restClient;
    private final boolean enabled;

    public FraudCaseBackfill(CaseService caseService,
                             @Value("${fraud.service.url:http://localhost:8085}") String fraudUrl,
                             @Value("${CASE_BACKFILL_FRAUD_CASES_ENABLED:false}") boolean enabled) {
        this.caseService = caseService;
        this.restClient = RestClient.builder().baseUrl(fraudUrl).build();
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        int opened = 0;
        int alreadyLinked = 0;
        int page = 0;
        int totalPages;
        do {
            final int currentPage = page;
            FraudPage result = restClient.get()
                    .uri(uri -> uri.path("/api/v1/fraud").queryParam("page", currentPage).queryParam("size", PAGE_SIZE).build())
                    .retrieve()
                    .body(FraudPage.class);
            if (result == null) {
                break;
            }
            for (FraudRow row : result.content()) {
                if (row.caseId() != null) {
                    alreadyLinked++;
                    continue;
                }
                caseService.openCaseForFraud(new FraudDetectedEvent(
                        UUID.randomUUID(), row.id(), row.fraudReference(), row.customerId(), row.accountId(),
                        row.transactionId(), row.riskScore(), row.riskLevel(), row.detectionRule(),
                        row.detectionDescription(), row.detectedAt(), true));
                opened++;
            }
            totalPages = result.totalPages();
            page++;
        } while (page < totalPages);
        log.info("FRAUD_CASE_BACKFILL opened={} alreadyLinked={}", opened, alreadyLinked);
    }
}
