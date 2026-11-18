package za.co.capitec.frauddetection.detection.entity;

import java.util.EnumSet;
import java.util.Set;

/** Investigation lifecycle of a fraud record. FALSE_POSITIVE and CONFIRMED_FRAUD are final. */
public enum InvestigationStatus {
    OPEN, UNDER_REVIEW, CONFIRMED_FRAUD, FALSE_POSITIVE;

    public Set<InvestigationStatus> allowedNext() {
        return switch (this) {
            case OPEN -> EnumSet.of(UNDER_REVIEW, FALSE_POSITIVE);
            case UNDER_REVIEW -> EnumSet.of(CONFIRMED_FRAUD, FALSE_POSITIVE);
            case CONFIRMED_FRAUD, FALSE_POSITIVE -> EnumSet.noneOf(InvestigationStatus.class);
        };
    }
}
