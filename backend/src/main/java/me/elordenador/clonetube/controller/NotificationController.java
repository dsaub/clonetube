package me.elordenador.clonetube.controller;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.decorators.RequireAuth;
import me.elordenador.clonetube.dtos.NotificationDTO;
import me.elordenador.clonetube.services.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @RequireAuth
    public List<NotificationDTO> list(Authentication auth) {
        return notificationService.list(auth.getName());
    }

    @GetMapping("/unread-count")
    @RequireAuth
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("unread", notificationService.unreadCount(auth.getName()));
    }

    @PostMapping("/{id}/read")
    @RequireAuth
    public NotificationDTO markRead(Authentication auth, @PathVariable Long id) {
        return notificationService.markRead(id, auth.getName());
    }
}
