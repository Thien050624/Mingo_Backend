package com.mingo.backend.forum;

import com.mingo.backend.forum.dto.ForumMessageResponse;
import com.mingo.backend.forum.dto.ReportForumMessageRequest;
import com.mingo.backend.forum.dto.SendForumMessageRequest;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/forum")
public class ForumController {

    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    @GetMapping("/messages")
    public Page<ForumMessageResponse> listMessages(Authentication auth,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "30") int size) {
        return forumService.listMessages(auth.getName(), page, size);
    }

    @GetMapping("/messages/search")
    public Page<ForumMessageResponse> searchMessages(Authentication auth,
                                                       @RequestParam String q,
                                                       @RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return forumService.searchMessages(auth.getName(), q, page, size);
    }

    @GetMapping("/messages/{id}/locate")
    public Map<String, Integer> locateMessagePage(Authentication auth, @PathVariable UUID id,
                                                   @RequestParam(defaultValue = "30") int size) {
        return Map.of("page", forumService.locateMessagePage(auth.getName(), id, size));
    }

    @PostMapping("/messages")
    public ForumMessageResponse sendMessage(Authentication auth, @RequestBody SendForumMessageRequest request) {
        return forumService.sendMessage(auth.getName(), request.text(), request.imageUrl(),
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
