package za.co.capitec.accountservice.account.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import za.co.capitec.accountservice.customer.repository.CustomerRefRepository;

import java.util.UUID;

@Component
public class CustomerServiceClient {

    private final RestClient restClient;
    private final CustomerRefRepository customerRefRepository;

    public CustomerServiceClient(
            RestClient.Builder builder,
            @Value("${customer.service.url}") String customerServiceUrl,
            CustomerRefRepository customerRefRepository
    ) {
        this.restClient = builder
                .baseUrl(customerServiceUrl)
                .build();
        this.customerRefRepository = customerRefRepository;
    }

    /**
     * Checks the customers received through customer.events first. Only
     * customers not yet known locally are looked up in customer-service.
     */
    public boolean customerExists(UUID customerId) {

        if (customerRefRepository.existsById(customerId)) {
            return true;
        }

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