package com.mingo.backend.user;

import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.user.dto.ChangeEmailRequest;
import com.mingo.backend.user.dto.ChangePasswordRequest;
import com.mingo.backend.user.dto.DeleteAccountRequest;
import com.mingo.backend.user.dto.UpdateProfileRequest;
import com.mingo.backend.user.dto.UserProfileResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserProfileResponse getMe(Authentication authentication) {
        return userService.getByEmail(authentication.getName());
    }

    @GetMapping("/search")
    public Page<ParticipantSummary> searchUsers(Authentication authentication, @RequestParam String q,
                                                 @PageableDefault(size = 10) Pageable pageable) {
        return userService.searchUsers(authentication.getName(), q, pageable);
    }

    @PatchMapping("/me")
    public UserProfileResponse updateMe(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(authentication.getName(), request);
    }

    @GetMapping("/{id}")
    public UserProfileResponse getById(Authentication authentication, @PathVariable UUID id) {
        return userService.getById(authentication.getName(), id);
    }

    @PatchMapping("/me/password")
    public void changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(authentication.getName(), request);
    }

    @PatchMapping("/me/email")
    public AuthResponse changeEmail(Authentication authentication, @Valid @RequestBody ChangeEmailRequest request) {
        return userService.changeEmail(authentication.getName(), request);
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(Authentication authentication, @Valid @RequestBody DeleteAccountRequest request) {
        userService.deleteAccount(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
