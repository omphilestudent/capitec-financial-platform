package za.co.capitec.frauddetection.detection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class HistoricalReevaluationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HistoricalReevaluationRunner.class);

    private final HistoricalReevaluationService service;
    private final HistoricalReevaluationService.Mode mode;

    public HistoricalReevaluationRunner(HistoricalReevaluationService service,
                                        @Value("${fraud.reevaluation.mode:OFF}") String mode) {
        this.service = service;
        this.mode = HistoricalReevaluationService.Mode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (mode) {
            case OFF -> { }
            case DRY_RUN -> {
                var summary = service.dryRun();
                log.warn("HISTORICAL_REEVALUATION dry-run rule={} candidates={} wouldSupersede={} wouldConfirm={} (nothing written)",
                        HistoricalReevaluationService.RULE_VERSION, summary.candidates(), summary.superseded(), summary.confirmed());
            }
            case APPLY -> {
                int supersededCount = 0;
                int confirmedCount = 0;
                int skipped = 0;
                var candidates = service.candidates();
                for (var candidate : candidates) {
                    boolean written = service.apply(candidate);
                    if (!written) {
                        skipped++;
                        continue;
                    }
                    if (service.decide(candidate).detected()) {
                        confirmedCount++;
                    } else {
                        supersededCount++;
                    }
                }
                log.warn("HISTORICAL_REEVALUATION applied rule={} candidates={} superseded={} confirmed={} skippedAlreadyRevised={}",
                        HistoricalReevaluationService.RULE_VERSION, candidates.size(), supersededCount, confirmedCount, skipped);
            }
        }
    }
}
