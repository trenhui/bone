package com.bone.metadata.auth.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(4);
    }

    public static void main(String[] args) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        String str = passwordEncoder.encode("123456");
        System.out.println(str);

        System.out.println(passwordEncoder.matches("123456","$2a$04$FS5nzU0nnQegppUUu7tHouHqXEkNzGB0vYCjZn1y8PcnR0q7uzzfC"));
    }

}
