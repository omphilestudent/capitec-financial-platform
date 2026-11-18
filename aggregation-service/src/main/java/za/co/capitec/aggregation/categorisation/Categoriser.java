package za.co.capitec.aggregation.categorisation;

import org.springframework.stereotype.Component;
import za.co.capitec.aggregation.domain.Category;
import za.co.capitec.aggregation.domain.NormalizedTransaction.Direction;

import java.util.Locale;
import java.util.Map;

@Component
public class Categoriser {

    private static final Map<String, Category> MCC = Map.of(
            "5411", Category.GROCERIES,
            "5541", Category.FUEL,
            "5542", Category.FUEL,
            "4900", Category.UTILITIES,
            "6011", Category.ATM_WITHDRAWAL);

    public Category categorise(String description, String mcc, Direction direction) {
        if (mcc != null && MCC.containsKey(mcc)) {
            return MCC.get(mcc);
        }
        String text = description == null ? "" : description.toLowerCase(Locale.ROOT);
        if (containsAny(text, "salary", "payroll")) return Category.SALARY;
        if (containsAny(text, "fuel", "engen", "shell", "sasol", "bp ")) return Category.FUEL;
        if (containsAny(text, "grocer", "checkers", "woolworths", "pick n pay", "shoprite")) return Category.GROCERIES;
        if (containsAny(text, "electric", "power", "water", "municipal", "telkom", "utility")) return Category.UTILITIES;
        if (containsAny(text, "loan", "repayment", "mortgage")) return Category.LOAN_REPAYMENT;
        if (containsAny(text, "atm", "cash withdrawal")) return Category.ATM_WITHDRAWAL;
        if (containsAny(text, "invoice", "supplier", "business")) return Category.BUSINESS_PAYMENT;
        if (containsAny(text, "eft", "transfer", "landlord")) return Category.TRANSFER;
        if (direction == Direction.CREDIT) {
            return Category.INCOME_OTHER;
        }
        if (containsAny(text, "card", "purchase", "pos", "takealot")) return Category.CARD_PURCHASE;
        return Category.OTHER;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
