package com.mingo.backend.forum;

import com.mingo.backend.forum.dto.CreateForumRoomRequest;
import com.mingo.backend.forum.dto.ForumMessageResponse;
import com.mingo.backend.forum.dto.ForumRoomResponse;
import com.mingo.backend.forum.dto.ReportForumMessageRequest;
import com.mingo.backend.forum.dto.SendForumMessageRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/forum")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/rooms")
    public List<ForumRoomResponse> listRooms() {
        return forumService.listRooms();
    }

    @PostMapping("/rooms")
    public ForumRoomResponse createRoom(Authentication auth, @Valid @RequestBody CreateForumRoomRequest request) {
        return forumService.createRoom(auth.getName(), request.name(), request.description());
    }

    @GetMapping("/rooms/{roomId}/messages")
    public Page<ForumMessageResponse> listMessages(Authentication auth, @PathVariable UUID roomId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return forumService.listMessages(auth.getName(), roomId, page, size);
    }

    @GetMapping("/rooms/{roomId}/messages/search")
    public Page<ForumMessageResponse> searchMessages(Authentication auth, @PathVariable UUID roomId,
                                                       @RequestParam String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return forumService.searchMessages(auth.getName(), roomId, q, page, size);
    }

    @GetMapping("/rooms/{roomId}/messages/{id}/locate")
    public Map<String, Integer> locateMessagePage(Authentication auth, @PathVariable UUID roomId, @PathVariable UUID id,
                                                   @RequestParam(defaultValue = "30") int size) {
        return Map.of("page", forumService.locateMessagePage(auth.getName(), roomId, id, size));
    }

    @PostMapping("/rooms/{roomId}/messages")
    public ForumMessageResponse sendMessage(Authentication auth, @PathVariable UUID roomId,
                                             @RequestBody SendForumMessageRequest request) {
        return forumService.sendMessage(auth.getName(), roomId, request.text(), request.imageUrl(),
                request.fileUrl(), request.fileName(), request.fileSize(), request.fileType());
    }

    @PostMapping("/messages/{id}/like")
    public ForumMessageResponse toggleLike(Authentication auth, @PathVariable UUID id) {
        return forumService.toggleLike(auth.getName(), id);
    }

    @DeleteMapping("/messages/{id}")
    public void recallMessage(Authentication auth, @PathVariable UUID id) {
        forumService.recallMessage(auth.getName(), id);
    }

    @PostMapping("/messages/{id}/report")
    public void reportMessage(Authentication auth, @PathVariable UUID id, @RequestBody ReportForumMessageRequest request) {
        forumService.reportMessage(auth.getName(), id, request.reason());
    }

    @DeleteMapping("/messages/{id}/report")
    public void unreportMessage(Authentication auth, @PathVariable UUID id) {
        forumService.unreportMessage(auth.getName(), id);
    }
}
