package za.co.capitec.transactionservice.transaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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

    public boolean accountExists(UUID accountId) {
        try {
            restClient.get()
                    .uri("/api/v1/accounts/{id}", accountId)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }
}
