package com.mingo.backend.friend;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.friend.dto.FriendRequestResponse;
import com.mingo.backend.friend.dto.FriendshipStatusResponse;
import com.mingo.backend.friend.dto.PresenceMessage;
import com.mingo.backend.friend.dto.SuggestionResponse;
import com.mingo.backend.friend.dto.UserSummary;
import com.mingo.backend.notification.NotificationService;
import com.mingo.backend.notification.NotificationType;
import com.mingo.backend.presence.OnlinePresenceService;
import com.mingo.backend.presence.UserPresenceChangedEvent;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserBlock;
import com.mingo.backend.user.UserBlockRepository;
import com.mingo.backend.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final OnlinePresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    public FriendService(FriendshipRepository friendshipRepository, UserBlockRepository userBlockRepository,
                          UserRepository userRepository, NotificationService notificationService,
                          OnlinePresenceService presenceService, SimpMessagingTemplate messagingTemplate) {
        this.friendshipRepository = friendshipRepository;
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.presenceService = presenceService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    @Transactional(readOnly = true)
    public void onPresenceChanged(UserPresenceChangedEvent event) {
        PresenceMessage message = new PresenceMessage(event.userId(), event.online());
        friendshipRepository.findAllByUserAndStatus(event.userId(), FriendshipStatus.ACCEPTED)
                .forEach(f -> messagingTemplate.convertAndSendToUser(
                        f.otherThan(event.userId()).getEmail(), "/queue/presence", message));
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listOnlineFriends(String email) {
        User me = findUser(email);
        Set<UUID> friendIds = friendIdsOf(me.getId());
        Set<UUID> onlineIds = presenceService.onlineAmong(friendIds);
        return friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.ACCEPTED).stream()
                .map(f -> f.otherThan(me.getId()))
                .filter(u -> onlineIds.contains(u.getId()))
                .map(UserSummary::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public FriendshipStatusResponse sendRequest(String email, UUID targetUserId) {
        User me = findUser(email);
        if (me.getId().equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự kết bạn với chính mình");
        }
        if (userBlockRepository.existsEitherWay(me.getId(), targetUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không thể thực hiện hành động này");
        }
        User target = findUserById(targetUserId);

        Friendship existing = friendshipRepository.findBetween(me.getId(), targetUserId).orElse(null);
        if (existing != null) {
            if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Hai người đã là bạn bè");
            }
            // A pending request already exists. If it was sent to me by the target, accept it now.
            if (existing.getAddressee().getId().equals(me.getId())) {
                existing.setStatus(FriendshipStatus.ACCEPTED);
                notificationService.notify(target, me, NotificationType.FRIEND_ACCEPTED, null, null);
                return new FriendshipStatusResponse("FRIENDS");
            }
            throw new ApiException(HttpStatus.BAD_REQUEST, "Lời mời kết bạn đang chờ xử lý");
        }

        Friendship friendship = new Friendship();
        friendship.setRequester(me);
        friendship.setAddressee(target);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);
        notificationService.notify(target, me, NotificationType.FRIEND_REQUEST, null, null);
        return new FriendshipStatusResponse("PENDING_SENT");
    }

    @Transactional
    public FriendshipStatusResponse acceptRequest(String email, UUID requesterId) {
        User me = findUser(email);
        Friendship friendship = friendshipRepository.findBetween(me.getId(), requesterId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy lời mời kết bạn"));

        if (friendship.getStatus() != FriendshipStatus.PENDING || !friendship.getAddressee().getId().equals(me.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không có lời mời nào để chấp nhận");
        }
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        notificationService.notify(friendship.getRequester(), me, NotificationType.FRIEND_ACCEPTED, null, null);
        return new FriendshipStatusResponse("FRIENDS");
    }

    @Transactional
    public FriendshipStatusResponse removeRelationship(String email, UUID otherUserId) {
        User me = findUser(email);
        Friendship friendship = friendshipRepository.findBetween(me.getId(), otherUserId).orElse(null);
        if (friendship != null) {
            friendshipRepository.delete(friendship);
        }
        return new FriendshipStatusResponse("NONE");
    }

    @Transactional(readOnly = true)
    public FriendshipStatusResponse getStatus(String email, UUID otherUserId) {
        User me = findUser(email);
        boolean blockedByMe = userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), otherUserId);
        return new FriendshipStatusResponse(statusBetween(me.getId(), otherUserId), blockedByMe);
    }

    @Transactional
    public void blockUser(String email, UUID targetUserId) {
        User me = findUser(email);
        if (me.getId().equals(targetUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự chặn chính mình");
        }
        User target = findUserById(targetUserId);
        if (!userBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), targetUserId)) {
            UserBlock block = new UserBlock();
            block.setBlocker(me);
            block.setBlocked(target);
            userBlockRepository.save(block);
        }
        friendshipRepository.findBetween(me.getId(), targetUserId).ifPresent(friendshipRepository::delete);
    }

    @Transactional
    public void unblockUser(String email, UUID targetUserId) {
        User me = findUser(email);
        userBlockRepository.deleteByBlockerIdAndBlockedId(me.getId(), targetUserId);
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listBlockedUsers(String email) {
        User me = findUser(email);
        return userBlockRepository.findByBlockerIdOrderByCreatedAtDesc(me.getId()).stream()
                .map(b -> UserSummary.from(b.getBlocked()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FriendRequestResponse> listIncomingRequests(String email) {
        User me = findUser(email);
        return friendshipRepository.findByAddresseeIdAndStatus(me.getId(), FriendshipStatus.PENDING).stream()
                .map(f -> new FriendRequestResponse(f.getId(), UserSummary.from(f.getRequester()), f.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listFriends(UUID userId) {
        return friendshipRepository.findAllByUserAndStatus(userId, FriendshipStatus.ACCEPTED).stream()
                .map(f -> UserSummary.from(f.otherThan(userId)))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SuggestionResponse> listSuggestions(String email) {
        User me = findUser(email);
        Set<UUID> myFriendIds = friendIdsOf(me.getId());
        Set<UUID> excluded = new HashSet<>(myFriendIds);
        excluded.add(me.getId());
        friendshipRepository.findAllByUserAndStatus(me.getId(), FriendshipStatus.PENDING)
                .forEach(f -> excluded.add(f.otherThan(me.getId()).getId()));

        return userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .filter(u -> !userBlockRepository.existsEitherWay(me.getId(), u.getId()))
                .filter(u -> !excluded.contains(u.getId()))
                .map(u -> {
                    long mutual = friendIdsOf(u.getId()).stream().filter(myFriendIds::contains).count();
                    return new SuggestionResponse(UserSummary.from(u), mutual);
                })
                .sorted(Comparator.comparingLong(SuggestionResponse::mutualCount).reversed())
                .limit(20)
                .collect(Collectors.toList());
    }

    private Set<UUID> friendIdsOf(UUID userId) {
        return friendshipRepository.findAllByUserAndStatus(userId, FriendshipStatus.ACCEPTED).stream()
                .map(f -> f.otherThan(userId).getId())
                .collect(Collectors.toSet());
    }

    private String statusBetween(UUID meId, UUID otherId) {
        Friendship friendship = friendshipRepository.findBetween(meId, otherId).orElse(null);
        if (friendship == null) return "NONE";
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return "FRIENDS";
        return friendship.getRequester().getId().equals(meId) ? "PENDING_SENT" : "PENDING_RECEIVED";
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private User findUserById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }
}
