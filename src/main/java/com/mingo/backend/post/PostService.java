package com.mingo.backend.post;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.ratelimit.RateLimiter;
import com.mingo.backend.post.dto.AuthorSummary;
import com.mingo.backend.post.dto.CommentRequest;
import com.mingo.backend.post.dto.CommentResponse;
import com.mingo.backend.post.dto.CreatePostRequest;
import com.mingo.backend.post.dto.PostResponse;
import com.mingo.backend.post.dto.ReactionRequest;
import com.mingo.backend.post.dto.UpdatePostRequest;
import com.mingo.backend.friend.FriendshipRepository;
import com.mingo.backend.notification.NotificationService;
import com.mingo.backend.notification.NotificationType;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final CommentReportRepository commentReportRepository;
    private final ReactionRepository reactionRepository;
    private final PostReportRepository postReportRepository;
    private final SavedPostRepository savedPostRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final NotificationService notificationService;
    private final RateLimiter rateLimiter;

    private static final int MAX_POSTS_PER_MINUTE = 5;

    public PostService(PostRepository postRepository, CommentRepository commentRepository,
                        CommentLikeRepository commentLikeRepository, CommentReportRepository commentReportRepository,
                        ReactionRepository reactionRepository,
                        PostReportRepository postReportRepository, SavedPostRepository savedPostRepository,
                        UserRepository userRepository, FriendshipRepository friendshipRepository,
                        UserBlockRepository userBlockRepository, NotificationService notificationService,
                        RateLimiter rateLimiter) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.commentReportRepository = commentReportRepository;
        this.reactionRepository = reactionRepository;
        this.postReportRepository = postReportRepository;
        this.savedPostRepository = savedPostRepository;
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.userBlockRepository = userBlockRepository;
        this.notificationService = notificationService;
        this.rateLimiter = rateLimiter;
    }

    @Transactional
    public PostResponse createPost(String email, CreatePostRequest request) {
        rateLimiter.checkAllowed("post:" + email, MAX_POSTS_PER_MINUTE, Duration.ofMinutes(1));
        User author = findUser(email);
        String content = request.content() != null ? request.content().trim() : "";
        List<String> images = request.images() != null ? request.images() : List.of();

        if (content.isBlank() && images.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bài viết cần có nội dung hoặc ảnh");
        }

        Post post = new Post();
        post.setAuthor(author);
        post.setContent(content);
        post.getImages().addAll(images);
        post.setVisibility(request.visibility() != null ? request.visibility() : PostVisibility.PUBLIC);

        postRepository.saveAndFlush(post);
        return toResponse(post, author.getId());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getFeed(String email, Pageable pageable) {
        User viewer = findUser(email);
        return toResponsePage(postRepository.findFeedVisibleTo(PostVisibility.PUBLIC, viewer.getId(), pageable), viewer.getId());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByUser(String email, UUID authorId, Pageable pageable) {
        User viewer = findUser(email);
        return toResponsePage(
                postRepository.findByAuthorVisibleTo(authorId, PostVisibility.PUBLIC, viewer.getId(), pageable), viewer.getId());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> searchPosts(String email, String query, Pageable pageable) {
        User viewer = findUser(email);
        if (!StringUtils.hasText(query)) {
            return Page.empty();
        }
        return toResponsePage(
                postRepository.searchVisibleTo(query.trim(), PostVisibility.PUBLIC, viewer.getId(), pageable), viewer.getId());
    }

    @Transactional(readOnly = true)
    public PostResponse getPost(String email, UUID postId) {
        User viewer = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, viewer.getId());
        return toResponse(post, viewer.getId());
    }

    @Transactional
    public PostResponse updatePost(String email, UUID postId, UpdatePostRequest request) {
        User viewer = findUser(email);
        Post post = findPost(postId);
        if (!post.getAuthor().getId().equals(viewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền sửa bài viết này");
        }

        String content = request.content() != null ? request.content().trim() : post.getContent();
        List<String> images = request.images() != null ? request.images() : post.getImages();
        if (content.isBlank() && images.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bài viết cần có nội dung hoặc ảnh");
        }

        post.setContent(content);
        if (request.images() != null) {
            post.getImages().clear();
            post.getImages().addAll(request.images());
        }
        if (request.visibility() != null) {
            post.setVisibility(request.visibility());
        }

        postRepository.saveAndFlush(post);
        return toResponse(post, viewer.getId());
    }

    @Transactional
    public void deletePost(String email, UUID postId) {
        User viewer = findUser(email);
        Post post = findPost(postId);
        if (!post.getAuthor().getId().equals(viewer.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xoá bài viết này");
        }
        postRepository.delete(post);
    }

    @Transactional
    public CommentResponse addComment(String email, UUID postId, CommentRequest request) {
        User author = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, author.getId());

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(request.content().trim());

        if (request.parentCommentId() != null) {
            Comment parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận"));
            if (!parent.getPost().getId().equals(postId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bình luận không thuộc bài viết này");
            }
            if (parent.getParentComment() != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Chỉ có thể phản hồi bình luận gốc");
            }
            comment.setParentComment(parent);
        } else if (request.imageUrl() != null) {
            if (!post.getImages().contains(request.imageUrl())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Ảnh không thuộc bài viết này");
            }
            comment.setImageUrl(request.imageUrl());
        }

        commentRepository.saveAndFlush(comment);

        if (comment.getParentComment() != null) {
            notificationService.notify(comment.getParentComment().getAuthor(), author,
                    NotificationType.COMMENT_REPLY, post, comment);
        } else {
            notificationService.notify(post.getAuthor(), author, NotificationType.POST_COMMENT, post, comment);
        }

        return buildCommentResponse(comment, author.getId());
    }

    @Transactional
    public CommentResponse likeComment(String email, UUID postId, UUID commentId) {
        User user = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, user.getId());
        Comment comment = findCommentOnPost(commentId, postId);

        if (commentLikeRepository.findByCommentIdAndUserId(commentId, user.getId()).isEmpty()) {
            CommentLike like = new CommentLike();
            like.setComment(comment);
            like.setUser(user);
            commentLikeRepository.save(like);
            notificationService.notify(comment.getAuthor(), user, NotificationType.COMMENT_LIKE, post, comment);
        }

        return buildCommentResponse(comment, user.getId());
    }

    @Transactional
    public CommentResponse unlikeComment(String email, UUID postId, UUID commentId) {
        User user = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, user.getId());
        Comment comment = findCommentOnPost(commentId, postId);

        commentLikeRepository.deleteByCommentIdAndUserId(commentId, user.getId());
        return buildCommentResponse(comment, user.getId());
    }

    @Transactional
    public void reportComment(String email, UUID postId, UUID commentId, String reason) {
        User me = findUser(email);
        Comment comment = findCommentOnPost(commentId, postId);

        if (commentReportRepository.existsByCommentIdAndReporterId(commentId, me.getId())) {
            return;
        }

        CommentReport report = new CommentReport();
        report.setComment(comment);
        report.setReporter(me);
        report.setReason(reason);
        commentReportRepository.save(report);
    }

    @Transactional
    public void unreportComment(String email, UUID postId, UUID commentId) {
        User me = findUser(email);
        findCommentOnPost(commentId, postId);
        commentReportRepository.deleteByCommentIdAndReporterId(commentId, me.getId());
    }

    @Transactional
    public PostResponse setReaction(String email, UUID postId, ReactionRequest request) {
        User user = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, user.getId());

        Optional<Reaction> existing = reactionRepository.findByPostIdAndUserId(postId, user.getId());
        boolean isNewReaction = existing.isEmpty();
        Reaction reaction = existing.orElseGet(() -> {
            Reaction r = new Reaction();
            r.setPost(post);
            r.setUser(user);
            return r;
        });
        reaction.setType(request.type());
        reactionRepository.save(reaction);

        if (isNewReaction) {
            notificationService.notify(post.getAuthor(), user, NotificationType.POST_REACTION, post, null);
        }

        return toResponse(post, user.getId());
    }

    @Transactional
    public PostResponse removeReaction(String email, UUID postId) {
        User user = findUser(email);
        Post post = findPost(postId);
        reactionRepository.deleteByPostIdAndUserId(postId, user.getId());
        return toResponse(post, user.getId());
    }

    @Transactional
    public void reportPost(String email, UUID postId, String reason) {
        User me = findUser(email);
        Post post = findPost(postId);

        if (postReportRepository.existsByPostIdAndReporterId(postId, me.getId())) {
            return;
        }

        PostReport report = new PostReport();
        report.setPost(post);
        report.setReporter(me);
        report.setReason(reason);
        postReportRepository.save(report);
    }

    @Transactional
    public void unreportPost(String email, UUID postId) {
        User me = findUser(email);
        postReportRepository.deleteByPostIdAndReporterId(postId, me.getId());
    }

    @Transactional
    public PostResponse savePost(String email, UUID postId) {
        User me = findUser(email);
        Post post = findPost(postId);
        assertVisible(post, me.getId());

        if (!savedPostRepository.existsByUserIdAndPostId(me.getId(), postId)) {
            SavedPost saved = new SavedPost();
            saved.setUser(me);
            saved.setPost(post);
            savedPostRepository.save(saved);
        }
        return toResponse(post, me.getId());
    }

    @Transactional
    public PostResponse unsavePost(String email, UUID postId) {
        User me = findUser(email);
        Post post = findPost(postId);
        savedPostRepository.deleteByUserIdAndPostId(me.getId(), postId);
        return toResponse(post, me.getId());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> listSavedPosts(String email, Pageable pageable) {
        User me = findUser(email);
        Page<SavedPost> saved = savedPostRepository.findByUserIdOrderByCreatedAtDesc(me.getId(), pageable);
        Map<UUID, PostResponse> responses = toResponses(
                saved.getContent().stream().map(SavedPost::getPost).toList(), me.getId());
        return saved.map(sp -> responses.get(sp.getPost().getId()));
    }

    private void assertVisible(Post post, UUID viewerId) {
        if (post.getAuthor().getId().equals(viewerId)) return;
        if (post.isHidden()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết");
        }
        if (userBlockRepository.existsEitherWay(post.getAuthor().getId(), viewerId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết");
        }
        if (post.getVisibility() == PostVisibility.PUBLIC) return;
        if (post.getVisibility() == PostVisibility.FRIENDS
                && friendshipRepository.areFriends(post.getAuthor().getId(), viewerId)) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền xem bài viết này");
    }

    private PostResponse toResponse(Post post, UUID viewerId) {
        List<Reaction> reactions = reactionRepository.findByPostId(post.getId());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (ReactionType t : ReactionType.values()) counts.put(t.name().toLowerCase(), 0L);
        String myReaction = null;
        for (Reaction r : reactions) {
            counts.merge(r.getType().name().toLowerCase(), 1L, Long::sum);
            if (r.getUser().getId().equals(viewerId)) myReaction = r.getType().name().toLowerCase();
        }

        List<CommentResponse> comments = commentRepository.findByPostIdAndParentCommentIsNullAndHiddenFalseOrderByCreatedAtAsc(post.getId())
                .stream().map(c -> buildCommentResponse(c, viewerId)).collect(Collectors.toList());

        // Materialize into a plain list while the session is still open — passing the lazy
        // Hibernate-managed collection reference straight through breaks when Jackson
        // serializes the response later, outside the transaction (LazyInitializationException).
        List<String> images = new ArrayList<>(post.getImages());
        boolean reportedByMe = postReportRepository.existsByPostIdAndReporterId(post.getId(), viewerId);
        boolean savedByMe = savedPostRepository.existsByUserIdAndPostId(viewerId, post.getId());

        return new PostResponse(
                post.getId(),
                AuthorSummary.from(post.getAuthor()),
                post.getContent(),
                images,
                post.getVisibility().name(),
                counts,
                myReaction,
                comments,
                reportedByMe,
                savedByMe,
                post.getCreatedAt());
    }

    private CommentResponse buildCommentResponse(Comment comment, UUID viewerId) {
        long likeCount = commentLikeRepository.countByCommentId(comment.getId());
        boolean likedByMe = commentLikeRepository.findByCommentIdAndUserId(comment.getId(), viewerId).isPresent();
        boolean reportedByMe = commentReportRepository.existsByCommentIdAndReporterId(comment.getId(), viewerId);

        // Replies never carry their own nested replies (one level of nesting only).
        List<CommentResponse> replies = comment.getParentComment() == null
                ? commentRepository.findByParentCommentIdAndHiddenFalseOrderByCreatedAtAsc(comment.getId())
                        .stream().map(r -> buildCommentResponse(r, viewerId)).collect(Collectors.toList())
                : List.of();

        return new CommentResponse(
                comment.getId(),
                AuthorSummary.from(comment.getAuthor()),
                comment.getContent(),
                comment.getImageUrl(),
                comment.getCreatedAt(),
                likeCount,
                likedByMe,
                reportedByMe,
                replies);
    }

    /**
     * Batched counterpart of {@link #toResponse(Post, UUID)} for list endpoints (feed, profile
     * posts, search, saved posts): loads reactions/comments/likes/reports/saved-state for the
     * whole page in a handful of {@code IN (...)} queries instead of per-post, per-comment
     * queries — a page of 20 posts previously issued 150-380 queries via the single-item path.
     */
    private Page<PostResponse> toResponsePage(Page<Post> page, UUID viewerId) {
        Map<UUID, PostResponse> responses = toResponses(page.getContent(), viewerId);
        return page.map(post -> responses.get(post.getId()));
    }

    private Map<UUID, PostResponse> toResponses(List<Post> posts, UUID viewerId) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<UUID> postIds = posts.stream().map(Post::getId).distinct().toList();

        Map<UUID, List<Reaction>> reactionsByPost = reactionRepository.findByPostIdIn(postIds).stream()
                .collect(Collectors.groupingBy(r -> r.getPost().getId()));

        List<Comment> topLevelComments = commentRepository.findByPostIdInAndParentCommentIsNullAndHiddenFalseOrderByCreatedAtAsc(postIds);
        Map<UUID, List<Comment>> topLevelByPost = topLevelComments.stream()
                .collect(Collectors.groupingBy(c -> c.getPost().getId()));

        List<UUID> topLevelIds = topLevelComments.stream().map(Comment::getId).toList();
        Map<UUID, List<Comment>> repliesByParent = topLevelIds.isEmpty() ? Map.of()
                : commentRepository.findByParentCommentIdInAndHiddenFalseOrderByCreatedAtAsc(topLevelIds).stream()
                        .collect(Collectors.groupingBy(c -> c.getParentComment().getId()));

        List<UUID> allCommentIds = new ArrayList<>(topLevelIds);
        repliesByParent.values().forEach(replies -> replies.forEach(r -> allCommentIds.add(r.getId())));

        Map<UUID, Long> likeCountByComment = allCommentIds.isEmpty() ? Map.of()
                : commentLikeRepository.countByCommentIdIn(allCommentIds).stream()
                        .collect(Collectors.toMap(CommentLikeCount::getCommentId, CommentLikeCount::getLikeCount));

        Set<UUID> likedByMeCommentIds = allCommentIds.isEmpty() ? Set.of()
                : commentLikeRepository.findByCommentIdInAndUserId(allCommentIds, viewerId).stream()
                        .map(cl -> cl.getComment().getId())
                        .collect(Collectors.toSet());

        Set<UUID> reportedByMeCommentIds = allCommentIds.isEmpty() ? Set.of()
                : commentReportRepository.findByCommentIdInAndReporterId(allCommentIds, viewerId).stream()
                        .map(r -> r.getComment().getId())
                        .collect(Collectors.toSet());

        Set<UUID> reportedPostIds = postReportRepository.findByPostIdInAndReporterId(postIds, viewerId).stream()
                .map(r -> r.getPost().getId())
                .collect(Collectors.toSet());

        Set<UUID> savedPostIds = savedPostRepository.findByUserIdAndPostIdIn(viewerId, postIds).stream()
                .map(sp -> sp.getPost().getId())
                .collect(Collectors.toSet());

        Map<UUID, PostResponse> result = new LinkedHashMap<>();
        for (Post post : posts) {
            List<Reaction> reactions = reactionsByPost.getOrDefault(post.getId(), List.of());
            Map<String, Long> counts = new LinkedHashMap<>();
            for (ReactionType t : ReactionType.values()) counts.put(t.name().toLowerCase(), 0L);
            String myReaction = null;
            for (Reaction r : reactions) {
                counts.merge(r.getType().name().toLowerCase(), 1L, Long::sum);
                if (r.getUser().getId().equals(viewerId)) myReaction = r.getType().name().toLowerCase();
            }

            List<CommentResponse> comments = topLevelByPost.getOrDefault(post.getId(), List.of()).stream()
                    .map(c -> buildCommentResponseBatched(c, repliesByParent, likeCountByComment, likedByMeCommentIds,
                            reportedByMeCommentIds))
                    .toList();

            List<String> images = new ArrayList<>(post.getImages());
            boolean reportedByMe = reportedPostIds.contains(post.getId());
            boolean savedByMe = savedPostIds.contains(post.getId());

            result.put(post.getId(), new PostResponse(
                    post.getId(),
                    AuthorSummary.from(post.getAuthor()),
                    post.getContent(),
                    images,
                    post.getVisibility().name(),
                    counts,
                    myReaction,
                    comments,
                    reportedByMe,
                    savedByMe,
                    post.getCreatedAt()));
        }
        return result;
    }

    private CommentResponse buildCommentResponseBatched(Comment comment, Map<UUID, List<Comment>> repliesByParent,
                                                          Map<UUID, Long> likeCountByComment, Set<UUID> likedByMeCommentIds,
                                                          Set<UUID> reportedByMeCommentIds) {
        long likeCount = likeCountByComment.getOrDefault(comment.getId(), 0L);
        boolean likedByMe = likedByMeCommentIds.contains(comment.getId());
        boolean reportedByMe = reportedByMeCommentIds.contains(comment.getId());

        List<CommentResponse> replies = comment.getParentComment() == null
                ? repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                        .map(r -> buildCommentResponseBatched(r, repliesByParent, likeCountByComment, likedByMeCommentIds,
                                reportedByMeCommentIds))
                        .toList()
                : List.of();

        return new CommentResponse(
                comment.getId(),
                AuthorSummary.from(comment.getAuthor()),
                comment.getContent(),
                comment.getImageUrl(),
                comment.getCreatedAt(),
                likeCount,
                likedByMe,
                reportedByMe,
                replies);
    }

    private Comment findCommentOnPost(UUID commentId, UUID postId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận"));
        if (!comment.getPost().getId().equals(postId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Bình luận không thuộc bài viết này");
        }
        return comment;
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private Post findPost(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
    }
}
