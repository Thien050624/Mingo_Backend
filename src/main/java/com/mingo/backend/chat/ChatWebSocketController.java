package com.mingo.backend.chat;

import com.mingo.backend.chat.dto.TypingRequest;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;

    public ChatWebSocketController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingRequest request, Principal principal) {
        if (principal == null) return;
        chatService.notifyTyping(principal.getName(), request.conversationId());
    }
}
