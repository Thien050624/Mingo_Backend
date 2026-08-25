package com.mingo.backend.friend;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.friend.dto.FriendshipStatusResponse;
import com.mingo.backend.friend.dto.UserSummary;
import com.mingo.backend.notification.NotificationService;
import com.mingo.backend.presence.OnlinePresenceService;
import com.mingo.backend.presence.UserPresenceChangedEvent;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlock;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendshipRepository friendshipRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private OnlinePresenceService presenceService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private FriendService friendService;

    private User me;
    private User other;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(friendshipRepository, userBlockRepository, userRepository,
                notificationService, presenceService, messagingTemplate);

        me = User.builder().id(UUID.randomUUID()).email("me@example.com").role(Role.USER).build();
        other = User.builder().id(UUID.randomUUID()).email("other@example.com").role(Role.USER).build();
        lenient().when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(me));
    }

    private Friendship friendshipBetween(User requester, User addressee, FriendshipStatus status) {
        Friendship f = new Friendship();
        f.setRequester(requester);
        f.setAddressee(addressee);
        f.setStatus(status);
        return f;
    }

    @Test
    void sendRequest_throwsBadRequest_whenTargetIsSelf() {
        assertThatThrownBy(() -> friendService.sendRequest("me@example.com", me.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chính mình");
    }

    @Test
    void sendRequest_throwsForbidden_whenEitherHasBlockedTheOther() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(true);

        assertThatThrownBy(() -> friendService.sendRequest("me@example.com", other.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.FORBIDDEN);

        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void sendRequest_createsPendingRequest_whenNoExistingRelationship() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(false);
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(friendshipRepository.findBetween(me.getId(), other.getId())).thenReturn(Optional.empty());

        FriendshipStatusResponse response = friendService.sendRequest("me@example.com", other.getId());

        assertThat(response.status()).isEqualTo("PENDING_SENT");
        verify(friendshipRepository).save(any(Friendship.class));
    }

    @Test
    void sendRequest_autoAccepts_whenTargetAlreadySentMeARequest() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(false);
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        Friendship existing = friendshipBetween(other, me, FriendshipStatus.PENDING);
        when(friendshipRepository.findBetween(me.getId(), other.getId())).thenReturn(Optional.of(existing));

        FriendshipStatusResponse response = friendService.sendRequest("me@example.com", other.getId());

        assertThat(response.status()).isEqualTo("FRIENDS");
        assertThat(existing.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void sendRequest_throwsBadRequest_whenAlreadyFriends() {
        when(userBlockRepository.existsEitherWay(me.getId(), other.getId())).thenReturn(false);
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        Friendship existing = friendshipBetween(me, other, FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findBetween(me.getId(), other.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> friendService.sendRequest("me@example.com", other.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã là bạn bè");
    }

    @Test
    void blockUser_removesExistingFriendship_andCreatesBlockRecord() {
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), other.getId())).thenReturn(false);
        Friendship existing = friendshipBetween(me, other, FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findBetween(me.getId(), other.getId())).thenReturn(Optional.of(existing));

        friendService.blockUser("me@example.com", other.getId());

        verify(userBlockRepository).save(any(UserBlock.class));
        verify(friendshipRepository).delete(existing);
    }

    @Test
    void blockUser_doesNotDuplicateBlockRecord_whenAlreadyBlocked() {
        when(userRepository.findById(other.getId())).thenReturn(Optional.of(other));
        when(userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), other.getId())).thenReturn(true);
        when(friendshipRepository.findBetween(me.getId(), other.getId())).thenReturn(Optional.empty());

        friendService.blockUser("me@example.com", other.getId());

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void blockUser_throwsBadRequest_whenTargetIsSelf() {
        assertThatThrownBy(() -> friendService.blockUser("me@example.com", me.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("chính mình");
    }

    @Test
    void listOnlineFriends_returnsOnlyFriendsCurrentlyOnline() {
        User onlineFriend = User.builder().id(UUID.randomUUID()).email("online@example.com").build();
        User offlineFriend = User.builder().id(UUID.randomUUID()).email("offline@example.com").build();
        Friendship f1 = friendshipBetween(me, onlineFriend, FriendshipStatus.ACCEPTED);
        Friendship f2 = friendshipBetween(me, offlineFriend, FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(f1, f2));
        when(presenceService.onlineAmong(any())).thenReturn(Set.of(onlineFriend.getId()));

        List<UserSummary> result = friendService.listOnlineFriends("me@example.com");

        assertThat(result).extracting(UserSummary::id).containsExactly(onlineFriend.getId());
    }

    @Test
    void onPresenceChanged_broadcastsToEachAcceptedFriend() {
        User friend1 = User.builder().id(UUID.randomUUID()).email("friend1@example.com").build();
        User friend2 = User.builder().id(UUID.randomUUID()).email("friend2@example.com").build();
        Friendship f1 = friendshipBetween(me, friend1, FriendshipStatus.ACCEPTED);
        Friendship f2 = friendshipBetween(friend2, me, FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(f1, f2));

        friendService.onPresenceChanged(new UserPresenceChangedEvent(me.getId(), true));

        verify(messagingTemplate).convertAndSendToUser(eq("friend1@example.com"), eq("/queue/presence"), any());
        verify(messagingTemplate).convertAndSendToUser(eq("friend2@example.com"), eq("/queue/presence"), any());
    }

    @Test
    void listSuggestions_excludesBlockedUsers() {
        User blocked = User.builder().id(UUID.randomUUID()).email("blocked@example.com").build();
        User suggestion = User.builder().id(UUID.randomUUID()).email("suggestion@example.com").build();
        when(friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());
        when(friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.PENDING))
                .thenReturn(List.of());
        when(userRepository.findAll()).thenReturn(List.of(blocked, suggestion));
        when(userBlockRepository.existsEitherWay(me.getId(), blocked.getId())).thenReturn(true);
        when(userBlockRepository.existsEitherWay(me.getId(), suggestion.getId())).thenReturn(false);

        var result = friendService.listSuggestions("me@example.com");

        assertThat(result).extracting(s -> s.user().id()).containsExactly(suggestion.getId());
    }
}
