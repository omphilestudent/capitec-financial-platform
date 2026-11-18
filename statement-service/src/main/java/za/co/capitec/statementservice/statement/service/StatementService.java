package za.co.capitec.statementservice.statement.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.statementservice.common.exception.AccessDeniedStatementException;
import za.co.capitec.statementservice.common.exception.InvalidStatementRequestException;
import za.co.capitec.statementservice.common.exception.StatementGenerationException;
import za.co.capitec.statementservice.common.exception.StatementNotFoundException;
import za.co.capitec.statementservice.common.messaging.OutboxWriter;
import za.co.capitec.statementservice.security.CurrentUser;
import za.co.capitec.statementservice.statement.client.AccountClient;
import za.co.capitec.statementservice.statement.client.AccountView;
import za.co.capitec.statementservice.statement.client.CustomerClient;
import za.co.capitec.statementservice.statement.client.CustomerView;
import za.co.capitec.statementservice.statement.eligibility.EligibilityService;
import za.co.capitec.statementservice.statement.web.dto.EligibilityResponse;
import za.co.capitec.statementservice.statement.domain.AccessType;
import za.co.capitec.statementservice.statement.domain.Statement;
import za.co.capitec.statementservice.statement.events.StatementEvents;
import za.co.capitec.statementservice.statement.events.StatementEvents.StatementGeneratedEvent;
import za.co.capitec.statementservice.statement.events.StatementEvents.StatementGenerationFailedEvent;
import za.co.capitec.statementservice.statement.pdf.Ledger;
import za.co.capitec.statementservice.statement.pdf.LedgerCalculator;
import za.co.capitec.statementservice.statement.pdf.StatementDocument;
import za.co.capitec.statementservice.statement.pdf.StatementPdfRenderer;
import za.co.capitec.statementservice.statement.repository.StatementRepository;
import za.co.capitec.statementservice.statement.storage.StatementStorage;
import za.co.capitec.statementservice.statement.web.dto.GenerateStatementRequest;
import za.co.capitec.statementservice.statement.web.dto.PageResponse;
import za.co.capitec.statementservice.statement.web.dto.StatementResponse;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(noRollbackFor = {AccessDeniedStatementException.class, StatementGenerationException.class})
public class StatementService {

    public record GenerateResult(StatementResponse statement, boolean created) {
    }

    private static final int MAX_PAGE_SIZE = 100;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountClient accountClient;
    private final CustomerClient customerClient;
    private final EligibilityService eligibility;
    private final LedgerCalculator ledgerCalculator;
    private final StatementPdfRenderer pdfRenderer;
    private final StatementStorage storage;
    private final StatementRepository statements;
    private final StatementAccess access;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final int maxCustomRangeDays;

    public StatementService(AccountClient accountClient,
                            CustomerClient customerClient,
                            EligibilityService eligibility,
                            LedgerCalculator ledgerCalculator,
                            StatementPdfRenderer pdfRenderer,
                            StatementStorage storage,
                            StatementRepository statements,
                            StatementAccess access,
                            OutboxWriter outboxWriter,
                            @Value("${statement.max-custom-range-days:366}") int maxCustomRangeDays) {
        this.accountClient = accountClient;
        this.customerClient = customerClient;
        this.eligibility = eligibility;
        this.ledgerCalculator = ledgerCalculator;
        this.pdfRenderer = pdfRenderer;
        this.storage = storage;
        this.statements = statements;
        this.access = access;
        this.outboxWriter = outboxWriter;
        this.clock = Clock.systemDefaultZone();
        this.maxCustomRangeDays = maxCustomRangeDays;
    }

    public GenerateResult generate(CurrentUser user, GenerateStatementRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate[] period = resolvePeriod(request, today);

        AccountView account = accountClient.getAccount(request.accountId());
        try {
            user.requireCustomer(account.customerId());
        } catch (AccessDeniedStatementException denied) {
            access.record(null, account.customerId(), user.subject(), AccessType.GENERATED, false, denied.getMessage());
            throw denied;
        }

        Optional<Statement> existing = statements.findByAccountIdAndPeriodStartAndPeriodEnd(
                account.id(), period[0], period[1]);
        if (existing.isPresent() && existing.get().getStatus().name().equals("GENERATED")) {
            access.record(existing.get().getId(), account.customerId(), user.subject(), AccessType.GENERATED, true,
                    "already generated");
            return new GenerateResult(StatementResponse.from(existing.get()), false);
        }

        if (request.accountType() != null && !request.accountType().equals(account.accountType())) {
            throw new InvalidStatementRequestException("accountType does not match the selected account");
        }
        var ledgerEntries = eligibility.load(account.customerId(), account.id(), period[1]);
        EligibilityService.Summary summary = EligibilityService.summarise(ledgerEntries, period[0], period[1]);
        if (summary.eligibleInPeriod() < 2) {
            throw new InsufficientEligibleTransactionsException(summary.eligibleInPeriod());
        }

        Statement statement = existing.orElseGet(() -> new Statement(
                UUID.randomUUID(), newReference(today), account.customerId(), account.id(),
                account.accountNumber(), account.accountType(), period[0], period[1], account.currency()));
        statement = statements.save(statement);

        try {
            CustomerView customer = customerClient.getCustomer(account.customerId());
            Ledger ledger = ledgerCalculator.calculate(EligibilityService.toLedgerViews(ledgerEntries), period[0], period[1]);

            LocalDateTime generatedAt = LocalDateTime.now(clock);
            StatementDocument document = new StatementDocument(
                    statement.getStatementReference(), customer.displayName(), customer.id().toString(),
                    account.accountNumber(), account.accountType(), period[0], period[1], account.currency(),
                    generatedAt, ledger.openingBalance(), ledger.closingBalance(), ledger.totalCredits(),
                    ledger.totalDebits(), ledger.excludedFailedCount(), ledger.rows());

            byte[] pdf = pdfRenderer.render(document);
            String key = storageKey(statement.getId(), period[0]);
            storage.store(key, pdf);

            statement.markGenerated(ledger.openingBalance(), ledger.closingBalance(), ledger.totalCredits(),
                    ledger.totalDebits(), key, generatedAt);
            statement.setEligibilityCounts(summary.eligibleInPeriod(), summary.excludedInPeriod());
            statement = statements.save(statement);

            outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                    StatementGeneratedEvent.EVENT_TYPE,
                    new StatementGeneratedEvent(UUID.randomUUID(), statement.getId(), statement.getStatementReference(),
                            statement.getCustomerId(), statement.getAccountId(), period[0], period[1],
                            statement.getClosingBalance(), generatedAt));
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.GENERATED, true, null);
            return new GenerateResult(StatementResponse.from(statement), existing.isEmpty());
        } catch (IOException | RuntimeException failure) {
            String reason = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            statement.markFailed(reason);
            statements.save(statement);
            outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                    StatementGenerationFailedEvent.EVENT_TYPE,
                    new StatementGenerationFailedEvent(UUID.randomUUID(), statement.getId(), statement.getCustomerId(),
                            statement.getAccountId(), period[0], period[1], reason, LocalDateTime.now(clock)));
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.GENERATED, false, reason);
            throw new StatementGenerationException(reason, failure);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementResponse> listForAccount(CurrentUser user, UUID accountId, int page, int size) {
        Pageable pageable = pageable(page, size);
        var result = user.staff()
                ? statements.findByAccountIdOrderByPeriodEndDescCreatedAtDesc(accountId, pageable)
                : statements.findByAccountIdAndCustomerIdOrderByPeriodEndDescCreatedAtDesc(accountId, customerOf(user), pageable);
        access.record(null, user.customerId(), user.subject(), AccessType.LIST, true, "account " + accountId);
        return PageResponse.of(result.map(StatementResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<StatementResponse> listForCustomer(CurrentUser user, UUID customerId, String accountType,
                                                           int page, int size) {
        user.requireCustomer(customerId);
        var result = accountType == null || accountType.isBlank()
                ? statements.findByCustomerIdOrderByPeriodEndDescCreatedAtDesc(customerId, pageable(page, size))
                : statements.findByCustomerIdAndAccountTypeOrderByPeriodEndDescCreatedAtDesc(
                        customerId, accountType.toUpperCase(java.util.Locale.ROOT), pageable(page, size));
        access.record(null, customerId, user.subject(), AccessType.LIST, true, "customer " + customerId);
        return PageResponse.of(result.map(StatementResponse::from));
    }

    @Transactional(readOnly = true)
    public EligibilityResponse eligibility(CurrentUser user, UUID accountId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null || to.isAfter(today) ? today : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new InvalidStatementRequestException("from must be on or before to");
        }
        AccountView account = accountClient.getAccount(accountId);
        user.requireCustomer(account.customerId());
        var summary = EligibilityService.summarise(eligibility.load(account.customerId(), accountId, end), start, end);
        return new EligibilityResponse(accountId, start, end, summary.eligibleInPeriod(), summary.excludedInPeriod(),
                summary.eligibleInPeriod() >= 2);
    }

    @Transactional(readOnly = true)
    public StatementResponse get(CurrentUser user, UUID statementId) {
        Statement statement = find(statementId);
        user.requireCustomer(statement.getCustomerId());
        access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.VIEW, true, null);
        return StatementResponse.from(statement);
    }

    Statement find(UUID statementId) {
        return statements.findById(statementId)
                .orElseThrow(() -> new StatementNotFoundException("Statement not found: " + statementId));
    }

    static UUID customerOf(CurrentUser user) {
        return user.customerId();
    }

    static Pageable pageable(int page, int size) {
        if (page < 0) {
            throw new InvalidStatementRequestException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidStatementRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size);
    }

    LocalDate[] resolvePeriod(GenerateStatementRequest request, LocalDate today) {
        LocalDate start;
        LocalDate end;
        if (request.periodType() == GenerateStatementRequest.PeriodType.MONTHLY) {
            if (request.year() == null || request.month() == null) {
                throw new InvalidStatementRequestException("year and month are required for a MONTHLY statement");
            }
            if (request.month() < 1 || request.month() > 12) {
                throw new InvalidStatementRequestException("month must be between 1 and 12");
            }
            YearMonth ym;
            try {
                ym = YearMonth.of(request.year(), request.month());
            } catch (java.time.DateTimeException e) {
                throw new InvalidStatementRequestException("year is out of range");
            }
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        } else {
            if (request.from() == null || request.to() == null) {
                throw new InvalidStatementRequestException("from and to are required for a CUSTOM statement");
            }
            start = request.from();
            end = request.to();
            if (start.isAfter(end)) {
                throw new InvalidStatementRequestException("from must be on or before to");
            }
            long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
            if (days > maxCustomRangeDays) {
                throw new InvalidStatementRequestException("custom range may not exceed " + maxCustomRangeDays + " days");
            }
        }
        if (start.isAfter(today)) {
            throw new InvalidStatementRequestException("statement period cannot start in the future");
        }
        if (end.isAfter(today)) {
            end = today;
        }
        return new LocalDate[]{start, end};
    }

    static String storageKey(UUID statementId, LocalDate periodStart) {
        return "statements/" + periodStart.getYear() + "/"
                + String.format("%02d", periodStart.getMonthValue()) + "/" + statementId + ".pdf";
    }

    static String newReference(LocalDate today) {
        byte[] random = new byte[4];
        RANDOM.nextBytes(random);
        return "STM-" + today.toString().replace("-", "") + "-" + HexFormat.of().withUpperCase().formatHex(random);
    }
}
