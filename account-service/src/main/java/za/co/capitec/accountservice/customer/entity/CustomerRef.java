package za.co.capitec.accountservice.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** A customer known to this service from a CustomerCreated event. */
@Entity
@Table(name = "customer_refs")
public class CustomerRef {

    @Id
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "client_type", nullable = false, length = 30)
    private String clientType;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    protected CustomerRef() {
    }

    public CustomerRef(UUID customerId, String firstName, String lastName,
                       String email, String clientType) {
        this.customerId = customerId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.clientType = clientType;
        this.receivedAt = LocalDateTime.now();
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public String getClientType() {
        return clientType;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }
}
