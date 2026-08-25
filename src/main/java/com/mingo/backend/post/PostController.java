package com.mingo.backend.post;

import com.mingo.backend.post.dto.CommentRequest;
import com.mingo.backend.post.dto.CommentResponse;
import com.mingo.backend.post.dto.CreatePostRequest;
import com.mingo.backend.post.dto.PostResponse;
import com.mingo.backend.post.dto.ReactionRequest;
import com.mingo.backend.post.dto.ReportPostRequest;
import com.mingo.backend.post.dto.UpdatePostRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(Authentication auth, @Valid @RequestBody CreatePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.createPost(auth.getName(), request));
    }

    @GetMapping
    public Page<PostResponse> getFeed(Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        return postService.getFeed(auth.getName(), pageable);
    }

    @GetMapping("/saved")
    public Page<PostResponse> listSavedPosts(Authentication auth, @PageableDefault(size = 20) Pageable pageable) {
        return postService.listSavedPosts(auth.getName(), pageable);
    }

    @GetMapping("/search")
    public Page<PostResponse> searchPosts(Authentication auth, @RequestParam String q,
                                           @PageableDefault(size = 10) Pageable pageable) {
        return postService.searchPosts(auth.getName(), q, pageable);
    }

    @GetMapping("/{id}")
    public PostResponse getPost(Authentication auth, @PathVariable UUID id) {
        return postService.getPost(auth.getName(), id);
    }

    @GetMapping("/user/{userId}")
    public Page<PostResponse> getPostsByUser(Authentication auth, @PathVariable UUID userId,
                                              @PageableDefault(size = 20) Pageable pageable) {
        return postService.getPostsByUser(auth.getName(), userId, pageable);
    }

    @PatchMapping("/{id}")
    public PostResponse updatePost(Authentication auth, @PathVariable UUID id,
                                    @Valid @RequestBody UpdatePostRequest request) {
        return postService.updatePost(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(Authentication auth, @PathVariable UUID id) {
        postService.deletePost(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(Authentication auth, @PathVariable UUID id,
                                                        @Valid @RequestBody CommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(postService.addComment(auth.getName(), id, request));
    }

    @PutMapping("/{postId}/comments/{commentId}/like")
    public CommentResponse likeComment(Authentication auth, @PathVariable UUID postId, @PathVariable UUID commentId) {
        return postService.likeComment(auth.getName(), postId, commentId);
    }

    @DeleteMapping("/{postId}/comments/{commentId}/like")
    public CommentResponse unlikeComment(Authentication auth, @PathVariable UUID postId, @PathVariable UUID commentId) {
        return postService.unlikeComment(auth.getName(), postId, commentId);
    }

    @PutMapping("/{id}/reaction")
    public PostResponse setReaction(Authentication auth, @PathVariable UUID id,
                                     @Valid @RequestBody ReactionRequest request) {
        return postService.setReaction(auth.getName(), id, request);
    }

    @DeleteMapping("/{id}/reaction")
    public PostResponse removeReaction(Authentication auth, @PathVariable UUID id) {
        return postService.removeReaction(auth.getName(), id);
    }

    @PostMapping("/{id}/report")
    public void reportPost(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ReportPostRequest request) {
        postService.reportPost(auth.getName(), id, request.reason());
    }

    @DeleteMapping("/{id}/report")
    public void unreportPost(Authentication auth, @PathVariable UUID id) {
        postService.unreportPost(auth.getName(), id);
    }

    @PutMapping("/{id}/save")
    public PostResponse savePost(Authentication auth, @PathVariable UUID id) {
        return postService.savePost(auth.getName(), id);
    }

    @DeleteMapping("/{id}/save")
    public PostResponse unsavePost(Authentication auth, @PathVariable UUID id) {
        return postService.unsavePost(auth.getName(), id);
    }
}
