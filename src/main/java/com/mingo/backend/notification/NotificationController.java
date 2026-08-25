package com.mingo.backend.notification;

import com.mingo.backend.notification.dto.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(Authentication auth,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return notificationService.list(auth.getName(), page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", notificationService.unreadCount(auth.getName()));
    }

    @PutMapping("/{id}/read")
    public void markAsRead(Authentication auth, @PathVariable UUID id) {
        notificationService.markAsRead(auth.getName(), id);
    }

    @PutMapping("/read-all")
    public void markAllAsRead(Authentication auth) {
        notificationService.markAllAsRead(auth.getName());
    }

    @DeleteMapping
    public void deleteAll(Authentication auth) {
        notificationService.deleteAll(auth.getName());
    }

    @DeleteMapping("/{id}")
    public void delete(Authentication auth, @PathVariable UUID id) {
        notificationService.delete(auth.getName(), id);
    }
}
