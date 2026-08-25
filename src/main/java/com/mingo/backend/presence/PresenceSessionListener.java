package com.mingo.backend.presence;

import com.mingo.backend.user.UserRepository;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class PresenceSessionListener {

    private final OnlinePresenceService presenceService;
    private final UserRepository userRepository;

    public PresenceSessionListener(OnlinePresenceService presenceService, UserRepository userRepository) {
        this.presenceService = presenceService;
        this.userRepository = userRepository;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (user == null || sessionId == null) return;

        userRepository.findByEmail(user.getName())
                .ifPresent(u -> presenceService.connect(sessionId, u.getId()));
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        presenceService.disconnect(event.getSessionId());
    }
}
