package za.co.capitec.apigateway.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reports the live health of each downstream service, read from its
 * /actuator/health endpoint. A service that cannot be reached is reported as
 * DOWN with reachable=false; nothing is assumed.
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceHealthController {

    public record ServiceHealth(String name, boolean reachable, String status, String url) {}

    private final RestClient restClient;
    private final Map<String, String> services = new LinkedHashMap<>();

    public ServiceHealthController(
            @Value("${customer.service.url}") String customerServiceUrl,
            @Value("${account.service.url}") String accountServiceUrl,
            @Value("${transaction.service.url}") String transactionServiceUrl,
            @Value("${case.service.url}") String caseServiceUrl,
            @Value("${fraud.service.url}") String fraudServiceUrl) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.restClient = RestClient.builder().requestFactory(factory).build();

        services.put("customer-service", customerServiceUrl);
        services.put("account-service", accountServiceUrl);
        services.put("transaction-service", transactionServiceUrl);
        services.put("case-service", caseServiceUrl);
        services.put("fraud-detection-service", fraudServiceUrl);
    }

    @GetMapping("/health")
    public List<ServiceHealth> health() {
        List<ServiceHealth> result = new ArrayList<>();
        services.forEach((name, baseUrl) -> result.add(check(name, baseUrl)));
        return result;
    }

    private ServiceHealth check(String name, String baseUrl) {
        try {
            Map<?, ?> body = restClient.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .body(Map.class);
            String status = body == null || body.get("status") == null
                    ? "UNKNOWN" : body.get("status").toString();
            return new ServiceHealth(name, true, status, baseUrl);
        } catch (Exception exception) {
            return new ServiceHealth(name, false, "DOWN", baseUrl);
        }
    }
}
