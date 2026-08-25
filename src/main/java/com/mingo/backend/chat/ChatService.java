package com.mingo.backend.chat;

import com.mingo.backend.chat.dto.ConversationResponse;
import com.mingo.backend.chat.dto.MessageEditEvent;
import com.mingo.backend.chat.dto.MessageLikeEvent;
import com.mingo.backend.chat.dto.MessagePinEvent;
import com.mingo.backend.chat.dto.MessageResponse;
import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.chat.dto.MessageUpdateEvent;
import com.mingo.backend.chat.dto.ReadReceipt;
import com.mingo.backend.chat.dto.ReadReceiptEvent;
import com.mingo.backend.chat.dto.TypingNotification;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MessageLikeRepository messageLikeRepository;
    private final MessageReportRepository messageReportRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatService(ConversationRepository conversationRepository,
                        ConversationParticipantRepository participantRepository,
                        MessageRepository messageRepository,
                        MessageLikeRepository messageLikeRepository,
                        MessageReportRepository messageReportRepository,
                        UserRepository userRepository,
                        UserBlockRepository userBlockRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.messageLikeRepository = messageLikeRepository;
        this.messageReportRepository = messageReportRepository;
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(String email) {
        User me = findUser(email);
        List<ConversationParticipant> myParticipants = participantRepository.findByUserId(me.getId()).stream()
                .filter(this::isVisible)
                .toList();

        List<UUID> conversationIds = myParticipants.stream().map(cp -> cp.getConversation().getId()).distinct().toList();
        if (conversationIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, List<ConversationParticipant>> participantsByConversation =
                participantRepository.findByConversationIdIn(conversationIds).stream()
                        .collect(Collectors.groupingBy(cp -> cp.getConversation().getId()));

        Map<UUID, MessageResponse> lastMessageByConversation = latestMessagesByConversation(conversationIds, me.getId());

        Map<UUID, Long> unreadByConversation = messageRepository.countUnreadPerConversation(conversationIds, me.getId()).stream()
                .collect(Collectors.toMap(ConversationUnreadCount::getConversationId, ConversationUnreadCount::getUnreadCount));

        return myParticipants.stream()
                .map(cp -> toResponse(cp.getConversation(), cp,
                        participantsByConversation.getOrDefault(cp.getConversation().getId(), List.of()),
                        lastMessageByConversation.get(cp.getConversation().getId()),
                        unreadByConversation.getOrDefault(cp.getConversation().getId(), 0L)))
                .sorted(Comparator.comparing(
                        (ConversationResponse c) -> c.lastMessage() != null ? c.lastMessage().createdAt() : c.createdAt())
                        .reversed())
                .toList();
    }

    /**
     * The latest visible message per conversation for {@code userId} (respecting each
     * conversation's clear-history cutoff), batched into a handful of {@code IN (...)} queries
     * instead of the 4-per-conversation cost ({@code toResponse}'s old per-item last-message +
     * likes + report lookups) that {@link #listConversations} used to pay.
     */
    private Map<UUID, MessageResponse> latestMessagesByConversation(List<UUID> conversationIds, UUID viewerId) {
        List<UUID> messageIds = messageRepository.findLatestMessageIdPerConversation(conversationIds, viewerId);
        if (messageIds.isEmpty()) {
            return Map.of();
        }

        List<Message> messages = messageRepository.findByIdInWithSender(messageIds);

        Map<UUID, List<ParticipantSummary>> likedByByMessage = messageLikeRepository.findByMessageIdIn(messageIds).stream()
                .collect(Collectors.groupingBy(l -> l.getMessage().getId(),
                        Collectors.mapping(l -> ParticipantSummary.from(l.getUser()), Collectors.toList())));

        Set<UUID> reportedByViewer = messageReportRepository.findByMessageIdInAndReporterId(messageIds, viewerId).stream()
                .map(r -> r.getMessage().getId())
                .collect(Collectors.toSet());

        return messages.stream().collect(Collectors.toMap(m -> m.getConversation().getId(), m -> MessageResponse.from(
                m, likedByByMessage.getOrDefault(m.getId(), List.of()), reportedByViewer.contains(m.getId()))));
    }

    @Transactional(readOnly = true)
    public long totalUnreadCount(String email) {
        User me = findUser(email);
        return participantRepository.findByUserId(me.getId()).stream()
                .filter(cp -> !cp.isMuted())
                .mapToLong(this::unreadCountFor)
                .sum();
    }

    @Transactional
    public void setMuted(String email, UUID conversationId, boolean muted) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        participant.setMuted(muted);
    }

    @Transactional
    public ConversationResponse getOrCreateDirect(String email, UUID otherUserId) {
        User me = findUser(email);
        if (me.getId().equals(otherUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tạo cuộc trò chuyện với chính mình");
        }
        if (userBlockRepository.existsEitherWay(me.getId(), otherUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không thể nhắn tin với người dùng này");
        }
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));

        Conversation conversation = participantRepository.findDirectConversation(me.getId(), otherUserId)
                .orElseGet(() -> createConversation(me, false, null, null, List.of(me, other)));

        ConversationParticipant myParticipant = requireParticipant(conversation.getId(), me.getId());
        return toResponse(conversation, myParticipant);
    }

    @Transactional
    public ConversationResponse createGroup(String email, String name, List<UUID> memberIds) {
        User me = findUser(email);
        if (!StringUtils.hasText(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tên nhóm không được để trống");
        }
        Set<UUID> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        ids.remove(me.getId());
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nhóm cần ít nhất 1 thành viên khác");
        }
        List<User> members = userRepository.findAllById(ids);
        if (members.size() != ids.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Một số thành viên không tồn tại");
        }
        List<User> allMembers = new java.util.ArrayList<>();
        allMembers.add(me);
        allMembers.addAll(members);

        Conversation conversation = createConversation(me, true, name, null, allMembers);
        ConversationParticipant myParticipant = requireParticipant(conversation.getId(), me.getId());
        return toResponse(conversation, myParticipant);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> listMessages(String email, UUID conversationId, int page, int size) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = participant.getClearedAt() != null
                ? messageRepository.findByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        conversationId, participant.getClearedAt(), pageable)
                : messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
        return messages.map(m -> toMessageResponse(m, me.getId()));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> searchMessages(String email, UUID conversationId, String query, int page, int size) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        if (!StringUtils.hasText(query)) {
            return Page.empty();
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = participant.getClearedAt() != null
                ? messageRepository.findByConversationIdAndRecalledFalseAndTextContainingIgnoreCaseAndCreatedAtAfterOrderByCreatedAtDesc(
                        conversationId, query, participant.getClearedAt(), pageable)
                : messageRepository.findByConversationIdAndRecalledFalseAndTextContainingIgnoreCaseOrderByCreatedAtDesc(
                        conversationId, query, pageable);
        return messages.map(m -> toMessageResponse(m, me.getId()));
    }

    @Transactional(readOnly = true)
    public int locateMessagePage(String email, UUID conversationId, UUID messageId, int size) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        Message target = messageRepository.findById(messageId)
                .filter(m -> m.getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        long newerCount = messageRepository.countByConversationIdAndCreatedAtAfter(conversationId, target.getCreatedAt());
        return (int) (newerCount / size);
    }

    @Transactional
    public MessageResponse sendMessage(String email, UUID conversationId, String text, String imageUrl,
                                        String fileUrl, String fileName, Long fileSize, String fileType,
                                        UUID replyToMessageId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        if (!StringUtils.hasText(text) && !StringUtils.hasText(imageUrl) && !StringUtils.hasText(fileUrl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tin nhắn không được để trống");
        }

        Message message = new Message();
        message.setConversation(participant.getConversation());
        message.setSender(me);
        message.setText(text);
        message.setImageUrl(imageUrl);
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setFileSize(fileSize);
        message.setFileType(fileType);
        if (replyToMessageId != null) {
            Message replyTo = messageRepository.findById(replyToMessageId)
                    .filter(m -> m.getConversation().getId().equals(conversationId))
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn để trả lời"));
            message.setReplyTo(replyTo);
        }
        messageRepository.saveAndFlush(message);

        participant.setLastReadAt(message.getCreatedAt());

        MessageResponse response = toMessageResponse(message, me.getId());
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/messages", response));

        return response;
    }

    @Transactional
    public MessageResponse forwardMessage(String email, UUID targetConversationId, UUID sourceMessageId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(targetConversationId, me.getId());

        Message source = messageRepository.findById(sourceMessageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn để chuyển tiếp"));
        requireParticipant(source.getConversation().getId(), me.getId());
        if (source.isRecalled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chuyển tiếp tin nhắn đã bị thu hồi");
        }
        if (!StringUtils.hasText(source.getText()) && source.getImageUrl() == null && source.getFileUrl() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chuyển tiếp tin nhắn này");
        }

        Message message = new Message();
        message.setConversation(participant.getConversation());
        message.setSender(me);
        message.setText(source.getText());
        message.setImageUrl(source.getImageUrl());
        message.setFileUrl(source.getFileUrl());
        message.setFileName(source.getFileName());
        message.setFileSize(source.getFileSize());
        message.setFileType(source.getFileType());
        message.setForwarded(true);
        messageRepository.saveAndFlush(message);

        participant.setLastReadAt(message.getCreatedAt());

        MessageResponse response = toMessageResponse(message, me.getId());
        participantRepository.findByConversationId(targetConversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/messages", response));

        return response;
    }

    @Transactional
    public void recallMessage(String email, UUID conversationId, UUID messageId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());

        Message message = requireMessageInConversation(conversationId, messageId);
        if (!message.getSender().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ có thể thu hồi tin nhắn của chính mình");
        }
        if (message.isRecalled()) {
            return;
        }

        message.setRecalled(true);

        MessageUpdateEvent event = new MessageUpdateEvent(conversationId, messageId, true);
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/message-updates", event));
    }

    @Transactional
    public MessageResponse editMessage(String email, UUID conversationId, UUID messageId, String text) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());

        Message message = requireMessageInConversation(conversationId, messageId);
        if (!message.getSender().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ có thể chỉnh sửa tin nhắn của chính mình");
        }
        if (message.isRecalled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể chỉnh sửa tin nhắn đã bị thu hồi");
        }
        if (!StringUtils.hasText(text)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống");
        }

        message.setText(text);
        message.setEdited(true);

        MessageEditEvent event = new MessageEditEvent(conversationId, messageId, text);
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/message-edits", event));

        return toMessageResponse(message, me.getId());
    }

    @Transactional
    public MessageResponse toggleLike(String email, UUID conversationId, UUID messageId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        Message message = requireMessageInConversation(conversationId, messageId);

        Optional<MessageLike> existing = messageLikeRepository.findByMessageIdAndUserId(messageId, me.getId());
        if (existing.isPresent()) {
            messageLikeRepository.delete(existing.get());
        } else {
            MessageLike like = new MessageLike();
            like.setMessage(message);
            like.setUser(me);
            messageLikeRepository.save(like);
        }

        List<ParticipantSummary> likedBy = messageLikeRepository.findByMessageId(messageId).stream()
                .map(l -> ParticipantSummary.from(l.getUser()))
                .toList();

        MessageLikeEvent event = new MessageLikeEvent(conversationId, messageId, likedBy);
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/message-likes", event));

        return toMessageResponse(message, me.getId());
    }

    @Transactional
    public MessageResponse togglePin(String email, UUID conversationId, UUID messageId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        Message message = requireMessageInConversation(conversationId, messageId);
        if (message.isRecalled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể ghim tin nhắn đã bị thu hồi");
        }

        boolean pinned = !message.isPinned();
        message.setPinned(pinned);
        message.setPinnedAt(pinned ? Instant.now() : null);

        MessagePinEvent event = new MessagePinEvent(conversationId, messageId, pinned);
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/message-pins", event));

        return toMessageResponse(message, me.getId());
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listPinnedMessages(String email, UUID conversationId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        return messageRepository.findByConversationIdAndPinnedTrueOrderByPinnedAtDesc(conversationId).stream()
                .map(m -> toMessageResponse(m, me.getId()))
                .toList();
    }

    @Transactional
    public void reportMessage(String email, UUID conversationId, UUID messageId, String reason) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        Message message = requireMessageInConversation(conversationId, messageId);

        if (messageReportRepository.existsByMessageIdAndReporterId(messageId, me.getId())) {
            return;
        }
        if (!StringUtils.hasText(reason)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lý do báo cáo");
        }

        MessageReport report = new MessageReport();
        report.setMessage(message);
        report.setReporter(me);
        report.setReason(reason);
        messageReportRepository.save(report);
    }

    @Transactional
    public void unreportMessage(String email, UUID conversationId, UUID messageId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());
        messageReportRepository.deleteByMessageIdAndReporterId(messageId, me.getId());
    }

    @Transactional
    public void adminRecallMessage(UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        UUID conversationId = message.getConversation().getId();

        if (!message.isRecalled()) {
            message.setRecalled(true);

            MessageUpdateEvent event = new MessageUpdateEvent(conversationId, messageId, true);
            participantRepository.findByConversationId(conversationId)
                    .forEach(cp -> messagingTemplate.convertAndSendToUser(
                            cp.getUser().getEmail(), "/queue/message-updates", event));
        }
        messageReportRepository.deleteByMessageId(messageId);
    }

    @Transactional(readOnly = true)
    public void notifyTyping(String email, UUID conversationId) {
        User me = findUser(email);
        requireParticipant(conversationId, me.getId());

        TypingNotification notification = new TypingNotification(conversationId, me.getId(), me.getDisplayNameOrDefault());
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/typing", notification));
    }

    @Transactional
    public void markRead(String email, UUID conversationId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        Instant now = Instant.now();
        participant.setLastReadAt(now);

        ReadReceiptEvent event = new ReadReceiptEvent(conversationId, me.getId(), now);
        participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .forEach(cp -> messagingTemplate.convertAndSendToUser(
                        cp.getUser().getEmail(), "/queue/read-receipts", event));
    }

    @Transactional
    public void clearConversation(String email, UUID conversationId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        Instant now = Instant.now();
        participant.setClearedAt(now);
        participant.setLastReadAt(now);
    }

    @Transactional
    public void leaveGroup(String email, UUID conversationId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        Conversation conversation = participant.getConversation();
        if (!conversation.isGroup()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể rời khỏi nhóm chat");
        }

        List<ConversationParticipant> remaining = participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .toList();

        participantRepository.delete(participant);

        Message systemMessage = new Message();
        systemMessage.setConversation(conversation);
        systemMessage.setSender(me);
        systemMessage.setType(MessageType.SYSTEM_LEFT);
        messageRepository.saveAndFlush(systemMessage);

        MessageResponse response = toMessageResponse(systemMessage, me.getId());
        remaining.forEach(cp -> messagingTemplate.convertAndSendToUser(
                cp.getUser().getEmail(), "/queue/messages", response));
    }

    @Transactional
    public void disbandGroup(String email, UUID conversationId) {
        User me = findUser(email);
        ConversationParticipant participant = requireParticipant(conversationId, me.getId());
        Conversation conversation = participant.getConversation();
        if (!conversation.isGroup()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể giải tán nhóm chat");
        }
        if (!conversation.getCreatedBy().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ trưởng nhóm mới có thể giải tán nhóm");
        }

        List<ConversationParticipant> remaining = participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                .toList();

        MessageResponse notice = new MessageResponse(
                UUID.randomUUID(), conversationId, ParticipantSummary.from(me),
                null, null, null, null, null, null, null, false, false, false,
                MessageType.SYSTEM_DISBANDED.name(), false, List.of(), false, Instant.now());

        conversationRepository.delete(conversation);

        remaining.forEach(cp -> messagingTemplate.convertAndSendToUser(
                cp.getUser().getEmail(), "/queue/messages", notice));
    }

    @Transactional
    public void removeMember(String email, UUID conversationId, UUID memberId) {
        User me = findUser(email);
        ConversationParticipant myParticipant = requireParticipant(conversationId, me.getId());
        Conversation conversation = myParticipant.getConversation();
        if (!conversation.isGroup()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể xoá thành viên khỏi nhóm chat");
        }
        if (!conversation.getCreatedBy().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Chỉ trưởng nhóm mới có thể xoá thành viên");
        }
        if (memberId.equals(me.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự xoá chính mình, hãy dùng chức năng rời nhóm");
        }

        ConversationParticipant target = requireParticipant(conversationId, memberId);
        User removedUser = target.getUser();

        List<ConversationParticipant> remaining = participantRepository.findByConversationId(conversationId).stream()
                .filter(cp -> !cp.getUser().getId().equals(memberId))
                .toList();

        participantRepository.delete(target);

        Message systemMessage = new Message();
        systemMessage.setConversation(conversation);
        systemMessage.setSender(me);
        systemMessage.setType(MessageType.SYSTEM_REMOVED);
        systemMessage.setText(removedUser.getDisplayNameOrDefault());
        messageRepository.saveAndFlush(systemMessage);

        MessageResponse response = toMessageResponse(systemMessage, me.getId());
        remaining.forEach(cp -> messagingTemplate.convertAndSendToUser(
                cp.getUser().getEmail(), "/queue/messages", response));

        MessageResponse kickNotice = new MessageResponse(
                UUID.randomUUID(), conversationId, ParticipantSummary.from(me),
                null, null, null, null, null, null, null, false, false, false,
                MessageType.SYSTEM_KICKED.name(), false, List.of(), false, Instant.now());
        messagingTemplate.convertAndSendToUser(removedUser.getEmail(), "/queue/messages", kickNotice);
    }

    @Transactional
    public ConversationResponse addMembers(String email, UUID conversationId, List<UUID> memberIds) {
        User me = findUser(email);
        ConversationParticipant myParticipant = requireParticipant(conversationId, me.getId());
        Conversation conversation = myParticipant.getConversation();
        if (!conversation.isGroup()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể thêm thành viên vào nhóm chat");
        }

        Set<UUID> existingIds = participantRepository.findByConversationId(conversationId).stream()
                .map(cp -> cp.getUser().getId())
                .collect(java.util.stream.Collectors.toSet());

        Set<UUID> ids = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        ids.removeAll(existingIds);
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chưa chọn thành viên hợp lệ để thêm");
        }

        List<User> newMembers = userRepository.findAllById(ids);
        if (newMembers.size() != ids.size()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Một số thành viên không tồn tại");
        }

        for (User member : newMembers) {
            ConversationParticipant cp = new ConversationParticipant();
            cp.setConversation(conversation);
            cp.setUser(member);
            participantRepository.save(cp);
        }

        List<ConversationParticipant> allParticipants = participantRepository.findByConversationId(conversationId);

        for (User member : newMembers) {
            Message systemMessage = new Message();
            systemMessage.setConversation(conversation);
            systemMessage.setSender(me);
            systemMessage.setType(MessageType.SYSTEM_ADDED);
            systemMessage.setText(member.getDisplayNameOrDefault());
            messageRepository.saveAndFlush(systemMessage);

            MessageResponse response = toMessageResponse(systemMessage, me.getId());
            allParticipants.stream()
                    .filter(cp -> !cp.getUser().getId().equals(me.getId()))
                    .forEach(cp -> messagingTemplate.convertAndSendToUser(
                            cp.getUser().getEmail(), "/queue/messages", response));
        }

        return toResponse(conversation, myParticipant);
    }

    private Conversation createConversation(User creator, boolean group, String name, String avatarUrl, List<User> members) {
        Conversation conversation = new Conversation();
        conversation.setGroup(group);
        conversation.setName(name);
        conversation.setAvatarUrl(avatarUrl);
        conversation.setCreatedBy(creator);
        conversationRepository.saveAndFlush(conversation);

        for (User member : members) {
            ConversationParticipant cp = new ConversationParticipant();
            cp.setConversation(conversation);
            cp.setUser(member);
            participantRepository.save(cp);
        }
        return conversation;
    }

    private ConversationParticipant requireParticipant(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền với cuộc trò chuyện này"));
    }

    private Instant floorFor(ConversationParticipant cp) {
        Instant since = cp.getLastReadAt() != null ? cp.getLastReadAt() : Instant.EPOCH;
        if (cp.getClearedAt() != null && cp.getClearedAt().isAfter(since)) {
            since = cp.getClearedAt();
        }
        return since;
    }

    private long unreadCountFor(ConversationParticipant cp) {
        return messageRepository.countByConversationIdAndCreatedAtAfterAndSenderIdNot(
                cp.getConversation().getId(), floorFor(cp), cp.getUser().getId());
    }

    private boolean isVisible(ConversationParticipant cp) {
        if (cp.getClearedAt() == null) return true;
        return messageRepository.findTopByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(
                cp.getConversation().getId(), cp.getClearedAt()).isPresent();
    }

    private ConversationResponse toResponse(Conversation conversation, ConversationParticipant myParticipant) {
        MessageResponse lastMessage = (myParticipant.getClearedAt() != null
                ? messageRepository.findTopByConversationIdAndCreatedAtAfterOrderByCreatedAtDesc(
                        conversation.getId(), myParticipant.getClearedAt())
                : messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId()))
                .map(m -> toMessageResponse(m, myParticipant.getUser().getId()))
                .orElse(null);

        return toResponse(conversation, myParticipant, participantRepository.findByConversationId(conversation.getId()),
                lastMessage, unreadCountFor(myParticipant));
    }

    private ConversationResponse toResponse(Conversation conversation, ConversationParticipant myParticipant,
                                             List<ConversationParticipant> allParticipants,
                                             MessageResponse lastMessage, long unread) {
        List<ParticipantSummary> participants = allParticipants.stream()
                .map(cp -> ParticipantSummary.from(cp.getUser()))
                .toList();

        List<ReadReceipt> readReceipts = allParticipants.stream()
                .map(cp -> new ReadReceipt(cp.getUser().getId(), cp.getLastReadAt()))
                .toList();

        return new ConversationResponse(
                conversation.getId(),
                conversation.isGroup(),
                conversation.getName(),
                conversation.getAvatarUrl(),
                conversation.getCreatedBy().getId(),
                participants,
                readReceipts,
                lastMessage,
                unread,
                myParticipant.isMuted(),
                conversation.getCreatedAt());
    }

    private Message requireMessageInConversation(UUID conversationId, UUID messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        if (!message.getConversation().getId().equals(conversationId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn");
        }
        return message;
    }

    private MessageResponse toMessageResponse(Message message, UUID viewerId) {
        List<ParticipantSummary> likedBy = messageLikeRepository.findByMessageId(message.getId()).stream()
                .map(l -> ParticipantSummary.from(l.getUser()))
                .toList();
        boolean reportedByMe = messageReportRepository.existsByMessageIdAndReporterId(message.getId(), viewerId);
        return MessageResponse.from(message, likedBy, reportedByMe);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }
}
