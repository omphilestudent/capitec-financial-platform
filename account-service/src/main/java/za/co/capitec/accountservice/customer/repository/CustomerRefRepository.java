package za.co.capitec.accountservice.customer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import za.co.capitec.accountservice.customer.entity.CustomerRef;

import java.util.UUID;

public interface CustomerRefRepository extends JpaRepository<CustomerRef, UUID> {
}
