package com.mingo.backend.friend;

import com.mingo.backend.friend.dto.FriendRequestResponse;
import com.mingo.backend.friend.dto.FriendshipStatusResponse;
import com.mingo.backend.friend.dto.SuggestionResponse;
import com.mingo.backend.friend.dto.UserSummary;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/friends")
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @PostMapping("/{userId}")
    public FriendshipStatusResponse sendRequest(Authentication auth, @PathVariable UUID userId) {
        return friendService.sendRequest(auth.getName(), userId);
    }

    @PutMapping("/{userId}/accept")
    public FriendshipStatusResponse acceptRequest(Authentication auth, @PathVariable UUID userId) {
        return friendService.acceptRequest(auth.getName(), userId);
    }

    @DeleteMapping("/{userId}")
    public FriendshipStatusResponse removeRelationship(Authentication auth, @PathVariable UUID userId) {
        return friendService.removeRelationship(auth.getName(), userId);
    }

    @GetMapping("/status/{userId}")
    public FriendshipStatusResponse getStatus(Authentication auth, @PathVariable UUID userId) {
        return friendService.getStatus(auth.getName(), userId);
    }

    @GetMapping("/requests")
    public List<FriendRequestResponse> listIncomingRequests(Authentication auth) {
        return friendService.listIncomingRequests(auth.getName());
    }

    @GetMapping("/user/{userId}")
    public List<UserSummary> listFriends(@PathVariable UUID userId) {
        return friendService.listFriends(userId);
    }

    @GetMapping("/suggestions")
    public List<SuggestionResponse> listSuggestions(Authentication auth) {
        return friendService.listSuggestions(auth.getName());
    }

    @PostMapping("/{userId}/block")
    public void blockUser(Authentication auth, @PathVariable UUID userId) {
        friendService.blockUser(auth.getName(), userId);
    }

    @DeleteMapping("/{userId}/block")
    public void unblockUser(Authentication auth, @PathVariable UUID userId) {
        friendService.unblockUser(auth.getName(), userId);
    }

    @GetMapping("/blocked")
    public List<UserSummary> listBlockedUsers(Authentication auth) {
        return friendService.listBlockedUsers(auth.getName());
    }

    @GetMapping("/online")
    public List<UserSummary> listOnlineFriends(Authentication auth) {
        return friendService.listOnlineFriends(auth.getName());
    }
}
