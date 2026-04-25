package com.bone.iam.domain.service;

import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SsoClient ssoClient;

    public Account authenticate(String username, String password) {
        Optional<Account> accountOptional = accountRepository.findByUsername(username);
        if (accountOptional.isEmpty()) {
            return null;
        }
        Account account = accountOptional.get();
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            return null;
        }
        return account;
    }

    public boolean ssoAuthenticate(String username, String password) {
        return ssoClient.authenticate(username, password);
    }
}
