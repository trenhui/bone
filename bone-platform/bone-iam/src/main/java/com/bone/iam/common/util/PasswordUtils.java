package com.bone.iam.common.util;

import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordUtils {
  public static String encodePassword(String password, PasswordEncoder passwordEncoder) {
    return passwordEncoder.encode(password);
  }

  public static boolean matches(
      String rawPassword, String encodedPassword, PasswordEncoder passwordEncoder) {
    return passwordEncoder.matches(rawPassword, encodedPassword);
  }

  public static boolean isPasswordStrong(String password) {
    // 密码强度检查：至少8位，包含大小写字母、数字和特殊字符
    return password.length() >= 8
        && password.matches(".*[A-Z].*")
        && password.matches(".*[a-z].*")
        && password.matches(".*\\d.*")
        && password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
  }
}
