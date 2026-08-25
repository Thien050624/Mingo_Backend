package com.mingo.backend.chat;

import com.mingo.backend.chat.dto.ConversationResponse;
import com.mingo.backend.chat.dto.MessageResponse;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class ChatServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageLikeRepository messageLikeRepository;
    @Mock private MessageReportRepository messageReportRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private ChatService chatService;

    private User me;
    private User other;
    private Conversation conversation;
    private ConversationParticipant myParticipant;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(conversationRepository, participantRepository, messageRepository,
                messageLikeRepository, messageReportRepository, userRepository, userBlockRepository, messagingTemplate);

        me = User.builder().id(UUID.randomUUID()).email("me@example.com").role(Role.USER).build();
        other = User.builder().id(UUID.randomUUID()).email("other@example.com").role(Role.USER).build();
        lenient().when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));

        conversation = new Conversation();
        ReflectionTestUtils.setField(conversation, "id", UUID.randomUUID());
        conversation.setGroup(false);
        conversation.setCreatedBy(me);

        myParticipant = new ConversationParticipant();
        myParticipant.setConversation(conversation);
        myParticipant.setUser(me);
    }

    private ConversationParticipant participantFor(User user) {
        ConversationParticipant cp = new ConversationParticipant();
        cp.setConversation(conversation);
        cp.setUser(user);
        return cp;
    }

    @Test
    void getOrCreateDirect_throwsBadRequest_whenTargetIsSelf() {
        assertThatThrownBy(() -> chatService.getOrCreateDirect("me@example.com", me.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void getOrCreateDirect_throwsForbidden_whenEitherHasBlockedTheOther() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(true);

        assertThatThrownBy(() -> chatService.getOrCreateDirect("me@example.com", other.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);

        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCreateDirect_reusesExistingConversation_insteadOfCreatingNew() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(false);
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(participantRepository.findDirectConversation(me.getId(), other.getId()))
                .thenReturn(Optional.of(conversation));
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        when(participantRepository.findByConversationId(conversation.getId())).thenReturn(List.of(myParticipant));

        chatService.getOrCreateDirect("me@example.com", other.getId());

        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void getOrCreateDirect_createsNewConversation_whenNoneExists() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(false);
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(participantRepository.findDirectConversation(me.getId(), other.getId())).thenReturn(Optional.empty());
        when(participantRepository.findByConversationIdAndUserId(any(), eq(me.getId())))
                .thenReturn(Optional.of(myParticipant));
        when(participantRepository.findByConversationId(any())).thenReturn(List.of(myParticipant));

        chatService.getOrCreateDirect("me@example.com", other.getId());

        verify(conversationRepository).saveAndFlush(any(Conversation.class));
        verify(participantRepository, times(2)).save(any(ConversationParticipant.class));
    }

    @Test
    void sendMessage_throwsBadRequest_whenNoTextImageOrFile() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));

        assertThatThrownBy(() -> chatService.sendMessage("me@example.com", conversation.getId(),
                null, null, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void sendMessage_throwsForbidden_whenCallerIsNotAParticipant() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage("me@example.com", conversation.getId(),
                "hi", null, null, null, null, null, null))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void sendMessage_broadcastsToOtherParticipants_butNotToSender() {
        ConversationParticipant otherParticipant = participantFor(other);
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        when(participantRepository.findByConversationId(conversation.getId()))
                .thenReturn(List.of(myParticipant, otherParticipant));

        chatService.sendMessage("me@example.com", conversation.getId(), "hello", null, null, null, null, null, null);

        verify(messagingTemplate).convertAndSendToUser(eq("other@example.com"), eq("/queue/messages"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(eq("me@example.com"), any(), any());
    }

    @Test
    void recallMessage_throwsForbidden_whenNotOwnMessage() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(other);
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.recallMessage("me@example.com", conversation.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void recallMessage_isNoOp_whenAlreadyRecalled() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(me);
        message.setRecalled(true);
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));

        chatService.recallMessage("me@example.com", conversation.getId(), UUID.randomUUID());

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    @Test
    void editMessage_throwsBadRequest_whenMessageAlreadyRecalled() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(me);
        message.setRecalled(true);
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatService.editMessage("me@example.com", conversation.getId(), UUID.randomUUID(), "new text"))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void toggleLike_addsLike_whenNotYetLiked() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(other);
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));
        when(messageLikeRepository.findByMessageIdAndUserId(any(), eq(me.getId()))).thenReturn(Optional.empty());
        when(messageLikeRepository.findByMessageId(any())).thenReturn(List.of());
        when(participantRepository.findByConversationId(conversation.getId())).thenReturn(List.of(myParticipant));

        chatService.toggleLike("me@example.com", conversation.getId(), UUID.randomUUID());

        verify(messageLikeRepository).save(any(MessageLike.class));
        verify(messageLikeRepository, never()).delete(any());
    }

    @Test
    void toggleLike_removesLike_whenAlreadyLiked() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(other);
        when(messageRepository.findById(any())).thenReturn(Optional.of(message));
        MessageLike existingLike = new MessageLike();
        when(messageLikeRepository.findByMessageIdAndUserId(any(), eq(me.getId()))).thenReturn(Optional.of(existingLike));
        when(messageLikeRepository.findByMessageId(any())).thenReturn(List.of());
        when(participantRepository.findByConversationId(conversation.getId())).thenReturn(List.of(myParticipant));

        chatService.toggleLike("me@example.com", conversation.getId(), UUID.randomUUID());

        verify(messageLikeRepository).delete(existingLike);
        verify(messageLikeRepository, never()).save(any());
    }

    @Test
    void leaveGroup_throwsBadRequest_whenConversationIsNotAGroup() {
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));

        assertThatThrownBy(() -> chatService.leaveGroup("me@example.com", conversation.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void disbandGroup_throwsForbidden_whenCallerIsNotTheGroupCreator() {
        conversation.setGroup(true);
        conversation.setCreatedBy(other);
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));

        assertThatThrownBy(() -> chatService.disbandGroup("me@example.com", conversation.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);

        verify(conversationRepository, never()).delete(any());
    }

    @Test
    void removeMember_throwsBadRequest_whenTargetIsSelf() {
        conversation.setGroup(true);
        conversation.setCreatedBy(me);
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));

        assertThatThrownBy(() -> chatService.removeMember("me@example.com", conversation.getId(), me.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void removeMember_throwsForbidden_whenCallerIsNotTheGroupCreator() {
        conversation.setGroup(true);
        conversation.setCreatedBy(other);
        when(participantRepository.findByConversationIdAndUserId(conversation.getId(), me.getId()))
                .thenReturn(Optional.of(myParticipant));

        assertThatThrownBy(() -> chatService.removeMember("me@example.com", conversation.getId(), other.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void createGroup_throwsBadRequest_whenNoOtherMembersProvided() {
        assertThatThrownBy(() -> chatService.createGroup("me@example.com", "Nhóm test", List.of(me.getId())))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void createGroup_throwsBadRequest_whenNameIsBlank() {
        assertThatThrownBy(() -> chatService.createGroup("me@example.com", "  ", List.of(other.getId())))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void adminRecallMessage_throwsNotFound_whenMessageDoesNotExist() {
        UUID messageId = UUID.randomUUID();
        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.adminRecallMessage(messageId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);

        verify(messageReportRepository, never()).deleteByMessageId(any());
    }

    @Test
    void adminRecallMessage_marksRecalled_broadcastsToAllParticipants_andDeletesReports() {
        UUID messageId = UUID.randomUUID();
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(other);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        ConversationParticipant otherParticipant = participantFor(other);
        when(participantRepository.findByConversationId(conversation.getId()))
                .thenReturn(List.of(myParticipant, otherParticipant));

        chatService.adminRecallMessage(messageId);

        assertThat(message.isRecalled()).isTrue();
        verify(messagingTemplate).convertAndSendToUser(eq("me@example.com"), eq("/queue/message-updates"), any());
        verify(messagingTemplate).convertAndSendToUser(eq("other@example.com"), eq("/queue/message-updates"), any());
        verify(messageReportRepository).deleteByMessageId(messageId);
    }

    @Test
    void adminRecallMessage_stillDeletesReports_butSkipsBroadcast_whenAlreadyRecalled() {
        UUID messageId = UUID.randomUUID();
        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(other);
        message.setRecalled(true);
        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        chatService.adminRecallMessage(messageId);

        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
        verify(messageReportRepository).deleteByMessageId(messageId);
        verify(participantRepository, never()).findByConversationId(any());
    }
}
