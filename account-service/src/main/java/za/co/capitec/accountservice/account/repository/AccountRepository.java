package za.co.capitec.accountservice.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.accountservice.account.entity.Account;

import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    List<Account> findByCustomerId(UUID customerId);

    boolean existsByAccountNumber(String accountNumber);
}
