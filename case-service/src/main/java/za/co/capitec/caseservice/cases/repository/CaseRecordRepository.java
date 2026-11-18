package za.co.capitec.caseservice.cases.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.co.capitec.caseservice.cases.entity.CaseRecord;

import java.util.Optional;
import java.util.UUID;

public interface CaseRecordRepository extends JpaRepository<CaseRecord, UUID>, JpaSpecificationExecutor<CaseRecord> {

    Optional<CaseRecord> findBySourceFraudId(UUID sourceFraudId);

    @Query(value = "select nextval('case_number_seq')", nativeQuery = true)
    long nextCaseNumber();
}
