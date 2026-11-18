package za.co.capitec.frauddetection.detection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.co.capitec.frauddetection.detection.entity.FraudDetection;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FraudDetectionRepository extends JpaRepository<FraudDetection, UUID>, JpaSpecificationExecutor<FraudDetection> {

    Optional<FraudDetection> findByTransactionId(UUID transactionId);

    List<FraudDetection> findByCaseIdOrderByDetectedAtDesc(UUID caseId);

    @Query(value = "select 'FRD-' || nextval('fraud_reference_seq')", nativeQuery = true)
    String nextFraudReference();
}
