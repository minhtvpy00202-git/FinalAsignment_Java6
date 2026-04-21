package com.poly.ASM.service.chat;

import com.poly.ASM.dto.chat.ChatMessageDto;
import com.poly.ASM.dto.chat.ChatConversationDto;
import com.poly.ASM.dto.chat.ChatSendRequest;
import org.springframework.security.core.Authentication;

import java.util.List;

public interface ChatService {

    List<ChatMessageDto> getUserHistory(Integer productId);

    List<ChatMessageDto> getAdminHistory(String customerId, Integer productId);

    List<ChatConversationDto> getAdminConversations();

    void handleIncoming(ChatSendRequest request, Authentication authentication, String principalName);
}
