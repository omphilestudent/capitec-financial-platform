package za.co.capitec.frauddetection.detection.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.frauddetection.common.exception.FraudNotFoundException;
import za.co.capitec.frauddetection.common.exception.InvalidFraudStateException;
import za.co.capitec.frauddetection.common.messaging.OutboxWriter;
import za.co.capitec.frauddetection.config.FraudRulesProperties;
import za.co.capitec.frauddetection.detection.dto.FraudResponse;
import za.co.capitec.frauddetection.detection.dto.PageResponse;
import za.co.capitec.frauddetection.detection.entity.FraudDetection;
import za.co.capitec.frauddetection.detection.entity.InvestigationStatus;
import za.co.capitec.frauddetection.detection.entity.PendingCaseLink;
import za.co.capitec.frauddetection.detection.entity.ProcessedTransaction;
import za.co.capitec.frauddetection.detection.repository.FraudDetectionRepository;
import za.co.capitec.frauddetection.detection.repository.PendingCaseLinkRepository;
import za.co.capitec.frauddetection.detection.repository.ProcessedEventStore;
import za.co.capitec.frauddetection.detection.repository.EvaluationPublicationRepository;
import za.co.capitec.frauddetection.messaging.event.FraudEvaluatedEvent;
import za.co.capitec.frauddetection.detection.repository.ProcessedTransactionRepository;
import za.co.capitec.frauddetection.detection.rules.Assessment;
import za.co.capitec.frauddetection.detection.rules.FraudRuleEngine;
import za.co.capitec.frauddetection.detection.rules.RiskLevel;
import za.co.capitec.frauddetection.detection.rules.RuleResult;
import za.co.capitec.frauddetection.messaging.event.CaseCreatedEvent;
import za.co.capitec.frauddetection.messaging.event.FraudDetectedEvent;
import za.co.capitec.frauddetection.messaging.event.TransactionCreatedEvent;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class FraudDetectionService {

    public enum Outcome { DETECTED, NOT_DETECTED, DUPLICATE, ACCOUNT_NOT_FOUND }

    public static final String FRAUD_TYPE = "TRANSACTION_RISK";
    public static final String DETECTION_SOURCE = "RULE_ENGINE";

    private static final int MAX_PAGE_SIZE = 100;

    private final FraudRuleEngine ruleEngine;
    private final AccountClient accountClient;
    private final FraudDetectionRepository fraudRepository;
    private final ProcessedTransactionRepository processedRepository;
    private final PendingCaseLinkRepository pendingLinkRepository;
    private final OutboxWriter outboxWriter;
    private final FraudRulesProperties rules;
    private final ProcessedEventStore processedEventStore;
    private final EvaluationPublicationRepository evaluationPublications;

    public FraudDetectionService(FraudRuleEngine ruleEngine,
                                 AccountClient accountClient,
                                 FraudDetectionRepository fraudRepository,
                                 ProcessedTransactionRepository processedRepository,
                                 PendingCaseLinkRepository pendingLinkRepository,
                                 OutboxWriter outboxWriter,
                                 FraudRulesProperties rules,
                                 ProcessedEventStore processedEventStore,
                                 EvaluationPublicationRepository evaluationPublications) {
        this.ruleEngine = ruleEngine;
        this.accountClient = accountClient;
        this.fraudRepository = fraudRepository;
        this.processedRepository = processedRepository;
        this.pendingLinkRepository = pendingLinkRepository;
        this.outboxWriter = outboxWriter;
        this.rules = rules;
        this.processedEventStore = processedEventStore;
        this.evaluationPublications = evaluationPublications;
    }

    public Outcome processTransaction(TransactionCreatedEvent event) {
        return processTransaction(event, "unknown");
    }

    public Outcome processTransaction(TransactionCreatedEvent event, String sourceTopic) {
        Outcome outcome = evaluate(event, sourceTopic);
        if (outcome != Outcome.DUPLICATE) {
            publishEvaluation(event, outcome);
        }
        return outcome;
    }

    private void publishEvaluation(TransactionCreatedEvent event, Outcome outcome) {
        if (!evaluationPublications.claim(event.transactionId(), outcome.name())) {
            return;
        }
        outboxWriter.write(UUID.randomUUID(), FraudEvaluatedEvent.TOPIC, event.transactionId().toString(),
                FraudEvaluatedEvent.EVENT_TYPE,
                new FraudEvaluatedEvent(UUID.randomUUID(), event.transactionId(), event.accountId(),
                        outcome.name(), LocalDateTime.now()));
    }

    private Outcome evaluate(TransactionCreatedEvent event, String sourceTopic) {
        UUID dedupeKey = event.eventId() != null ? event.eventId() : event.transactionId();
        if (!processedEventStore.claim(dedupeKey, sourceTopic, event.transactionId())) {
            return Outcome.DUPLICATE;
        }
        if (processedRepository.existsById(event.transactionId())) {
            return Outcome.DUPLICATE;
        }

        LocalDateTime windowStart = ruleEngine.velocityWindowStart(event.createdAt());
        long priorInWindow = processedRepository
                .countByAccountIdAndTransactionCreatedAtBetween(event.accountId(), windowStart, event.createdAt());
        Assessment assessment = ruleEngine.evaluate(event.amount(), priorInWindow + 1, event.currency(), event.description());

        if (!assessment.detected()) {
            processedRepository.save(processed(event, "NOT_DETECTED"));
            return Outcome.NOT_DETECTED;
        }

        Optional<AccountClient.AccountView> account = accountClient.findAccount(event.accountId());
        if (account.isEmpty()) {
            processedRepository.save(processed(event, "ACCOUNT_NOT_FOUND"));
            return Outcome.ACCOUNT_NOT_FOUND;
        }

        processedRepository.save(processed(event, "DETECTED"));

        RuleResult primary = assessment.primaryRule();
        FraudDetection detection = new FraudDetection(
                UUID.randomUUID(),
                fraudRepository.nextFraudReference(),
                account.get().customerId(),
                event.accountId(),
                event.transactionId(),
                FRAUD_TYPE,
                categoryFor(primary.ruleId()),
                assessment.riskScore(),
                assessment.riskLevel(),
                primary.ruleId(),
                assessment.triggered().stream().map(RuleResult::explanation).collect(Collectors.joining("; ")),
                DETECTION_SOURCE
        );
        fraudRepository.saveAndFlush(detection);

        pendingLinkRepository.findById(detection.getId()).ifPresent(link -> {
            detection.linkToCase(link.getCaseId());
            pendingLinkRepository.delete(link);
        });

        outboxWriter.write(
                UUID.randomUUID(),
                FraudDetectedEvent.TOPIC,
                detection.getId().toString(),
                FraudDetectedEvent.EVENT_TYPE,
                new FraudDetectedEvent(
                        UUID.randomUUID(),
                        detection.getId(),
                        detection.getFraudReference(),
                        detection.getCustomerId(),
                        detection.getAccountId(),
                        detection.getTransactionId(),
                        detection.getRiskScore(),
                        detection.getRiskLevel().name(),
                        detection.getDetectionRule(),
                        detection.getDetectionDescription(),
                        detection.getDetectedAt(),
                        detection.getRiskLevel().requiresInvestigation()
                )
        );
        return Outcome.DETECTED;
    }

    public void linkCase(CaseCreatedEvent event) {
        UUID fraudId = event.sourceFraudId();
        if (fraudId == null) {
            return;
        }
        Optional<FraudDetection> fraud = fraudRepository.findById(fraudId);
        if (fraud.isPresent()) {
            FraudDetection detection = fraud.get();
            if (detection.getCaseId() == null) {
                detection.linkToCase(event.caseId());
            }
            return;
        }
        if (!pendingLinkRepository.existsById(fraudId)) {
            pendingLinkRepository.save(new PendingCaseLink(fraudId, event.caseId()));
        }
    }

    @Transactional(readOnly = true)
    public FraudResponse getFraud(UUID id) {
        return fraudRepository.findById(id)
                .map(FraudResponse::from)
                .orElseThrow(() -> new FraudNotFoundException("Fraud record not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<FraudResponse> getFraudForCase(UUID caseId) {
        return fraudRepository.findByCaseIdOrderByDetectedAtDesc(caseId).stream()
                .map(FraudResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FraudResponse getFraudForTransaction(UUID transactionId) {
        return fraudRepository.findByTransactionId(transactionId)
                .map(FraudResponse::from)
                .orElseThrow(() -> new FraudNotFoundException("No fraud record for transaction " + transactionId));
    }

    @Transactional(readOnly = true)
    public PageResponse<FraudResponse> search(RiskLevel riskLevel, InvestigationStatus status, UUID customerId,
                                              LocalDate from, LocalDate to, String query,
                                              String sortBy, String direction, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortField = switch (sortBy == null ? "" : sortBy) {
            case "riskScore" -> "riskScore";
            case "riskLevel" -> "riskLevel";
            case "fraudReference" -> "fraudReference";
            default -> "detectedAt";
        };

        var result = fraudRepository.findAll(
                filter(riskLevel, status, customerId, from, to, query),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(dir, sortField).and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new PageResponse<>(
                result.map(FraudResponse::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    public FraudResponse updateStatus(UUID id, InvestigationStatus next) {
        FraudDetection detection = fraudRepository.findById(id)
                .orElseThrow(() -> new FraudNotFoundException("Fraud record not found: " + id));
        if (detection.getInvestigationStatus() == next) {
            return FraudResponse.from(detection);
        }
        if (!detection.getInvestigationStatus().allowedNext().contains(next)) {
            throw new InvalidFraudStateException("An investigation cannot move from "
                    + detection.getInvestigationStatus() + " to " + next);
        }
        detection.changeStatus(next);
        return FraudResponse.from(detection);
    }

    public List<RuleSummary> activeRules() {
        return List.of(
                new RuleSummary(FraudRuleEngine.HIGH_AMOUNT, rules.highAmount().enabled(),
                        "Amount at or above " + rules.highAmount().threshold().toPlainString(), rules.highAmount().weight()),
                new RuleSummary(FraudRuleEngine.VELOCITY, rules.velocity().enabled(),
                        "More than " + rules.velocity().maxTransactions() + " transactions for one account in "
                                + rules.velocity().windowMinutes() + " minutes", rules.velocity().weight())
        );
    }

    public record RuleSummary(String ruleId, boolean enabled, String condition, int weight) {}

    private static ProcessedTransaction processed(TransactionCreatedEvent e, String outcome) {
        return new ProcessedTransaction(e.transactionId(), e.accountId(), e.amount(), e.currency(),
                e.createdAt(), outcome);
    }

    private static String categoryFor(String ruleId) {
        return switch (ruleId) {
            case FraudRuleEngine.HIGH_AMOUNT -> "HIGH_VALUE_TRANSACTION";
            case FraudRuleEngine.VELOCITY -> "UNUSUAL_ACTIVITY_PATTERN";
            case FraudRuleEngine.CROSS_BORDER -> "CROSS_BORDER_TRANSACTION";
            case FraudRuleEngine.LARGE_EFT -> "LARGE_EFT_PAYMENT";
            default -> "OTHER";
        };
    }

    static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static Specification<FraudDetection> filter(RiskLevel riskLevel, InvestigationStatus status,
                                                        UUID customerId, LocalDate from, LocalDate to, String query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (riskLevel != null) predicates.add(cb.equal(root.get("riskLevel"), riskLevel));
            if (status != null) predicates.add(cb.equal(root.get("investigationStatus"), status));
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            if (from != null) {
                Instant start = from.atStartOfDay(ZoneId.systemDefault()).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("detectedAt"), LocalDateTime.ofInstant(start, ZoneId.systemDefault())));
            }
            if (to != null) {
                LocalDateTime end = to.plusDays(1).atStartOfDay();
                predicates.add(cb.lessThan(root.get("detectedAt"), end));
            }
            if (query != null && !query.isBlank()) {
                String q = query.trim();
                List<Predicate> any = new ArrayList<>();
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                any.add(cb.like(cb.lower(root.get("fraudReference")), like));
                any.add(cb.like(cb.lower(root.get("detectionRule")), like));

                UUID id = parseUuid(q);
                if (id != null) {
                    any.add(cb.equal(root.get("transactionId"), id));
                    any.add(cb.equal(root.get("accountId"), id));
                    any.add(cb.equal(root.get("customerId"), id));
                    any.add(cb.equal(root.get("caseId"), id));
                }
                predicates.add(cb.or(any.toArray(new Predicate[0])));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
