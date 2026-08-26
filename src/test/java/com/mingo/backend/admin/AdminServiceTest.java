package com.mingo.backend.admin;

import com.mingo.backend.chat.ChatService;
import com.mingo.backend.chat.MessageReportRepository;
import com.mingo.backend.chat.MessageRepository;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.forum.ForumMessageReportRepository;
import com.mingo.backend.forum.ForumMessageRepository;
import com.mingo.backend.forum.ForumService;
import com.mingo.backend.post.CommentRepository;
import com.mingo.backend.post.Post;
import com.mingo.backend.post.PostReportRepository;
import com.mingo.backend.post.PostRepository;
import com.mingo.backend.post.ReactionRepository;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import com.mingo.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private ForumMessageRepository forumMessageRepository;
    @Mock private ForumMessageReportRepository forumMessageReportRepository;
    @Mock private ForumService forumService;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageReportRepository messageReportRepository;
    @Mock private ChatService chatService;
    @Mock private AdminAuditLogRepository auditLogRepository;

    private AdminService adminService;

    private User admin;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, postRepository, postReportRepository, reactionRepository,
                commentRepository, forumMessageRepository, forumMessageReportRepository, forumService,
                messageRepository, messageReportRepository, chatService, auditLogRepository);

        admin = User.builder().id(UUID.randomUUID()).email("admin@example.com").role(Role.ADMIN).build();
        targetUser = User.builder().id(UUID.randomUUID()).email("target@example.com").role(Role.USER).build();
        lenient().when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
    }

    private AdminAuditLog captureSavedLog() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void setUserBanned_bansUser_andLogsBanAction() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        adminService.setUserBanned("admin@example.com", targetUser.getId(), true);

        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.BANNED);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAdmin()).isEqualTo(admin);
        assertThat(log.getAction()).isEqualTo(AdminAction.BAN_USER);
        assertThat(log.getTargetType()).isEqualTo("USER");
        assertThat(log.getTargetId()).isEqualTo(targetUser.getId());
        assertThat(log.getDetails()).isEqualTo("target@example.com");
    }

    @Test
    void setUserBanned_unbansUser_andLogsUnbanAction() {
        targetUser.setStatus(UserStatus.BANNED);
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        adminService.setUserBanned("admin@example.com", targetUser.getId(), false);

        assertThat(targetUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captureSavedLog().getAction()).isEqualTo(AdminAction.UNBAN_USER);
    }

    @Test
    void setUserBanned_throwsBadRequest_whenTargetIsAdmin_andDoesNotLog() {
        User otherAdmin = User.builder().id(UUID.randomUUID()).email("other-admin@example.com").role(Role.ADMIN).build();
        when(userRepository.findById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> adminService.setUserBanned("admin@example.com", otherAdmin.getId(), true))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(auditLogRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void deleteUser_deletesUser_andLogsDeleteAction() {
        when(userRepository.findById(targetUser.getId())).thenReturn(Optional.of(targetUser));

        adminService.deleteUser("admin@example.com", targetUser.getId());

        verify(userRepository).delete(targetUser);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.DELETE_USER);
        assertThat(log.getTargetId()).isEqualTo(targetUser.getId());
        assertThat(log.getDetails()).isEqualTo("target@example.com");
    }

    @Test
    void deleteUser_throwsBadRequest_whenTargetIsAdmin_andDoesNotDeleteOrLog() {
        User otherAdmin = User.builder().id(UUID.randomUUID()).email("other-admin@example.com").role(Role.ADMIN).build();
        when(userRepository.findById(otherAdmin.getId())).thenReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> adminService.deleteUser("admin@example.com", otherAdmin.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).delete(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void setPostHidden_hidesPost_andLogsHideAction() {
        Post post = new Post();
        post.setAuthor(targetUser);
        post.setVisibility(com.mingo.backend.post.PostVisibility.PUBLIC);
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(reactionRepository.findByPostId(any())).thenReturn(List.of());
        when(commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(postReportRepository.countByPostId(any())).thenReturn(0L);

        adminService.setPostHidden("admin@example.com", postId, true);

        assertThat(post.isHidden()).isTrue();
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.HIDE_POST);
        assertThat(log.getTargetId()).isEqualTo(postId);
    }

    @Test
    void setPostHidden_unhidesPost_andLogsUnhideAction() {
        Post post = new Post();
        post.setAuthor(targetUser);
        post.setHidden(true);
        post.setVisibility(com.mingo.backend.post.PostVisibility.PUBLIC);
        UUID postId = UUID.randomUUID();
        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(reactionRepository.findByPostId(any())).thenReturn(List.of());
        when(commentRepository.findByPostIdAndParentCommentIsNullOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(postReportRepository.countByPostId(any())).thenReturn(0L);

        adminService.setPostHidden("admin@example.com", postId, false);

        assertThat(post.isHidden()).isFalse();
        assertThat(captureSavedLog().getAction()).isEqualTo(AdminAction.UNHIDE_POST);
    }

    @Test
    void deletePost_throwsNotFound_whenPostMissing_andDoesNotLog() {
        UUID postId = UUID.randomUUID();
        when(postRepository.existsById(postId)).thenReturn(false);

        assertThatThrownBy(() -> adminService.deletePost("admin@example.com", postId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);

        verify(postRepository, never()).deleteById(any());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void deletePost_deletesPost_andLogsDeleteAction() {
        UUID postId = UUID.randomUUID();
        when(postRepository.existsById(postId)).thenReturn(true);

        adminService.deletePost("admin@example.com", postId);

        verify(postRepository).deleteById(postId);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.DELETE_POST);
        assertThat(log.getTargetId()).isEqualTo(postId);
    }

    @Test
    void deleteForumMessage_delegatesToForumService_andLogsAction() {
        UUID messageId = UUID.randomUUID();

        adminService.deleteForumMessage("admin@example.com", messageId);

        verify(forumService).adminRecallMessage(messageId);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.DELETE_FORUM_MESSAGE);
        assertThat(log.getTargetId()).isEqualTo(messageId);
    }

    @Test
    void clearForumMessages_delegatesToForumService_andLogsActionWithRoomIdAsTarget() {
        UUID roomId = UUID.randomUUID();

        adminService.clearForumMessages("admin@example.com", roomId);

        verify(forumService).clearRoomMessages(roomId);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.CLEAR_FORUM_MESSAGES);
        assertThat(log.getTargetId()).isEqualTo(roomId);
    }

    @Test
    void setForumMessageHidden_delegatesToForumService_andLogsHideAction() {
        UUID messageId = UUID.randomUUID();

        adminService.setForumMessageHidden("admin@example.com", messageId, true);

        verify(forumService).setMessageHidden(messageId, true);
        assertThat(captureSavedLog().getAction()).isEqualTo(AdminAction.HIDE_FORUM_MESSAGE);
    }

    @Test
    void setForumMessageHidden_delegatesToForumService_andLogsUnhideAction() {
        UUID messageId = UUID.randomUUID();

        adminService.setForumMessageHidden("admin@example.com", messageId, false);

        verify(forumService).setMessageHidden(messageId, false);
        assertThat(captureSavedLog().getAction()).isEqualTo(AdminAction.UNHIDE_FORUM_MESSAGE);
    }

    @Test
    void deleteChatMessage_delegatesToChatService_andLogsAction() {
        UUID messageId = UUID.randomUUID();

        adminService.deleteChatMessage("admin@example.com", messageId);

        verify(chatService).adminRecallMessage(messageId);
        AdminAuditLog log = captureSavedLog();
        assertThat(log.getAction()).isEqualTo(AdminAction.DELETE_CHAT_MESSAGE);
        assertThat(log.getTargetId()).isEqualTo(messageId);
    }

    @Test
    void listAuditLog_passesFiltersThroughToRepository() {
        UUID adminId = UUID.randomUUID();
        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to = Instant.parse("2026-01-31T23:59:59Z");
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdmin(admin);
        entry.setAction(AdminAction.BAN_USER);
        entry.setTargetType("USER");
        Page<AdminAuditLog> page = new PageImpl<>(List.of(entry));
        when(auditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                eq(PageRequest.of(0, 30)))).thenReturn(page);

        Page<com.mingo.backend.admin.dto.AdminAuditLogResponse> result =
                adminService.listAuditLog(adminId, AdminAction.BAN_USER, from, to, PageRequest.of(0, 30));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).action()).isEqualTo(AdminAction.BAN_USER);
        verify(auditLogRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                eq(PageRequest.of(0, 30)));
    }

    @Test
    void listAuditLog_allowsNullFilters_forUnfilteredView() {
        when(auditLogRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                eq(PageRequest.of(0, 30)))).thenReturn(new PageImpl<>(List.of()));

        Page<com.mingo.backend.admin.dto.AdminAuditLogResponse> result =
                adminService.listAuditLog(null, null, null, null, PageRequest.of(0, 30));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listAdmins_returnsOnlyAdminRoleUsers() {
        User anotherAdmin = User.builder().id(UUID.randomUUID()).email("second-admin@example.com").role(Role.ADMIN).build();
        when(userRepository.findByRoleOrderByEmailAsc(Role.ADMIN)).thenReturn(List.of(admin, anotherAdmin));

        List<com.mingo.backend.admin.dto.AdminSummaryResponse> result = adminService.listAdmins();

        assertThat(result).extracting(com.mingo.backend.admin.dto.AdminSummaryResponse::email)
                .containsExactly("admin@example.com", "second-admin@example.com");
    }

    @Test
    void logAction_throwsNotFound_whenAdminEmailDoesNotResolveToAUser() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        UUID messageId = UUID.randomUUID();

        assertThatThrownBy(() -> adminService.deleteChatMessage("ghost@example.com", messageId))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
