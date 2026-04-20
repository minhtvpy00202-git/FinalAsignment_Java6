package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.order.Order;
import com.poly.ASM.entity.order.OrderDetail;
import com.poly.ASM.entity.product.Product;
import com.poly.ASM.entity.product.ProductSize;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.repository.cart.CartItemRepository;
import com.poly.ASM.service.auth.AuthService;
import com.poly.ASM.service.cart.CartItem;
import com.poly.ASM.service.cart.CartService;
import com.poly.ASM.service.notification.NotificationService;
import com.poly.ASM.service.order.OrderDetailService;
import com.poly.ASM.service.order.OrderService;
import com.poly.ASM.service.payment.PayosPaymentService;
import com.poly.ASM.service.product.CategoryService;
import com.poly.ASM.service.product.ProductService;
import com.poly.ASM.service.product.ProductSizeService;
import com.poly.ASM.service.review.ProductReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.exception.PayOSException;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLink;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSetMetaData;
import java.sql.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/order-workflow")
@RequiredArgsConstructor
public class OrderController {
    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final CartService cartService;
    private final AuthService authService;
    private final ProductSizeService productSizeService;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final ProductReviewService productReviewService;
    private final NotificationService notificationService;
    private final PayosPaymentService payosPaymentService;
    private final JdbcTemplate jdbcTemplate;
    private final CartItemRepository cartItemRepository;
    @Value("${app.debug.payos-response:false}")
    private boolean debugPayosResponse;

    @GetMapping("/checkout")
    public ResponseEntity<ApiResponse<?>> checkoutForm() {
        Account user = authService.getUser();
        Map<String, Object> data = new HashMap<>();
        data.put("items", cartService.getCartItems());
        data.put("totalPrice", cartService.getTotalPrice());
        data.put("shippingPhone", user != null ? user.getPhone() : "");
        data.put("address", user != null ? user.getAddress() : "");
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu checkout thành công", data));
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<ApiResponse<?>> checkout(@RequestParam(value = "address", required = false) String address,
                                                   @RequestParam(value = "addressDetail", required = false) String addressDetail,
                                                   @RequestParam(value = "provinceCode", required = false) String provinceCode,
                                                   @RequestParam(value = "wardCode", required = false) String wardCode,
                                                   @RequestParam(value = "shippingPhone", required = false) String shippingPhone,
                                                   @RequestParam(value = "lat", required = false) Double lat,
                                                   @RequestParam(value = "lng", required = false) Double lng,
                                                   @RequestParam(value = "deliveryDistanceMeters", required = false) Long deliveryDistanceMeters,
                                                   @RequestParam(value = "expectedDeliveryDate", required = false) String expectedDeliveryDate,
                                                   @RequestParam(value = "expectedDeliveryLabel", required = false) String expectedDeliveryLabel,
                                                   @RequestParam("paymentMethod") String paymentMethod) {
        boolean bankPayment = "BANK".equalsIgnoreCase(paymentMethod);
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Order reusablePendingOrder = bankPayment
                ? orderService.findLatestPendingPaymentByUsername(user.getUsername()).orElse(null)
                : null;
        List<CartItem> items = cartService.getCartItems();
        if (items.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Giỏ hàng trống", null));
        }
        for (CartItem item : items) {
            if (item.getSizeId() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn size trước khi đặt hàng.", null));
            }
            Optional<ProductSize> productSize = productSizeService.findByProductIdAndSizeId(item.getProductId(), item.getSizeId());
            if (productSize.isEmpty() || productSize.get().getQuantity() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Sản phẩm trong giỏ hàng không còn tồn kho.", null));
            }
            Integer stock = productSize.get().getQuantity();
            Integer qty = item.getQuantity();
            if (qty == null || qty <= 0 || qty > stock) {
                String name = item.getName() != null ? item.getName() : "Sản phẩm";
                String size = item.getSizeName() != null ? item.getSizeName() : "size đã chọn";
                return ResponseEntity.badRequest().body(ApiResponse.error(name + " (" + size + ") vượt quá tồn kho. Vui lòng giảm số lượng.", null));
            }
        }
        ensureOrderAddressColumns();
        String normalizedProvinceCode = provinceCode == null ? null : provinceCode.trim();
        String normalizedWardCode = wardCode == null ? null : wardCode.trim();
        if ((normalizedProvinceCode != null && !normalizedProvinceCode.isBlank())
                || (normalizedWardCode != null && !normalizedWardCode.isBlank())) {
            if (normalizedProvinceCode == null || normalizedProvinceCode.isBlank()
                    || normalizedWardCode == null || normalizedWardCode.isBlank()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng chọn đầy đủ tỉnh/thành và phường/xã.", null));
            }
            if (findProvinceName(normalizedProvinceCode).isEmpty() || findWardName(normalizedWardCode, normalizedProvinceCode).isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Địa chỉ hành chính không hợp lệ.", null));
            }
        }
        String resolvedAddress = buildOrderAddress(address, addressDetail, normalizedProvinceCode, normalizedWardCode);
        if (resolvedAddress == null || resolvedAddress.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập địa chỉ nhận hàng hợp lệ.", null));
        }
        String resolvedShippingPhone = firstNonBlank(shippingPhone, user.getPhone());
        if (resolvedShippingPhone == null || resolvedShippingPhone.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Vui lòng nhập số điện thoại giao hàng.", null));
        }
        resolvedShippingPhone = resolvedShippingPhone.replaceAll("\\s+", "");
        if (!isValidShippingPhone(resolvedShippingPhone)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại phải gồm 10 số, bắt đầu bằng 0 và không được là 10 số 0.", null));
        }
        Order savedOrder;
        if (bankPayment) {
            if (reusablePendingOrder != null) {
                Long oldOrderId = reusablePendingOrder.getId();
                try {
                    payosPaymentService.cancelPaymentLink(oldOrderId, "Recreate payment order");
                } catch (Exception ignored) {
                }
                notificationService.deleteByOrderId(oldOrderId);
                orderDetailService.deleteByOrderId(oldOrderId);
                orderService.deleteById(oldOrderId);
            }
            Order order = new Order();
            order.setAccount(user);
            order.setAddress(resolvedAddress);
            order.setStatus("PENDING_PAYMENT");
            savedOrder = orderService.create(order);
            clearOrderPaymentData(savedOrder.getId());
        } else {
            Order order = new Order();
            order.setAccount(user);
            order.setAddress(resolvedAddress);
            order.setStatus("PLACED_UNPAID");
            savedOrder = orderService.create(order);
        }
        updateOrderAdministrative(savedOrder.getId(), normalizedProvinceCode, normalizedWardCode);
        updateOrderCoordinates(savedOrder.getId(), lat, lng);
        updateOrderShippingPhone(savedOrder.getId(), resolvedShippingPhone);
        updateOrderDeliveryEstimate(savedOrder.getId(), deliveryDistanceMeters, expectedDeliveryDate, expectedDeliveryLabel);
        notificationService.notifyOrderPlacedForUser(user, savedOrder);
        notificationService.notifyOrderPlacedForAdmins(savedOrder);
        for (CartItem item : items) {
            if (item.getSizeId() == null) {
                continue;
            }
            Optional<ProductSize> productSize = productSizeService.findByProductIdAndSizeId(item.getProductId(), item.getSizeId());
            if (productSize.isEmpty() || productSize.get().getQuantity() < item.getQuantity()) {
                continue;
            }
            OrderDetail detail = new OrderDetail();
            Product product = new Product();
            product.setId(item.getProductId());
            detail.setProduct(product);
            detail.setOrder(savedOrder);
            detail.setPrice(item.getPrice());
            detail.setQuantity(item.getQuantity());
            detail.setSizeId(item.getSizeId());
            detail.setSizeName(item.getSizeName());
            orderDetailService.create(detail);
            
        }
        if (!bankPayment) {
            orderService.reserveInventoryForOrder(savedOrder.getId());
        }
        if (!bankPayment) {
            cartService.clearCart();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", savedOrder.getId());
        data.put("nextAction", bankPayment ? "BANK_TRANSFER" : "VIEW_ORDER_DETAIL");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đặt hàng thành công", data));
    }

    @GetMapping("/bank-transfer/{id}")
    public ResponseEntity<ApiResponse<?>> bankTransfer(@PathVariable("id") Long id, HttpServletRequest request) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(id);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        List<OrderDetail> details = orderDetailService.findByOrderId(id);
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            Map<String, Object> data = new HashMap<>();
            data.put("order", toOrderData(order));
            data.put("totalPrice", calculateOrderTotal(details));
            data.put("status", order.getStatus());
            return ResponseEntity.ok(ApiResponse.success("Đơn hàng đã được xử lý", data));
        }
        BigDecimal total = calculateOrderTotal(details);
        long amount = toPayosAmount(total);
        if (amount <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Số tiền thanh toán không hợp lệ", null));
        }
        String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
        String returnUrl = baseUrl + "/api/order-workflow/payos/return?orderId=" + order.getId();
        String cancelUrl = baseUrl + "/api/order-workflow/payos/cancel?orderId=" + order.getId();
        try {
            Optional<Map<String, String>> cachedPayment = findOrderPaymentData(order.getId());
            if (cachedPayment.isPresent()) {
                Map<String, String> cache = cachedPayment.get();
                Map<String, Object> existingData = toBankTransferResponseData(
                        order,
                        total,
                        cache.get("checkoutUrl"),
                        cache.get("qrCode"),
                        cache.get("accountName"),
                        cache.get("accountNumber"),
                        cache.get("bankBin"),
                        cache.get("paymentLinkId"),
                        amount
                );
                return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chuyển khoản thành công", existingData));
            }
            CreatePaymentLinkResponse response = payosPaymentService.createPaymentLink(
                    order.getId(),
                    amount,
                    "Thanh toan don hang #" + order.getId(),
                    returnUrl,
                    cancelUrl
            );
            saveOrderPaymentData(
                    order.getId(),
                    response.getCheckoutUrl(),
                    response.getQrCode(),
                    response.getAccountName(),
                    response.getAccountNumber(),
                    response.getBin(),
                    response.getPaymentLinkId()
            );
            Map<String, Object> data = toBankTransferResponseData(
                    order,
                    total,
                    response.getCheckoutUrl(),
                    response.getQrCode(),
                    response.getAccountName(),
                    response.getAccountNumber(),
                    response.getBin(),
                    response.getPaymentLinkId(),
                    amount
            );
            if (debugPayosResponse) {
                Map<String, Object> debugData = new HashMap<>();
                debugData.put("orderCode", order.getId());
                debugData.put("amount", amount);
                debugData.put("bin", response.getBin());
                debugData.put("accountName", response.getAccountName());
                debugData.put("accountNumber", response.getAccountNumber());
                debugData.put("checkoutUrl", response.getCheckoutUrl());
                debugData.put("paymentLinkId", response.getPaymentLinkId());
                debugData.put("qrCode", response.getQrCode());
                data.put("payosDebug", debugData);
                log.info("PAYOS_CREATE_LINK_DEBUG {}", debugData);
            }
            return ResponseEntity.ok(ApiResponse.success("Tạo thông tin chuyển khoản thành công", data));
        } catch (PayOSException ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(ApiResponse.error("Không thể tạo link thanh toán. Vui lòng thử lại.", null));
        }
    }

    @PostMapping("/bank-transfer/confirm")
    public ResponseEntity<ApiResponse<?>> confirmBankTransfer(@RequestParam("orderId") Long orderId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        try {
            PaymentLink paymentLink = payosPaymentService.getPaymentLink(orderId);
            if (paymentLink != null && paymentLink.getStatus() == PaymentLinkStatus.PAID) {
                updateOrderStatusIfChanged(order, "PLACED_PAID");
                return ResponseEntity.ok(ApiResponse.success("Thanh toán thành công", Map.of("paid", true, "orderId", orderId)));
            }
        } catch (Exception ignored) {
            // Log lỗi nếu cần thiết
        }
        return ResponseEntity.ok(ApiResponse.success("Thanh toán chưa hoàn tất", Map.of("paid", false, "orderId", orderId)));
    }

    @PostMapping("/bank-transfer/cancel/switch-cod")
    public ResponseEntity<ApiResponse<?>> switchToCodAfterCancel(@RequestParam("orderId") Long orderId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        try {
            payosPaymentService.cancelIfPending(orderId, "Switch to COD");
        } catch (Exception ignored) {
        }
        updateOrderStatusIfChanged(orderOpt.get(), "PLACED_UNPAID");
        return ResponseEntity.ok(ApiResponse.success("Đã chuyển sang COD", Map.of("orderId", orderId)));
    }

    @PostMapping("/bank-transfer/cancel/payos")
    public ResponseEntity<ApiResponse<?>> cancelPayosOnly(@RequestParam("orderId") Long orderId,
                                                          @RequestParam(value = "reason", required = false) String reason) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return ResponseEntity.ok(ApiResponse.success("Đơn hàng không còn ở trạng thái chờ thanh toán", Map.of("orderId", orderId)));
        }
        try {
            String cancelReason = firstNonBlank(reason, "Leave bank transfer page");
            payosPaymentService.cancelIfPending(orderId, cancelReason);
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(ApiResponse.success("Đã gửi yêu cầu hủy thanh toán lên PayOS", Map.of("orderId", orderId)));
    }

    @PostMapping("/bank-transfer/cancel/delete")
    @Transactional
    public ResponseEntity<ApiResponse<?>> cancelAndDeleteOrder(@RequestParam("orderId") Long orderId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        try {
            payosPaymentService.cancelPaymentLink(orderId, "Cancel order");
        } catch (PayOSException ignored) {
        }
        notificationService.deleteByOrderId(orderId);
        orderDetailService.deleteByOrderId(orderId);
        orderService.deleteById(orderId);
        return ResponseEntity.ok(ApiResponse.success("Hủy và xóa đơn hàng thành công", Map.of("orderId", orderId)));
    }

    @GetMapping("/payos/return")
    public ResponseEntity<ApiResponse<?>> payosReturn(@RequestParam("orderId") Long orderId) {
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Không tìm thấy đơn hàng", null));
        }
        try {
            PaymentLink paymentLink = payosPaymentService.getPaymentLink(orderId);
            if (paymentLink != null && paymentLink.getStatus() == PaymentLinkStatus.PAID) {
                updateOrderStatusIfChanged(orderOpt.get(), "PLACED_PAID");
                return ResponseEntity.ok(ApiResponse.success("Thanh toán thành công", Map.of("paid", true, "orderId", orderId)));
            }
        } catch (PayOSException ignored) {
        }
        return ResponseEntity.ok(ApiResponse.success("Thanh toán chưa hoàn tất", Map.of("paid", false, "orderId", orderId)));
    }

    @GetMapping("/payos/cancel")
    public ResponseEntity<ApiResponse<?>> payosCancel(@RequestParam("orderId") Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Đã hủy thanh toán", Map.of("orderId", orderId)));
    }

    @GetMapping("/payos/status")
    public ResponseEntity<ApiResponse<?>> payosStatus(@RequestParam("orderId") Long orderId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Unauthorized", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Forbidden", null));
        }
        Map<String, Object> response = new HashMap<>();
        try {
            PaymentLink paymentLink = payosPaymentService.getPaymentLink(orderId);
            if (paymentLink != null && paymentLink.getStatus() != null) {
                PaymentLinkStatus status = paymentLink.getStatus();
                response.put("status", status.getValue());
                if (status == PaymentLinkStatus.PAID) {
                    updateOrderStatusIfChanged(orderOpt.get(), "PLACED_PAID");
                    response.put("paid", true);
                }
            }
        } catch (PayOSException ex) {
            response.put("message", "PayOS error");
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy trạng thái thanh toán thành công", response));
    }

    @PostMapping("/payos/webhook")
    public ResponseEntity<ApiResponse<?>> payosWebhook(@RequestBody Webhook webhook) {
        try {
            WebhookData data = payosPaymentService.verifyWebhook(webhook);
            if (data == null || data.getOrderCode() == null) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid webhook", null));
            }
            PaymentLink paymentLink = payosPaymentService.getPaymentLink(data.getOrderCode());
            if (paymentLink != null && paymentLink.getStatus() != null) {
                applyPaymentStatus(data.getOrderCode(), paymentLink.getStatus());
            }
            return ResponseEntity.ok(ApiResponse.success("Webhook hợp lệ", null));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid webhook", null));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<ApiResponse<?>> list() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        List<Order> orders = orderService.findByAccountUsername(user.getUsername());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", order.getId());
            item.put("address", order.getAddress());
            item.put("shippingPhone", findOrderShippingPhone(order.getId()).orElse(""));
            item.put("expectedDeliveryLabel", findOrderExpectedDeliveryLabel(order.getId()).orElse(""));
            item.put("expectedDeliveryDate", findOrderExpectedDeliveryDate(order.getId()).orElse(""));
            item.put("deliveryDistanceM", findOrderDeliveryDistanceMeters(order.getId()).orElse(0L));
            item.put("deliveredAt", findOrderDeliveredAt(order.getId()).orElse(null));
            item.put("status", order.getStatus());
            item.put("createDate", order.getCreateDate());
            result.add(item);
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", result));
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<ApiResponse<?>> detail(@PathVariable("id") Long id) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(id);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        List<OrderDetail> details = orderDetailService.findByOrderId(id);
        BigDecimal totalAmount = calculateOrderTotal(details);
        Map<String, Object> data = new HashMap<>();
        data.put("order", toOrderData(order));
        data.put("details", toOrderDetailData(details));
        data.put("totalAmount", totalAmount);
        data.put("totalPrice", totalAmount);
        data.put("reviewable", isDeliveredStatus(order.getStatus()));
        data.put("reviewedProductIds", productReviewService.findReviewedProductIds(user.getUsername(), id));
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", data));
    }

    @PutMapping("/{orderId}/shipping")
    @Transactional
    public ResponseEntity<ApiResponse<?>> updateShipping(@PathVariable("orderId") Long orderId,
                                                         @RequestBody Map<String, Object> payload) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        String status = String.valueOf(order.getStatus() == null ? "" : order.getStatus()).trim();
        if (!"PLACED_UNPAID".equals(status) && !"PLACED_PAID".equals(status)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Chỉ cho phép sửa thông tin giao hàng với đơn đã đặt.", null));
        }
        String address = String.valueOf(payload.getOrDefault("address", "")).trim();
        String shippingPhone = String.valueOf(payload.getOrDefault("shippingPhone", "")).trim();
        if (address.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Địa chỉ giao hàng không hợp lệ.", null));
        }
        if (!isValidShippingPhone(shippingPhone)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại phải gồm 10 số, bắt đầu bằng 0 và không được là 10 số 0.", null));
        }
        order.setAddress(address);
        orderService.update(order);
        updateOrderShippingPhone(orderId, shippingPhone);
        Map<String, Object> data = new HashMap<>();
        data.put("order", toOrderData(order));
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin giao hàng thành công.", data));
    }

    @PostMapping("/retry-payment/{id}")
    public ResponseEntity<ApiResponse<?>> retryPayment(@PathVariable("id") Long id) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(id);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        if (!"PENDING_PAYMENT".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Đơn hàng này không ở trạng thái chờ thanh toán.", null));
        }
        List<OrderDetail> details = orderDetailService.findByOrderId(id);
        if (details == null || details.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Đơn hàng không còn sản phẩm để thanh toán lại.", null));
        }
        cartService.clearCart();
        int addedCount = 0;
        for (OrderDetail detail : details) {
            Integer productId = detail.getProduct() != null ? detail.getProduct().getId() : null;
            Integer sizeId = detail.getSizeId();
            Integer quantity = detail.getQuantity();
            if (productId == null || sizeId == null || quantity == null || quantity <= 0) {
                continue;
            }
            boolean added = cartService.add(productId, sizeId, quantity);
            if (added) {
                addedCount++;
            }
        }
        if (addedCount == 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Không thể đưa lại sản phẩm vào giỏ hàng. Vui lòng kiểm tra tồn kho.", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Đã chuẩn bị giỏ hàng để thanh toán lại.", Map.of("orderId", id, "added", addedCount)));
    }

    @GetMapping("/my-product-list")
    public ResponseEntity<ApiResponse<?>> myProductList() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        List<OrderDetail> details = orderDetailService.findByOrderAccountUsername(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm đã mua thành công", toOrderDetailData(details)));
    }

    @GetMapping("/my-delivered-product-list")
    public ResponseEntity<ApiResponse<?>> myDeliveredProductList() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        List<Order> orders = orderService.findByAccountUsername(user.getUsername());
        List<OrderDetail> delivered = new ArrayList<>();
        for (Order order : orders) {
            if (order == null || !isDeliveredStatus(order.getStatus()) || order.getId() == null) {
                continue;
            }
            delivered.addAll(orderDetailService.findByOrderId(order.getId()));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm đã mua (đã giao) thành công", toOrderDetailData(delivered)));
    }

    @DeleteMapping("/{orderId}/details/{detailId}")
    @Transactional
    public ResponseEntity<ApiResponse<?>> deleteOrderDetail(@PathVariable("orderId") Long orderId,
                                                            @PathVariable("detailId") Long detailId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        if (!"PLACED_UNPAID".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Chỉ hỗ trợ xoá sản phẩm với đơn chưa thanh toán.", null));
        }
        Optional<OrderDetail> detailOpt = orderDetailService.findById(detailId);
        if (detailOpt.isEmpty() || detailOpt.get().getOrder() == null || !orderId.equals(detailOpt.get().getOrder().getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Không tìm thấy dòng sản phẩm trong đơn hàng.", null));
        }
        OrderDetail deletingDetail = detailOpt.get();
        Integer productId = deletingDetail.getProduct() == null ? null : deletingDetail.getProduct().getId();
        Integer sizeId = deletingDetail.getSizeId();
        Integer quantity = deletingDetail.getQuantity();
        if (productId != null && sizeId != null && quantity != null && quantity > 0) {
            productSizeService.findByProductIdAndSizeId(productId, sizeId).ifPresent(ps -> {
                int current = ps.getQuantity() == null ? 0 : ps.getQuantity();
                ps.setQuantity(current + quantity);
                productSizeService.save(ps);
            });
        }
        orderDetailService.deleteById(detailId);
        List<OrderDetail> remaining = orderDetailService.findByOrderId(orderId);
        if (remaining.isEmpty()) {
            notificationService.deleteByOrderId(orderId);
            orderService.deleteById(orderId);
            return ResponseEntity.ok(ApiResponse.success("Đã xoá sản phẩm cuối cùng, đơn hàng đã được xoá.", Map.of("orderDeleted", true)));
        }
        BigDecimal totalAmount = calculateOrderTotal(remaining);
        Map<String, Object> data = new HashMap<>();
        data.put("orderDeleted", false);
        data.put("order", toOrderData(order));
        data.put("details", toOrderDetailData(remaining));
        data.put("totalAmount", totalAmount);
        return ResponseEntity.ok(ApiResponse.success("Đã xoá sản phẩm khỏi đơn hàng.", data));
    }

    @PostMapping("/{orderId}/details/{detailId}/exchange")
    @Transactional
    public ResponseEntity<ApiResponse<?>> exchangeOrderDetail(@PathVariable("orderId") Long orderId,
                                                              @PathVariable("detailId") Long detailId,
                                                              @RequestBody Map<String, Object> payload) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        if (!"PLACED_UNPAID".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Chỉ hỗ trợ đổi sản phẩm với đơn chưa thanh toán.", null));
        }
        Optional<OrderDetail> detailOpt = orderDetailService.findById(detailId);
        if (detailOpt.isEmpty() || detailOpt.get().getOrder() == null || !orderId.equals(detailOpt.get().getOrder().getId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Không tìm thấy dòng sản phẩm trong đơn hàng.", null));
        }
        Integer productId = toInt(payload.get("productId"));
        Integer sizeId = toInt(payload.get("sizeId"));
        Integer quantity = toInt(payload.get("quantity"));
        if (productId == null || sizeId == null || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Dữ liệu đổi sản phẩm không hợp lệ.", null));
        }
        Optional<ProductSize> productSizeOpt = productSizeService.findByProductIdAndSizeId(productId, sizeId);
        if (productSizeOpt.isEmpty() || productSizeOpt.get().getQuantity() == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Không tìm thấy tồn kho của size đã chọn.", null));
        }
        Optional<Product> productOpt = productService.findById(productId);
        if (productOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Không tìm thấy sản phẩm cần đổi.", null));
        }
        Product product = productOpt.get();
        OrderDetail detail = detailOpt.get();
        Integer oldProductId = detail.getProduct() == null ? null : detail.getProduct().getId();
        Integer oldSizeId = detail.getSizeId();
        Integer oldQuantity = detail.getQuantity() == null ? 0 : detail.getQuantity();
        if (oldProductId == null || oldSizeId == null || oldQuantity <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Dữ liệu sản phẩm hiện tại trong đơn không hợp lệ.", null));
        }
        ProductSize newProductSize = productSizeOpt.get();
        boolean sameProductAndSize = oldProductId.equals(productId) && oldSizeId.equals(sizeId);
        if (sameProductAndSize) {
            int available = (newProductSize.getQuantity() == null ? 0 : newProductSize.getQuantity()) + oldQuantity;
            if (quantity > available) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số lượng vượt quá tồn kho của size đã chọn.", null));
            }
            newProductSize.setQuantity(available - quantity);
            productSizeService.save(newProductSize);
        } else {
            int newStock = newProductSize.getQuantity() == null ? 0 : newProductSize.getQuantity();
            if (newStock < quantity) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số lượng vượt quá tồn kho của size đã chọn.", null));
            }
            Optional<ProductSize> oldProductSizeOpt = productSizeService.findByProductIdAndSizeId(oldProductId, oldSizeId);
            if (oldProductSizeOpt.isPresent() && oldProductSizeOpt.get().getQuantity() != null) {
                ProductSize oldProductSize = oldProductSizeOpt.get();
                oldProductSize.setQuantity(oldProductSize.getQuantity() + oldQuantity);
                productSizeService.save(oldProductSize);
            }
            newProductSize.setQuantity(newStock - quantity);
            productSizeService.save(newProductSize);
        }
        detail.setProduct(product);
        detail.setPrice(product.getPrice());
        detail.setQuantity(quantity);
        detail.setSizeId(sizeId);
        detail.setSizeName(newProductSize.getSize() == null ? "" : newProductSize.getSize().getName());
        orderDetailService.update(detail);

        List<OrderDetail> updated = orderDetailService.findByOrderId(orderId);
        BigDecimal totalAmount = calculateOrderTotal(updated);
        Map<String, Object> data = new HashMap<>();
        data.put("order", toOrderData(order));
        data.put("details", toOrderDetailData(updated));
        data.put("totalAmount", totalAmount);
        return ResponseEntity.ok(ApiResponse.success("Đổi sản phẩm thành công.", data));
    }

    @GetMapping("/exchange-catalog")
    public ResponseEntity<ApiResponse<?>> exchangeCatalog(@RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "categoryId", required = false) String categoryId,
                                                          @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                                                          @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                                                          @RequestParam(value = "sort", required = false) String sort,
                                                          @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
                                                          @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, 30);
        Page<Product> result = productService.searchWithFiltersPage(keyword, categoryId, minPrice, maxPrice, sort, safePage, safeSize);
        List<Product> products = result.getContent();
        List<Integer> productIds = products.stream().map(Product::getId).toList();
        Map<Integer, List<Map<String, Object>>> sizesByProduct = new HashMap<>();
        for (ProductSize ps : productSizeService.findByProductIds(productIds)) {
            if (ps.getProduct() == null || ps.getProduct().getId() == null || ps.getSize() == null) {
                continue;
            }
            Integer pid = ps.getProduct().getId();
            sizesByProduct.computeIfAbsent(pid, k -> new ArrayList<>()).add(Map.of(
                    "sizeId", ps.getSize().getId(),
                    "sizeName", ps.getSize().getName(),
                    "stock", ps.getQuantity() == null ? 0 : ps.getQuantity()
            ));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Product product : products) {
            BigDecimal finalPrice = calcFinalPrice(product.getPrice(), product.getDiscount());
            Map<String, Object> row = new HashMap<>();
            row.put("id", product.getId());
            row.put("name", product.getName());
            row.put("image", product.getImage());
            row.put("price", product.getPrice());
            row.put("discount", product.getDiscount());
            row.put("finalPrice", finalPrice);
            row.put("categoryName", product.getCategory() == null ? "" : product.getCategory().getName());
            row.put("sizes", sizesByProduct.getOrDefault(product.getId(), List.of()));
            rows.add(row);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("categories", categoryService.findAll());
        data.put("page", result.getNumber());
        data.put("size", safeSize);
        data.put("totalPages", result.getTotalPages());
        data.put("totalElements", result.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm đổi hàng thành công.", data));
    }

    @PostMapping("/{orderId}/refund-request")
    @Transactional
    public ResponseEntity<ApiResponse<?>> requestRefund(@PathVariable("orderId") Long orderId) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Không có quyền truy cập đơn hàng", null));
        }
        Order order = orderOpt.get();
        if (!"PLACED_PAID".equals(order.getStatus())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Chỉ cho phép yêu cầu hoàn tiền với đơn đã thanh toán ở trạng thái đã đặt.", null));
        }
        ensureRefundRequestTable();
        Integer exists = jdbcTemplate.queryForObject(
                "select count(1) from dbo.order_refund_requests where order_id = ?",
                Integer.class,
                orderId
        );
        if (exists != null && exists > 0) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Đơn hàng này đã gửi yêu cầu hoàn tiền trước đó.", null));
        }
        jdbcTemplate.update("""
                INSERT INTO dbo.order_refund_requests(order_id, username, status, decline_reason, created_at, decided_at)
                VALUES(?, ?, 'PENDING', NULL, GETDATE(), NULL)
                """, orderId, user.getUsername());
        order.setStatus("REFUND_REQUEST");
        orderService.update(order);
        notificationService.notifyRefundRequestForAdmins(order);
        return ResponseEntity.ok(ApiResponse.success("Đã gửi yêu cầu hoàn tiền.", Map.of("orderId", orderId, "status", "PENDING")));
    }

    @GetMapping("/refund-requests")
    public ResponseEntity<ApiResponse<?>> myRefundRequests() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        ensureRefundRequestTable();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT order_id, status, decline_reason, created_at, decided_at
                FROM dbo.order_refund_requests
                WHERE username = ?
                ORDER BY created_at DESC
                """, user.getUsername());
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", row.get("order_id"));
            item.put("status", row.get("status"));
            item.put("declineReason", row.get("decline_reason"));
            item.put("createdAt", row.get("created_at"));
            item.put("decidedAt", row.get("decided_at"));
            data.add(item);
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách yêu cầu hoàn tiền thành công.", data));
    }

    private boolean isDeliveredStatus(String status) {
        if (status == null) {
            return false;
        }
        return "DELIVERED_SUCCESS".equals(status) || "DONE".equals(status);
    }

    private Integer toInt(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal calcFinalPrice(BigDecimal price, BigDecimal discount) {
        BigDecimal p = price == null ? BigDecimal.ZERO : price;
        BigDecimal d = discount == null ? BigDecimal.ZERO : discount;
        if (d.compareTo(BigDecimal.ZERO) <= 0) {
            return p;
        }
        BigDecimal discountAmount = p.multiply(d).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal finalPrice = p.subtract(discountAmount);
        return finalPrice.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : finalPrice;
    }

    private void ensureRefundRequestTable() {
        try {
            jdbcTemplate.execute("""
                    IF OBJECT_ID('dbo.order_refund_requests', 'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.order_refund_requests (
                            id BIGINT IDENTITY(1,1) PRIMARY KEY,
                            order_id BIGINT NOT NULL,
                            username VARCHAR(50) NOT NULL,
                            status VARCHAR(30) NOT NULL,
                            decline_reason NVARCHAR(MAX) NULL,
                            created_at DATETIME NOT NULL DEFAULT GETDATE(),
                            decided_at DATETIME NULL
                        );
                        CREATE UNIQUE INDEX idx_refund_order_unique ON dbo.order_refund_requests(order_id);
                        CREATE INDEX idx_refund_username ON dbo.order_refund_requests(username);
                        CREATE INDEX idx_refund_status ON dbo.order_refund_requests(status);
                    END
                    """);
        } catch (Exception ignored) {
        }
    }

    private BigDecimal calculateOrderTotal(List<OrderDetail> details) {
        BigDecimal total = BigDecimal.ZERO;
        if (details == null) {
            return total;
        }
        for (OrderDetail detail : details) {
            if (detail.getPrice() == null || detail.getQuantity() == null) {
                continue;
            }
            BigDecimal unitPrice = detail.getPrice();
            BigDecimal qty = BigDecimal.valueOf(detail.getQuantity());
            BigDecimal discountPercent = detail.getProduct() != null && detail.getProduct().getDiscount() != null
                    ? detail.getProduct().getDiscount()
                    : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(qty);
            if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal discountAmount = lineTotal.multiply(discountPercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                lineTotal = lineTotal.subtract(discountAmount);
            }
            total = total.add(lineTotal);
        }
        return total;
    }

    private long toPayosAmount(BigDecimal total) {
        if (total == null) {
            return 0;
        }
        return total.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private String buildQrImageSrc(String qrCode) {
        if (qrCode == null || qrCode.isBlank()) {
            return null;
        }
        if (qrCode.startsWith("data:")) {
            return qrCode;
        }
        if (qrCode.startsWith("iVBOR") || qrCode.startsWith("/9j/")) {
            return "data:image/png;base64," + qrCode;
        }
        String encoded = URLEncoder.encode(qrCode, StandardCharsets.UTF_8);
        return "https://api.qrserver.com/v1/create-qr-code/?size=260x260&data=" + encoded;
    }

    private Map<String, Object> toOrderData(Order order) {
        Map<String, Object> orderData = new HashMap<>();
        if (order == null) {
            return orderData;
        }
        orderData.put("id", order.getId());
        orderData.put("address", order.getAddress());
        orderData.put("shippingPhone", findOrderShippingPhone(order.getId()).orElse(""));
        orderData.put("expectedDeliveryLabel", findOrderExpectedDeliveryLabel(order.getId()).orElse(""));
        orderData.put("expectedDeliveryDate", findOrderExpectedDeliveryDate(order.getId()).orElse(""));
        orderData.put("deliveryDistanceM", findOrderDeliveryDistanceMeters(order.getId()).orElse(0L));
        orderData.put("deliveredAt", findOrderDeliveredAt(order.getId()).orElse(null));
        orderData.put("status", order.getStatus());
        orderData.put("createDate", order.getCreateDate());
        return orderData;
    }

    private List<Map<String, Object>> toOrderDetailData(List<OrderDetail> details) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (details == null) {
            return result;
        }
        for (OrderDetail detail : details) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", detail.getId());
            row.put("price", detail.getPrice());
            row.put("quantity", detail.getQuantity());
            row.put("sizeId", detail.getSizeId());
            row.put("sizeName", detail.getSizeName());
            Product product = detail.getProduct();
            if (product != null) {
                Map<String, Object> productData = new HashMap<>();
                productData.put("id", product.getId());
                productData.put("name", product.getName());
                productData.put("image", product.getImage());
                productData.put("price", product.getPrice());
                productData.put("discount", product.getDiscount());
                row.put("product", productData);
                row.put("productId", product.getId());
                row.put("productName", product.getName());
            } else {
                row.put("product", null);
                row.put("productId", null);
                row.put("productName", "");
            }
            result.add(row);
        }
        return result;
    }

    private void applyPaymentStatus(Long orderId, PaymentLinkStatus status) {
        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty()) {
            return;
        }
        Order order = orderOpt.get();
        if (status == PaymentLinkStatus.PAID) {
            updateOrderStatusIfChanged(order, "PLACED_PAID");
        }
    }

    private void updateOrderStatusIfChanged(Order order, String status) {
        if (order == null || status == null) {
            return;
        }
        String previous = order.getStatus();
        if (previous != null && previous.equals(status)) {
            return;
        }
        order.setStatus(status);
        orderService.update(order);
        notificationService.notifyOrderStatusChange(order, status);
        
        if ("PENDING_PAYMENT".equals(previous) && ("PLACED_PAID".equals(status) || "PLACED_UNPAID".equals(status))) {
            if (order.getAccount() != null) {
                String username = order.getAccount().getUsername();
                List<OrderDetail> details = orderDetailService.findByOrderId(order.getId());
                for (OrderDetail detail : details) {
                    if (detail.getProduct() != null && detail.getSizeId() != null) {
                        cartItemRepository.findByAccountUsernameAndProductIdAndSizeId(username, detail.getProduct().getId(), detail.getSizeId())
                                .ifPresent(cartItemRepository::delete);
                    }
                }
            }
        }
    }

    private String buildOrderAddress(String address, String addressDetail, String provinceCode, String wardCode) {
        String baseAddress = firstNonBlank(addressDetail, address);
        if (provinceCode == null || provinceCode.isBlank() || wardCode == null || wardCode.isBlank()) {
            return baseAddress;
        }
        Optional<String> province = findProvinceName(provinceCode);
        Optional<String> ward = findWardName(wardCode, provinceCode);
        if (province.isEmpty() || ward.isEmpty()) {
            return baseAddress;
        }
        if (baseAddress == null || baseAddress.isBlank()) {
            return ward.get() + ", " + province.get();
        }
        return baseAddress + ", " + ward.get() + ", " + province.get();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private boolean isValidShippingPhone(String phone) {
        if (phone == null) {
            return false;
        }
        String normalized = phone.replaceAll("\\s+", "").trim();
        if (!normalized.matches("^0\\d{9}$")) {
            return false;
        }
        return !"0000000000".equals(normalized);
    }

    private Map<String, Object> toBankTransferResponseData(Order order,
                                                           BigDecimal total,
                                                           String checkoutUrl,
                                                           String qrCode,
                                                           String accountName,
                                                           String accountNumber,
                                                           String bankBin,
                                                           String paymentLinkId,
                                                           long amount) {
        Map<String, Object> data = new HashMap<>();
        data.put("order", toOrderData(order));
        data.put("totalPrice", total);
        data.put("checkoutUrl", checkoutUrl);
        data.put("qrImageSrc", buildQrImageSrc(qrCode));
        data.put("accountName", firstNonBlank(accountName, ""));
        data.put("accountNumber", firstNonBlank(accountNumber, ""));
        data.put("bankName", "BIDV");
        data.put("bankBin", bankBin);
        data.put("paymentLinkId", paymentLinkId);
        data.put("amount", amount);
        return data;
    }


    private Optional<String> findProvinceName(String provinceCode) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "select full_name from dbo.provinces where code = ?",
                    String.class,
                    provinceCode
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findWardName(String wardCode, String provinceCode) {
        try {
            String value = jdbcTemplate.queryForObject(
                    "select full_name from dbo.wards where code = ? and province_code = ?",
                    String.class,
                    wardCode,
                    provinceCode
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void updateOrderAdministrative(Long orderId, String provinceCode, String wardCode) {
        if (orderId == null) {
            return;
        }
        ensureOrderAddressColumns();
        try {
            jdbcTemplate.update("update dbo.orders set province_code = ?, ward_code = ? where id = ?",
                    provinceCode, wardCode, orderId);
        } catch (Exception ignored) {
        }
    }

    private void ensureOrderAddressColumns() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'province_code') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD province_code NVARCHAR(20) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'ward_code') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD ward_code NVARCHAR(20) NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private void updateOrderShippingPhone(Long orderId, String shippingPhone) {
        if (orderId == null) {
            return;
        }
        ensureOrderShippingPhoneColumn();
        try {
            jdbcTemplate.update("update dbo.orders set shipping_phone = ? where id = ?", shippingPhone, orderId);
        } catch (Exception ignored) {
        }
    }

    private void updateOrderDeliveryEstimate(Long orderId, Long distanceMeters, String expectedDeliveryDate, String expectedDeliveryLabel) {
        if (orderId == null) {
            return;
        }
        ensureOrderDeliveryColumns();
        Date expectedDate = null;
        try {
            if (expectedDeliveryDate != null && !expectedDeliveryDate.isBlank()) {
                expectedDate = Date.valueOf(LocalDate.parse(expectedDeliveryDate.trim()));
            }
        } catch (Exception ignored) {
            expectedDate = null;
        }
        try {
            jdbcTemplate.update(
                    "update dbo.orders set delivery_distance_m = ?, expected_delivery_date = ?, expected_delivery_label = ? where id = ?",
                    distanceMeters,
                    expectedDate,
                    firstNonBlank(expectedDeliveryLabel, ""),
                    orderId
            );
        } catch (Exception ignored) {
        }
    }

    private Optional<String> findOrderShippingPhone(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderShippingPhoneColumn();
        try {
            String value = jdbcTemplate.queryForObject(
                    "select shipping_phone from dbo.orders where id = ?",
                    String.class,
                    orderId
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findOrderExpectedDeliveryLabel(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderDeliveryColumns();
        try {
            String value = jdbcTemplate.queryForObject(
                    "select expected_delivery_label from dbo.orders where id = ?",
                    String.class,
                    orderId
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findOrderExpectedDeliveryDate(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderDeliveryColumns();
        try {
            String value = jdbcTemplate.queryForObject(
                    "select convert(varchar(10), expected_delivery_date, 23) from dbo.orders where id = ?",
                    String.class,
                    orderId
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<Long> findOrderDeliveryDistanceMeters(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderDeliveryColumns();
        try {
            Long value = jdbcTemplate.queryForObject(
                    "select delivery_distance_m from dbo.orders where id = ?",
                    Long.class,
                    orderId
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<String> findOrderDeliveredAt(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderDeliveredTimeColumn();
        try {
            String value = jdbcTemplate.queryForObject(
                    "select convert(varchar(19), delivered_at, 120) from dbo.orders where id = ?",
                    String.class,
                    orderId
            );
            return Optional.ofNullable(value);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void ensureOrderShippingPhoneColumn() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'shipping_phone') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD shipping_phone NVARCHAR(20) NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private void ensureOrderDeliveryColumns() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'delivery_distance_m') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD delivery_distance_m BIGINT NULL;
                END
                IF COL_LENGTH('dbo.orders', 'expected_delivery_date') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD expected_delivery_date DATE NULL;
                END
                IF COL_LENGTH('dbo.orders', 'expected_delivery_label') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD expected_delivery_label NVARCHAR(100) NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private void ensureOrderDeliveredTimeColumn() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'delivered_at') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD delivered_at DATETIME2 NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private void saveOrderPaymentData(Long orderId,
                                      String checkoutUrl,
                                      String qrCode,
                                      String accountName,
                                      String accountNumber,
                                      String bankBin,
                                      String paymentLinkId) {
        if (orderId == null) {
            return;
        }
        ensureOrderPaymentColumns();
        try {
            jdbcTemplate.update(
                    "update dbo.orders set pay_checkout_url = ?, pay_qr_code = ?, pay_account_name = ?, pay_account_number = ?, pay_bank_bin = ?, pay_payment_link_id = ? where id = ?",
                    checkoutUrl,
                    qrCode,
                    accountName,
                    accountNumber,
                    bankBin,
                    paymentLinkId,
                    orderId
            );
        } catch (Exception ignored) {
        }
    }

    private Optional<Map<String, String>> findOrderPaymentData(Long orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        ensureOrderPaymentColumns();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select pay_checkout_url, pay_qr_code, pay_account_name, pay_account_number, pay_bank_bin, pay_payment_link_id from dbo.orders where id = ?",
                    orderId
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> row = rows.get(0);
            String checkoutUrl = row.get("pay_checkout_url") != null ? String.valueOf(row.get("pay_checkout_url")) : "";
            String qrCode = row.get("pay_qr_code") != null ? String.valueOf(row.get("pay_qr_code")) : "";
            if (checkoutUrl.isBlank() || qrCode.isBlank()) {
                return Optional.empty();
            }
            Map<String, String> result = new HashMap<>();
            result.put("checkoutUrl", checkoutUrl);
            result.put("qrCode", qrCode);
            result.put("accountName", row.get("pay_account_name") != null ? String.valueOf(row.get("pay_account_name")) : "");
            result.put("accountNumber", row.get("pay_account_number") != null ? String.valueOf(row.get("pay_account_number")) : "");
            result.put("bankBin", row.get("pay_bank_bin") != null ? String.valueOf(row.get("pay_bank_bin")) : "");
            result.put("paymentLinkId", row.get("pay_payment_link_id") != null ? String.valueOf(row.get("pay_payment_link_id")) : "");
            return Optional.of(result);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void clearOrderPaymentData(Long orderId) {
        if (orderId == null) {
            return;
        }
        ensureOrderPaymentColumns();
        try {
            jdbcTemplate.update(
                    "update dbo.orders set pay_checkout_url = null, pay_qr_code = null, pay_account_name = null, pay_account_number = null, pay_bank_bin = null, pay_payment_link_id = null where id = ?",
                    orderId
            );
        } catch (Exception ignored) {
        }
    }

    private void ensureOrderPaymentColumns() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'pay_checkout_url') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_checkout_url NVARCHAR(1000) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'pay_qr_code') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_qr_code NVARCHAR(MAX) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'pay_account_name') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_account_name NVARCHAR(255) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'pay_account_number') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_account_number NVARCHAR(100) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'pay_bank_bin') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_bank_bin NVARCHAR(20) NULL;
                END
                IF COL_LENGTH('dbo.orders', 'pay_payment_link_id') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD pay_payment_link_id NVARCHAR(255) NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private void updateOrderCoordinates(Long orderId, Double lat, Double lng) {
        if (orderId == null || lat == null || lng == null) {
            return;
        }
        Optional<ColumnPair> columns = findLatLngColumns(orderId);
        if (columns.isEmpty()) {
            return;
        }
        ColumnPair pair = columns.get();
        try {
            jdbcTemplate.update("update orders set " + pair.lat + " = ?, " + pair.lng + " = ? where id = ?",
                    lat, lng, orderId);
        } catch (Exception ignored) {
        }
    }

    private Optional<ColumnPair> findLatLngColumns(Long orderId) {
        ensureOrderCoordinateColumns();
        try {
            return jdbcTemplate.query("select * from orders where id = ?", rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                ResultSetMetaData meta = rs.getMetaData();
                String latCol = null;
                String lngCol = null;
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String label = meta.getColumnLabel(i);
                    String columnName = meta.getColumnName(i);
                    String key = (label == null || label.isBlank() ? columnName : label).toLowerCase(Locale.ROOT);
                    if (latCol == null && (key.equals("lat") || key.equals("latitude") || key.equals("order_lat") || key.equals("order_latitude") || key.equals("shipping_lat") || key.equals("delivery_lat") || key.equals("ship_lat"))) {
                        latCol = columnName;
                    }
                    if (lngCol == null && (key.equals("lng") || key.equals("longitude") || key.equals("order_lng") || key.equals("order_longitude") || key.equals("shipping_lng") || key.equals("delivery_lng") || key.equals("ship_lng"))) {
                        lngCol = columnName;
                    }
                }
                if (latCol == null || lngCol == null) {
                    return Optional.empty();
                }
                return Optional.of(new ColumnPair(latCol, lngCol));
            }, orderId);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void ensureOrderCoordinateColumns() {
        try {
            jdbcTemplate.execute("""
                IF COL_LENGTH('dbo.orders', 'delivery_lat') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD delivery_lat FLOAT NULL;
                END
                IF COL_LENGTH('dbo.orders', 'delivery_lng') IS NULL
                BEGIN
                    ALTER TABLE dbo.orders ADD delivery_lng FLOAT NULL;
                END
            """);
        } catch (Exception ignored) {
        }
    }

    private static class ColumnPair {
        private final String lat;
        private final String lng;

        private ColumnPair(String lat, String lng) {
            this.lat = lat;
            this.lng = lng;
        }
    }
}
