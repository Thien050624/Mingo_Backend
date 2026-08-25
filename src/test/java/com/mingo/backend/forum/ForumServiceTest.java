package com.mingo.backend.forum;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.forum.dto.ForumMessageUpdateEvent;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForumServiceTest {

    @Mock private ForumMessageRepository messageRepository;
    @Mock private ForumMessageReportRepository reportRepository;
    @Mock private ForumMessageLikeRepository likeRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ForumService forumService;

    private User sender;
    private User other;
    private ForumMessage message;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        forumService = new ForumService(messageRepository, reportRepository, likeRepository, userRepository, messagingTemplate);

        sender = User.builder().id(UUID.randomUUID()).email("sender@example.com").role(Role.USER).build();
        other = User.builder().id(UUID.randomUUID()).email("other@example.com").role(Role.USER).build();

        messageId = UUID.randomUUID();
        message = new ForumMessage();
        ReflectionTestUtils.setField(message, "id", messageId);
        message.setSender(sender);
        message.setText("hello everyone");
    }

    @Test
    void adminRecallMessage_throwsNotFound_whenMessageDoesNotExist() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forumService.adminRecallMessage(messageId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(reportRepository, never()).deleteByMessageId(any());
    }

    @Test
    void adminRecallMessage_marksRecalled_broadcastsToAllUsers_andDeletesReports() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findAll()).thenReturn(List.of(sender, other));

        forumService.adminRecallMessage(messageId);

        assertThat(message.isRecalled()).isTrue();
        verify(messagingTemplate).convertAndSendToUser(eq("sender@example.com"), eq("/queue/forum-updates"), any(ForumMessageUpdateEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq("other@example.com"), eq("/queue/forum-updates"), any(ForumMessageUpdateEvent.class));
        verify(reportRepository).deleteByMessageId(messageId);
    }

    @Test
    void adminRecallMessage_stillDeletesReports_butSkipsBroadcast_whenAlreadyRecalled() {
        message.setRecalled(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        forumService.adminRecallMessage(messageId);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verify(reportRepository).deleteByMessageId(messageId);
        verify(userRepository, never()).findAll();
    }

    @Test
    void clearAllMessages_deletesAllInBatch_andBroadcastsToEveryUser() {
        when(userRepository.findAll()).thenReturn(List.of(sender, other));

        forumService.clearAllMessages();

        verify(messageRepository).deleteAllInBatch();
        verify(messagingTemplate).convertAndSendToUser(eq("sender@example.com"), eq("/queue/forum-cleared"), any());
        verify(messagingTemplate).convertAndSendToUser(eq("other@example.com"), eq("/queue/forum-cleared"), any());
    }

    @Test
    void setMessageHidden_throwsNotFound_whenMessageDoesNotExist() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forumService.setMessageHidden(messageId, true))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void setMessageHidden_setsHiddenTrue_andBroadcastsUpdateToEveryUser() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findAll()).thenReturn(List.of(sender, other));

        forumService.setMessageHidden(messageId, true);

        assertThat(message.isHidden()).isTrue();
        verify(messagingTemplate).convertAndSendToUser(eq("sender@example.com"), eq("/queue/forum-updates"), any(ForumMessageUpdateEvent.class));
        verify(messagingTemplate).convertAndSendToUser(eq("other@example.com"), eq("/queue/forum-updates"), any(ForumMessageUpdateEvent.class));
    }

    @Test
    void setMessageHidden_setsHiddenFalse_whenUnhiding() {
        message.setHidden(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(userRepository.findAll()).thenReturn(List.of());

        forumService.setMessageHidden(messageId, false);

        assertThat(message.isHidden()).isFalse();
    }
}
