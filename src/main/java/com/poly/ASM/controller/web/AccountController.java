package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.entity.user.Authority;
import com.poly.ASM.entity.user.Role;
import com.poly.ASM.service.auth.AuthService;
import com.poly.ASM.service.auth.AuthProviderService;
import com.poly.ASM.service.user.AccountService;
import com.poly.ASM.service.user.AuthorityService;
import com.poly.ASM.service.user.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final RoleService roleService;
    private final AuthorityService authorityService;
    private final AuthService authService;
    private final AuthProviderService authProviderService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Đăng ký tài khoản thường:
     * - validate trùng username/email
     * - validate độ mạnh mật khẩu
     * - gán role USER mặc định.
     */
    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<?>> signUp(@RequestParam("username") String username,
                                                  @RequestParam("password") String password,
                                                  @RequestParam("fullname") String fullname,
                                                  @RequestParam("email") String email,
                                                  @RequestParam(value = "phone", required = false) String phone,
                                                  @RequestParam(value = "address", required = false) String address) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPassword = password == null ? "" : password.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        if (accountService.findByUsername(normalizedUsername).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username đã tồn tại", null));
        }
        if (accountService.findByEmail(normalizedEmail).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email đã tồn tại", null));
        }
        if (!isStrongPassword(normalizedPassword)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.", null));
        }

        Account account = new Account();
        account.setUsername(normalizedUsername);
        account.setPassword(passwordEncoder.encode(normalizedPassword));
        account.setFullname(fullname);
        account.setEmail(normalizedEmail);
        account.setPhone(phone == null || phone.isBlank() ? "0000000000" : phone.trim());
        account.setAddress(address == null || address.isBlank() ? "Chưa cập nhật" : address.trim());
        account.setActivated(true);
        Account saved = accountService.create(account);

        Role role = roleService.findById("USER")
                .orElseGet(() -> roleService.create(new Role("USER", "Khach hang", null)));
        Authority authority = new Authority();
        authority.setAccount(saved);
        authority.setRole(role);
        authorityService.create(authority);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đăng ký thành công, vui lòng đăng nhập", saved));
    }

    /**
     * Lấy hồ sơ người dùng hiện tại cho trang account/edit-profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<?>> profile() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("fullname", user.getFullname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        data.put("address", user.getAddress());
        data.put("photo", user.getPhoto());
        data.put("activated", user.getActivated());
        data.put("accountType", authProviderService.isGoogleAccount(user) ? "GOOGLE" : "NORMAL");
        return ResponseEntity.ok(ApiResponse.success("Lấy hồ sơ người dùng thành công", data));
    }

    /**
     * Cập nhật hồ sơ:
     * - chặn đổi email với tài khoản Google
     * - validate phone duy nhất/to đúng định dạng
     * - hỗ trợ upload avatar.
     */
    @PostMapping("/profile")
    public ResponseEntity<ApiResponse<?>> editProfile(@RequestParam("fullname") String fullname,
                                                       @RequestParam("email") String email,
                                                       @RequestParam(value = "phone", required = false) String phone,
                                                       @RequestParam(value = "address", required = false) String address,
                                                       @RequestParam(value = "photoFile", required = false) MultipartFile photoFile) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        Optional<Account> byEmail = accountService.findByEmail(email);
        if (byEmail.isPresent() && !byEmail.get().getUsername().equals(user.getUsername())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email đã tồn tại", null));
        }
        if (authProviderService.isGoogleAccount(user) && email != null && !email.trim().equalsIgnoreCase(String.valueOf(user.getEmail()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Tài khoản Google không được phép thay đổi email.", null));
        }
        if (phone != null && !phone.isBlank()) {
            String normalizedPhone = phone.replaceAll("\\s+", "").trim();
            if (!isValidPhone(normalizedPhone)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại phải gồm 10 số, bắt đầu bằng 0 và không được là 10 số 0.", null));
            }
            Optional<Account> byPhone = accountService.findByPhone(normalizedPhone);
            if (byPhone.isPresent() && !byPhone.get().getUsername().equals(user.getUsername())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại đã tồn tại", null));
            }
            phone = normalizedPhone;
        }

        user.setFullname(fullname);
        user.setEmail(email);
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone.trim());
        }
        if (address != null && !address.isBlank()) {
            user.setAddress(address.trim());
        }
        String photoName = savePhoto(photoFile);
        if (photoName != null) {
            user.setPhoto(photoName);
        }
        Account saved = accountService.update(user);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật hồ sơ thành công", saved));
    }

    /**
     * Upload avatar vào thư mục uploads và trả tên file đã lưu.
     */
    private String savePhoto(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String fileName = "avatar-" + UUID.randomUUID() + ext;
        Path uploadDir = Path.of("uploads");
        try {
            Files.createDirectories(uploadDir);
            Files.write(uploadDir.resolve(fileName), file.getBytes());
            return fileName;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Đổi mật khẩu cho tài khoản thường.
     * Tài khoản Google bị chặn ở tầng backend để tránh bypass UI.
     */
    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<?>> changePassword(@RequestParam("currentPassword") String currentPassword,
                                                          @RequestParam("newPassword") String newPassword) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        if (authProviderService.isGoogleAccount(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error("Tài khoản Google không hỗ trợ đổi mật khẩu tại hệ thống.", null));
        }
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu hiện tại không đúng", null));
        }
        if (!isStrongPassword(newPassword)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu mới phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.", null));
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        accountService.update(user);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công", null));
    }

    /**
     * Demo reset mật khẩu: sinh mật khẩu mới và trả về response.
     * (Môi trường production nên gửi qua email thay vì trả thẳng ra API).
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<?>> forgotPassword(@RequestParam("email") String email) {
        Optional<Account> account = accountService.findByEmail(email);
        if (account.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email không tồn tại", null));
        }
        Account user = account.get();
        String newPassword = generateStrongPassword();
        user.setPassword(passwordEncoder.encode(newPassword));
        accountService.update(user);
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("newPassword", newPassword);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công", data));
    }

    private boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }

    private String generateStrongPassword() {
        String base = UUID.randomUUID().toString().replace("-", "");
        return base.substring(0, 8) + "Aa1!";
    }

    private boolean isValidPhone(String phone) {
        if (phone == null) {
            return false;
        }
        if (!phone.matches("^0\\d{9}$")) {
            return false;
        }
        return !"0000000000".equals(phone);
    }

}
