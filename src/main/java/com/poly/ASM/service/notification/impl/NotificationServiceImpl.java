package com.poly.ASM.service.notification.impl;

import com.poly.ASM.entity.notification.Notification;
import com.poly.ASM.entity.order.Order;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.entity.user.Authority;
import com.poly.ASM.repository.notification.NotificationRepository;
import com.poly.ASM.repository.user.AuthorityRepository;
import com.poly.ASM.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificationRepository notificationRepository;
    private final AuthorityRepository authorityRepository;

    /**
     * Hàm gốc tạo notification; các hàm notify* sẽ gom về đây để lưu DB nhất quán.
     */
    @Override
    public Notification createNotification(Account account, Order order, String title, String content) {
        Notification notification = new Notification();
        notification.setAccount(account);
        notification.setOrder(order);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        return notificationRepository.save(notification);
    }

    /**
     * Notify cho user sau khi đặt hàng thành công.
     */
    @Override
    public void notifyOrderPlacedForUser(Account account, Order order) {
        String time = FORMATTER.format(order.getCreateDate());
        String title = "Đặt hàng thành công";
        String content = "Bạn đã đặt hàng thành công đơn #" + order.getId() + " vào lúc " + time;
        createNotification(account, order, title, content);
    }

    /**
     * Notify cho toàn bộ admin khi có đơn mới.
     */
    @Override
    public void notifyOrderPlacedForAdmins(Order order) {
        String time = FORMATTER.format(order.getCreateDate());
        String title = "Đơn hàng mới";
        String content = "Bạn có đơn hàng mới #" + order.getId() + " vào lúc " + time;
        List<Authority> admins = authorityRepository.findByRoleId("ADMIN");
        for (Authority authority : admins) {
            Account account = authority.getAccount();
            if (account != null) {
                createNotification(account, order, title, content);
            }
        }
    }

    /**
     * Notify trạng thái giao hàng cho cả user và admin.
     * Chỉ xử lý các trạng thái terminal thành công/thất bại.
     */
    @Override
    public void notifyOrderStatusChange(Order order, String status) {
        String userTitle = "Cập nhật đơn hàng";
        String userContent;
        String adminTitle;
        String adminContent;
        if ("DELIVERED_SUCCESS".equals(status) || "DONE".equals(status)) {
            userContent = "Đơn hàng #" + order.getId() + " đã được giao thành công";
            adminTitle = "Giao hàng thành công";
            adminContent = "Đơn hàng #" + order.getId() + " đã giao thành công";
        } else if ("DELIVERY_FAILED".equals(status) || "CANCEL".equals(status)) {
            userContent = "Đơn hàng #" + order.getId() + " giao hàng thất bại";
            adminTitle = "Giao hàng thất bại";
            adminContent = "Đơn hàng #" + order.getId() + " giao hàng thất bại";
        } else {
            return;
        }
        if (order.getAccount() != null) {
            createNotification(order.getAccount(), order, userTitle, userContent);
        }
        List<Authority> admins = authorityRepository.findByRoleId("ADMIN");
        for (Authority authority : admins) {
            Account account = authority.getAccount();
            if (account != null) {
                createNotification(account, order, adminTitle, adminContent);
            }
        }
    }

    /**
     * Notify hỗ trợ chat: đẩy cho admin được chỉ định hoặc toàn bộ admin nếu chưa assign.
     */
    @Override
    public void notifyChatSupportForAdmins(String customerId, String customerFullname, Integer productId, String previewText, String assignedAdminId) {
        String user = customerFullname == null || customerFullname.isBlank() ? customerId : customerFullname;
        String title = "Hỗ trợ chat mới";
        String preview = previewText == null || previewText.isBlank() ? "Khách hàng vừa gửi tin nhắn mới." : previewText;
        String content = user + " cần hỗ trợ ở sản phẩm #" + productId + ": " + preview
                + "\n[CHAT_CTX]customerId=" + customerId + ";productId=" + productId;
        List<Authority> admins = authorityRepository.findByRoleId("ADMIN");
        for (Authority authority : admins) {
            Account account = authority.getAccount();
            if (account != null) {
                if (assignedAdminId != null && !assignedAdminId.isBlank() && !assignedAdminId.equals(account.getUsername())) {
                    continue;
                }
                createNotification(account, null, title, content);
            }
        }
    }

    /**
     * Notify cho admin khi user gửi yêu cầu hoàn tiền.
     */
    @Override
    public void notifyRefundRequestForAdmins(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }
        String title = "Yêu cầu hoàn tiền";
        String content = "Đơn hàng #" + order.getId() + " vừa gửi yêu cầu hoàn tiền.";
        List<Authority> admins = authorityRepository.findByRoleId("ADMIN");
        for (Authority authority : admins) {
            Account account = authority.getAccount();
            if (account != null) {
                createNotification(account, order, title, content);
            }
        }
    }

    /**
     * Notify kết quả duyệt/từ chối hoàn tiền cho đúng người đã gửi yêu cầu.
     */
    @Override
    public void notifyRefundResultForUser(Account account, Long orderId, boolean approved, String reason) {
        if (account == null) {
            return;
        }
        String title = approved ? "Yêu cầu hoàn tiền đã được duyệt" : "Yêu cầu hoàn tiền bị từ chối";
        String content = approved
                ? "Yêu cầu hoàn tiền cho đơn #" + orderId + " đã được chấp nhận."
                : "Yêu cầu hoàn tiền cho đơn #" + orderId + " bị từ chối. Lý do: " + (reason == null || reason.isBlank() ? "Không có" : reason);
        createNotification(account, null, title, content);
    }

    @Override
    public long countUnread(String username) {
        return notificationRepository.countByAccountUsernameAndReadFalse(username);
    }

    @Override
    public List<Notification> getLatest(String username, int limit) {
        return notificationRepository.findLatestByUsername(username, PageRequest.of(0, limit));
    }

    @Override
    public Optional<Notification> findByIdAndUsername(Long id, String username) {
        return notificationRepository.findByIdAndUsername(id, username);
    }

    @Override
    public Notification markRead(Notification notification) {
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    @Override
    public void deleteByOrderId(Long orderId) {
        if (orderId == null) {
            return;
        }
        notificationRepository.deleteByOrderId(orderId);
    }
}
