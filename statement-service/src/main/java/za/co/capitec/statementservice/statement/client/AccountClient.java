package za.co.capitec.statementservice.statement.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import za.co.capitec.statementservice.common.exception.StatementNotFoundException;
import za.co.capitec.statementservice.common.exception.UpstreamServiceException;

import java.util.UUID;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(@Value("${account.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public AccountView getAccount(UUID accountId) {
        try {
            AccountView account = restClient.get()
                    .uri("/api/v1/accounts/{id}/reference", accountId)
                    .retrieve()
                    .body(AccountView.class);
            if (account == null) {
                throw new UpstreamServiceException("account-service returned an empty response", null);
            }
            return account;
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new StatementNotFoundException("Account not found: " + accountId);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new UpstreamServiceException("account-service is unavailable", e);
        }
    }
}
