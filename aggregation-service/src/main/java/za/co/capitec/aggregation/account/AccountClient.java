package za.co.capitec.aggregation.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import za.co.capitec.aggregation.common.exception.NotFoundException;
import za.co.capitec.aggregation.common.exception.UpstreamServiceException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountClient {

    public record AccountView(UUID id, UUID customerId, String accountNumber, String accountType,
                              BigDecimal balance, String currency, String status) {
    }

    private final RestClient restClient;

    public AccountClient(@Value("${account.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public Optional<AccountView> find(UUID accountId) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri("/api/v1/accounts/{id}/reference", accountId)
                    .retrieve()
                    .body(AccountView.class));
        } catch (HttpClientErrorException.NotFound notFound) {
            return Optional.empty();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new UpstreamServiceException("account-service is unavailable", e);
        }
    }

    public AccountView require(UUID accountId) {
        return find(accountId).orElseThrow(() -> new NotFoundException("Account not found: " + accountId));
    }
}
