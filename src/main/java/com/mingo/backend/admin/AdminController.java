package com.mingo.backend.admin;

import com.mingo.backend.admin.dto.AdminAuditLogResponse;
import com.mingo.backend.admin.dto.AdminChatMessageResponse;
import com.mingo.backend.admin.dto.AdminCommentResponse;
import com.mingo.backend.admin.dto.AdminForumMessageResponse;
import com.mingo.backend.admin.dto.AdminPostResponse;
import com.mingo.backend.admin.dto.AdminStatsResponse;
import com.mingo.backend.admin.dto.AdminSummaryResponse;
import com.mingo.backend.admin.dto.AdminUserResponse;
import com.mingo.backend.admin.dto.SetCommentHiddenRequest;
import com.mingo.backend.admin.dto.SetForumMessageHiddenRequest;
import com.mingo.backend.admin.dto.SetPostHiddenRequest;
import com.mingo.backend.admin.dto.SetUserBannedRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public AdminStatsResponse getStats() {
        return adminService.getStats();
    }

    @GetMapping("/users")
    public Page<AdminUserResponse> listUsers(@RequestParam(required = false) String query,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return adminService.listUsers(query, pageable);
    }

    @PatchMapping("/users/{id}/status")
    public AdminUserResponse setUserBanned(Authentication auth, @PathVariable UUID id, @RequestBody SetUserBannedRequest request) {
        return adminService.setUserBanned(auth.getName(), id, request.banned());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(Authentication auth, @PathVariable UUID id) {
        adminService.deleteUser(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts")
    public Page<AdminPostResponse> listPosts(@RequestParam(required = false) String filter,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return adminService.listPosts(filter, pageable);
    }

    @PatchMapping("/posts/{id}/hidden")
    public AdminPostResponse setPostHidden(Authentication auth, @PathVariable UUID id, @RequestBody SetPostHiddenRequest request) {
        return adminService.setPostHidden(auth.getName(), id, request.hidden());
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(Authentication auth, @PathVariable UUID id) {
        adminService.deletePost(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/forum/messages")
    public Page<AdminForumMessageResponse> listForumMessages(@PageableDefault(size = 30) Pageable pageable) {
        return adminService.listForumMessages(pageable);
    }

    @DeleteMapping("/forum/messages/{id}")
    public ResponseEntity<Void> deleteForumMessage(Authentication auth, @PathVariable UUID id) {
        adminService.deleteForumMessage(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/forum/rooms/{roomId}/messages")
    public ResponseEntity<Void> clearForumMessages(Authentication auth, @PathVariable UUID roomId) {
        adminService.clearForumMessages(auth.getName(), roomId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/forum/messages/{id}/hidden")
    public ResponseEntity<Void> setForumMessageHidden(Authentication auth, @PathVariable UUID id,
                                                        @RequestBody SetForumMessageHiddenRequest request) {
        adminService.setForumMessageHidden(auth.getName(), id, request.hidden());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/comments")
    public Page<AdminCommentResponse> listCommentReports(@PageableDefault(size = 30) Pageable pageable) {
        return adminService.listCommentReports(pageable);
    }

    @PatchMapping("/comments/{id}/hidden")
    public ResponseEntity<Void> setCommentHidden(Authentication auth, @PathVariable UUID id,
                                                  @RequestBody SetCommentHiddenRequest request) {
        adminService.setCommentHidden(auth.getName(), id, request.hidden());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(Authentication auth, @PathVariable UUID id) {
        adminService.deleteComment(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/chat/messages")
    public Page<AdminChatMessageResponse> listChatMessageReports(@PageableDefault(size = 30) Pageable pageable) {
        return adminService.listChatMessageReports(pageable);
    }

    @DeleteMapping("/chat/messages/{id}")
    public ResponseEntity<Void> deleteChatMessage(Authentication auth, @PathVariable UUID id) {
        adminService.deleteChatMessage(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/audit-log")
    public Page<AdminAuditLogResponse> listAuditLog(@RequestParam(required = false) UUID adminId,
                                                      @RequestParam(required = false) AdminAction action,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                                      @PageableDefault(size = 30, sort = "createdAt",
                                                              direction = Sort.Direction.DESC) Pageable pageable) {
        return adminService.listAuditLog(adminId, action, from, to, pageable);
    }

    @GetMapping("/admins")
    public List<AdminSummaryResponse> listAdmins() {
        return adminService.listAdmins();
    }

    @GetMapping("/audit-log/export")
    public ResponseEntity<byte[]> exportAuditLog(@RequestParam(required = false) UUID adminId,
                                                  @RequestParam(required = false) AdminAction action,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                  @RequestParam(required = false)
                                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<AdminAuditLogResponse> logs = adminService.exportAuditLog(adminId, action, from, to);

        StringBuilder csv = new StringBuilder();
        csv.append("Thời gian,Quản trị viên,Email,Hành động,Đối tượng,ID đối tượng,Chi tiết\n");
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
        for (AdminAuditLogResponse log : logs) {
            csv.append(csvField(formatter.format(log.createdAt()))).append(',')
                    .append(csvField(log.adminName())).append(',')
                    .append(csvField(log.adminEmail())).append(',')
                    .append(csvField(log.action().name())).append(',')
                    .append(csvField(log.targetType())).append(',')
                    .append(csvField(log.targetId() == null ? "" : log.targetId().toString())).append(',')
                    .append(csvField(log.details())).append('\n');
        }

        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(body, 0, content, bom.length, body.length);

        String filename = "audit-log-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content);
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
