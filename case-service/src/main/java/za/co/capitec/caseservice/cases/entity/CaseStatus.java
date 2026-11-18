package za.co.capitec.caseservice.cases.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Case lifecycle. A status may move only to the statuses listed for it; anything
 * else is rejected. CLOSED is final.
 */
public enum CaseStatus {
    NEW, OPEN, IN_PROGRESS, PENDING, ESCALATED, RESOLVED, CLOSED;

    private static final Set<CaseStatus> NONE = EnumSet.noneOf(CaseStatus.class);

    public Set<CaseStatus> allowedNext() {
        return switch (this) {
            case NEW -> EnumSet.of(OPEN, IN_PROGRESS, PENDING, ESCALATED, RESOLVED);
            case OPEN -> EnumSet.of(IN_PROGRESS, PENDING, ESCALATED, RESOLVED);
            case IN_PROGRESS -> EnumSet.of(PENDING, ESCALATED, RESOLVED);
            case PENDING -> EnumSet.of(IN_PROGRESS, ESCALATED, RESOLVED);
            case ESCALATED -> EnumSet.of(IN_PROGRESS, RESOLVED);
            case RESOLVED -> EnumSet.of(CLOSED);
            case CLOSED -> NONE;
        };
    }

    public boolean canMoveTo(CaseStatus next) {
        return allowedNext().contains(next);
    }
}
