package com.bone.iam.application.service;

import com.bone.core.exception.BizException;
import com.bone.iam.common.IamErrorCodes;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 密码强度校验（NIST 800-63B 方向：长度 + 常见弱口令拒绝）。
 *
 * <p>注：已从 {@code domain.service} 迁移至 {@code application.service}，因该类作为应用层策略服务供 CommandHandler 调用。
 */
@Service
public class PasswordPolicyValidator {

  private static final int MIN_LENGTH = 8;
  private static final Set<String> WEAK_PASSWORDS =
      Set.of("123456", "12345678", "password", "admin", "bone123", "qwerty", "111111", "000000");

  /** 创建/重置密码时强制校验；不通过则抛出业务异常。 */
  public void assertAcceptable(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      throw BizException.of(400, IamErrorCodes.WEAK_PASSWORD + ": 密码长度至少 " + MIN_LENGTH + " 位");
    }
    if (isWeak(rawPassword)) {
      throw BizException.of(400, IamErrorCodes.WEAK_PASSWORD + ": 密码过于简单，请使用更复杂的密码");
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
