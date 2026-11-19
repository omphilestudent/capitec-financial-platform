package za.co.capitec.apigateway.session;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MvpSessionControllerTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-test-only";

    @Test
    void issuesAThreePartTokenWithCustomerClaimsAndAValidSignature() throws Exception {
        UUID customer = UUID.randomUUID();
        var response = new MvpSessionController(SECRET, "test-issuer").create(
                new MvpSessionController.SessionRequest(customer));

        String[] parts = response.token().split("\\.");
        assertEquals(3, parts.length);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertTrue(payload.contains("\"customer_id\":\"" + customer + "\""));
        assertTrue(payload.contains("\"roles\":[\"CUSTOMER\"]"));
        assertTrue(payload.contains("\"iss\":\"test-issuer\""));
        assertEquals(customer, response.customerId());
        assertEquals(30 * 60, response.expiresInSeconds());

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8)));
        assertEquals(expected, parts[2]);
    }

    @Test
    void refusesToStartWithAShortSecret() {
        assertThrows(IllegalStateException.class, () -> new MvpSessionController("short", "x"));
    }

    @Test
    void rejectsARequestWithoutACustomer() {
        assertThrows(IllegalArgumentException.class,
                () -> new MvpSessionController(SECRET, "x").create(new MvpSessionController.SessionRequest(null)));
    }
}
