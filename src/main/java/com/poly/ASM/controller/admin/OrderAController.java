package com.poly.ASM.controller.admin;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.order.Order;
import com.poly.ASM.exception.ApiException;
import com.poly.ASM.repository.review.ProductReviewRepository;
import com.poly.ASM.service.notification.NotificationService;
import com.poly.ASM.service.order.OrderDetailService;
import com.poly.ASM.service.order.OrderService;
import com.poly.ASM.service.payment.PayosPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.payos.exception.PayOSException;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@RequiredArgsConstructor
public class OrderAController {

    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final NotificationService notificationService;
    private final ProductReviewRepository productReviewRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PayosPaymentService payosPaymentService;

    @GetMapping
    public ResponseEntity<ApiResponse<?>> index(@RequestParam(value = "tab", required = false, defaultValue = "pending") String tab,
                                                @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
                                                @RequestParam(value = "size", required = false, defaultValue = "10") Integer size) {
        String normalizedTab = tab == null ? "pending" : tab.trim().toLowerCase(Locale.ROOT);
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null || size <= 0 ? 10 : Math.min(size, 100);
        List<Map<String, Object>> filtered = orderService.findAll().stream()
                .filter(order -> order != null)
                .filter(order -> isInTab(order.getStatus(), normalizedTab))
                .map(order -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", order.getId());
                    item.put("status", order.getStatus());
                    item.put("address", order.getAddress());
                    item.put("createDate", order.getCreateDate());
                    item.put("expectedDeliveryDate", findOrderExpectedDeliveryDate(order.getId()).orElse(""));
                    item.put("deliveryDistanceM", findOrderDeliveryDistanceMeters(order.getId()).orElse(0L));
                    item.put("deliveredAt", findOrderDeliveredAt(order.getId()).orElse(null));
                    if (order.getAccount() != null) {
                        Map<String, Object> account = new HashMap<>();
                        account.put("username", order.getAccount().getUsername());
                        item.put("account", account);
                    } else {
                        item.put("account", null);
                    }
                    return item;
                })
                .toList();
        int from = Math.min(safePage * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil(filtered.size() / (double) safeSize);
        Map<String, Object> data = new HashMap<>();
        data.put("rows", filtered.subList(from, to));
        data.put("page", safePage);
        data.put("size", safeSize);
        data.put("totalPages", totalPages);
        data.put("totalElements", filtered.size());
        data.put("tab", normalizedTab);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng quản trị thành công", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> detail(@PathVariable("id") Long id) {
        Optional<Order> order = orderService.findById(id);
        Order current = order.orElse(null);
        if (current == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Không tìm thấy đơn hàng", null));
        }
        Map<String, Object> orderData = new HashMap<>();
        orderData.put("id", current.getId());
        orderData.put("address", current.getAddress());
        orderData.put("shippingPhone", findOrderShippingPhone(current.getId()).orElse(""));
        orderData.put("status", current.getStatus());
        orderData.put("createDate", current.getCreateDate());
        List<Map<String, Object>> details = orderDetailService.findByOrderId(id).stream().map(detail -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", detail.getId());
            item.put("price", detail.getPrice());
            item.put("quantity", detail.getQuantity());
            item.put("sizeId", detail.getSizeId());
            item.put("sizeName", detail.getSizeName());
            if (detail.getProduct() != null) {
                Map<String, Object> product = new HashMap<>();
                product.put("id", detail.getProduct().getId());
                product.put("name", detail.getProduct().getName());
                item.put("product", product);
            }
            return item;
        }).toList();
        Map<String, Object> data = new HashMap<>();
        data.put("order", orderData);
        data.put("details", details);
        if (current != null) {
            Optional<double[]> coords = findOrderCoordinates(current.getId());
            coords.ifPresent(values -> {
                data.put("deliveryLat", values[0]);
                data.put("deliveryLng", values[1]);
            });
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", data));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable("id") Long id) {
        notificationService.deleteByOrderId(id);
        productReviewRepository.deleteByOrderId(id);
        orderDetailService.deleteByOrderId(id);
        orderService.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa đơn hàng thành công", null));
    }

    @PutMapping("/{id}/status")
    @Transactional
    public ResponseEntity<ApiResponse<?>> updateStatus(@PathVariable("id") Long id,
                                                        @RequestParam("status") String status) {
        orderService.findById(id).ifPresent(order -> {
            String previous = order.getStatus();
            if (!isValidTransition(previous, status)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ cho bước cập nhật hiện tại");
            }
            order.setStatus(status);
            orderService.update(order);
            updateOrderDeliveredTime(order.getId(), previous, status);
            if (previous == null || !previous.equals(status)) {
                notificationService.notifyOrderStatusChange(order, status);
            }
        });
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái đơn hàng thành công", Map.of("id", id, "status", status)));
    }

    private boolean isValidTransition(String currentStatus, String nextStatus) {
        if (currentStatus == null || nextStatus == null || nextStatus.isBlank()) {
            return false;
        }
        if ("PENDING_PAYMENT".equals(currentStatus)) {
            return "PLACED_UNPAID".equals(nextStatus) || "PLACED_PAID".equals(nextStatus);
        }
        if ("PLACED_UNPAID".equals(currentStatus)) {
            return "SHIPPING_UNPAID".equals(nextStatus);
        }
        if ("PLACED_PAID".equals(currentStatus)) {
            return "SHIPPING_PAID".equals(nextStatus);
        }
        if ("SHIPPING_UNPAID".equals(currentStatus) || "SHIPPING_PAID".equals(currentStatus)) {
            return "DELIVERED_SUCCESS".equals(nextStatus) || "DELIVERY_FAILED".equals(nextStatus);
        }
        if ("DELIVERED_SUCCESS".equals(currentStatus) || "DONE".equals(currentStatus)) {
            return "DELIVERED_SUCCESS".equals(nextStatus) || "DONE".equals(nextStatus);
        }
        if ("DELIVERY_FAILED".equals(currentStatus)) {
            return "DELIVERY_FAILED".equals(nextStatus)
                    || "SHIPPING_UNPAID".equals(nextStatus)
                    || "SHIPPING_PAID".equals(nextStatus);
        }
        if ("CANCEL".equals(currentStatus)) {
            return "CANCEL".equals(nextStatus);
        }
        return currentStatus.equals(nextStatus);
    }

    @PostMapping("/payos/cancel")
    public ResponseEntity<ApiResponse<?>> cancelPayos(@RequestParam("orderCode") Long orderCode) {
        String message;
        boolean error = false;
        if (orderCode == null || orderCode <= 0) {
            message = "Mã đơn không ở trạng thái đang chờ thanh toán. Xin thử lại";
            error = true;
        } else {
            boolean cancelled = false;
            try {
                cancelled = payosPaymentService.cancelIfPending(orderCode, "Admin cancel pending order");
            } catch (PayOSException ignored) {
                cancelled = false;
            }
            if (cancelled) {
                message = "Đã gửi lệnh huỷ lên PayOS cho đơn #" + orderCode;
            } else {
                message = "Mã đơn không ở trạng thái đang chờ thanh toán. Xin thử lại";
                error = true;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("orderCode", orderCode);
        if (error) {
            return ResponseEntity.badRequest().body(ApiResponse.error(message, data));
        }
        return ResponseEntity.ok(ApiResponse.success(message, data));
    }

    private Optional<double[]> findOrderCoordinates(Long orderId) {
        ensureOrderCoordinateColumns();
        try {
            return jdbcTemplate.query("select * from orders where id = ?", rs -> {
                if (!rs.next()) {
                    return Optional.empty();
                }
                ResultSetMetaData meta = rs.getMetaData();
                Integer latIndex = null;
                Integer lngIndex = null;
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String name = meta.getColumnLabel(i);
                    if (name == null || name.isBlank()) {
                        name = meta.getColumnName(i);
                    }
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    String key = name.toLowerCase(Locale.ROOT);
                    if (latIndex == null && (key.equals("lat") || key.equals("latitude") || key.equals("order_lat") || key.equals("order_latitude") || key.equals("shipping_lat") || key.equals("delivery_lat") || key.equals("ship_lat"))) {
                        latIndex = i;
                    }
                    if (lngIndex == null && (key.equals("lng") || key.equals("longitude") || key.equals("order_lng") || key.equals("order_longitude") || key.equals("shipping_lng") || key.equals("delivery_lng") || key.equals("ship_lng"))) {
                        lngIndex = i;
                    }
                }
                if (latIndex == null || lngIndex == null) {
                    return Optional.empty();
                }
                Object latObj = rs.getObject(latIndex);
                Object lngObj = rs.getObject(lngIndex);
                if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
                    return Optional.empty();
                }
                double lat = ((Number) latObj).doubleValue();
                double lng = ((Number) lngObj).doubleValue();
                return Optional.of(new double[]{lat, lng});
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

    private boolean isInTab(String status, String tab) {
        String current = status == null ? "" : status;
        if ("placed".equals(tab)) {
            return "PLACED_UNPAID".equals(current) || "PLACED_PAID".equals(current);
        }
        if ("delivered".equals(tab)) {
            return "DELIVERED_SUCCESS".equals(current) || "DONE".equals(current);
        }
        return "PENDING_PAYMENT".equals(current);
    }

    private Optional<String> findOrderShippingPhone(Long orderId) {
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

    private Optional<String> findOrderExpectedDeliveryDate(Long orderId) {
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

    private void updateOrderDeliveredTime(Long orderId, String previousStatus, String nextStatus) {
        if (orderId == null) {
            return;
        }
        ensureOrderDeliveredTimeColumn();
        boolean wasDelivered = "DELIVERED_SUCCESS".equals(previousStatus) || "DONE".equals(previousStatus);
        boolean nowDelivered = "DELIVERED_SUCCESS".equals(nextStatus) || "DONE".equals(nextStatus);
        try {
            if (!wasDelivered && nowDelivered) {
                jdbcTemplate.update("update dbo.orders set delivered_at = ? where id = ?", new Timestamp(System.currentTimeMillis()), orderId);
            } else if (wasDelivered && !nowDelivered) {
                jdbcTemplate.update("update dbo.orders set delivered_at = null where id = ?", orderId);
            }
        } catch (Exception ignored) {
        }
    }

}
