package com.poly.ASM.security;

import com.poly.ASM.entity.user.Account;
import com.poly.ASM.entity.user.Authority;
import com.poly.ASM.entity.user.Role;
import com.poly.ASM.repository.user.AuthorityRepository;
import com.poly.ASM.service.cart.CartService;
import com.poly.ASM.service.auth.AuthProviderService;
import com.poly.ASM.service.user.AccountService;
import com.poly.ASM.service.user.AuthorityService;
import com.poly.ASM.service.user.RoleService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GoogleOAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final AccountService accountService;
    private final RoleService roleService;
    private final AuthorityService authorityService;
    private final AuthorityRepository authorityRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProviderService authProviderService;
    private final CartService cartService;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtCookieService jwtCookieService;
    private final CustomUserDetailsService customUserDetailsService;
    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    /**
     * Luồng thành công OAuth2:
     * - tạo/tìm account theo email Google
     * - đánh dấu provider GOOGLE
     * - đảm bảo role USER
     * - đồng bộ giỏ hàng session
     * - phát JWT cookie và redirect về frontend.
     */
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws ServletException, IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = toSafeString(oAuth2User.getAttribute("email"));
        if (email.isBlank()) {
            throw new UsernameNotFoundException("Google account does not contain email");
        }

        Account account = accountService.findByEmail(email)
                .orElseGet(() -> createAccountFromGoogleProfile(oAuth2User, email));
        authProviderService.markGoogle(account.getUsername());
        ensureUserRole(account);
        cartService.mergeSessionCartToUserCart(account.getUsername());
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(account.getUsername());
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);
        tokenBlacklistService.storeRefreshToken(
                refreshToken,
                userDetails.getUsername(),
                Instant.ofEpochMilli(jwtService.extractExpiration(refreshToken).getTime())
        );
        jwtCookieService.writeAccessCookie(response, accessToken);
        jwtCookieService.writeRefreshCookie(response, refreshToken);
        String normalizedBase = frontendBaseUrl == null ? "http://localhost:5173" : frontendBaseUrl.trim();
        if (normalizedBase.endsWith("/")) {
            normalizedBase = normalizedBase.substring(0, normalizedBase.length() - 1);
        }
        getRedirectStrategy().sendRedirect(request, response, normalizedBase + "/home/index");
    }

    /**
     * Khởi tạo account mới từ profile Google khi hệ thống chưa có email tương ứng.
     */
    private Account createAccountFromGoogleProfile(OAuth2User oAuth2User, String email) {
        String displayName = toSafeString(oAuth2User.getAttribute("name"));
        String photoUrl = toSafeString(oAuth2User.getAttribute("picture"));
        String username = generateUsernameFromEmail(email);

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setFullname(limit(displayName.isBlank() ? username : displayName, 100));
        account.setEmail(limit(email, 100));
        account.setPhone("0000000000");
        account.setAddress("Chưa cập nhật");
        account.setPhoto(limit(photoUrl, 255));
        account.setActivated(true);
        return accountService.create(account);
    }

    /**
     * Đảm bảo account luôn có authority USER.
     */
    private void ensureUserRole(Account account) {
        Role userRole = roleService.findById("USER")
                .orElseGet(() -> roleService.create(new Role("USER", "Khach hang", null)));
        boolean hasRole = authorityRepository.existsByAccountUsernameAndRoleId(account.getUsername(), "USER");
        if (!hasRole) {
            Authority authority = new Authority();
            authority.setAccount(account);
            authority.setRole(userRole);
            authorityService.create(authority);
        }
    }

    private String generateUsernameFromEmail(String email) {
        String localPart = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        String normalized = localPart.toLowerCase()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_+", "_");
        if (normalized.isBlank()) {
            normalized = "google_user";
        }
        String base = limit(normalized, 40);
        String candidate = base;
        int counter = 1;
        while (accountService.findByUsername(candidate).isPresent()) {
            String suffix = "_" + counter;
            candidate = limit(base, Math.max(1, 50 - suffix.length())) + suffix;
            counter++;
        }
        return candidate;
    }

    private String toSafeString(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
