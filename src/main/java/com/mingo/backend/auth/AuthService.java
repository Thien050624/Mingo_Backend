package com.mingo.backend.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.auth.dto.LoginRequest;
import com.mingo.backend.auth.dto.RefreshRequest;
import com.mingo.backend.auth.dto.RegisterRequest;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.ratelimit.RateLimiter;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import com.mingo.backend.user.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final int MAX_ATTEMPTS_PER_MINUTE = 5;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final CustomUserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final RateLimiter rateLimiter;
    private final GoogleIdTokenVerifier googleVerifier;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            CustomUserDetailsService userDetailsService,
            JwtService jwtService,
            RateLimiter rateLimiter,
            @Value("${app.google.client-id}") String googleClientId) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.googleVerifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, String clientIp) {
        rateLimiter.checkAllowed("register:" + clientIp, MAX_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));

        String normalizedEmail = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email này đã được sử dụng");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .onboarded(false)
                .build();
        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse loginWithGoogle(String idToken, String clientIp) {
        rateLimiter.checkAllowed("google-login:" + clientIp, MAX_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));

        GoogleIdToken token;
        try {
            token = googleVerifier.verify(idToken);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            token = null;
        }
        if (token == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Token Google không hợp lệ");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email Google chưa được xác minh");
        }
        String normalizedEmail = payload.getEmail().trim().toLowerCase();

        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        User user = existing.orElseGet(() -> {
            // No password is ever set for a Google-only account; a random hash keeps the
            // (non-null) column satisfied while making password login naturally impossible.
            User created = User.builder()
                    .email(normalizedEmail)
                    .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .role(Role.USER)
                    .onboarded(false)
                    .build();
            userRepository.save(created);
            return created;
        });

        if (user.getStatus() == UserStatus.BANNED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khoá");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request, String clientIp) {
        String normalizedEmail = request.email().trim().toLowerCase();
        rateLimiter.checkAllowed("login:" + clientIp + ":" + normalizedEmail, MAX_ATTEMPTS_PER_MINUTE, Duration.ofMinutes(1));

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password()));
        } catch (BadCredentialsException ex) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
        } catch (DisabledException ex) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khoá");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!"refresh".equals(jwtService.extractTokenType(token))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
        }

        String email = jwtService.extractUsername(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        if (!userDetails.isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khoá");
        }
        if (!jwtService.isTokenValid(token, userDetails)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn hoặc không hợp lệ");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Không tìm thấy người dùng"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtService.generateAccessToken(userDetails, user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                user.isOnboarded());
    }
}
