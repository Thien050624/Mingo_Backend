package com.mingo.backend.admin;

import com.mingo.backend.admin.dto.AdminAuditLogResponse;
import com.mingo.backend.admin.dto.AdminChatMessageResponse;
import com.mingo.backend.admin.dto.AdminCommentResponse;
import com.mingo.backend.admin.dto.AdminForumMessageResponse;
import com.mingo.backend.admin.dto.AdminPostResponse;
import com.mingo.backend.admin.dto.AdminStatsResponse;
import com.mingo.backend.admin.dto.AdminSummaryResponse;
import com.mingo.backend.admin.dto.AdminUserResponse;
import com.mingo.backend.chat.ChatService;
import com.mingo.backend.chat.MessageReportRepository;
import com.mingo.backend.chat.MessageRepository;
import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.forum.ForumMessageReportRepository;
import com.mingo.backend.forum.ForumMessageRepository;
import com.mingo.backend.forum.ForumService;
import com.mingo.backend.post.Comment;
import com.mingo.backend.post.CommentRepository;
import com.mingo.backend.post.CommentReportRepository;
import com.mingo.backend.post.Post;
import com.mingo.backend.post.PostReportRepository;
import com.mingo.backend.post.PostRepository;
import com.mingo.backend.post.ReactionRepository;
import com.mingo.backend.post.dto.AuthorSummary;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import com.mingo.backend.user.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final PostReportRepository postReportRepository;
    private final ReactionRepository reactionRepository;
    private final CommentRepository commentRepository;
    private final CommentReportRepository commentReportRepository;
    private final ForumMessageRepository forumMessageRepository;
    private final ForumMessageReportRepository forumMessageReportRepository;
    private final ForumService forumService;
    private final MessageRepository messageRepository;
    private final MessageReportRepository messageReportRepository;
    private final ChatService chatService;
    private final AdminAuditLogRepository auditLogRepository;

    public AdminService(UserRepository userRepository, PostRepository postRepository,
                         PostReportRepository postReportRepository, ReactionRepository reactionRepository,
                         CommentRepository commentRepository, CommentReportRepository commentReportRepository,
                         ForumMessageRepository forumMessageRepository,
                         ForumMessageReportRepository forumMessageReportRepository, ForumService forumService,
                         MessageRepository messageRepository, MessageReportRepository messageReportRepository,
                         ChatService chatService, AdminAuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.postReportRepository = postReportRepository;
        this.reactionRepository = reactionRepository;
        this.commentRepository = commentRepository;
        this.commentReportRepository = commentReportRepository;
        this.forumMessageRepository = forumMessageRepository;
        this.forumMessageReportRepository = forumMessageReportRepository;
        this.forumService = forumService;
        this.messageRepository = messageRepository;
        this.messageReportRepository = messageReportRepository;
        this.chatService = chatService;
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public AdminStatsResponse getStats() {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);

        long totalUsers = userRepository.count();
        long newUsersThisWeek = userRepository.countByCreatedAtAfter(weekAgo);
        long totalPosts = postRepository.count();
        long postsToday = postRepository.countByCreatedAtAfter(startOfToday);
        long totalForumMessages = forumMessageRepository.count();
        long reportsPending = postReportRepository.count() + forumMessageReportRepository.count()
                + messageReportRepository.count() + commentReportRepository.count();
        List<Long> userGrowth = computeUserGrowth();

        return new AdminStatsResponse(totalUsers, newUsersThisWeek, totalPosts, postsToday,
                totalForumMessages, reportsPending, userGrowth);
    }

    private List<Long> computeUserGrowth() {
        YearMonth current = YearMonth.now(ZoneOffset.UTC);
        List<YearMonth> months = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            months.add(current.minusMonths(i));
        }

        long[] counts = new long[12];
        for (Instant createdAt : userRepository.findAllCreatedAt()) {
            YearMonth ym = YearMonth.from(createdAt.atZone(ZoneOffset.UTC));
            int idx = months.indexOf(ym);
            if (idx >= 0) counts[idx]++;
        }

        List<Long> result = new ArrayList<>();
        for (long c : counts) result.add(c);
        return result;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String query, Pageable pageable) {
        Page<User> page = StringUtils.hasText(query)
                ? userRepository.searchUsers(query.trim(), pageable)
                : userRepository.findAllByOrderByCreatedAtDesc(pageable);
        return page.map(u -> AdminUserResponse.from(u, postRepository.countByAuthorId(u.getId())));
    }

    @Transactional
    public AdminUserResponse setUserBanned(String adminEmail, UUID userId, boolean banned) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể khoá tài khoản quản trị viên");
        }
        user.setStatus(banned ? UserStatus.BANNED : UserStatus.ACTIVE);
        userRepository.save(user);
        logAction(adminEmail, banned ? AdminAction.BAN_USER : AdminAction.UNBAN_USER, "USER", user.getId(), user.getEmail());
        return AdminUserResponse.from(user, postRepository.countByAuthorId(user.getId()));
    }

    @Transactional
    public void deleteUser(String adminEmail, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể xoá tài khoản quản trị viên");
        }
        String email = user.getEmail();
        userRepository.delete(user);
        logAction(adminEmail, AdminAction.DELETE_USER, "USER", userId, email);
    }

    @Transactional(readOnly = true)
    public Page<AdminPostResponse> listPosts(String filter, Pageable pageable) {
        Page<Post> page = switch (filter == null ? "all" : filter) {
            case "visible" -> postRepository.findByHiddenFalseOrderByCreatedAtDesc(pageable);
            case "hidden" -> postRepository.findByHiddenTrueOrderByCreatedAtDesc(pageable);
            case "reported" -> postRepository.findReportedOrderByCreatedAtDesc(pageable);
            default -> postRepository.findAllByOrderByCreatedAtDesc(pageable);
        };
        return page.map(this::toAdminPostResponse);
    }

    private AdminPostResponse toAdminPostResponse(Post post) {
        long likes = reactionRepository.findByPostId(post.getId()).size();
        long comments = commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(post.getId()).size();
        long reports = postReportRepository.countByPostId(post.getId());
        String image = post.getImages().isEmpty() ? null : post.getImages().get(0);
        return new AdminPostResponse(post.getId(), AuthorSummary.from(post.getAuthor()), post.getContent(),
                image, post.getCreatedAt(), likes, comments, post.isHidden(), reports);
    }

    @Transactional
    public AdminPostResponse setPostHidden(String adminEmail, UUID postId, boolean hidden) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        post.setHidden(hidden);
        postRepository.save(post);
        logAction(adminEmail, hidden ? AdminAction.HIDE_POST : AdminAction.UNHIDE_POST, "POST", postId, null);
        return toAdminPostResponse(post);
    }

    @Transactional
    public void deletePost(String adminEmail, UUID postId) {
        if (!postRepository.existsById(postId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết");
        }
        postRepository.deleteById(postId);
        logAction(adminEmail, AdminAction.DELETE_POST, "POST", postId, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminForumMessageResponse> listForumMessages(Pageable pageable) {
        return forumMessageRepository.findReportedOrderByCreatedAtDesc(pageable)
                .map(m -> new AdminForumMessageResponse(
                        m.getId(),
                        m.getRoom().getId(),
                        m.getRoom().getName(),
                        ParticipantSummary.from(m.getSender()),
                        m.isRecalled() ? null : m.getText(),
                        m.getCreatedAt(),
                        forumMessageReportRepository.countByMessageId(m.getId()),
                        m.isHidden()));
    }

    @Transactional
    public void deleteForumMessage(String adminEmail, UUID messageId) {
        forumService.adminRecallMessage(messageId);
        logAction(adminEmail, AdminAction.DELETE_FORUM_MESSAGE, "FORUM_MESSAGE", messageId, null);
    }

    @Transactional
    public void clearForumMessages(String adminEmail, UUID roomId) {
        forumService.clearRoomMessages(roomId);
        logAction(adminEmail, AdminAction.CLEAR_FORUM_MESSAGES, "FORUM_ROOM", roomId, null);
    }

    @Transactional
    public void setForumMessageHidden(String adminEmail, UUID messageId, boolean hidden) {
        forumService.setMessageHidden(messageId, hidden);
        logAction(adminEmail, hidden ? AdminAction.HIDE_FORUM_MESSAGE : AdminAction.UNHIDE_FORUM_MESSAGE,
                "FORUM_MESSAGE", messageId, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminCommentResponse> listCommentReports(Pageable pageable) {
        return commentRepository.findReportedOrderByCreatedAtDesc(pageable)
                .map(c -> new AdminCommentResponse(
                        c.getId(),
                        c.getPost().getId(),
                        AuthorSummary.from(c.getAuthor()),
                        c.getContent(),
                        c.getCreatedAt(),
                        commentReportRepository.countByCommentId(c.getId()),
                        c.isHidden()));
    }

    @Transactional
    public void setCommentHidden(String adminEmail, UUID commentId, boolean hidden) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận"));
        comment.setHidden(hidden);
        commentRepository.save(comment);
        logAction(adminEmail, hidden ? AdminAction.HIDE_COMMENT : AdminAction.UNHIDE_COMMENT, "COMMENT", commentId, null);
    }

    @Transactional
    public void deleteComment(String adminEmail, UUID commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận");
        }
        commentRepository.deleteById(commentId);
        logAction(adminEmail, AdminAction.DELETE_COMMENT, "COMMENT", commentId, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminChatMessageResponse> listChatMessageReports(Pageable pageable) {
        return messageRepository.findReportedOrderByCreatedAtDesc(pageable)
                .map(m -> new AdminChatMessageResponse(
                        m.getId(),
                        m.getConversation().getId(),
                        ParticipantSummary.from(m.getSender()),
                        m.isRecalled() ? null : m.getText(),
                        m.getCreatedAt(),
                        messageReportRepository.countByMessageId(m.getId())));
    }

    @Transactional
    public void deleteChatMessage(String adminEmail, UUID messageId) {
        chatService.adminRecallMessage(messageId);
        logAction(adminEmail, AdminAction.DELETE_CHAT_MESSAGE, "CHAT_MESSAGE", messageId, null);
    }

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> listAuditLog(UUID adminId, AdminAction action, Instant from, Instant to,
                                                      Pageable pageable) {
        Specification<AdminAuditLog> spec = buildAuditLogSpec(adminId, action, from, to);
        return auditLogRepository.findAll(spec, pageable).map(AdminAuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> exportAuditLog(UUID adminId, AdminAction action, Instant from, Instant to) {
        Specification<AdminAuditLog> spec = buildAuditLogSpec(adminId, action, from, to);
        return auditLogRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(AdminAuditLogResponse::from)
                .toList();
    }

    private Specification<AdminAuditLog> buildAuditLogSpec(UUID adminId, AdminAction action, Instant from, Instant to) {
        Specification<AdminAuditLog> spec = Specification.where(null);
        if (adminId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("admin").get("id"), adminId));
        }
        if (action != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("action"), action));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        return spec;
    }

    @Transactional(readOnly = true)
    public List<AdminSummaryResponse> listAdmins() {
        return userRepository.findByRoleOrderByEmailAsc(Role.ADMIN).stream()
                .map(AdminSummaryResponse::from)
                .toList();
    }

    private void logAction(String adminEmail, AdminAction action, String targetType, UUID targetId, String details) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy quản trị viên"));
        AdminAuditLog log = new AdminAuditLog();
        log.setAdmin(admin);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetails(details);
        auditLogRepository.save(log);
    }
}
