package com.bone.iam.application.support;

import com.bone.iam.common.IamErrorCodes;
import com.bone.iam.common.IamErrors;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 密码强度校验（NIST 800-63B 方向：长度 + 常见弱口令拒绝）。
 *
 * <p>/*
 *
 * <p>应用层密码策略服务，置于 {@code application.policy} 语义化子包，供 CommandHandler / *ApplicationService 调用。 原
 * {@code application.service} 协作子包已随 ADR-0033 撤销废止，相关协作构件归位于 *ApplicationService 或 policy/binding
 * 等语义化子包。
 */
@Service
public class PasswordPolicyValidator {

  private static final int MIN_LENGTH = 8;
  private static final Set<String> WEAK_PASSWORDS =
      Set.of("123456", "12345678", "password", "admin", "bone123", "qwerty", "111111", "000000");

  /** 创建/重置密码时强制校验；不通过则抛出业务异常。 */
  public void assertAcceptable(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      throw IamErrors.of(IamErrorCodes.WEAK_PASSWORD, "密码长度至少 " + MIN_LENGTH + " 位");
    }
    if (isWeak(rawPassword)) {
      throw IamErrors.of(IamErrorCodes.WEAK_PASSWORD, "密码过于简单，请使用更复杂的密码");
    }
  }

  /** 登录后判断是否需要强制改密（默认/弱口令）。 */
  public boolean requiresPasswordChange(String rawPassword) {
    return isWeak(rawPassword);
  }

  public boolean isWeak(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      return true;
    }
    String normalized = rawPassword.trim().toLowerCase(Locale.ROOT);
    return normalized.length() < MIN_LENGTH || WEAK_PASSWORDS.contains(normalized);
  }
}
