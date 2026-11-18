package za.co.capitec.frauddetection.detection.controller;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import za.co.capitec.frauddetection.detection.dto.FraudResponse;
import za.co.capitec.frauddetection.detection.dto.PageResponse;
import za.co.capitec.frauddetection.detection.dto.UpdateFraudStatusRequest;
import za.co.capitec.frauddetection.detection.entity.InvestigationStatus;
import za.co.capitec.frauddetection.detection.rules.RiskLevel;
import za.co.capitec.frauddetection.detection.service.FraudDetectionService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
public class FraudController {

    private final FraudDetectionService fraudDetectionService;

    public FraudController(FraudDetectionService fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    @GetMapping
    public PageResponse<FraudResponse> search(
            @RequestParam(required = false) RiskLevel riskLevel,
            @RequestParam(required = false) InvestigationStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return fraudDetectionService.search(riskLevel, status, customerId, from, to, q,
                sortBy, direction, page, size);
    }

    @GetMapping("/{id}")
    public FraudResponse getFraud(@PathVariable UUID id) {
        return fraudDetectionService.getFraud(id);
    }

    @PatchMapping("/{id}/status")
    public FraudResponse updateStatus(@PathVariable UUID id,
                                      @Valid @RequestBody UpdateFraudStatusRequest request) {
        return fraudDetectionService.updateStatus(id, request.investigationStatus());
    }

    @GetMapping("/case/{caseId}")
    public List<FraudResponse> getForCase(@PathVariable UUID caseId) {
        return fraudDetectionService.getFraudForCase(caseId);
    }

    @GetMapping("/transaction/{transactionId}")
    public FraudResponse getForTransaction(@PathVariable UUID transactionId) {
        return fraudDetectionService.getFraudForTransaction(transactionId);
    }

    /** The rules in force, read from configuration. */
    @GetMapping("/rules")
    public List<FraudDetectionService.RuleSummary> rules() {
        return fraudDetectionService.activeRules();
    }
}
