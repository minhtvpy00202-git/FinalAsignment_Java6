package com.poly.ASM.controller.web;

import com.poly.ASM.dto.user.AccountDTO;
import com.poly.ASM.dto.user.AccountRequestDTO;
import com.poly.ASM.entity.user.Account;
import com.poly.ASM.service.user.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserRestController {

    private final AccountService accountService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public List<AccountDTO> findAll() {
        return accountService.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{username}")
    public ResponseEntity<AccountDTO> findByUsername(@PathVariable String username) {
        return accountService.findByUsername(username)
                .map(account -> ResponseEntity.ok(toDto(account)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody AccountRequestDTO request) {
        if (request.getUsername() == null || request.getUsername().isBlank()) {
            return ResponseEntity.badRequest().body("Username is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            return ResponseEntity.badRequest().body("Password is required");
        }
        if (!isStrongPassword(request.getPassword())) {
            return ResponseEntity.badRequest().body("Password must be at least 8 chars with upper/lowercase, number and special character");
        }
        if (accountService.findByUsername(request.getUsername()).isPresent()) {
            return ResponseEntity.status(409).body("Username already exists");
        }

        Account account = new Account();
        applyRequest(account, request, true);
        Account saved = accountService.create(account);
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{username}")
    public ResponseEntity<?> update(@PathVariable String username, @RequestBody AccountRequestDTO request) {
        Optional<Account> existing = accountService.findByUsername(username);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (request.getPassword() != null && !request.getPassword().isBlank() && !isStrongPassword(request.getPassword())) {
            return ResponseEntity.badRequest().body("Password must be at least 8 chars with upper/lowercase, number and special character");
        }

        Account account = existing.get();
        applyRequest(account, request, false);
        Account saved = accountService.update(account);
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> delete(@PathVariable String username) {
        accountService.deleteByUsername(username);
        return ResponseEntity.noContent().build();
    }

    private AccountDTO toDto(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setUsername(account.getUsername());
        dto.setFullname(account.getFullname());
        dto.setEmail(account.getEmail());
        dto.setPhone(account.getPhone());
        dto.setAddress(account.getAddress());
        dto.setPhoto(account.getPhoto());
        dto.setActivated(account.getActivated());
        return dto;
    }

    private void applyRequest(Account account, AccountRequestDTO request, boolean isCreate) {
        if (isCreate) {
            account.setUsername(request.getUsername());
            account.setPassword(passwordEncoder.encode(request.getPassword()));
        } else if (request.getPassword() != null && !request.getPassword().isBlank()) {
            account.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getFullname() != null) {
            account.setFullname(request.getFullname());
        }
        if (request.getEmail() != null) {
            account.setEmail(request.getEmail());
        }
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            account.setPhone(request.getPhone().trim());
        } else if (isCreate) {
            account.setPhone("0000000000");
        }
        if (request.getAddress() != null && !request.getAddress().isBlank()) {
            account.setAddress(request.getAddress().trim());
        } else if (isCreate) {
            account.setAddress("Chưa cập nhật");
        }
        if (request.getPhoto() != null) {
            account.setPhoto(request.getPhoto());
        }
        if (request.getActivated() != null) {
            account.setActivated(request.getActivated());
        }
    }

    private boolean isStrongPassword(String password) {
        if (password == null) {
            return false;
        }
        return password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    }
}
