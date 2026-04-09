package com.poly.ASM.controller.admin;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.service.order.ReportService;
import com.poly.ASM.service.order.dto.RevenueOrderRow;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportAController {

    private final ReportService reportService;

    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<?>> revenue(@RequestParam(value = "fromDate", required = false) LocalDate fromDate,
                                                   @RequestParam(value = "toDate", required = false) LocalDate toDate,
                                                   @RequestParam(value = "sortField", required = false) String sortField,
                                                   @RequestParam(value = "sortDir", required = false) String sortDir) {
        String fieldValue = sortField != null ? sortField : "orderId";
        String dirValue = sortDir != null ? sortDir : "asc";
        java.util.List<RevenueOrderRow> rows = reportService.revenueByDeliveredOrders(fromDate, toDate, fieldValue, dirValue);
        BigDecimal total = BigDecimal.ZERO;
        for (RevenueOrderRow row : rows) {
            if (row.getLineTotal() != null) {
                total = total.add(row.getLineTotal());
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("rows", rows);
        data.put("grandTotal", total);
        data.put("fromDate", fromDate);
        data.put("toDate", toDate);
        data.put("sortField", fieldValue);
        data.put("sortDir", dirValue);
        return ResponseEntity.ok(ApiResponse.success("Lấy báo cáo doanh thu thành công", data));
    }

    @GetMapping("/vip")
    public ResponseEntity<ApiResponse<?>> vip() {
        return ResponseEntity.ok(ApiResponse.success("Lấy top khách hàng VIP thành công", reportService.top10VipCustomers()));
    }

    @GetMapping("/revenue/export")
    public ResponseEntity<byte[]> exportRevenue(@RequestParam(value = "fromDate", required = false) LocalDate fromDate,
                                                @RequestParam(value = "toDate", required = false) LocalDate toDate,
                                                @RequestParam(value = "sortField", required = false) String sortField,
                                                @RequestParam(value = "sortDir", required = false) String sortDir,
                                                @RequestParam(value = "mode", required = false) String mode) {
        String fieldValue = sortField != null ? sortField : "orderId";
        String dirValue = sortDir != null ? sortDir : "asc";
        String modeValue = mode != null && !mode.isBlank() ? mode : "summary";
        java.util.List<RevenueOrderRow> rows = reportService.revenueByDeliveredOrders(fromDate, toDate, fieldValue, dirValue);
        byte[] bytes = buildRevenueCsv(rows, fromDate, toDate, modeValue);
        String fileName = "doanh-thu-" + modeValue + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(bytes);
    }

    private byte[] buildRevenueCsv(java.util.List<RevenueOrderRow> rows,
                                   LocalDate fromDate,
                                   LocalDate toDate,
                                   String mode) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF');
        sb.append("BÁO CÁO DOANH THU - ").append(mode.toUpperCase()).append("\n");
        sb.append("Khoảng thời gian,").append(fromDate != null ? fromDate : "").append(" đến ").append(toDate != null ? toDate : "").append("\n\n");
        sb.append("STT,Mã đơn,Ngày đặt,Sản phẩm,Thể loại,Số lượng,Đơn giá,Giảm giá,Thành tiền\n");
        BigDecimal total = BigDecimal.ZERO;
        int stt = 1;
        for (RevenueOrderRow item : rows) {
            sb.append(stt++).append(",");
            sb.append(escapeCsv(item.getOrderId() != null ? String.valueOf(item.getOrderId()) : "")).append(",");
            sb.append(escapeCsv(item.getOrderCreateDate() != null ? item.getOrderCreateDate().format(dtf) : "")).append(",");
            sb.append(escapeCsv(item.getProductName() != null ? item.getProductName() : "")).append(",");
            sb.append(escapeCsv(item.getCategoryName() != null ? item.getCategoryName() : "")).append(",");
            sb.append(item.getQuantity() != null ? item.getQuantity() : 0).append(",");
            sb.append(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO).append(",");
            sb.append(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO).append(",");
            sb.append(item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO).append("\n");
            if (item.getLineTotal() != null) {
                total = total.add(item.getLineTotal());
            }
        }
        sb.append(",,,,,,,Tổng cộng,").append(total).append("\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
