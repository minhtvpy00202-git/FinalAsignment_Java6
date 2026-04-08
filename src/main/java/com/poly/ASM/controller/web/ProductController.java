package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.product.Category;
import com.poly.ASM.entity.product.Product;
import com.poly.ASM.exception.ApiException;
import com.poly.ASM.service.product.CategoryService;
import com.poly.ASM.service.product.ProductService;
import com.poly.ASM.service.review.ProductReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/store/products")
@RequiredArgsConstructor
public class ProductController {

    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductReviewService productReviewService;

    @GetMapping("/category/{id}")
    public ResponseEntity<ApiResponse<?>> listByCategory(@PathVariable("id") String categoryId,
                                                          @RequestParam(value = "keyword", required = false) String keyword,
                                                          @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                                                          @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                                                          @RequestParam(value = "priceRange", required = false) String priceRange,
                                                          @RequestParam(value = "sort", required = false) String sort,
                                                          @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
                                                          @RequestParam(value = "size", required = false, defaultValue = "12") Integer size) {
        Optional<Category> category = categoryService.findById(categoryId);
        if (category.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy danh mục");
        }
        BigDecimal[] range = applyQuickRange(priceRange, minPrice, maxPrice);
        minPrice = range[0];
        maxPrice = range[1];
        Page<Product> pageResult = productService.searchWithFiltersPage(keyword, categoryId, minPrice, maxPrice, sort, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("category", category.get());
        data.put("products", pageResult.getContent());
        data.put("categories", categoryService.findAll());
        data.put("keyword", keyword);
        data.put("categoryId", categoryId);
        data.put("minPrice", minPrice);
        data.put("maxPrice", maxPrice);
        data.put("priceRange", priceRange);
        data.put("sort", sort);
        data.put("currentPage", pageResult.getNumber());
        data.put("totalPages", pageResult.getTotalPages());
        data.put("pageSize", size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm theo danh mục thành công", data));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> detail(@PathVariable("id") Integer productId) {
        Optional<Product> product = productService.findByIdWithSizes(productId);
        if (product.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm");
        }
        Product current = product.get();
        String categoryId = current.getCategory() != null ? current.getCategory().getId() : null;
        List<Product> related = categoryId == null
                ? List.of()
                : productService.findTop4ByCategoryIdAndIdNot(categoryId, current.getId());

        var stats = productReviewService.getStats(productId);
        double avgRating = stats != null && stats.getAvgRating() != null ? stats.getAvgRating() : 0.0;
        long avgRounded = Math.round(avgRating);
        Map<String, Object> data = new HashMap<>();
        data.put("product", current);
        data.put("relatedProducts", related);
        data.put("reviews", productReviewService.findByProductId(productId));
        data.put("reviewStats", stats);
        data.put("avgRatingRounded", avgRounded);
        data.put("avgRatingValue", avgRating);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", data));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> listAll(@RequestParam(value = "keyword", required = false) String keyword,
                                                   @RequestParam(value = "categoryId", required = false) String categoryId,
                                                   @RequestParam(value = "minPrice", required = false) BigDecimal minPrice,
                                                   @RequestParam(value = "maxPrice", required = false) BigDecimal maxPrice,
                                                   @RequestParam(value = "priceRange", required = false) String priceRange,
                                                   @RequestParam(value = "sort", required = false) String sort,
                                                   @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
                                                   @RequestParam(value = "size", required = false, defaultValue = "12") Integer size) {
        Category category = new Category();
        if (categoryId != null && !categoryId.isBlank()) {
            categoryService.findById(categoryId).ifPresentOrElse(
                    found -> category.setName(found.getName()),
                    () -> category.setName("Tất cả sản phẩm")
            );
        } else {
            category.setName("Tất cả sản phẩm");
        }
        BigDecimal[] range = applyQuickRange(priceRange, minPrice, maxPrice);
        minPrice = range[0];
        maxPrice = range[1];
        Page<Product> pageResult = productService.searchWithFiltersPage(keyword, categoryId, minPrice, maxPrice, sort, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("category", category);
        data.put("products", pageResult.getContent());
        data.put("categories", categoryService.findAll());
        data.put("keyword", keyword);
        data.put("categoryId", categoryId);
        data.put("minPrice", minPrice);
        data.put("maxPrice", maxPrice);
        data.put("priceRange", priceRange);
        data.put("sort", sort);
        data.put("currentPage", pageResult.getNumber());
        data.put("totalPages", pageResult.getTotalPages());
        data.put("pageSize", size);
        data.put("totalElements", pageResult.getTotalElements());
        data.put("totalProducts", pageResult.getTotalElements());
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", data));
    }

    private BigDecimal[] applyQuickRange(String priceRange, BigDecimal minPrice, BigDecimal maxPrice) {
        if (priceRange == null || priceRange.isBlank()) {
            return new BigDecimal[]{minPrice, maxPrice};
        }
        return switch (priceRange) {
            case "under_500" -> new BigDecimal[]{null, BigDecimal.valueOf(500_000)};
            case "500_1m" -> new BigDecimal[]{BigDecimal.valueOf(500_000), BigDecimal.valueOf(1_000_000)};
            case "1m_2m" -> new BigDecimal[]{BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2_000_000)};
            case "over_2m" -> new BigDecimal[]{BigDecimal.valueOf(2_000_000), null};
            default -> new BigDecimal[]{minPrice, maxPrice};
        };
    }
}
