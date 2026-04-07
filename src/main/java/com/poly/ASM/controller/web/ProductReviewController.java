package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.order.Order;
import com.poly.ASM.entity.order.OrderDetail;
import com.poly.ASM.entity.product.Product;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.service.auth.AuthService;
import com.poly.ASM.service.order.OrderDetailService;
import com.poly.ASM.service.order.OrderService;
import com.poly.ASM.service.review.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ProductReviewController {

    private final AuthService authService;
    private final OrderService orderService;
    private final OrderDetailService orderDetailService;
    private final ProductReviewService productReviewService;

    @PostMapping("/order")
    public ResponseEntity<ApiResponse<?>> create(@RequestParam("orderId") Long orderId,
                                                  @RequestParam("productId") Integer productId,
                                                  @RequestParam("starRating") Integer starRating,
                                                  @RequestParam(value = "reviewContent", required = false) String reviewContent,
                                                  @RequestParam(value = "images", required = false) MultipartFile[] images) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }

        Optional<Order> orderOpt = orderService.findById(orderId);
        if (orderOpt.isEmpty() || orderOpt.get().getAccount() == null
                || !user.getUsername().equals(orderOpt.get().getAccount().getUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Đơn hàng không hợp lệ.", null));
        }

        Order order = orderOpt.get();
        if (!isDeliveredStatus(order.getStatus())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Chỉ được đánh giá khi đơn hàng giao thành công.", null));
        }

        boolean hasProduct = orderDetailService.findByOrderId(orderId)
                .stream()
                .map(OrderDetail::getProduct)
                .anyMatch(product -> product != null && productId.equals(product.getId()));
        if (!hasProduct) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Sản phẩm không thuộc đơn hàng.", null));
        }

        if (productReviewService.hasReviewed(user.getUsername(), productId, orderId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Bạn đã đánh giá sản phẩm này rồi.", null));
        }

        if (starRating == null || starRating < 1 || starRating > 5) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error("Vui lòng chọn số sao hợp lệ.", null));
        }

        List<String> imageNames = saveReviewImages(images);
        Product product = new Product();
        product.setId(productId);
        productReviewService.createReview(user, product, order, starRating, reviewContent, imageNames);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã gửi đánh giá.", null));
    }

    private boolean isDeliveredStatus(String status) {
        return "DELIVERED_SUCCESS".equals(status) || "DONE".equals(status);
    }

    private List<String> saveReviewImages(MultipartFile[] files) {
        List<String> results = new ArrayList<>();
        if (files == null || files.length == 0) {
            return results;
        }
        Path uploadDir = Path.of("uploads");
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String original = file.getOriginalFilename();
            String ext = "";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }
            String fileName = "review-" + UUID.randomUUID() + ext;
            try {
                Files.createDirectories(uploadDir);
                Files.write(uploadDir.resolve(fileName), file.getBytes());
                results.add(fileName);
            } catch (IOException ignored) {
                // ignore failed image save
            }
        }
        return results;
    }
}
