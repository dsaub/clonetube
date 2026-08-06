package me.elordenador.clonetube.services;

import lombok.RequiredArgsConstructor;
import me.elordenador.clonetube.dtos.NotificationDTO;
import me.elordenador.clonetube.models.Notification;
import me.elordenador.clonetube.models.User;
import me.elordenador.clonetube.repository.NotificationRepository;
import me.elordenador.clonetube.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Transactional
    public Notification notify(User user, String type, String title, String body) {
        Notification notification = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .body(body)
                .isRead(false)
                .createdAt(Instant.now())
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> list(String username) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId(username)).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(String username) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId(username));
    }

    @Transactional
    public NotificationDTO markRead(Long id, String username) {
        Integer userId = userId(username);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (!notification.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your notification");
        }
        notification.setIsRead(true);
        return toDto(notificationRepository.save(notification));
    }

    private Integer userId(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"))
                .getId();
    }

    private NotificationDTO toDto(Notification n) {
        return new NotificationDTO(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getIsRead(), n.getCreatedAt());
    }
}
