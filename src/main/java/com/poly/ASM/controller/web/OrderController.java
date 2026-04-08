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
import com.poly.ASM.service.product.ProductSizeService;
import com.poly.ASM.service.review.ProductReviewService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/order-workflow")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final CartService cartService;
    private final AuthService authService;
    private final ProductSizeService productSizeService;
    private final ProductReviewService productReviewService;
    private final NotificationService notificationService;
    private final PayosPaymentService payosPaymentService;
    private final JdbcTemplate jdbcTemplate;
    private final CartItemRepository cartItemRepository;

    @GetMapping("/checkout")
    public ResponseEntity<ApiResponse<?>> checkoutForm() {
        Map<String, Object> data = new HashMap<>();
        data.put("items", cartService.getCartItems());
        data.put("totalPrice", cartService.getTotalPrice());
        return ResponseEntity.ok(ApiResponse.success("Lấy dữ liệu checkout thành công", data));
    }

    @PostMapping("/checkout")
    @Transactional
    public ResponseEntity<ApiResponse<?>> checkout(@RequestParam(value = "address", required = false) String address,
                                                   @RequestParam(value = "addressDetail", required = false) String addressDetail,
                                                   @RequestParam(value = "provinceCode", required = false) String provinceCode,
                                                   @RequestParam(value = "wardCode", required = false) String wardCode,
                                                   @RequestParam(value = "lat", required = false) Double lat,
                                                   @RequestParam(value = "lng", required = false) Double lng,
                                                   @RequestParam("paymentMethod") String paymentMethod) {
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
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
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
        Order order = new Order();
        order.setAccount(user);
        order.setAddress(resolvedAddress);
        order.setStatus("BANK".equalsIgnoreCase(paymentMethod) ? "PENDING_PAYMENT" : "PLACED_UNPAID");
        Order savedOrder = orderService.create(order);
        updateOrderAdministrative(savedOrder.getId(), normalizedProvinceCode, normalizedWardCode);
        updateOrderCoordinates(savedOrder.getId(), lat, lng);
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
            
            // Deduct inventory when order is placed
            int currentQty = productSize.get().getQuantity();
            productSize.get().setQuantity(Math.max(0, currentQty - item.getQuantity()));
            productSizeService.save(productSize.get());
        }
        if (!"BANK".equalsIgnoreCase(paymentMethod)) {
            cartService.clearCart();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", savedOrder.getId());
        data.put("nextAction", "BANK".equalsIgnoreCase(paymentMethod) ? "BANK_TRANSFER" : "VIEW_ORDER_DETAIL");
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
            CreatePaymentLinkResponse response = payosPaymentService.createPaymentLink(
                    order.getId(),
                    amount,
                    "Thanh toan don hang #" + order.getId(),
                    returnUrl,
                    cancelUrl
            );
            Map<String, Object> data = new HashMap<>();
            data.put("order", toOrderData(order));
            data.put("totalPrice", total);
            data.put("checkoutUrl", response.getCheckoutUrl());
            data.put("qrImageSrc", buildQrImageSrc(response.getQrCode()));
            data.put("accountName", response.getAccountName());
            data.put("accountNumber", response.getAccountNumber());
            data.put("bankName", "BIDV");
            data.put("bankBin", response.getBin());
            data.put("paymentLinkId", response.getPaymentLinkId());
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

    @GetMapping("/my-product-list")
    public ResponseEntity<ApiResponse<?>> myProductList() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        List<OrderDetail> details = orderDetailService.findByOrderAccountUsername(user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm đã mua thành công", toOrderDetailData(details)));
    }

    private boolean isDeliveredStatus(String status) {
        if (status == null) {
            return false;
        }
        return "DELIVERED_SUCCESS".equals(status) || "DONE".equals(status);
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
