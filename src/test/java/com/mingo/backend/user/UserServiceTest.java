package com.mingo.backend.user;

import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.friend.FriendshipRepository;
import com.mingo.backend.post.PostRepository;
import com.mingo.backend.user.dto.ChangePasswordRequest;
import com.mingo.backend.user.dto.UpdateProfileRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserBlockRepository userBlockRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private PostRepository postRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private JwtService jwtService;

    private UserService userService;

    private User viewer;
    private User target;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userBlockRepository, friendshipRepository, postRepository,
                passwordEncoder, userDetailsService, jwtService);
        viewer = User.builder().id(UUID.randomUUID()).email("viewer@example.com").role(Role.USER).build();
        target = User.builder().id(UUID.randomUUID()).email("target@example.com").role(Role.USER).build();
        lenient().when(userRepository.findByEmail("viewer@example.com")).thenReturn(Optional.of(viewer));
    }

    @Test
    void getById_returnsProfile_whenNotBlocked() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userBlockRepository.existsEitherWay(viewer.getId(), target.getId())).thenReturn(false);

        var response = userService.getById("viewer@example.com", target.getId());

        assertThat(response.id()).isEqualTo(target.getId());
    }

    @Test
    void getById_throwsNotFound_whenEitherSideHasBlockedTheOther() {
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userBlockRepository.existsEitherWay(viewer.getId(), target.getId())).thenReturn(true);

        assertThatThrownBy(() -> userService.getById("viewer@example.com", target.getId()))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void getById_allowsViewingOwnProfile_withoutCheckingBlockStatus() {
        when(userRepository.findById(viewer.getId())).thenReturn(Optional.of(viewer));

        var response = userService.getById("viewer@example.com", viewer.getId());

        assertThat(response.id()).isEqualTo(viewer.getId());
        verify(userBlockRepository, never()).existsEitherWay(any(), any());
    }

    @Test
    void changePassword_throwsUnauthorized_whenCurrentPasswordWrong() {
        viewer.setPasswordHash("hashed");
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword("viewer@example.com",
                new ChangePasswordRequest("wrong", "newpass123")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.UNAUTHORIZED);

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_marksOnboarded_onceRequiredFieldsArePresent() {
        viewer.setOnboarded(false);
        UpdateProfileRequest request = new UpdateProfileRequest("Ten Nguoi Dung", "male", "http://a/avatar.png",
                null, null, null);

        var response = userService.updateProfile("viewer@example.com", request);

        assertThat(response.onboarded()).isTrue();
    }

    @Test
    void updateProfile_staysNotOnboarded_whenRequiredFieldStillMissing() {
        viewer.setOnboarded(false);
        UpdateProfileRequest request = new UpdateProfileRequest("Ten Nguoi Dung", null, null, null, null, null);

        var response = userService.updateProfile("viewer@example.com", request);

        assertThat(response.onboarded()).isFalse();
    }

    @Test
    void deleteAccount_throwsBadRequest_forAdminAccounts() {
        viewer.setRole(Role.ADMIN);
        viewer.setPasswordHash("hashed");
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteAccount("viewer@example.com",
                new com.mingo.backend.user.dto.DeleteAccountRequest("correct")))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("status", org.springframework.http.HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).delete(any());
    }
}
