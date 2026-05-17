package com.bone.iam.domain.service;

import com.bone.iam.domain.account.Account;
import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.repository.AccountRepository;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SsoClient ssoClient;

    public Account authenticate(String username, String password) {
        Account account;
        try {
            account = accountRepository.findOneByCriteria(
                    Criteria.<Account>create().eq("username", username));
        } catch (MultipleResultsException e) {
            return null;
        }
        if (account == null) {
            return null;
        }
        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            return null;
        }
        return account;
    }

    public boolean ssoAuthenticate(String username, String password) {
        return ssoClient.authenticate(username, password);
    }
}
