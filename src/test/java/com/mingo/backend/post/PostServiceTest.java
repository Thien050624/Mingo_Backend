package com.mingo.backend.post;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.ratelimit.RateLimiter;
import com.mingo.backend.friend.FriendshipRepository;
import com.mingo.backend.notification.NotificationService;
import com.mingo.backend.post.dto.PostResponse;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository;
    @Mock private CommentReportRepository commentReportRepository;
    @Mock private ReactionRepository reactionRepository;
    @Mock private PostReportRepository postReportRepository;
    @Mock private SavedPostRepository savedPostRepository;
    @Mock private UserRepository userRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private NotificationService notificationService;
    @Mock private RateLimiter rateLimiter;

    private PostService postService;

    private User viewer;
    private User author;

    @BeforeEach
    void setUp() {
        postService = new PostService(postRepository, commentRepository, commentLikeRepository, commentReportRepository,
                reactionRepository,
                postReportRepository, savedPostRepository, userRepository, friendshipRepository, userBlockRepository,
                notificationService, rateLimiter);

        viewer = User.builder().id(UUID.randomUUID()).email("viewer@example.com").role(Role.USER).build();
        author = User.builder().id(UUID.randomUUID()).email("author@example.com").role(Role.USER).build();
        when(userRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(viewer));
    }

    private Post postWith(PostVisibility visibility) {
        Post post = new Post();
        post.setAuthor(author);
        post.setContent("hello");
        post.setVisibility(visibility);
        when(postRepository.findById(any())).thenReturn(Optional.of(post));
        return post;
    }

    private void stubToResponseCollaborators() {
        when(reactionRepository.findByPostId(any())).thenReturn(List.of());
        when(commentRepository.findByPostIdAndParentCommentIsNullAndHiddenFalseOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(postReportRepository.existsByPostIdAndReporterId(any(), any())).thenReturn(false);
        when(savedPostRepository.existsByUserIdAndPostId(any(), any())).thenReturn(false);
    }

    @Test
    void getPost_allowsAuthor_regardlessOfVisibility() {
        Post post = postWith(PostVisibility.PRIVATE);
        when(userRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(author));
        stubToResponseCollaborators();

        PostResponse response = postService.getPost("viewer@example.com", UUID.randomUUID());

        assertThat(response.content()).isEqualTo("hello");
    }

    @Test
    void getPost_allowsAnyone_whenPublic() {
        postWith(PostVisibility.PUBLIC);
        when(userBlockRepository.existsEitherWay(author.getId(), viewer.getId())).thenReturn(false);
        stubToResponseCollaborators();

        assertThat(postService.getPost("viewer@example.com", UUID.randomUUID())).isNotNull();
    }

    @Test
    void getPost_throwsForbidden_whenPrivateAndNotAuthor() {
        postWith(PostVisibility.PRIVATE);
        when(userBlockRepository.existsEitherWay(author.getId(), viewer.getId())).thenReturn(false);

        assertThatThrownBy(() -> postService.getPost("viewer@example.com", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void getPost_allowsFriendsOnlyPost_whenViewerIsFriend() {
        postWith(PostVisibility.FRIENDS);
        when(userBlockRepository.existsEitherWay(author.getId(), viewer.getId())).thenReturn(false);
        when(friendshipRepository.areFriends(author.getId(), viewer.getId())).thenReturn(true);
        stubToResponseCollaborators();

        assertThat(postService.getPost("viewer@example.com", UUID.randomUUID())).isNotNull();
    }

    @Test
    void getPost_throwsForbidden_whenFriendsOnlyAndViewerIsNotFriend() {
        postWith(PostVisibility.FRIENDS);
        when(userBlockRepository.existsEitherWay(author.getId(), viewer.getId())).thenReturn(false);
        when(friendshipRepository.areFriends(author.getId(), viewer.getId())).thenReturn(false);

        assertThatThrownBy(() -> postService.getPost("viewer@example.com", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void getPost_throwsNotFound_whenEitherSideHasBlockedTheOther_evenIfPublic() {
        postWith(PostVisibility.PUBLIC);
        when(userBlockRepository.existsEitherWay(author.getId(), viewer.getId())).thenReturn(true);

        assertThatThrownBy(() -> postService.getPost("viewer@example.com", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getPost_throwsNotFound_whenPostIsHidden_evenForPublicVisibility() {
        Post post = postWith(PostVisibility.PUBLIC);
        post.setHidden(true);

        assertThatThrownBy(() -> postService.getPost("viewer@example.com", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getPost_throwsNotFound_whenPostIsHidden_evenForItsOwnAuthor() {
        Post post = postWith(PostVisibility.PUBLIC);
        post.setHidden(true);
        when(userRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(author));

        assertThatThrownBy(() -> postService.getPost("viewer@example.com", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
