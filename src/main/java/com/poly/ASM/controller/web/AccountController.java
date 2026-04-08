package com.poly.ASM.controller.web;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.entity.user.Authority;
import com.poly.ASM.entity.user.Role;
import com.poly.ASM.service.auth.AuthService;
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
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/sign-up")
    public ResponseEntity<ApiResponse<?>> signUp(@RequestParam("username") String username,
                                                  @RequestParam("password") String password,
                                                  @RequestParam("fullname") String fullname,
                                                  @RequestParam("email") String email,
                                                  @RequestParam(value = "phone", required = false) String phone,
                                                  @RequestParam(value = "address", required = false) String address) {
        if (accountService.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Username đã tồn tại", null));
        }
        if (accountService.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Email đã tồn tại", null));
        }
        if (!isStrongPassword(password)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Mật khẩu phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.", null));
        }

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(passwordEncoder.encode(password));
        account.setFullname(fullname);
        account.setEmail(email);
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

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<?>> profile() {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
        }
        return ResponseEntity.ok(ApiResponse.success("Lấy hồ sơ người dùng thành công", user));
    }

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
        if (phone != null && !phone.isBlank()) {
            Optional<Account> byPhone = accountService.findByPhone(phone.trim());
            if (byPhone.isPresent() && !byPhone.get().getUsername().equals(user.getUsername())) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Số điện thoại đã tồn tại", null));
            }
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

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<?>> changePassword(@RequestParam("currentPassword") String currentPassword,
                                                          @RequestParam("newPassword") String newPassword) {
        Account user = authService.getUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Vui lòng đăng nhập", null));
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
}
