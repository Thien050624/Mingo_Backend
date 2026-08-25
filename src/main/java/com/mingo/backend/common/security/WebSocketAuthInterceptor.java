package com.mingo.backend.common.security;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

/**
 * Authenticates STOMP CONNECT frames using the same JWT access token scheme as REST requests.
 * The browser's WebSocket API can't set an Authorization header on the handshake itself, so the
 * token travels as a native STOMP header on CONNECT instead — the HTTP handshake to /ws is left
 * open (see SecurityConfig) and this is where real authentication happens.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public WebSocketAuthInterceptor(JwtService jwtService, CustomUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    String email = jwtService.extractUsername(token);
                    if ("access".equals(jwtService.extractTokenType(token))) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                        if (jwtService.isTokenValid(token, userDetails)) {
                            accessor.setUser(new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities()));
                        }
                    }
                } catch (Exception ex) {
                    // Invalid/expired token: leave the session without a Principal. Anything that
                    // requires convertAndSendToUser simply won't reach this connection.
                }
            }
        }

        return message;
    }
}
