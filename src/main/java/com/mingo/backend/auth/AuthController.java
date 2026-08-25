package com.mingo.backend.auth;

import com.mingo.backend.auth.dto.AuthResponse;
import com.mingo.backend.auth.dto.LoginRequest;
import com.mingo.backend.auth.dto.RefreshRequest;
import com.mingo.backend.auth.dto.RegisterRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request, clientIp(httpRequest)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    private String clientIp(HttpServletRequest request) {
        // No reverse proxy sits in front of this backend, so X-Forwarded-For would be
        // client-supplied and trivially spoofable — use the actual socket address instead.
        return request.getRemoteAddr();
    }
}
