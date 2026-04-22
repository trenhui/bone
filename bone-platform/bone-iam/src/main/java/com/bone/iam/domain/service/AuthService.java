package com.bone.iam.domain.service;

import com.bone.iam.domain.client.SsoClient;
import com.bone.iam.domain.model.user.User;
import com.bone.iam.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SsoClient ssoClient;

    public User authenticate(String username, String password) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return null;
        }
        User user = userOptional.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return null;
        }
        return user;
    }

    public boolean ssoAuthenticate(String username, String password) {
        return ssoClient.authenticate(username, password);
    }
}