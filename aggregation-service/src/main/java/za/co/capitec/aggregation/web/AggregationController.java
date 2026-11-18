package za.co.capitec.aggregation.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import za.co.capitec.aggregation.account.AccountClient;
import za.co.capitec.aggregation.aggregation.AggregationQueries;
import za.co.capitec.aggregation.aggregation.AggregationQueries.Scope;
import za.co.capitec.aggregation.aggregation.AggregationQueryService;
import za.co.capitec.aggregation.aggregation.ImportService;
import za.co.capitec.aggregation.common.exception.AccessDeniedException;
import za.co.capitec.aggregation.domain.SourceSystem;
import za.co.capitec.aggregation.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/aggregation")
public class AggregationController {

    public record ImportRequest(@NotNull UUID accountId, @NotNull SourceSystem source,
                                @NotNull LocalDate from, @NotNull LocalDate to,
                                @NotNull Integer count, @NotNull Long seed) {
    }

    public record TransactionPage(List<AggregationQueries.TransactionRow> items, int page, int size, long totalElements,
                                  int totalPages) {
    }

    private final AggregationQueryService queries;
    private final ImportService imports;
    private final AccountClient accounts;

    public AggregationController(AggregationQueryService queries, ImportService imports, AccountClient accounts) {
        this.queries = queries;
        this.imports = imports;
        this.accounts = accounts;
    }

    @GetMapping("/sources")
    public List<SourceSystem> sources(@AuthenticationPrincipal Jwt jwt) {
        requireStaff(CurrentUser.from(jwt));
        return imports.sources();
    }

    @PostMapping("/imports")
    @ResponseStatus(HttpStatus.OK)
    public ImportService.ImportResult importFrom(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        requireStaff(CurrentUser.from(jwt));
        return imports.importFrom(request.source(), request.accountId(), request.from(), request.to(),
                request.count(), request.seed());
    }

    @GetMapping("/transactions")
    public TransactionPage transactions(@AuthenticationPrincipal Jwt jwt,
                                        @RequestParam(required = false) UUID accountId,
                                        @RequestParam(required = false) UUID customerId,
                                        @RequestParam(required = false) String category,
                                        @RequestParam(required = false) String direction,
                                        @RequestParam(required = false) String source,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        CurrentUser user = CurrentUser.from(jwt);
        UUID scopedCustomer = resolveCustomer(user, accountId, customerId);
        var filter = queries.filter(accountId, scopedCustomer, category, direction, source, status, from, to);
        long total = queries.count(filter);
        var items = queries.page(filter, page, size);
        return new TransactionPage(items, page, size, total, (int) Math.ceil(total / (double) size));
    }

    @GetMapping("/transactions/{id}")
    public AggregationQueries.TransactionRow transaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        AggregationQueries.TransactionRow row = queries.transaction(id);
        if (row == null) {
            throw new za.co.capitec.aggregation.common.exception.NotFoundException("Transaction not found: " + id);
        }
        CurrentUser.from(jwt).requireCustomer(row.customerId());
        return row;
    }

    @GetMapping("/accounts/{accountId}/summary")
    public AggregationQueries.Totals accountSummary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        authoriseAccount(CurrentUser.from(jwt), accountId);
        return queries.totals(Scope.account(accountId), from, to);
    }

    @GetMapping("/customers/{customerId}/summary")
    public AggregationQueries.Totals customerSummary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID customerId,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentUser.from(jwt).requireCustomer(customerId);
        return queries.totals(Scope.customer(customerId), from, to);
    }

    @GetMapping("/summary")
    public AggregationQueries.Totals platformSummary(@AuthenticationPrincipal Jwt jwt,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireStaff(CurrentUser.from(jwt));
        return queries.totals(Scope.all(), from, to);
    }

    @GetMapping("/accounts/{accountId}/categories")
    public List<AggregationQueries.CategoryRow> accountCategories(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        authoriseAccount(CurrentUser.from(jwt), accountId);
        return queries.categories(Scope.account(accountId), from, to);
    }

    @GetMapping("/customers/{customerId}/categories")
    public List<AggregationQueries.CategoryRow> customerCategories(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID customerId,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentUser.from(jwt).requireCustomer(customerId);
        return queries.categories(Scope.customer(customerId), from, to);
    }

    @GetMapping("/accounts/{accountId}/trends")
    public List<AggregationQueries.TrendPoint> accountTrends(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                             @RequestParam(defaultValue = "DAY") String granularity) {
        authoriseAccount(CurrentUser.from(jwt), accountId);
        return queries.trends(Scope.account(accountId), from, to, granularity);
    }

    @GetMapping("/customers/{customerId}/trends")
    public List<AggregationQueries.TrendPoint> customerTrends(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID customerId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                              @RequestParam(defaultValue = "DAY") String granularity) {
        CurrentUser.from(jwt).requireCustomer(customerId);
        return queries.trends(Scope.customer(customerId), from, to, granularity);
    }

    @GetMapping("/accounts/{accountId}/velocity")
    public za.co.capitec.aggregation.aggregation.AggregationQueries.Velocity accountVelocity(@AuthenticationPrincipal Jwt jwt,
                                                                                             @PathVariable UUID accountId,
                                                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        authoriseAccount(CurrentUser.from(jwt), accountId);
        return queries.velocity(Scope.account(accountId), from, to);
    }

    @GetMapping("/customers/{customerId}/velocity")
    public za.co.capitec.aggregation.aggregation.AggregationQueries.Velocity customerVelocity(@AuthenticationPrincipal Jwt jwt,
                                                                                              @PathVariable UUID customerId,
                                                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentUser.from(jwt).requireCustomer(customerId);
        return queries.velocity(Scope.customer(customerId), from, to);
    }

    private void authoriseAccount(CurrentUser user, UUID accountId) {
        UUID owner = accounts.require(accountId).customerId();
        user.requireCustomer(owner);
    }

    private static UUID resolveCustomer(CurrentUser user, UUID accountId, UUID customerId) {
        if (user.staff()) {
            return customerId;
        }
        if (customerId != null && !customerId.equals(user.customerId())) {
            throw new AccessDeniedException("You are not authorised to view this customer");
        }
        return user.customerId();
    }

    private static void requireStaff(CurrentUser user) {
        if (!user.staff()) {
            throw new AccessDeniedException("This operation is restricted to staff");
        }
    }
}
