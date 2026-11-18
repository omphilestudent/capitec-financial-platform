package za.co.capitec.caseservice.cases.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CaseStatusTest {

    @Test
    void newCaseCanBeWorkedOrResolved() {
        assertTrue(CaseStatus.NEW.canMoveTo(CaseStatus.IN_PROGRESS));
        assertTrue(CaseStatus.NEW.canMoveTo(CaseStatus.RESOLVED));
    }

    @Test
    void newCaseCannotBeClosedDirectly() {
        assertFalse(CaseStatus.NEW.canMoveTo(CaseStatus.CLOSED));
    }

    @Test
    void resolvedCaseCanOnlyBeClosed() {
        assertTrue(CaseStatus.RESOLVED.canMoveTo(CaseStatus.CLOSED));
        assertFalse(CaseStatus.RESOLVED.canMoveTo(CaseStatus.IN_PROGRESS));
    }

    @Test
    void closedIsFinal() {
        for (CaseStatus next : CaseStatus.values()) {
            assertFalse(CaseStatus.CLOSED.canMoveTo(next), "CLOSED must not move to " + next);
        }
    }

    @Test
    void escalationIsAllowedFromActiveStatuses() {
        assertTrue(CaseStatus.OPEN.canMoveTo(CaseStatus.ESCALATED));
        assertTrue(CaseStatus.IN_PROGRESS.canMoveTo(CaseStatus.ESCALATED));
        assertTrue(CaseStatus.PENDING.canMoveTo(CaseStatus.ESCALATED));
    }

    @Test
    void noStatusMovesToItself() {
        for (CaseStatus s : CaseStatus.values()) {
            assertFalse(s.canMoveTo(s), s + " must not transition to itself");
        }
    }
}
