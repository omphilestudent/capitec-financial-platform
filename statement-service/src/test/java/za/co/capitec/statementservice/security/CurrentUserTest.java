
package za.co.capitec.statementservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import za.co.capitec.statementservice.common.exception.AccessDeniedStatementException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CurrentUserTest {

    private Jwt jwt(List<String> roles, String customerId) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "HS256")
                .subject("someone")
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (customerId != null) {
            builder.claim("customer_id", customerId);
        }
        return builder.build();
    }

    @Test
    void customerTokenIsBoundToItsOwnCustomerIdOnly() {
        UUID mine = UUID.randomUUID();
        CurrentUser user = CurrentUser.from(jwt(List.of("CUSTOMER"), mine.toString()));

        assertFalse(user.staff());
        assertTrue(user.canAccessCustomer(mine));
        assertFalse(user.canAccessCustomer(UUID.randomUUID()));
    }

    @Test
    void staffTokenCanAccessAnyCustomer() {
        CurrentUser user = CurrentUser.from(jwt(List.of("STAFF"), null));
        assertTrue(user.staff());
        assertTrue(user.canAccessCustomer(UUID.randomUUID()));
    }

    @Test
    void tokenWithoutAnyStatementRoleIsRejected() {
        assertThrows(AccessDeniedStatementException.class,
                () -> CurrentUser.from(jwt(List.of("SOMETHING_ELSE"), null)));
    }

    @Test
    void customerTokenWithMalformedCustomerIdIsRejected() {
        assertThrows(AccessDeniedStatementException.class,
                () -> CurrentUser.from(jwt(List.of("CUSTOMER"), "not-a-uuid")));
    }
}
