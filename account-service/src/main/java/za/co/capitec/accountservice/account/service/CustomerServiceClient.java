package za.co.capitec.accountservice.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CustomerServiceClient {

    private final RestClient restClient;

    public CustomerServiceClient(
            RestClient.Builder builder,
            @Value("${customer.service.url}") String customerServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(customerServiceUrl)
                .build();
    }

    public boolean customerExists(UUID customerId) {

        try {
            restClient.get()
                    .uri("/api/v1/customers/{id}", customerId)
                    .retrieve()
                    .toBodilessEntity();

            return true;

        } catch (HttpClientErrorException.NotFound exception) {
            return false;
        }
    }
}