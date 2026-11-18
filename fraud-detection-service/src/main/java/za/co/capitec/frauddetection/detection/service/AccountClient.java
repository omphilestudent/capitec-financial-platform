package za.co.capitec.frauddetection.detection.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/** Looks up the customer that owns an account, from account-service. */
@Component
public class AccountClient {

    public record AccountView(UUID id, UUID customerId) {}

    private final RestClient restClient;

    public AccountClient(@Value("${account.service.url}") String accountServiceUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder().baseUrl(accountServiceUrl).requestFactory(factory).build();
    }

    /** Empty when the account does not exist. Throws if account-service cannot answer. */
    public Optional<AccountView> findAccount(UUID accountId) {
        try {
            AccountView view = restClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .retrieve()
                    .body(AccountView.class);
            return Optional.ofNullable(view);
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (ResourceAccessException | HttpServerErrorException unavailable) {
            throw new AccountLookupException("account-service unavailable", unavailable);
        }
    }
}
