package za.co.capitec.financialplatform.account.dto;

import za.co.capitec.financialplatform.account.entity.AccountStatus;
import za.co.capitec.financialplatform.account.entity.AccountType;

/**
 * Partial update: null fields are left unchanged.
 * Closing an account is done with DELETE, so CLOSED is not accepted here.
 */
public record UpdateAccountRequest(

    AccountType accountType,

    AccountStatus status
){

}
