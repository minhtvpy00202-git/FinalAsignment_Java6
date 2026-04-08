package com.poly.ASM.repository.user;

import com.poly.ASM.entity.user.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByPhone(String phone);

    List<Account> findByIsDeleteFalse();

    Optional<Account> findByUsernameAndIsDeleteFalse(String username);
}
