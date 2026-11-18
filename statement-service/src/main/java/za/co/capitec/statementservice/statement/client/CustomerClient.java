package za.co.capitec.statementservice.statement.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import za.co.capitec.statementservice.common.exception.StatementNotFoundException;
import za.co.capitec.statementservice.common.exception.UpstreamServiceException;

import java.util.UUID;

@Component
public class CustomerClient {

    private final RestClient restClient;

    public CustomerClient(@Value("${customer.service.url}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public CustomerView getCustomer(UUID customerId) {
        try {
            CustomerView customer = restClient.get()
                    .uri("/api/v1/customers/{id}", customerId)
                    .retrieve()
                    .body(CustomerView.class);
            if (customer == null) {
                throw new UpstreamServiceException("customer-service returned an empty response", null);
            }
            return customer;
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new StatementNotFoundException("Customer not found: " + customerId);
        } catch (RestClientResponseException | ResourceAccessException e) {
            throw new UpstreamServiceException("customer-service is unavailable", e);
        }
    }
}
