package com.poly.ASM.controller.web;

import com.poly.ASM.dto.chat.ChatSendRequest;
import com.poly.ASM.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWsController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void send(ChatSendRequest request, Authentication authentication, Principal principal) {
        chatService.handleIncoming(request, authentication, principal == null ? null : principal.getName());
    }
}
