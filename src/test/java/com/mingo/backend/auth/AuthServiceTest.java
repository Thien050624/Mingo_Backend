package com.mingo.backend.auth;

import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.auth.dto.LoginRequest;
import com.mingo.backend.auth.dto.RegisterRequest;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.ratelimit.RateLimiter;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.user.Role;
import com.mingo.backend.user.User;
import com.mingo.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, authenticationManager, userDetailsService,
                jwtService, new RateLimiter());
    }

    private UserDetails fakeUserDetails(String email) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(email)
                .password("hashed")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void register_createsUser_whenEmailNotTaken() {
        RegisterRequest request = new RegisterRequest("New@Example.com", "password123");
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(userDetailsService.loadUserByUsername("new@example.com")).thenReturn(fakeUserDetails("new@example.com"));
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.register(request, "127.0.0.1");

        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.onboarded()).isFalse();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_throwsConflict_whenEmailAlreadyTaken() {
        RegisterRequest request = new RegisterRequest("dup@example.com", "password123");
        when(userRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("đã được sử dụng");

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_returnsTokens_whenCredentialsValid() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hashed")
                .role(Role.USER)
                .onboarded(true)
                .build();

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(fakeUserDetails("user@example.com"));
        when(jwtService.generateAccessToken(any(), anyString())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse response = authService.login(request, "127.0.0.1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.onboarded()).isTrue();
    }

    @Test
    void login_throwsUnauthorized_whenCredentialsInvalid() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        doThrow(new BadCredentialsException("bad"))
                .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("không đúng");
    }
}
