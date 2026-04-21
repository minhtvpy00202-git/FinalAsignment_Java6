package com.poly.ASM.service.chat.impl;

import com.poly.ASM.dto.chat.ChatLockNotice;
import com.poly.ASM.dto.chat.ChatConversationDto;
import com.poly.ASM.dto.chat.ChatMessageDto;
import com.poly.ASM.dto.chat.ChatSendRequest;
import com.poly.ASM.entity.chat.ChatMessage;
import com.poly.ASM.entity.product.Product;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.exception.ApiException;
import com.poly.ASM.repository.chat.ChatMessageRepository;
import com.poly.ASM.repository.product.ProductRepository;
import com.poly.ASM.service.auth.AuthService;
import com.poly.ASM.service.chat.ChatService;
import com.poly.ASM.service.notification.NotificationService;
import com.poly.ASM.service.user.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final AuthService authService;
    private final AccountService accountService;
    private final ProductRepository productRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    @Override
    public List<ChatMessageDto> getUserHistory(Integer productId) {
        Account user = authService.getUser();
        if (user == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập");
        }
        if (productId == null || productId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "productId không hợp lệ");
        }
        String assignedAdminId = chatMessageRepository.findAssignedAdminId(user.getUsername(), productId);
        String assignedAdminFullname = resolveFullname(assignedAdminId);
        return chatMessageRepository.findByCustomerIdAndProductIdOrderByCreatedAtAsc(user.getUsername(), productId)
                .stream()
                .map(msg -> toDto(msg, assignedAdminFullname))
                .toList();
    }

    @Override
    public List<ChatMessageDto> getAdminHistory(String customerId, Integer productId) {
        if (!authService.hasRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền truy cập");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "customerId không hợp lệ");
        }
        if (productId == null || productId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "productId không hợp lệ");
        }
        String assignedAdminId = chatMessageRepository.findAssignedAdminId(customerId, productId);
        String assignedAdminFullname = resolveFullname(assignedAdminId);
        return chatMessageRepository.findByCustomerIdAndProductIdOrderByCreatedAtAsc(customerId, productId)
                .stream()
                .map(msg -> toDto(msg, assignedAdminFullname))
                .toList();
    }

    @Override
    public List<ChatConversationDto> getAdminConversations() {
        if (!authService.hasRole("ADMIN")) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền truy cập");
        }
        Account currentAdmin = authService.getUser();
        String currentAdminUsername = currentAdmin == null ? null : currentAdmin.getUsername();
        LinkedHashMap<String, ChatMessage> latestByConversation = new LinkedHashMap<>();
        for (ChatMessage message : chatMessageRepository.findTop500ByOrderByCreatedAtDesc()) {
            if (message == null || message.getCustomerId() == null || message.getProductId() == null) {
                continue;
            }
            String key = message.getCustomerId() + "::" + message.getProductId();
            if (!latestByConversation.containsKey(key)) {
                latestByConversation.put(key, message);
            }
        }
        return latestByConversation.values().stream().map(message -> {
            String assignedAdminId = chatMessageRepository.findAssignedAdminId(message.getCustomerId(), message.getProductId());
            String assignedAdminFullname = resolveFullname(assignedAdminId);
            Optional<Account> customerOpt = accountService.findByUsername(message.getCustomerId());
            String customerFullname = customerOpt.map(a -> a.getFullname() == null ? "" : a.getFullname()).orElse("");
            String customerPhoto = customerOpt.map(a -> a.getPhoto() == null ? "" : a.getPhoto()).orElse("");
            Optional<Product> productOpt = productRepository.findByIdWithCategory(message.getProductId());
            String productName = productOpt.map(Product::getName).orElse("");
            String productImage = productOpt.map(Product::getImage).orElse("");
            BigDecimal productPrice = productOpt.map(Product::getPrice).orElse(BigDecimal.ZERO);
            BigDecimal productDiscount = productOpt.map(p -> p.getDiscount() == null ? BigDecimal.ZERO : p.getDiscount()).orElse(BigDecimal.ZERO);
            String categoryName = productOpt
                    .map(Product::getCategory)
                    .map(c -> c.getName() == null ? "" : c.getName())
                    .orElse("");
            BigDecimal productFinalPrice = calcFinalPrice(productPrice, productDiscount);
            boolean lockedByOther = assignedAdminId != null
                    && !assignedAdminId.isBlank()
                    && currentAdminUsername != null
                    && !assignedAdminId.equals(currentAdminUsername);
            String lastText = message.getContent() != null && !message.getContent().isBlank()
                    ? message.getContent()
                    : (message.getMediaUrl() == null || message.getMediaUrl().isBlank() ? "" : "[Ảnh]");
            return new ChatConversationDto(
                    message.getCustomerId(),
                    customerFullname,
                    customerPhoto,
                    message.getProductId(),
                    productName,
                    productImage,
                    productPrice,
                    productDiscount,
                    productFinalPrice,
                    categoryName,
                    assignedAdminId,
                    assignedAdminFullname,
                    lockedByOther,
                    lastText,
                    message.getCreatedAt()
            );
        }).toList();
    }

    @Override
    @Transactional
    public void handleIncoming(ChatSendRequest request, Authentication authentication, String principalName) {
        String actorUsername = resolveActorUsername(authentication, principalName);
        if (actorUsername == null || actorUsername.isBlank()) {
            return;
        }
        Integer productId = request == null ? null : request.productId();
        if (productId == null || productId <= 0) {
            return;
        }
        String content = request.content() == null ? null : request.content().trim();
        String mediaUrl = request.mediaUrl() == null ? null : request.mediaUrl().trim();
        if ((content == null || content.isBlank()) && (mediaUrl == null || mediaUrl.isBlank())) {
            return;
        }

        boolean isAdmin = hasRole(authentication, "ADMIN") || isAdminAccount(actorUsername);
        if (isAdmin) {
            handleAdminSend(actorUsername, request.customerId(), productId, content, mediaUrl);
            return;
        }
        handleUserSend(actorUsername, productId, content, mediaUrl);
    }

    private void handleUserSend(String username, Integer productId, String content, String mediaUrl) {
        ChatMessage message = new ChatMessage();
        message.setProductId(productId);
        message.setCustomerId(username);
        message.setAdminId(null);
        message.setContent(content);
        message.setMediaUrl(mediaUrl);
        message.setSenderRole("USER");
        ChatMessage saved = chatMessageRepository.save(message);

        String assignedAdminId = chatMessageRepository.findAssignedAdminId(username, productId);
        String assignedAdminFullname = resolveFullname(assignedAdminId);
        ChatMessageDto dto = toDto(saved, assignedAdminFullname);

        messagingTemplate.convertAndSendToUser(username, "/queue/messages", dto);
        messagingTemplate.convertAndSend("/topic/admin/messages", dto);

        String preview = content != null && !content.isBlank() ? content : "[Ảnh]";
        notificationService.notifyChatSupportForAdmins(username, resolveFullname(username), productId, preview, assignedAdminId);
    }

    private void handleAdminSend(String adminUsername, String customerId, Integer productId, String content, String mediaUrl) {
        if (customerId == null || customerId.isBlank()) {
            messagingTemplate.convertAndSendToUser(adminUsername, "/queue/chat-lock",
                    new ChatLockNotice(productId, null, "", "Vui lòng chọn khách hàng trước khi gửi."));
            return;
        }
        String assignedAdminId = chatMessageRepository.findAssignedAdminId(customerId, productId);
        if (assignedAdminId != null && !assignedAdminId.isBlank() && !assignedAdminId.equals(adminUsername)) {
            String fullname = resolveFullname(assignedAdminId);
            messagingTemplate.convertAndSendToUser(adminUsername, "/queue/chat-lock",
                    new ChatLockNotice(productId, customerId, fullname, "Tin nhắn này đã được nhận hỗ trợ bởi Admin " + fullname));
            return;
        }
        if (assignedAdminId == null || assignedAdminId.isBlank()) {
            int updated = chatMessageRepository.assignAdminIfNull(adminUsername, customerId, productId);
            if (updated == 0) {
                String latestAssigned = chatMessageRepository.findAssignedAdminId(customerId, productId);
                if (latestAssigned != null && !latestAssigned.isBlank() && !latestAssigned.equals(adminUsername)) {
                    String fullname = resolveFullname(latestAssigned);
                    messagingTemplate.convertAndSendToUser(adminUsername, "/queue/chat-lock",
                            new ChatLockNotice(productId, customerId, fullname, "Tin nhắn này đã được nhận hỗ trợ bởi Admin " + fullname));
                    return;
                }
            }
        }
        ChatMessage message = new ChatMessage();
        message.setProductId(productId);
        message.setCustomerId(customerId);
        message.setAdminId(adminUsername);
        message.setContent(content);
        message.setMediaUrl(mediaUrl);
        message.setSenderRole("ADMIN");
        ChatMessage saved = chatMessageRepository.save(message);

        String adminFullname = resolveFullname(adminUsername);
        ChatMessageDto dto = toDto(saved, adminFullname);
        messagingTemplate.convertAndSendToUser(customerId, "/queue/messages", dto);
        messagingTemplate.convertAndSendToUser(adminUsername, "/queue/messages", dto);
        messagingTemplate.convertAndSend("/topic/admin/messages", dto);
    }

    private ChatMessageDto toDto(ChatMessage entity, String assignedAdminFullname) {
        if (entity == null) {
            return new ChatMessageDto(null, null, null, null, null, null, null, null, assignedAdminFullname);
        }
        return new ChatMessageDto(
                entity.getId(),
                entity.getProductId(),
                entity.getCustomerId(),
                entity.getAdminId(),
                entity.getSenderRole(),
                entity.getContent(),
                entity.getMediaUrl(),
                entity.getCreatedAt(),
                assignedAdminFullname
        );
    }

    private boolean hasRole(Authentication authentication, String role) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        String needle = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority != null && needle.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdminAccount(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return accountService.findByUsername(username)
                .map(account -> account.getAuthorities() != null && account.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority != null
                                        && authority.getRole() != null
                                        && "ADMIN".equalsIgnoreCase(authority.getRole().getId())))
                .orElse(false);
    }

    private String resolveActorUsername(Authentication authentication, String principalName) {
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getName() != null
                && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        if (principalName != null && !principalName.isBlank() && !"anonymousUser".equals(principalName)) {
            return principalName;
        }
        return null;
    }

    private String resolveFullname(String username) {
        if (username == null || username.isBlank()) {
            return "";
        }
        Optional<Account> account = accountService.findByUsername(username);
        return account.map(a -> a.getFullname() == null ? "" : a.getFullname()).orElse("");
    }

    private BigDecimal calcFinalPrice(BigDecimal price, BigDecimal discount) {
        BigDecimal safePrice = price == null ? BigDecimal.ZERO : price;
        BigDecimal safeDiscount = discount == null ? BigDecimal.ZERO : discount;
        if (safeDiscount.compareTo(BigDecimal.ZERO) <= 0) {
            return safePrice;
        }
        BigDecimal discountAmount = safePrice.multiply(safeDiscount).divide(BigDecimal.valueOf(100));
        BigDecimal finalPrice = safePrice.subtract(discountAmount);
        return finalPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : finalPrice;
    }
}
