package za.co.capitec.transactionservice.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class AccountServiceClient {

    private final RestClient restClient;

    public AccountServiceClient(
            RestClient.Builder builder,
            @Value("${account.service.url}") String accountServiceUrl) {

        this.restClient = builder
                .baseUrl(accountServiceUrl)
                .build();
    }

    public record AccountReference(String accountType) {
    }

    public String accountType(UUID accountId) {
        try {
            AccountReference reference = restClient.get()
                    .uri("/api/v1/accounts/{id}/reference", accountId)
                    .retrieve()
                    .body(AccountReference.class);
            if (reference == null || reference.accountType() == null) {
                throw new AccountServiceUnavailableException("Account type unavailable", null);
            }
            return reference.accountType();
        } catch (HttpClientErrorException.NotFound exception) {
            throw new AccountNotFoundException("Account not found: " + accountId);
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new AccountServiceUnavailableException(
                    "Account service is unavailable", exception);
        }
    }

    /**
     * @return true if the account exists, false if account-service reports 404.
     * @throws AccountServiceUnavailableException if account-service cannot be reached
     *         or fails, so no decision can be made about the account.
     */
    public boolean accountExists(UUID accountId) {
        try {
            restClient.get()
                    .uri("/api/v1/accounts/{id}/reference", accountId)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            throw new AccountServiceUnavailableException(
                    "Account service is unavailable", exception);
        }
    }
}
