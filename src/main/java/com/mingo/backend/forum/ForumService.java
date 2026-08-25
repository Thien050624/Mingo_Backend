package com.mingo.backend.forum;

import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.forum.dto.ForumMessageLikeEvent;
import com.mingo.backend.forum.dto.ForumMessageResponse;
import com.mingo.backend.forum.dto.ForumMessageUpdateEvent;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ForumService {

    private final ForumMessageRepository messageRepository;
    private final ForumMessageReportRepository reportRepository;
    private final ForumMessageLikeRepository likeRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public ForumService(ForumMessageRepository messageRepository,
                         ForumMessageReportRepository reportRepository,
                         ForumMessageLikeRepository likeRepository,
                         UserRepository userRepository,
                         SimpMessagingTemplate messagingTemplate) {
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.likeRepository = likeRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional(readOnly = true)
    public Page<ForumMessageResponse> listMessages(String email, int page, int size) {
        User me = findUser(email);
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByHiddenFalseOrderByCreatedAtDesc(pageable)
                .map(m -> toResponse(m, me.getId()));
    }

    @Transactional(readOnly = true)
    public Page<ForumMessageResponse> searchMessages(String email, String query, int page, int size) {
        User me = findUser(email);
        if (!StringUtils.hasText(query)) {
            return Page.empty();
        }
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByHiddenFalseAndRecalledFalseAndTextContainingIgnoreCaseOrderByCreatedAtDesc(query, pageable)
                .map(m -> toResponse(m, me.getId()));
    }

    @Transactional(readOnly = true)
    public int locateMessagePage(String email, UUID messageId, int size) {
        findUser(email);
        ForumMessage target = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        long newerCount = messageRepository.countByCreatedAtAfter(target.getCreatedAt());
        return (int) (newerCount / size);
    }

    @Transactional
    public ForumMessageResponse sendMessage(String email, String text, String imageUrl,
                                             String fileUrl, String fileName, Long fileSize, String fileType) {
        User me = findUser(email);
        if (!StringUtils.hasText(text) && !StringUtils.hasText(imageUrl) && !StringUtils.hasText(fileUrl)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Tin nhắn không được để trống");
        }

        ForumMessage message = new ForumMessage();
        message.setSender(me);
        message.setText(text);
        message.setImageUrl(imageUrl);
        message.setFileUrl(fileUrl);
        message.setFileName(fileName);
        message.setFileSize(fileSize);
        message.setFileType(fileType);
        messageRepository.saveAndFlush(message);

        ForumMessageResponse response = toResponse(message, me.getId());
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-messages", response));

        return response;
    }

    @Transactional
    public ForumMessageResponse toggleLike(String email, UUID messageId) {
        User me = findUser(email);
        ForumMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        Optional<ForumMessageLike> existing = likeRepository.findByMessageIdAndUserId(messageId, me.getId());
        if (existing.isPresent()) {
            likeRepository.delete(existing.get());
        } else {
            ForumMessageLike like = new ForumMessageLike();
            like.setMessage(message);
            like.setUser(me);
            likeRepository.save(like);
        }

        List<ParticipantSummary> likedBy = likeRepository.findByMessageId(messageId).stream()
                .map(l -> ParticipantSummary.from(l.getUser()))
                .toList();

        ForumMessageLikeEvent event = new ForumMessageLikeEvent(messageId, likedBy);
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-likes", event));

        return toResponse(message, me.getId());
    }

    @Transactional
    public void recallMessage(String email, UUID messageId) {
        User me = findUser(email);
        ForumMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        if (!message.getSender().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn chỉ có thể thu hồi tin nhắn của chính mình");
        }
        if (message.isRecalled()) {
            return;
        }

        message.setRecalled(true);

        ForumMessageUpdateEvent event = new ForumMessageUpdateEvent(messageId, true, message.isHidden());
        userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(me.getId()))
                .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-updates", event));
    }

    @Transactional
    public void adminRecallMessage(UUID messageId) {
        ForumMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        if (!message.isRecalled()) {
            message.setRecalled(true);

            ForumMessageUpdateEvent event = new ForumMessageUpdateEvent(messageId, true, message.isHidden());
            userRepository.findAll()
                    .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-updates", event));
        }
        reportRepository.deleteByMessageId(messageId);
    }

    @Transactional
    public void clearAllMessages() {
        messageRepository.deleteAllInBatch();
        userRepository.findAll()
                .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-cleared", java.util.Map.of()));
    }

    @Transactional
    public void setMessageHidden(UUID messageId, boolean hidden) {
        ForumMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));
        message.setHidden(hidden);

        ForumMessageUpdateEvent event = new ForumMessageUpdateEvent(messageId, message.isRecalled(), hidden);
        userRepository.findAll()
                .forEach(u -> messagingTemplate.convertAndSendToUser(u.getEmail(), "/queue/forum-updates", event));
    }

    @Transactional
    public void reportMessage(String email, UUID messageId, String reason) {
        User me = findUser(email);
        ForumMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tin nhắn"));

        if (reportRepository.existsByMessageIdAndReporterId(messageId, me.getId())) {
            return;
        }

        ForumMessageReport report = new ForumMessageReport();
        report.setMessage(message);
        report.setReporter(me);
        report.setReason(reason);
        reportRepository.save(report);
    }

    @Transactional
    public void unreportMessage(String email, UUID messageId) {
        User me = findUser(email);
        reportRepository.deleteByMessageIdAndReporterId(messageId, me.getId());
    }

    private ForumMessageResponse toResponse(ForumMessage message, UUID viewerId) {
        boolean reportedByMe = reportRepository.existsByMessageIdAndReporterId(message.getId(), viewerId);
        List<ParticipantSummary> likedBy = likeRepository.findByMessageId(message.getId()).stream()
                .map(l -> ParticipantSummary.from(l.getUser()))
                .toList();
        return ForumMessageResponse.from(message, likedBy, reportedByMe);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }
}
