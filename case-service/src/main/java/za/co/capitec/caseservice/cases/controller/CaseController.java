package za.co.capitec.caseservice.cases.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import za.co.capitec.caseservice.cases.dto.ActivityResponse;
import za.co.capitec.caseservice.cases.dto.CaseResponse;
import za.co.capitec.caseservice.cases.dto.CreateCaseRequest;
import za.co.capitec.caseservice.cases.dto.PageResponse;
import za.co.capitec.caseservice.cases.dto.UpdateCaseRequest;
import za.co.capitec.caseservice.cases.entity.CasePriority;
import za.co.capitec.caseservice.cases.entity.CaseStatus;
import za.co.capitec.caseservice.cases.service.CaseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cases")
public class CaseController {

    /** Name recorded in the case history. Authentication is not implemented, so the caller states it. */
    static final String ACTOR_HEADER = "X-Actor";
    static final String DEFAULT_ACTOR = "Operator";

    private final CaseService caseService;

    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CaseResponse createCase(@Valid @RequestBody CreateCaseRequest request,
                                   @RequestHeader(value = ACTOR_HEADER, required = false) String actor) {
        return caseService.createCase(request, actorOrDefault(actor));
    }

    @GetMapping
    public PageResponse<CaseResponse> listCases(
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) CasePriority priority,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return caseService.listCases(status, priority, customerId, q, page, size);
    }

    @GetMapping("/{id}")
    public CaseResponse getCase(@PathVariable UUID id) {
        return caseService.getCase(id);
    }

    @PatchMapping("/{id}")
    public CaseResponse updateCase(@PathVariable UUID id,
                                   @Valid @RequestBody UpdateCaseRequest request,
                                   @RequestHeader(value = ACTOR_HEADER, required = false) String actor) {
        return caseService.updateCase(id, request, actorOrDefault(actor));
    }

    @GetMapping("/{id}/activity")
    public List<ActivityResponse> getActivity(@PathVariable UUID id) {
        return caseService.getActivity(id);
    }

    private static String actorOrDefault(String actor) {
        return actor == null || actor.isBlank() ? DEFAULT_ACTOR : actor.trim();
    }
}
