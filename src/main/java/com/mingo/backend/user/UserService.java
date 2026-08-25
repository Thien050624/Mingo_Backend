package com.mingo.backend.user;

import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.chat.dto.ParticipantSummary;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.user.dto.ChangeEmailRequest;
import com.mingo.backend.user.dto.ChangePasswordRequest;
import com.mingo.backend.user.dto.DeleteAccountRequest;
import com.mingo.backend.user.dto.UpdateProfileRequest;
import com.mingo.backend.user.dto.UserProfileResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository, UserBlockRepository userBlockRepository,
                        PasswordEncoder passwordEncoder, CustomUserDetailsService userDetailsService,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.userBlockRepository = userBlockRepository;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
    }

    public UserProfileResponse getByEmail(String email) {
        return UserProfileResponse.from(findByEmail(email));
    }

    public UserProfileResponse getById(String viewerEmail, UUID id) {
        User viewer = findByEmail(viewerEmail);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (!viewer.getId().equals(id) && userBlockRepository.existsEitherWay(viewer.getId(), id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng");
        }
        return UserProfileResponse.from(user);
    }

    public Page<ParticipantSummary> searchUsers(String viewerEmail, String query, Pageable pageable) {
        if (!StringUtils.hasText(query)) {
            return Page.empty();
        }
        User viewer = findByEmail(viewerEmail);
        return userRepository.searchUsersExcludingBlocked(query.trim(), viewer.getId(), pageable)
                .map(ParticipantSummary::from);
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request) {
        User user = findByEmail(email);

        if (request.displayName() != null) user.setDisplayName(request.displayName());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.bio() != null) user.setBio(request.bio());
        if (request.work() != null) user.setWork(request.work());
        if (request.location() != null) user.setLocation(request.location());

        if (!user.isOnboarded()
                && isNotBlank(user.getDisplayName())
                && isNotBlank(user.getGender())
                && isNotBlank(user.getAvatarUrl())) {
            user.setOnboarded(true);
        }

        userRepository.save(user);
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Mật khẩu hiện tại không đúng");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse changeEmail(String email, ChangeEmailRequest request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Mật khẩu hiện tại không đúng");
        }
        String normalizedEmail = request.newEmail().trim().toLowerCase();
        if (!normalizedEmail.equals(user.getEmail()) && userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email này đã được sử dụng");
        }
        user.setEmail(normalizedEmail);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(normalizedEmail);
        String accessToken = jwtService.generateAccessToken(userDetails, user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getEmail(),
                user.getRole().name(), user.isOnboarded());
    }

    @Transactional
    public void deleteAccount(String email, DeleteAccountRequest request) {
        User user = findByEmail(email);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Mật khẩu hiện tại không đúng");
        }
        if (user.getRole() == Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể xoá tài khoản quản trị viên");
        }
        userRepository.delete(user);
    }

    private User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }
}
