package com.mingo.backend.notification;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.notification.dto.NotificationResponse;
import com.mingo.backend.post.Comment;
import com.mingo.backend.post.Post;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository,
                                SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Records a notification for {@code recipient}. Silently does nothing when the recipient
     * is the same as the actor, since users never need to be notified about their own actions.
     * Also pushes it over WebSocket so a connected recipient sees it instantly instead of
     * waiting for the client's next poll.
     */
    @Transactional
    public void notify(User recipient, User actor, NotificationType type, Post post, Comment comment) {
        if (recipient.getId().equals(actor.getId())) return;

        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(type);
        notification.setPost(post);
        notification.setComment(comment);
        notificationRepository.saveAndFlush(notification);

        messagingTemplate.convertAndSendToUser(
                recipient.getEmail(), "/queue/notifications", NotificationResponse.from(notification));
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(String email, int page, int size) {
        User me = findUser(email);
        Pageable pageable = PageRequest.of(page, size);
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.getId(), pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(String email) {
        User me = findUser(email);
        return notificationRepository.countByRecipientIdAndReadFalse(me.getId());
    }

    @Transactional
    public void markAsRead(String email, UUID notificationId) {
        User me = findUser(email);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!notification.getRecipient().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền với thông báo này");
        }
        notification.setRead(true);
    }

    @Transactional
    public void markAllAsRead(String email) {
        User me = findUser(email);
        notificationRepository.markAllAsRead(me.getId());
    }

    @Transactional
    public void deleteAll(String email) {
        User me = findUser(email);
        notificationRepository.deleteAllByRecipientId(me.getId());
    }

    @Transactional
    public void delete(String email, UUID notificationId) {
        User me = findUser(email);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!notification.getRecipient().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền với thông báo này");
        }
        notificationRepository.delete(notification);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }
}
