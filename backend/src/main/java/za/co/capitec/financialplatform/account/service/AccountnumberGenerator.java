package za.co.capitec.financialplatform.account.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberGenerator {

private final SecureRandom random = new SecureRandom();

public String generate() {
    StringBuilder accountNumber = new StringBuilder();

    for (int i = 0; i < 11; i++){
    accountNumber.append(random.nextInt(10));
    }

    return accountNumber.toString();
}
}
