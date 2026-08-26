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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForumServiceTest {

    @Mock private ForumRoomRepository roomRepository;
    @Mock private ForumMessageRepository messageRepository;
    @Mock private ForumMessageReportRepository reportRepository;
    @Mock private ForumMessageLikeRepository likeRepository;
    @Mock private UserRepository userRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ForumService forumService;

    private User sender;
    private ForumRoom room;
    private UUID roomId;
    private ForumMessage message;
    private UUID messageId;

    @BeforeEach
    void setUp() {
        forumService = new ForumService(roomRepository, messageRepository, reportRepository, likeRepository,
                userRepository, messagingTemplate);

        sender = User.builder().id(UUID.randomUUID()).email("sender@example.com").role(Role.USER).build();

        roomId = UUID.randomUUID();
        room = new ForumRoom();
        ReflectionTestUtils.setField(room, "id", roomId);
        room.setName("Test Room");

        messageId = UUID.randomUUID();
        message = new ForumMessage();
        ReflectionTestUtils.setField(message, "id", messageId);
        message.setRoom(room);
        message.setSender(sender);
        message.setText("hello everyone");
    }

    private String updatesTopic() {
        return "/topic/forum-rooms/" + roomId + "/updates";
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
    void adminRecallMessage_marksRecalled_broadcastsToRoomTopic_andDeletesReports() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        forumService.adminRecallMessage(messageId);

        assertThat(message.isRecalled()).isTrue();
        verify(messagingTemplate).convertAndSend(eq(updatesTopic()), any(ForumMessageUpdateEvent.class));
        verify(reportRepository).deleteByMessageId(messageId);
    }

    @Test
    void adminRecallMessage_stillDeletesReports_butSkipsBroadcast_whenAlreadyRecalled() {
        message.setRecalled(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        forumService.adminRecallMessage(messageId);

        verify(messagingTemplate, never()).convertAndSend(any(String.class), any(Object.class));
        verify(reportRepository).deleteByMessageId(messageId);
    }

    @Test
    void clearRoomMessages_deletesByRoomId_andBroadcastsToRoomTopic() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        forumService.clearRoomMessages(roomId);

        verify(messageRepository).deleteByRoomId(roomId);
        verify(messagingTemplate).convertAndSend(eq("/topic/forum-rooms/" + roomId + "/cleared"), any(Object.class));
    }

    @Test
    void clearRoomMessages_throwsNotFound_whenRoomDoesNotExist() {
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forumService.clearRoomMessages(roomId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);

        verify(messageRepository, never()).deleteByRoomId(any());
    }

    @Test
    void setMessageHidden_throwsNotFound_whenMessageDoesNotExist() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> forumService.setMessageHidden(messageId, true))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void setMessageHidden_setsHiddenTrue_andBroadcastsUpdateToRoomTopic() {
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        forumService.setMessageHidden(messageId, true);

        assertThat(message.isHidden()).isTrue();
        verify(messagingTemplate).convertAndSend(eq(updatesTopic()), any(ForumMessageUpdateEvent.class));
    }

    @Test
    void setMessageHidden_setsHiddenFalse_whenUnhiding() {
        message.setHidden(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        forumService.setMessageHidden(messageId, false);

        assertThat(message.isHidden()).isFalse();
    }
}
