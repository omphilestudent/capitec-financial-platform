package za.co.capitec.caseservice.cases.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.caseservice.cases.dto.ActivityResponse;
import za.co.capitec.caseservice.cases.dto.CaseResponse;
import za.co.capitec.caseservice.cases.dto.CreateCaseRequest;
import za.co.capitec.caseservice.cases.dto.PageResponse;
import za.co.capitec.caseservice.cases.dto.UpdateCaseRequest;
import za.co.capitec.caseservice.cases.entity.CaseActivity;
import za.co.capitec.caseservice.cases.entity.CaseOrigin;
import za.co.capitec.caseservice.cases.entity.CasePriority;
import za.co.capitec.caseservice.cases.entity.CaseRecord;
import za.co.capitec.caseservice.cases.entity.CaseStatus;
import za.co.capitec.caseservice.cases.entity.CaseType;
import za.co.capitec.caseservice.cases.repository.CaseActivityRepository;
import za.co.capitec.caseservice.cases.repository.CaseRecordRepository;
import za.co.capitec.caseservice.common.exception.CaseNotFoundException;
import za.co.capitec.caseservice.common.exception.InvalidCaseStateException;
import za.co.capitec.caseservice.common.messaging.OutboxWriter;
import za.co.capitec.caseservice.messaging.event.CaseCreatedEvent;
import za.co.capitec.caseservice.messaging.event.CaseUpdatedEvent;
import za.co.capitec.caseservice.messaging.event.FraudDetectedEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CaseService {

    public static final String SYSTEM_ACTOR = "Fraud Detection";
    private static final int MAX_PAGE_SIZE = 100;

    private final CaseRecordRepository caseRepository;
    private final CaseActivityRepository activityRepository;
    private final OutboxWriter outboxWriter;

    public CaseService(CaseRecordRepository caseRepository,
                       CaseActivityRepository activityRepository,
                       OutboxWriter outboxWriter) {
        this.caseRepository = caseRepository;
        this.activityRepository = activityRepository;
        this.outboxWriter = outboxWriter;
    }

    /** A user opens a case for a real customer. */
    public CaseResponse createCase(CreateCaseRequest request, String actor) {
        CaseRecord record = new CaseRecord(
                UUID.randomUUID(),
                nextCaseNumber(),
                request.subject().trim(),
                blankToNull(request.description()),
                request.priority(),
                CaseOrigin.MANUAL,
                request.caseType(),
                request.customerId(),
                request.accountId(),
                request.transactionId(),
                null,
                blankToNull(request.owner()) == null ? actor : request.owner().trim()
        );
        caseRepository.save(record);
        recordCreated(record, actor, "Case opened manually");
        return CaseResponse.from(record);
    }

    /**
     * Opens a case for a fraud alert that needs investigation. Idempotent: a fraud
     * event that was already handled returns the existing case, so a duplicate Kafka
     * delivery never creates a second case.
     */
    public CaseResponse openCaseForFraud(FraudDetectedEvent fraud) {
        var existing = caseRepository.findBySourceFraudId(fraud.fraudId());
        if (existing.isPresent()) {
            return CaseResponse.from(existing.get());
        }

        CaseRecord record = new CaseRecord(
                UUID.randomUUID(),
                nextCaseNumber(),
                "Fraud alert " + fraud.fraudReference() + ": " + fraud.detectionRule(),
                fraud.description(),
                priorityFor(fraud.riskLevel()),
                CaseOrigin.FRAUD_DETECTION,
                CaseType.FRAUD_INVESTIGATION,
                fraud.customerId(),
                fraud.accountId(),
                fraud.transactionId(),
                fraud.fraudId(),
                SYSTEM_ACTOR
        );
        try {
            caseRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException duplicate) {
            // A concurrent delivery created the case first.
            return caseRepository.findBySourceFraudId(fraud.fraudId())
                    .map(CaseResponse::from)
                    .orElseThrow(() -> duplicate);
        }
        recordCreated(record, SYSTEM_ACTOR, "Opened from fraud alert " + fraud.fraudReference());
        return CaseResponse.from(record);
    }

    @Transactional(readOnly = true)
    public CaseResponse getCase(UUID id) {
        return CaseResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CaseResponse> listCases(CaseStatus status, CasePriority priority, UUID customerId,
                                                String query, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var result = caseRepository.findAll(
                filter(status, priority, customerId, query),
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        return new PageResponse<>(
                result.map(CaseResponse::from).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> getActivity(UUID id) {
        find(id);
        return activityRepository.findByCaseIdOrderByCreatedAtAsc(id).stream()
                .map(ActivityResponse::from)
                .toList();
    }

    public CaseResponse updateCase(UUID id, UpdateCaseRequest request, String actor) {
        CaseRecord record = find(id);
        List<CaseUpdatedEvent> events = new ArrayList<>();

        if (request.status() != null && request.status() != record.getStatus()) {
            CaseStatus from = record.getStatus();
            CaseStatus to = request.status();
            if (!from.canMoveTo(to)) {
                throw new InvalidCaseStateException(
                        "A case cannot move from " + from + " to " + to);
            }
            String resolution = blankToNull(request.resolutionDetails());
            if ((to == CaseStatus.RESOLVED || to == CaseStatus.CLOSED)
                    && resolution == null && blankToNull(record.getResolutionDetails()) == null) {
                throw new InvalidCaseStateException(
                        "Resolution details are required before a case is " + to.name().toLowerCase(Locale.ROOT));
            }
            record.changeStatus(to, resolution);
            activity(record, "STATUS_CHANGED", "Status changed from " + from + " to " + to, actor);
            events.add(statusEvent(record, from, to));
        } else if (blankToNull(request.resolutionDetails()) != null) {
            record.changeStatus(record.getStatus(), request.resolutionDetails().trim());
        }

        if (request.priority() != null && request.priority() != record.getPriority()) {
            activity(record, "PRIORITY_CHANGED",
                    "Priority changed from " + record.getPriority() + " to " + request.priority(), actor);
            record.changePriority(request.priority());
        }

        String investigator = blankToNull(request.assignedInvestigator());
        if (investigator != null && !investigator.equals(record.getAssignedInvestigator())) {
            activity(record, "ASSIGNED", "Assigned to " + investigator, actor);
            record.assign(investigator);
            events.add(assignmentEvent(record));
        }

        String owner = blankToNull(request.owner());
        if (owner != null && !owner.equals(record.getOwner())) {
            activity(record, "OWNER_CHANGED", "Owner changed to " + owner, actor);
            record.changeOwner(owner);
        }

        String description = blankToNull(request.description());
        if (description != null && !description.equals(record.getDescription())) {
            activity(record, "DESCRIPTION_UPDATED", "Description updated", actor);
            record.changeDescription(description);
        }

        caseRepository.save(record);
        events.forEach(e -> outboxWriter.write(e.eventId(), CaseUpdatedEvent.TOPIC,
                record.getId().toString(), CaseUpdatedEvent.EVENT_TYPE, e));
        return CaseResponse.from(record);
    }

    /* ---------- helpers ---------- */

    private CaseRecord find(UUID id) {
        return caseRepository.findById(id)
                .orElseThrow(() -> new CaseNotFoundException("Case not found: " + id));
    }

    private String nextCaseNumber() {
        return "CASE-" + caseRepository.nextCaseNumber();
    }

    private void recordCreated(CaseRecord record, String actor, String description) {
        activity(record, "CREATED", description, actor);
        outboxWriter.write(
                UUID.randomUUID(),
                CaseCreatedEvent.TOPIC,
                record.getId().toString(),
                CaseCreatedEvent.EVENT_TYPE,
                new CaseCreatedEvent(
                        UUID.randomUUID(), record.getId(), record.getCaseNumber(), record.getSubject(),
                        record.getPriority().name(), record.getOrigin().name(), record.getCustomerId(),
                        record.getAccountId(), record.getSourceFraudId(), Instant.now())
        );
    }

    private void activity(CaseRecord record, String type, String description, String actor) {
        activityRepository.save(new CaseActivity(record.getId(), type, description, actor));
    }

    private CaseUpdatedEvent statusEvent(CaseRecord record, CaseStatus from, CaseStatus to) {
        return new CaseUpdatedEvent(UUID.randomUUID(), record.getId(), record.getCaseNumber(),
                "STATUS_CHANGED", from.name(), to.name(), record.getAssignedInvestigator(), Instant.now());
    }

    private CaseUpdatedEvent assignmentEvent(CaseRecord record) {
        return new CaseUpdatedEvent(UUID.randomUUID(), record.getId(), record.getCaseNumber(),
                "ASSIGNED", null, record.getStatus().name(), record.getAssignedInvestigator(), Instant.now());
    }

    private static CasePriority priorityFor(String riskLevel) {
        return "CRITICAL".equals(riskLevel) ? CasePriority.CRITICAL : CasePriority.HIGH;
    }

    private static Specification<CaseRecord> filter(CaseStatus status, CasePriority priority,
                                                    UUID customerId, String query) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (customerId != null) predicates.add(cb.equal(root.get("customerId"), customerId));
            String q = blankToNull(query);
            if (q != null) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                List<jakarta.persistence.criteria.Predicate> any = new ArrayList<>();
                any.add(cb.like(cb.lower(root.get("caseNumber")), like));
                any.add(cb.like(cb.lower(root.get("subject")), like));
                // ID columns are UUIDs: match exactly when the search term is a full ID.
                UUID id = parseUuid(q);
                if (id != null) {
                    any.add(cb.equal(root.get("customerId"), id));
                    any.add(cb.equal(root.get("accountId"), id));
                    any.add(cb.equal(root.get("transactionId"), id));
                }
                predicates.add(cb.or(any.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
