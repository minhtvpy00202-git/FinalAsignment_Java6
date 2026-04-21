package com.poly.ASM.controller.admin;

import com.poly.ASM.dto.common.ApiResponse;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.entity.user.Authority;
import com.poly.ASM.entity.user.Role;
import com.poly.ASM.exception.ApiException;
import com.poly.ASM.service.user.AccountService;
import com.poly.ASM.service.user.AuthorityService;
import com.poly.ASM.service.user.RoleService;
import com.poly.ASM.service.auth.AuthService;
import com.poly.ASM.service.auth.AuthProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
public class AccountAController {

    private final AccountService accountService;
    private final RoleService roleService;
    private final AuthorityService authorityService;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuthProviderService authProviderService;

    /**
     * Trả dữ liệu quản trị account + danh sách role cho màn admin/account.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> index() {
        List<Map<String, Object>> accounts = accountService.findAll().stream()
                .map(this::toAccountView)
                .toList();
        List<Map<String, Object>> roles = roleService.findAll().stream()
                .map(this::toRoleView)
                .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("accounts", accounts);
        data.put("roles", roles);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách tài khoản thành công", data));
    }

    /**
     * Tạo tài khoản thủ công từ admin.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@RequestParam("username") String username,
                                                  @RequestParam("password") String password,
                                                  @RequestParam("fullname") String fullname,
                                                  @RequestParam("email") String email,
                                                  @RequestParam("phone") String phone,
                                                  @RequestParam("address") String address,
                                                  @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                                  @RequestParam(value = "activated", required = false) Boolean activated,
                                                  @RequestParam("roleId") String roleId) {
        String normalizedUsername = username == null ? "" : username.trim();
        String normalizedPassword = password == null ? "" : password.trim();
        String normalizedEmail = email == null ? "" : email.trim();
        validatePhoneAndAddress(phone, address);
        if (!isStrongPassword(normalizedPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Mật khẩu phải có tối thiểu 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt");
        }
        Account account = new Account();
        account.setUsername(normalizedUsername);
        account.setPassword(passwordEncoder.encode(normalizedPassword));
        account.setFullname(fullname);
        account.setEmail(normalizedEmail);
        account.setPhone(phone.trim());
        account.setAddress(address.trim());
        String photoName = saveImage(photoFile);
        if (photoName != null) {
            account.setPhoto(photoName);
        }
        account.setActivated(activated != null ? activated : true);
        Account saved = accountService.create(account);

        Role role = roleService.findById(roleId)
                .orElseGet(() -> roleService.create(new Role(roleId, roleId, null)));
        Authority authority = new Authority();
        authority.setAccount(saved);
        authority.setRole(role);
        authorityService.create(authority);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Tạo tài khoản thành công", null));
    }

    /**
     * Lấy chi tiết account phục vụ chỉnh sửa.
     */
    @GetMapping("/{username}")
    public ResponseEntity<ApiResponse<?>> edit(@PathVariable("username") String username) {
        Optional<Account> account = accountService.findByUsername(username);
        if (account.isEmpty() || Boolean.TRUE.equals(account.get().getIsDelete())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }

        String roleId = resolveRoleId(username);
        Map<String, Object> data = new HashMap<>();
        data.put("account", toAccountView(account.get()));
        data.put("roles", roleService.findAll().stream().map(this::toRoleView).toList());
        data.put("roleId", roleId);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết tài khoản thành công", data));
    }

    /**
     * Cập nhật hồ sơ account từ admin (không cho đổi password qua API này).
     */
    @PutMapping("/{username}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable("username") String username,
                                                  @RequestParam(value = "password", required = false) String password,
                                                  @RequestParam("fullname") String fullname,
                                                  @RequestParam("email") String email,
                                                  @RequestParam("phone") String phone,
                                                  @RequestParam("address") String address,
                                                  @RequestParam(value = "photoFile", required = false) MultipartFile photoFile,
                                                  @RequestParam(value = "activated", required = false) Boolean activated,
                                                  @RequestParam("roleId") String roleId) {
        Account account = accountService.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (Boolean.TRUE.equals(account.getIsDelete())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        validatePhoneAndAddress(phone, address);
        if (password != null && !password.isBlank()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin không được phép đổi mật khẩu của khách hàng qua API này");
        }
        account.setFullname(fullname);
        account.setEmail(email);
        account.setPhone(phone.trim());
        account.setAddress(address.trim());
        String photoName = saveImage(photoFile);
        if (photoName != null) {
            account.setPhoto(photoName);
        }
        account.setActivated(activated != null ? activated : true);
        Account saved = accountService.update(account);

        authorityService.deleteByAccountUsername(username);
        Role role = roleService.findById(roleId)
                .orElseGet(() -> roleService.create(new Role(roleId, roleId, null)));
        Authority authority = new Authority();
        authority.setAccount(saved);
        authority.setRole(role);
        authorityService.create(authority);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật tài khoản thành công", null));
    }

    /**
     * Soft delete account.
     */
    @DeleteMapping("/{username}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable("username") String username) {
        Account currentUser = authService.getUser();
        if (currentUser != null && currentUser.getUsername().equals(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể xóa tài khoản đang đăng nhập");
        }

        Account account = accountService.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (Boolean.TRUE.equals(account.getIsDelete())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        accountService.deleteByUsername(username);
        return ResponseEntity.ok(ApiResponse.success("Xóa tài khoản thành công", null));
    }

    /**
     * Cập nhật role tài khoản.
     */
    @PutMapping("/{username}/role")
    public ResponseEntity<ApiResponse<?>> updateRole(@PathVariable("username") String username,
                                                     @RequestParam("roleId") String roleId) {
        Account account = accountService.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (Boolean.TRUE.equals(account.getIsDelete())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        String normalizedRoleId = roleId == null ? "" : roleId.trim().toUpperCase();
        if (normalizedRoleId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Vai trò không hợp lệ");
        }
        authorityService.deleteByAccountUsername(username);
        Role role = roleService.findById(normalizedRoleId)
                .orElseGet(() -> roleService.create(new Role(normalizedRoleId, normalizedRoleId, null)));
        Authority authority = new Authority();
        authority.setAccount(account);
        authority.setRole(role);
        authorityService.create(authority);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò thành công", Map.of("username", username, "roleId", normalizedRoleId)));
    }

    /**
     * Khóa/mở khóa tài khoản.
     */
    @PutMapping("/{username}/activation")
    public ResponseEntity<ApiResponse<?>> updateActivation(@PathVariable("username") String username,
                                                           @RequestParam("activated") Boolean activated) {
        Account currentUser = authService.getUser();
        if (currentUser != null && currentUser.getUsername().equals(username)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Không thể tự khoá tài khoản đang đăng nhập");
        }
        Account account = accountService.findByUsername(username)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        if (Boolean.TRUE.equals(account.getIsDelete())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản");
        }
        account.setActivated(activated != null ? activated : true);
        accountService.update(account);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái tài khoản thành công", Map.of("username", username, "activated", account.getActivated())));
    }

    private String saveImage(MultipartFile file) {
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

    private Map<String, Object> toAccountView(Account account) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", account.getUsername());
        map.put("fullname", account.getFullname());
        map.put("email", account.getEmail());
        map.put("phone", account.getPhone());
        map.put("address", account.getAddress());
        map.put("photo", account.getPhoto());
        map.put("activated", account.getActivated());
        map.put("roleId", resolveRoleId(account.getUsername()));
        map.put("accountType", resolveAccountType(account));
        return map;
    }

    private String resolveAccountType(Account account) {
        return authProviderService.isGoogleAccount(account) ? "GOOGLE" : "NORMAL";
    }

    private String resolveRoleId(String username) {
        List<Authority> authorities = authorityService.findByAccountUsername(username);
        if (!authorities.isEmpty() && authorities.get(0).getRole() != null) {
            return authorities.get(0).getRole().getId();
        }
        return "USER";
    }

    private Map<String, Object> toRoleView(Role role) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getName());
        return map;
    }

    private void validatePhoneAndAddress(String phone, String address) {
        String normalizedPhone = phone == null ? "" : phone.trim();
        String normalizedAddress = address == null ? "" : address.trim();
        if (normalizedPhone.isBlank() || !normalizedPhone.matches("^(0|\\+84)\\d{9,10}$")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ");
        }
        if (normalizedAddress.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Địa chỉ là bắt buộc");
        }
    }

    private boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }
}
