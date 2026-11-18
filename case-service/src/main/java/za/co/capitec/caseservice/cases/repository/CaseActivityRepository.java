package za.co.capitec.caseservice.cases.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.caseservice.cases.entity.CaseActivity;

import java.util.List;
import java.util.UUID;

public interface CaseActivityRepository extends JpaRepository<CaseActivity, UUID> {

    List<CaseActivity> findByCaseIdOrderByCreatedAtAsc(UUID caseId);
}
