package za.co.capitec.statementservice.statement.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.service.DownloadService;
import za.co.capitec.statementservice.statement.service.StatementService;
import za.co.capitec.statementservice.statement.web.dto.DownloadLinkResponse;
import za.co.capitec.statementservice.statement.web.dto.EligibilityResponse;
import za.co.capitec.statementservice.statement.web.dto.GenerateStatementRequest;
import za.co.capitec.statementservice.statement.web.dto.PageResponse;
import za.co.capitec.statementservice.statement.web.dto.StatementResponse;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {

    private final StatementService statements;
    private final DownloadService downloads;

    public StatementController(StatementService statements, DownloadService downloads) {
        this.statements = statements;
        this.downloads = downloads;
    }

    @PostMapping("/generate")
    public ResponseEntity<StatementResponse> generate(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody GenerateStatementRequest request) {
        StatementService.GenerateResult result = statements.generate(CurrentUser.from(jwt), request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.statement());
    }

    @GetMapping("/accounts/{accountId}")
    public PageResponse<StatementResponse> forAccount(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID accountId,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return statements.listForAccount(CurrentUser.from(jwt), accountId, page, size);
    }

    @GetMapping("/customers/{customerId}")
    public PageResponse<StatementResponse> forCustomer(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID customerId,
                                                       @RequestParam(required = false) String accountType,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return statements.listForCustomer(CurrentUser.from(jwt), customerId, accountType, page, size);
    }

    @GetMapping("/eligibility")
    public EligibilityResponse eligibility(@AuthenticationPrincipal Jwt jwt,
                                           @RequestParam UUID accountId,
                                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                                           @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to) {
        return statements.eligibility(CurrentUser.from(jwt), accountId, from, to);
    }

    @GetMapping("/{statementId}")
    public StatementResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID statementId) {
        return statements.get(CurrentUser.from(jwt), statementId);
    }

    @PostMapping("/{statementId}/download-link")
    public ResponseEntity<DownloadLinkResponse> createLink(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable UUID statementId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(downloads.createLink(CurrentUser.from(jwt), statementId));
    }

    @PostMapping("/{statementId}/revoke-links")
    public Map<String, Object> revokeLinks(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID statementId) {
        int revoked = downloads.revokeLinks(CurrentUser.from(jwt), statementId);
        return Map.of("statementId", statementId, "revokedCount", revoked);
    }
}
