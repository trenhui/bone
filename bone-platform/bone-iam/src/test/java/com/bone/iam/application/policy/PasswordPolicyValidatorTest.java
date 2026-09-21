package com.bone.iam.application.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

  private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

  @Test
  void rejectsWeakPasswordOnAssert() {
    assertThrows(BizException.class, () -> validator.assertAcceptable("123456"));
  }

  @Test
  void acceptsStrongPassword() {
    validator.assertAcceptable("Bone@2026Secure");
    assertFalse(validator.isWeak("Bone@2026Secure"));
  }

  @Test
  void requiresChangeForDefaultPassword() {
    assertTrue(validator.requiresPasswordChange("123456"));
  }
}
