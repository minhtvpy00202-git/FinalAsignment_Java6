package com.poly.ASM.service.user.impl;

import com.poly.ASM.entity.user.Account;
import com.poly.ASM.repository.user.AccountRepository;
import com.poly.ASM.service.user.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<Account> findAll() {
        return accountRepository.findByIsDeleteFalse();
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return accountRepository.findById(username);
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    @Override
    public Optional<Account> findByPhone(String phone) {
        return accountRepository.findByPhone(phone);
    }

    @Override
    public Account create(Account account) {
        if (account != null && account.getPassword() != null && !account.getPassword().isBlank()) {
            String rawPassword = account.getPassword();
            if (!(rawPassword.startsWith("$2a$") || rawPassword.startsWith("$2b$") || rawPassword.startsWith("$2y$"))) {
                account.setPassword(passwordEncoder.encode(rawPassword));
            }
        }
        return accountRepository.save(account);
    }

    @Override
    public Account update(Account account) {
        return accountRepository.save(account);
    }

    @Override
    public void deleteByUsername(String username) {
        accountRepository.findById(username).ifPresent(account -> {
            account.setIsDelete(true);
            accountRepository.save(account);
        });
    }
}
