package za.co.capitec.accountservice.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import za.co.capitec.accountservice.common.exception.LedgerUnavailableException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class LedgerBalanceClient {

    private record BalanceResponse(UUID accountId, String currency, BigDecimal postedCredits,
                                   BigDecimal postedDebits, BigDecimal balance) {
    }

    private final RestClient restClient;

    public LedgerBalanceClient(@Value("${transaction.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public BigDecimal postedBalance(UUID accountId) {
        try {
            BalanceResponse response = restClient.get()
                    .uri("/api/v1/transactions/account/{id}/balance", accountId)
                    .retrieve()
                    .body(BalanceResponse.class);
            if (response == null || response.balance() == null) {
                throw new LedgerUnavailableException("transaction-service returned no balance");
            }
            return response.balance();
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new LedgerUnavailableException("Balance is temporarily unavailable");
        }
    }
}
