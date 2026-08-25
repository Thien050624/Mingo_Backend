package com.mingo.backend.notification;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private NotificationService notificationService;

    private User recipient;
    private User actor;
    private Notification notification;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository, messagingTemplate);

        recipient = User.builder().id(UUID.randomUUID()).email("recipient@example.com").role(Role.USER).build();
        actor = User.builder().id(UUID.randomUUID()).email("actor@example.com").role(Role.USER).build();

        notification = new Notification();
        notification.setRecipient(recipient);
        notification.setActor(actor);
        notification.setType(NotificationType.POST_REACTION);
    }

    @Test
    void notify_doesNothing_whenRecipientIsActor() {
        notificationService.notify(recipient, recipient, NotificationType.POST_REACTION, null, null);

        verify(notificationRepository, never()).saveAndFlush(any());
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void notify_savesAndBroadcasts_whenRecipientDiffersFromActor() {
        notificationService.notify(recipient, actor, NotificationType.POST_COMMENT, null, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo(recipient);
        assertThat(saved.getActor()).isEqualTo(actor);
        assertThat(saved.getType()).isEqualTo(NotificationType.POST_COMMENT);

        verify(messagingTemplate).convertAndSendToUser(eq("recipient@example.com"), eq("/queue/notifications"), any());
    }

    @Test
    void list_returnsPageFromRepository() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(recipient.getId()), any()))
                .thenReturn(page);

        Page<?> result = notificationService.list("recipient@example.com", 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void unreadCount_returnsCountFromRepository() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        when(notificationRepository.countByRecipientIdAndReadFalse(recipient.getId())).thenReturn(3L);

        long count = notificationService.unreadCount("recipient@example.com");

        assertThat(count).isEqualTo(3L);
    }

    @Test
    void markAsRead_throwsNotFound_whenNotificationDoesNotExist() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead("recipient@example.com", id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void markAsRead_throwsForbidden_whenCallerIsNotTheRecipient() {
        User someoneElse = User.builder().id(UUID.randomUUID()).email("someone@example.com").role(Role.USER).build();
        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(someoneElse));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.markAsRead("someone@example.com", id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void markAsRead_setsReadTrue_whenCallerIsTheRecipient() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        notificationService.markAsRead("recipient@example.com", id);

        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAllAsRead_delegatesToRepositoryBulkUpdate() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));

        notificationService.markAllAsRead("recipient@example.com");

        verify(notificationRepository).markAllAsRead(recipient.getId());
    }

    @Test
    void deleteAll_delegatesToRepositoryBulkDelete() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));

        notificationService.deleteAll("recipient@example.com");

        verify(notificationRepository).deleteAllByRecipientId(recipient.getId());
    }

    @Test
    void delete_throwsNotFound_whenNotificationDoesNotExist() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.delete("recipient@example.com", id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void delete_throwsForbidden_whenCallerIsNotTheRecipient() {
        User someoneElse = User.builder().id(UUID.randomUUID()).email("someone@example.com").role(Role.USER).build();
        when(userRepository.findByEmail("someone@example.com")).thenReturn(Optional.of(someoneElse));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        assertThatThrownBy(() -> notificationService.delete("someone@example.com", id))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.FORBIDDEN);

        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void delete_removesNotification_whenCallerIsTheRecipient() {
        when(userRepository.findByEmail("recipient@example.com")).thenReturn(Optional.of(recipient));
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        notificationService.delete("recipient@example.com", id);

        verify(notificationRepository).delete(notification);
    }
}
