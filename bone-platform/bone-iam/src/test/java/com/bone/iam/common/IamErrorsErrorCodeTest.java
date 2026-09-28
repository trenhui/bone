package com.bone.iam.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 锁定 C-1 修复：{@link IamErrors#of(String, Object, Throwable)} 必须经由 4 参构造器把业务码写入 {@code
 * BizException.errorCode}， 否则响应层 {@code ProblemDetail.errorCode} 恒为 null，前端 i18n / 监控按码聚合全部失效。
 */
class IamErrorsErrorCodeTest {

  @Test
  void ofCarriesErrorCodeToBizException() {
    assertThat(IamErrors.of(IamErrorCodes.LOGIN_FAILED).getErrorCode())
        .isEqualTo(IamErrorCodes.LOGIN_FAILED);
  }

  @Test
  void ofWithDetailAndCauseStillCarriesErrorCode() {
    RuntimeException cause = new RuntimeException("boom");
    assertThat(IamErrors.of(IamErrorCodes.ACCOUNT_NOT_FOUND, "id=1", cause).getErrorCode())
        .isEqualTo(IamErrorCodes.ACCOUNT_NOT_FOUND);
  }
}
