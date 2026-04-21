package com.poly.ASM.service.auth;

import com.poly.ASM.entity.user.Account;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthProviderService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Đánh dấu tài khoản đã đăng nhập qua Google vào bảng mapping provider.
     * Dùng MERGE để idempotent: gọi nhiều lần vẫn chỉ còn 1 bản ghi.
     */
    public void markGoogle(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        ensureTable();
        try {
            jdbcTemplate.update("""
                    MERGE INTO account_auth_provider t
                    USING (SELECT ? AS username, 'GOOGLE' AS provider) s
                    ON (t.username = s.username)
                    WHEN MATCHED THEN UPDATE SET provider = s.provider
                    WHEN NOT MATCHED THEN INSERT (username, provider) VALUES (s.username, s.provider);
                    """, username);
        } catch (Exception ignored) {
        }
    }

    /**
     * Xác định account hiện tại có phải Google hay không.
     * Ưu tiên đọc từ bảng provider; fallback heuristic cho dữ liệu cũ chưa được migrate.
     */
    public boolean isGoogleAccount(Account account) {
        if (account == null || account.getUsername() == null || account.getUsername().isBlank()) {
            return false;
        }
        ensureTable();
        try {
            String provider = jdbcTemplate.queryForObject(
                    "select provider from account_auth_provider where username = ?",
                    String.class,
                    account.getUsername()
            );
            if ("GOOGLE".equalsIgnoreCase(provider)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        if (looksLikeGoogle(account)) {
            markGoogle(account.getUsername());
            return true;
        }
        return false;
    }

    private boolean looksLikeGoogle(Account account) {
        String phone = account.getPhone() == null ? "" : account.getPhone().trim();
        String address = account.getAddress() == null ? "" : account.getAddress().trim();
        String photo = account.getPhoto() == null ? "" : account.getPhoto().trim();
        String email = account.getEmail() == null ? "" : account.getEmail().trim().toLowerCase();
        String username = account.getUsername() == null ? "" : account.getUsername().trim().toLowerCase();
        boolean defaultProfile = "0000000000".equals(phone) && "Chưa cập nhật".equalsIgnoreCase(address);
        boolean hasRemotePhoto = photo.startsWith("http://") || photo.startsWith("https://");
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String normalized = localPart.replaceAll("[^a-z0-9._-]", "_").replaceAll("_+", "_");
        boolean usernameMatchesGooglePattern = !normalized.isBlank() && (username.equals(normalized) || username.startsWith(normalized + "_"));
        return usernameMatchesGooglePattern && (hasRemotePhoto || defaultProfile);
    }

    /**
     * Tự tạo bảng mapping nếu DB chưa có.
     * Mục tiêu là giảm phụ thuộc migration thủ công trong môi trường demo.
     */
    private void ensureTable() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS account_auth_provider (
                        username VARCHAR(50) PRIMARY KEY,
                        provider VARCHAR(20) NOT NULL
                    )
                    """);
        } catch (Exception ignored) {
        }
    }
}
