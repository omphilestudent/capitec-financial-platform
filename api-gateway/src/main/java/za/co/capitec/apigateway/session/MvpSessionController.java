package za.co.capitec.apigateway.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/session")
public class MvpSessionController {

    public record SessionRequest(UUID customerId) {
    }

    public record SessionResponse(UUID customerId, String token, long expiresInSeconds) {
    }

    private static final long TTL_SECONDS = 30 * 60;

    private final byte[] secret;
    private final String issuer;

    public MvpSessionController(@Value("${mvp.session.jwt-secret}") String secret,
                                @Value("${mvp.session.jwt-issuer:capitec-fin-platform}") String issuer) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("MVP session signing secret must be set and at least 32 bytes long");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
    }

    @PostMapping
    public SessionResponse create(@RequestBody SessionRequest request) {
        if (request == null || request.customerId() == null) {
            throw new IllegalArgumentException("customerId is required");
        }
        long now = Instant.now().getEpochSecond();
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        String header = enc.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payloadJson = "{\"sub\":\"customer-" + request.customerId() + "\",\"roles\":[\"CUSTOMER\"],\"customer_id\":\""
                + request.customerId() + "\",\"iss\":\"" + issuer + "\",\"iat\":" + now + ",\"exp\":" + (now + TTL_SECONDS)
                + ",\"jti\":\"" + UUID.randomUUID() + "\"}";
        String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = sign(header + "." + payload);
        return new SessionResponse(request.customerId(), header + "." + payload + "." + signature, TTL_SECONDS);
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to sign session", e);
        }
    }
}
