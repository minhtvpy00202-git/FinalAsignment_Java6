package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.exception.ApiException;
import com.poly.ASM.security.JwtCookieService;
import com.poly.ASM.security.JwtService;
import com.poly.ASM.security.TokenBlacklistService;
import com.poly.ASM.security.CustomUserDetailsService;
import com.poly.ASM.service.user.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtCookieService jwtCookieService;
    private final AccountService accountService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<?>> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        if (request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username và password là bắt buộc");
        }
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        tokenBlacklistService.storeRefreshToken(
                refreshToken,
                userDetails.getUsername(),
                Instant.ofEpochMilli(jwtService.extractExpiration(refreshToken).getTime())
        );
        List<String> roles = userDetails.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .toList();
        jwtCookieService.writeAccessCookie(response, accessToken);
        jwtCookieService.writeRefreshCookie(response, refreshToken);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Đăng nhập thành công",
                        new LoginResponse(accessToken, refreshToken, "Bearer", userDetails.getUsername(), roles)
                )
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<?>> refresh(@RequestBody(required = false) RefreshRequest request,
                                                  HttpServletRequest servletRequest,
                                                  HttpServletResponse response) {
        String refreshToken = request != null && request.refreshToken() != null && !request.refreshToken().isBlank()
                ? request.refreshToken().trim()
                : jwtCookieService.readRefreshToken(servletRequest);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Refresh token là bắt buộc");
        }
        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
        }
        try {
            if (!"refresh".equals(jwtService.extractTokenType(refreshToken))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
            }
            String username = jwtService.extractUsername(refreshToken);
            if (!tokenBlacklistService.isRefreshTokenActive(refreshToken, username)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token đã bị thu hồi hoặc không tồn tại");
            }
            UserDetails userDetails = customUserDetailsService.loadUserByUsername(username);
            if (!jwtService.isTokenValid(refreshToken, userDetails)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token hết hạn hoặc không hợp lệ");
            }
            blacklistToken(refreshToken);
            String newAccessToken = jwtService.generateAccessToken(userDetails);
            String newRefreshToken = jwtService.generateRefreshToken(userDetails);
            tokenBlacklistService.storeRefreshToken(
                    newRefreshToken,
                    userDetails.getUsername(),
                    Instant.ofEpochMilli(jwtService.extractExpiration(newRefreshToken).getTime())
            );
            jwtCookieService.writeAccessCookie(response, newAccessToken);
            jwtCookieService.writeRefreshCookie(response, newRefreshToken);
            return ResponseEntity.ok(
                    ApiResponse.success(
                            "Làm mới token thành công",
                            Map.of(
                                    "accessToken", newAccessToken,
                                    "refreshToken", newRefreshToken,
                                    "tokenType", "Bearer",
                                    "username", userDetails.getUsername()
                            )
                    )
            );
        } catch (RuntimeException e) {
            if (e instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ");
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(@RequestBody(required = false) LogoutRequest request,
                                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse response) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            blacklistToken(accessToken);
        }
        String accessTokenFromCookie = jwtCookieService.readAccessToken(servletRequest);
        if (accessTokenFromCookie != null && !accessTokenFromCookie.isBlank()) {
            blacklistToken(accessTokenFromCookie);
        }
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            blacklistToken(request.refreshToken().trim());
        }
        String refreshTokenFromCookie = jwtCookieService.readRefreshToken(servletRequest);
        if (refreshTokenFromCookie != null && !refreshTokenFromCookie.isBlank()) {
            blacklistToken(refreshTokenFromCookie);
        }
        jwtCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(ApiResponse.success("Đăng xuất thành công", null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<?>> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        var account = accountService.findByUsername(authentication.getName()).orElse(null);
        return ResponseEntity.ok(
                ApiResponse.success(
                        "Lấy thông tin tài khoản thành công",
                        Map.of(
                                "username", authentication.getName(),
                                "fullname", account != null ? account.getFullname() : "",
                                "photo", account != null ? account.getPhoto() : "",
                                "email", account != null ? account.getEmail() : "",
                                "roles", authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList()
                        )
                )
        );
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String accessToken, String refreshToken, String tokenType, String username, List<String> roles) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    private void blacklistToken(String token) {
        try {
            Date expiration = jwtService.extractExpiration(token);
            String username = jwtService.extractUsername(token);
            String tokenType = jwtService.extractTokenType(token);
            tokenBlacklistService.blacklist(
                    token,
                    Instant.ofEpochMilli(expiration.getTime()),
                    username,
                    tokenType != null ? tokenType.toUpperCase() : null
            );
        } catch (RuntimeException ignored) {
        }
    }
}
