package za.co.capitec.notificationservice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.capitec.notificationservice.notification.entity.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsBySourceEventId(UUID sourceEventId);
}
