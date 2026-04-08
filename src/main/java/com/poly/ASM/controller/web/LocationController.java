package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/provinces")
    public ResponseEntity<ApiResponse<?>> provinces() {
        List<Map<String, Object>> data = jdbcTemplate.query(
                "select code, full_name from dbo.provinces order by full_name",
                (rs, index) -> Map.<String, Object>of(
                        "code", rs.getString("code"),
                        "name", rs.getString("full_name")
                )
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tỉnh/thành thành công", data));
    }

    @GetMapping("/wards")
    public ResponseEntity<ApiResponse<?>> wards(@RequestParam("provinceCode") String provinceCode) {
        if (provinceCode == null || provinceCode.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phường/xã thành công", List.of()));
        }
        List<Map<String, Object>> data = jdbcTemplate.query(
                "select code, full_name from dbo.wards where province_code = ? order by full_name",
                (rs, index) -> Map.<String, Object>of(
                        "code", rs.getString("code"),
                        "name", rs.getString("full_name")
                ),
                provinceCode.trim()
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách phường/xã thành công", data));
    }
}
