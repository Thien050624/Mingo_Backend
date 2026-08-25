package com.mingo.backend.chat;

import com.mingo.backend.chat.dto.AddMembersRequest;
import com.mingo.backend.chat.dto.ConversationResponse;
import com.mingo.backend.chat.dto.CreateDirectConversationRequest;
import com.mingo.backend.chat.dto.CreateGroupConversationRequest;
import com.mingo.backend.chat.dto.EditMessageRequest;
import com.mingo.backend.chat.dto.ForwardMessageRequest;
import com.mingo.backend.chat.dto.MessageResponse;
import com.mingo.backend.chat.dto.ReportMessageRequest;
import com.mingo.backend.chat.dto.SendMessageRequest;
import com.mingo.backend.chat.dto.SetMutedRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(Authentication auth) {
        return chatService.listConversations(auth.getName());
    }

    @GetMapping("/conversations/unread-count")
    public Map<String, Long> unreadCount(Authentication auth) {
        return Map.of("count", chatService.totalUnreadCount(auth.getName()));
    }

    @PostMapping("/conversations/direct")
    public ConversationResponse createDirect(Authentication auth, @RequestBody CreateDirectConversationRequest request) {
        return chatService.getOrCreateDirect(auth.getName(), request.otherUserId());
    }

    @PostMapping("/conversations/group")
    public ConversationResponse createGroup(Authentication auth, @RequestBody CreateGroupConversationRequest request) {
        return chatService.createGroup(auth.getName(), request.name(), request.memberIds());
    }

    @GetMapping("/conversations/{id}/messages")
    public Page<MessageResponse> listMessages(Authentication auth, @PathVariable UUID id,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "30") int size) {
        return chatService.listMessages(auth.getName(), id, page, size);
    }

    @GetMapping("/conversations/{id}/messages/search")
    public Page<MessageResponse> searchMessages(Authentication auth, @PathVariable UUID id,
                                                 @RequestParam String q,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return chatService.searchMessages(auth.getName(), id, q, page, size);
    }

    @GetMapping("/conversations/{id}/messages/{messageId}/locate")
    public Map<String, Integer> locateMessagePage(Authentication auth, @PathVariable UUID id,
                                                   @PathVariable UUID messageId,
                                                   @RequestParam(defaultValue = "30") int size) {
        return Map.of("page", chatService.locateMessagePage(auth.getName(), id, messageId, size));
    }

    @PostMapping("/conversations/{id}/messages")
    public MessageResponse sendMessage(Authentication auth, @PathVariable UUID id, @RequestBody SendMessageRequest request) {
        return chatService.sendMessage(auth.getName(), id, request.text(), request.imageUrl(),
                request.fileUrl(), request.fileName(), request.fileSize(), request.fileType(),
                request.replyToMessageId());
    }

    @PostMapping("/conversations/{id}/messages/forward")
    public MessageResponse forwardMessage(Authentication auth, @PathVariable UUID id, @RequestBody ForwardMessageRequest request) {
        return chatService.forwardMessage(auth.getName(), id, request.sourceMessageId());
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    public void recallMessage(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId) {
        chatService.recallMessage(auth.getName(), id, messageId);
    }

    @PutMapping("/conversations/{id}/messages/{messageId}")
    public MessageResponse editMessage(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId,
                                        @RequestBody EditMessageRequest request) {
        return chatService.editMessage(auth.getName(), id, messageId, request.text());
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/like")
    public MessageResponse toggleLike(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId) {
        return chatService.toggleLike(auth.getName(), id, messageId);
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/pin")
    public MessageResponse togglePin(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId) {
        return chatService.togglePin(auth.getName(), id, messageId);
    }

    @GetMapping("/conversations/{id}/messages/pinned")
    public List<MessageResponse> listPinnedMessages(Authentication auth, @PathVariable UUID id) {
        return chatService.listPinnedMessages(auth.getName(), id);
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/report")
    public void reportMessage(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId,
                               @RequestBody ReportMessageRequest request) {
        chatService.reportMessage(auth.getName(), id, messageId, request.reason());
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}/report")
    public void unreportMessage(Authentication auth, @PathVariable UUID id, @PathVariable UUID messageId) {
        chatService.unreportMessage(auth.getName(), id, messageId);
    }

    @PutMapping("/conversations/{id}/read")
    public void markRead(Authentication auth, @PathVariable UUID id) {
        chatService.markRead(auth.getName(), id);
    }

    @DeleteMapping("/conversations/{id}/participants/me")
    public void clearConversation(Authentication auth, @PathVariable UUID id) {
        chatService.clearConversation(auth.getName(), id);
    }

    @PutMapping("/conversations/{id}/mute")
    public void setMuted(Authentication auth, @PathVariable UUID id, @RequestBody SetMutedRequest request) {
        chatService.setMuted(auth.getName(), id, request.muted());
    }

    @PostMapping("/conversations/{id}/leave")
    public void leaveGroup(Authentication auth, @PathVariable UUID id) {
        chatService.leaveGroup(auth.getName(), id);
    }

    @DeleteMapping("/conversations/{id}")
    public void disbandGroup(Authentication auth, @PathVariable UUID id) {
        chatService.disbandGroup(auth.getName(), id);
    }

    @PostMapping("/conversations/{id}/members")
    public ConversationResponse addMembers(Authentication auth, @PathVariable UUID id, @RequestBody AddMembersRequest request) {
        return chatService.addMembers(auth.getName(), id, request.memberIds());
    }

    @DeleteMapping("/conversations/{id}/members/{memberId}")
    public void removeMember(Authentication auth, @PathVariable UUID id, @PathVariable UUID memberId) {
        chatService.removeMember(auth.getName(), id, memberId);
    }
}
