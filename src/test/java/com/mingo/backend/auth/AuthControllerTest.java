package com.mingo.backend.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.auth.dto.LoginRequest;
import com.mingo.backend.auth.dto.RefreshRequest;
import com.mingo.backend.auth.dto.RegisterRequest;
import com.mingo.backend.common.exception.ApiException;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // JwtAuthenticationFilter is a @Component (a Filter), so @WebMvcTest picks it up and
    // needs its constructor dependencies satisfied even though addFilters=false skips
    // actually running it.
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AuthResponse sampleResponse() {
        return new AuthResponse("access-token", "refresh-token", UUID.randomUUID(),
                "user@example.com", "USER", false);
    }

    @Test
    void register_returns201WithTokens_whenRequestIsValid() throws Exception {
        when(authService.register(any(RegisterRequest.class), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("user@example.com", "password123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void register_returns400_whenEmailIsInvalid() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("not-an-email", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_returns400_whenPasswordTooShort() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("user@example.com", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void register_returns409_whenServiceReportsEmailAlreadyTaken() throws Exception {
        when(authService.register(any(RegisterRequest.class), anyString()))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "Email này đã được sử dụng"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("dup@example.com", "password123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email này đã được sử dụng"));
    }

    @Test
    void login_returns200WithTokens_whenCredentialsValid() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString())).thenReturn(sampleResponse());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void login_returns401_whenServiceReportsInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class), anyString()))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user@example.com", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng"));
    }

    @Test
    void login_returns400_whenEmailIsBlank() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("", "password123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void refresh_returns200WithNewTokens_whenRefreshTokenValid() throws Exception {
        when(authService.refresh(any(RefreshRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("some-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"));
    }

    @Test
    void refresh_returns401_whenRefreshTokenInvalid() throws Exception {
        when(authService.refresh(any(RefreshRequest.class)))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("bad-token"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refresh_returns400_whenRefreshTokenBlank() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.refreshToken").exists());
    }
}
