package za.co.capitec.frauddetection.detection.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.frauddetection.detection.entity.PendingCaseLink;

import java.util.UUID;

public interface PendingCaseLinkRepository extends JpaRepository<PendingCaseLink, UUID> {
}
