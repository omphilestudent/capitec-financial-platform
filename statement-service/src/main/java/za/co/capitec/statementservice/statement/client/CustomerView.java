package za.co.capitec.statementservice.statement.client;

import java.util.UUID;

public record CustomerView(UUID id, String firstName, String lastName, String clientType) {

    public String displayName() {
        return (firstName + " " + lastName).trim();
    }
}
