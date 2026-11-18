package za.co.capitec.statementservice.security;

import org.springframework.security.oauth2.jwt.Jwt;
import za.co.capitec.statementservice.common.exception.AccessDeniedStatementException;

import java.util.List;
import java.util.UUID;

public record CurrentUser(String subject, boolean staff, UUID customerId) {

    public static final String ROLE_STAFF = "STAFF";
    public static final String ROLE_CUSTOMER = "CUSTOMER";

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        boolean staff = roles != null && roles.contains(ROLE_STAFF);
        boolean customer = roles != null && roles.contains(ROLE_CUSTOMER);
        UUID customerId = null;
        String claim = jwt.getClaimAsString("customer_id");
        if (customer && claim != null) {
            try {
                customerId = UUID.fromString(claim);
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedStatementException("Token customer_id is not a valid identifier");
            }
        }
        if (!staff && customerId == null) {
            throw new AccessDeniedStatementException("Token does not grant access to statements");
        }
        return new CurrentUser(jwt.getSubject(), staff, customerId);
    }

    public boolean canAccessCustomer(UUID customer) {
        return staff || (customerId != null && customerId.equals(customer));
    }

    public void requireCustomer(UUID customer) {
        if (!canAccessCustomer(customer)) {
            throw new AccessDeniedStatementException("You are not authorised to access this customer's statements");
        }
    }
}
