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
import java.util.List;
import java.util.Optional;
import za.co.capitec.statementservice.statement.domain.StatementStatus;
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
    private final za.co.capitec.statementservice.statement.repository.StatementSnapshotRepository snapshots;
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
                            za.co.capitec.statementservice.statement.repository.StatementSnapshotRepository snapshots,
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
        this.snapshots = snapshots;
        this.access = access;
        this.outboxWriter = outboxWriter;
        this.clock = Clock.systemDefaultZone();
        this.maxCustomRangeDays = maxCustomRangeDays;
    }

    public GenerateResult generate(CurrentUser user, GenerateStatementRequest request) {
        LocalDate today = LocalDate.now(clock);
        LocalDate[] period = resolvePeriod(request, today);

        AccountView account = accountClient.getAccount(request.accountId());
        authorise(user, account);
        if (request.accountType() != null && !request.accountType().equals(account.accountType())) {
            throw new InvalidStatementRequestException("accountType does not match the selected account");
        }

        Optional<Statement> current = statements.findByAccountIdAndPeriodStartAndPeriodEndAndVersionStatus(
                account.id(), period[0], period[1], "CURRENT");
        Basis basis = basisFor(account, period);
        if (current.isPresent() && current.get().getStatus() == StatementStatus.GENERATED) {
            boolean unchanged = basis.fingerprint().equals(current.get().getEligibilityFingerprint());
            access.record(current.get().getId(), account.customerId(), user.subject(), AccessType.GENERATED, true,
                    unchanged ? "already generated" : "eligibility changed; regeneration required");
            return new GenerateResult(StatementResponse.from(current.get()), false);
        }
        return issueVersion(user, account, period, basis, current.orElse(null), "Initial generation");
    }

    @Transactional
    public GenerateResult regenerate(CurrentUser user, UUID statementId, String reason) {
        Statement previous = find(statementId);
        user.requireCustomer(previous.getCustomerId());
        if (!"CURRENT".equals(previous.getVersionStatus())) {
            throw new InvalidStatementRequestException("Only the current version of a statement can be regenerated");
        }
        if (previous.getStatus() != StatementStatus.GENERATED) {
            throw new InvalidStatementRequestException("Only a generated statement can be regenerated");
        }
        AccountView account = accountClient.getAccount(previous.getAccountId());
        LocalDate[] period = {previous.getPeriodStart(), previous.getPeriodEnd()};
        Basis basis = basisFor(account, period);
        if (basis.fingerprint().equals(previous.getEligibilityFingerprint())) {
            access.record(previous.getId(), previous.getCustomerId(), user.subject(), AccessType.GENERATED, true,
                    "regeneration requested; eligibility unchanged, no new version");
            return new GenerateResult(StatementResponse.from(previous), false);
        }
        String why = reason == null || reason.isBlank() ? "Eligibility changed since the statement was issued" : reason.trim();
        return issueVersion(user, account, period, basis, previous, why);
    }

    @Transactional(readOnly = true)
    public List<StatementResponse> versions(CurrentUser user, UUID statementId) {
        Statement anchor = find(statementId);
        user.requireCustomer(anchor.getCustomerId());
        List<Statement> all = statements.findByAccountIdAndPeriodStartAndPeriodEndOrderByVersionNumberDesc(
                anchor.getAccountId(), anchor.getPeriodStart(), anchor.getPeriodEnd());
        access.record(anchor.getId(), anchor.getCustomerId(), user.subject(), AccessType.LIST, true, "versions");
        return all.stream()
                .filter(v -> user.staff() || "CURRENT".equals(v.getVersionStatus()))
                .map(StatementResponse::from)
                .toList();
    }

    private void authorise(CurrentUser user, AccountView account) {
        try {
            user.requireCustomer(account.customerId());
        } catch (AccessDeniedStatementException denied) {
            access.record(null, account.customerId(), user.subject(), AccessType.GENERATED, false, denied.getMessage());
            throw denied;
        }
    }

    private record Basis(EligibilityService.Summary summary, List<EligibilityService.Entry> entries, String fingerprint) {
    }

    private Basis basisFor(AccountView account, LocalDate[] period) {
        var entries = eligibility.load(account.customerId(), account.id(), period[1]);
        EligibilityService.Summary summary = EligibilityService.summarise(entries, period[0], period[1]);
        return new Basis(summary, entries, fingerprintOf(entries));
    }

    static String fingerprintOf(List<EligibilityService.Entry> entries) {
        StringBuilder basis = new StringBuilder();
        entries.stream()
                .filter(e -> e.status() == EligibilityService.Status.ELIGIBLE)
                .sorted(java.util.Comparator.comparing(e -> e.row().aggregatedTransactionId().toString()))
                .forEach(e -> basis.append(e.row().aggregatedTransactionId()).append('|')
                        .append(e.row().direction()).append('|')
                        .append(e.row().amount().toPlainString()).append('|')
                        .append(e.row().occurredAt()).append('\n'));
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(basis.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private GenerateResult issueVersion(CurrentUser user, AccountView account, LocalDate[] period, Basis basis,
                                        Statement previous, String reason) {
        if (basis.summary().eligibleInPeriod() < 2) {
            throw new InsufficientEligibleTransactionsException(basis.summary().eligibleInPeriod());
        }
        LocalDate today = LocalDate.now(clock);
        int nextVersion = statements.maxVersion(account.id(), period[0], period[1]) + 1;
        Statement statement = new Statement(UUID.randomUUID(), newReference(today), account.customerId(), account.id(),
                account.accountNumber(), account.accountType(), period[0], period[1], account.currency());
        statement.configureVersion(nextVersion, reason, basis.fingerprint());
        statement = statements.save(statement);

        try {
            CustomerView customer = customerClient.getCustomer(account.customerId());
            Ledger ledger = ledgerCalculator.calculate(EligibilityService.toLedgerViews(basis.entries()),
                    period[0], period[1]);

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
            statement.setEligibilityCounts(basis.summary().eligibleInPeriod(), basis.summary().excludedInPeriod());
            if (previous != null) {
                previous.markSuperseded(statement.getId());
                statements.saveAndFlush(previous);
            }
            statement.promoteToCurrent();
            statement = statements.saveAndFlush(statement);
            snapshots.save(statement.getId(), ledger.rows());

            outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                    StatementGeneratedEvent.EVENT_TYPE,
                    new StatementGeneratedEvent(UUID.randomUUID(), statement.getId(), statement.getStatementReference(),
                            statement.getCustomerId(), statement.getAccountId(), period[0], period[1],
                            statement.getClosingBalance(), generatedAt));
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.GENERATED, true,
                    "version " + nextVersion + ": " + reason);
            return new GenerateResult(StatementResponse.from(statement), true);
        } catch (IOException | RuntimeException failure) {
            String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            statement.markFailedVersion(message);
            statements.save(statement);
            outboxWriter.write(UUID.randomUUID(), StatementEvents.TOPIC, statement.getId().toString(),
                    StatementGenerationFailedEvent.EVENT_TYPE,
                    new StatementGenerationFailedEvent(UUID.randomUUID(), statement.getId(), statement.getCustomerId(),
                            statement.getAccountId(), period[0], period[1], message, LocalDateTime.now(clock)));
            access.record(statement.getId(), statement.getCustomerId(), user.subject(), AccessType.GENERATED, false, message);
            throw new StatementGenerationException(message, failure);
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
