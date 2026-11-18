
package za.co.capitec.transactionservice.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.transactionservice.common.messaging.OutboxWriter;
import za.co.capitec.transactionservice.messaging.event.TransactionCreatedEvent;
import za.co.capitec.transactionservice.transaction.dto.CreateTransactionRequest;
import za.co.capitec.transactionservice.transaction.dto.TransactionFilter;
import za.co.capitec.transactionservice.transaction.dto.TransactionResponse;
import za.co.capitec.transactionservice.transaction.dto.TransactionSummaryResponse;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.entity.TransactionStatus;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.entity.TransactionType;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final OutboxWriter outboxWriter;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountServiceClient accountServiceClient,
            OutboxWriter outboxWriter) {
        this.transactionRepository = transactionRepository;
        this.accountServiceClient = accountServiceClient;
        this.outboxWriter = outboxWriter;
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request) {

        if (!accountServiceClient.accountExists(request.accountId())) {
            throw new AccountNotFoundException(
                    "Account not found: " + request.accountId()
            );
        }

        String reference = generateTransactionReference();

        boolean seed = Boolean.TRUE.equals(request.syntheticSeed());
        if (request.occurredAt() != null && !seed) {
            throw new LedgerRuleException("occurredAt is only accepted for synthetic seed data");
        }
        if (seed && request.occurredAt() != null) {
            LocalDateTime earliest = LocalDateTime.now().minusMonths(16);
            if (request.occurredAt().isBefore(earliest) || request.occurredAt().isAfter(LocalDateTime.now())) {
                throw new LedgerRuleException("Synthetic occurredAt must be within the last 16 months and not in the future");
            }
        }

        Transaction transaction = new Transaction(
                request.accountId(),
                reference,
                request.type(),
                request.amount(),
                request.currency().trim().toUpperCase(),
                request.description() == null ? null : request.description().trim()
        );

        if (seed) {
            transaction.markSynthetic(request.occurredAt());
        }
        Transaction savedTransaction = transactionRepository.save(transaction);

        UUID eventId = UUID.randomUUID();
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                eventId,
                savedTransaction.getId(),
                savedTransaction.getAccountId(),
                savedTransaction.getTransactionReference(),
                savedTransaction.getType().name(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus().name(),
                savedTransaction.getDescription(),
                savedTransaction.getCreatedAt()
        );

        outboxWriter.write(
                eventId,
                TransactionCreatedEvent.TOPIC,
                savedTransaction.getId().toString(),
                TransactionCreatedEvent.EVENT_TYPE,
                event
        );
        outboxWriter.writeCopy(
                eventId,
                TransactionCreatedEvent.LEGACY_TOPIC,
                savedTransaction.getId().toString(),
                TransactionCreatedEvent.EVENT_TYPE,
                event
        );

        return TransactionResponse.from(savedTransaction);
    }

    public TransactionResponse postFunding(za.co.capitec.transactionservice.transaction.dto.FundingRequest request) {
        if (!"ZAR".equals(request.currency() == null ? "ZAR" : request.currency())) {
            throw new LedgerRuleException("Only ZAR funding is supported");
        }
        if (request.entryKind() == za.co.capitec.transactionservice.transaction.entity.EntryKind.STANDARD) {
            throw new LedgerRuleException("Funding requests must be OPENING_BALANCE or FUNDING");
        }
        var existing = transactionRepository.findByIdempotencyKey(request.idempotencyKey());
        if (existing.isPresent()) {
            Transaction previous = existing.get();
            if (!previous.getAccountId().equals(request.accountId())
                    || previous.getAmount().compareTo(request.amount()) != 0
                    || previous.getEntryKind() != request.entryKind()) {
                throw new LedgerRuleException("idempotencyKey was already used for a different posting");
            }
            return TransactionResponse.from(previous);
        }
        if (!accountServiceClient.accountExists(request.accountId())) {
            throw new AccountNotFoundException("Account not found: " + request.accountId());
        }
        if (request.entryKind() == za.co.capitec.transactionservice.transaction.entity.EntryKind.OPENING_BALANCE
                && transactionRepository.existsByAccountIdAndEntryKind(request.accountId(),
                za.co.capitec.transactionservice.transaction.entity.EntryKind.OPENING_BALANCE)) {
            throw new LedgerRuleException("This account already has an opening balance");
        }
        Transaction funding = Transaction.funding(request.accountId(), generateTransactionReference(),
                request.amount().setScale(2, java.math.RoundingMode.HALF_UP), "ZAR",
                request.description() == null ? null : request.description().trim(),
                request.entryKind(), request.idempotencyKey());
        Transaction saved;
        try {
            saved = transactionRepository.saveAndFlush(funding);
        } catch (org.springframework.dao.DataIntegrityViolationException race) {
            throw new LedgerRuleException("A posting with this idempotencyKey or opening balance already exists");
        }
        publishCreated(saved);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public za.co.capitec.transactionservice.transaction.dto.AccountBalanceResponse balance(UUID accountId) {
        if (!accountServiceClient.accountExists(accountId)) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        }
        BigDecimal credits = transactionRepository.sumAmount(accountId,
                TransactionType.CREDIT, TransactionStatus.COMPLETED).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal debits = transactionRepository.sumAmount(accountId,
                TransactionType.DEBIT, TransactionStatus.COMPLETED).setScale(2, java.math.RoundingMode.HALF_UP);
        return new za.co.capitec.transactionservice.transaction.dto.AccountBalanceResponse(
                accountId, "ZAR", credits, debits, credits.subtract(debits).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @org.springframework.beans.factory.annotation.Value("${ledger.credit-overdraft-limit:50000.00}")
    private BigDecimal creditOverdraftLimit;

    @Transactional
    public TransactionResponse postPending(UUID transactionId) {
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        if (tx.getStatus() == TransactionStatus.COMPLETED) {
            return TransactionResponse.from(tx);
        }
        if (tx.getStatus() == TransactionStatus.FAILED) {
            throw new LedgerRuleException("A failed transaction cannot be posted");
        }
        transactionRepository.lockAccountForPosting(tx.getAccountId().toString());
        if (tx.getType() == TransactionType.DEBIT) {
            String accountType = accountServiceClient.accountType(tx.getAccountId());
            BigDecimal credits = transactionRepository.sumAmount(tx.getAccountId(),
                    TransactionType.CREDIT, TransactionStatus.COMPLETED);
            BigDecimal debits = transactionRepository.sumAmount(tx.getAccountId(),
                    TransactionType.DEBIT, TransactionStatus.COMPLETED);
            BigDecimal balanceAfter = credits.subtract(debits).subtract(tx.getAmount());
            BigDecimal floor = "CREDIT".equals(accountType)
                    ? creditOverdraftLimit.negate()
                    : BigDecimal.ZERO;
            if (balanceAfter.compareTo(floor) < 0) {
                throw new LedgerRuleException("Insufficient funds: this debit would take the account below "
                        + floor.setScale(2) + " ZAR");
            }
        }
        tx.markPosted();
        return TransactionResponse.from(transactionRepository.saveAndFlush(tx));
    }

    private void publishCreated(Transaction savedTransaction) {
        UUID eventId = UUID.randomUUID();
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                eventId,
                savedTransaction.getId(),
                savedTransaction.getAccountId(),
                savedTransaction.getTransactionReference(),
                savedTransaction.getType().name(),
                savedTransaction.getAmount(),
                savedTransaction.getCurrency(),
                savedTransaction.getStatus().name(),
                savedTransaction.getDescription(),
                savedTransaction.getCreatedAt()
        );
        outboxWriter.write(eventId, TransactionCreatedEvent.TOPIC, savedTransaction.getId().toString(),
                TransactionCreatedEvent.EVENT_TYPE, event);
        outboxWriter.writeCopy(eventId, TransactionCreatedEvent.LEGACY_TOPIC, savedTransaction.getId().toString(),
                TransactionCreatedEvent.EVENT_TYPE, event);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(
                        "Transaction not found: " + transactionId
                ));

        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getRecentTransactions() {
        return transactionRepository.findTop100ByOrderByCreatedAtDesc()
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(UUID accountId) {
        return getTransactionsByAccount(accountId, new TransactionFilter(null, null, null));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccount(UUID accountId, TransactionFilter filter) {
        ensureAccountExists(accountId);
        return findFiltered(accountId, filter).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse getTransactionSummary(UUID accountId, TransactionFilter filter) {
        ensureAccountExists(accountId);
        List<Transaction> rows = findFiltered(accountId, filter);

        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal debits = BigDecimal.ZERO;
        for (Transaction transaction : rows) {
            if (transaction.getType() == TransactionType.CREDIT) {
                credits = credits.add(transaction.getAmount());
            } else {
                debits = debits.add(transaction.getAmount());
            }
        }

        return new TransactionSummaryResponse(
                filter.from(),
                filter.to(),
                filter.type() == null ? "ALL" : filter.type().name(),
                rows.size(),
                credits,
                debits,
                credits.subtract(debits)
        );
    }

    private void ensureAccountExists(UUID accountId) {
        if (!accountServiceClient.accountExists(accountId)) {
            throw new AccountNotFoundException(
                    "Account not found: " + accountId
            );
        }
    }

    private List<Transaction> findFiltered(UUID accountId, TransactionFilter filter) {
        if (filter.from() != null && filter.to() != null && filter.from().isAfter(filter.to())) {
            throw new InvalidTransactionFilterException(
                    "'from' date must be on or before 'to' date");
        }

        LocalDateTime fromInclusive = filter.from() == null
                ? LocalDateTime.of(1970, 1, 1, 0, 0)
                : filter.from().atStartOfDay();
        LocalDateTime toExclusive = filter.to() == null
                ? LocalDateTime.of(9999, 12, 31, 0, 0)
                : filter.to().plusDays(1).atStartOfDay();

        if (filter.type() == null) {
            return transactionRepository
                    .findByAccountIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                            accountId, fromInclusive, toExclusive);
        }
        return transactionRepository
                .findByAccountIdAndTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        accountId, filter.type(), fromInclusive, toExclusive);
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }
}
