package za.co.capitec.notificationservice.notification.controller;

import org.springframework.web.bind.annotation.*;
import za.co.capitec.notificationservice.notification.service.NotificationService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    public record NotificationResponse(UUID id, String type, String severity, String title, String message,
                                       UUID caseId, UUID fraudId, UUID customerId,
                                       LocalDateTime createdAt, LocalDateTime readAt) {}

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(unreadOnly, page, size).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getNotificationType(), n.getSeverity(), n.getTitle(),
                        n.getMessage(), n.getCaseId(), n.getFraudId(), n.getCustomerId(),
                        n.getCreatedAt(), n.getReadAt()))
                .toList();
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id) {
        var n = service.markRead(id);
        return new NotificationResponse(n.getId(), n.getNotificationType(), n.getSeverity(), n.getTitle(),
                n.getMessage(), n.getCaseId(), n.getFraudId(), n.getCustomerId(),
                n.getCreatedAt(), n.getReadAt());
    }
}
