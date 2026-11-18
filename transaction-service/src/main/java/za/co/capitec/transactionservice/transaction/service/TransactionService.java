package za.co.capitec.transactionservice.transaction.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.capitec.transactionservice.transaction.dto.CreateTransactionRequest;
import za.co.capitec.transactionservice.transaction.dto.TransactionResponse;
import za.co.capitec.transactionservice.transaction.entity.Transaction;
import za.co.capitec.transactionservice.transaction.repository.TransactionRepository;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;

    public TransactionService(
            TransactionRepository transactionRepository,
            AccountServiceClient accountServiceClient) {
        this.transactionRepository = transactionRepository;
        this.accountServiceClient = accountServiceClient;
    }

    public TransactionResponse createTransaction(CreateTransactionRequest request) {

        if (!accountServiceClient.accountExists(request.accountId())) {
            throw new AccountNotFoundException(
                    "Account not found: " + request.accountId()
            );
        }

        String reference = generateTransactionReference();

        Transaction transaction = new Transaction(
                request.accountId(),
                reference,
                request.type(),
                request.amount(),
                request.currency().trim().toUpperCase(),
                request.description() == null ? null : request.description().trim()
        );

        Transaction savedTransaction = transactionRepository.save(transaction);

        return TransactionResponse.from(savedTransaction);
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
    public List<TransactionResponse> getTransactionsByAccount(UUID accountId) {

        if (!accountServiceClient.accountExists(accountId)) {
            throw new AccountNotFoundException(
                    "Account not found: " + accountId
            );
        }

        return transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private String generateTransactionReference() {
        return "TXN-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase();
    }
}
