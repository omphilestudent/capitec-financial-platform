package za.co.capitec.financialplatform.customer.exception;

public class DuplicateCustomerEmailException extends RuntimeException {

    public DuplicateCustomerEmailException(String message) {
        super(message);
    }
}
