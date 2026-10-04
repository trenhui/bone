package com.bone.platform.alert.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import org.junit.jupiter.api.Test;

/** 错误码 → HTTP 状态唯一真源：漏登记即类加载失败，此处验证配对与未知码拒绝。 */
class NotificationErrorsMappingTest {

  @Test
  void everyKnownCodeMapsToRegisteredStatus() {
    assertThat(NotificationErrors.httpStatusOf(NotificationErrorCodes.NOT_FOUND)).isEqualTo(404);
    assertThat(NotificationErrors.httpStatusOf(NotificationErrorCodes.ACCESS_DENIED))
        .isEqualTo(403);
    assertThat(NotificationErrors.httpStatusOf(NotificationErrorCodes.INVALID_PARAM))
        .isEqualTo(400);
    assertThat(NotificationErrors.httpStatusOf(NotificationErrorCodes.TENANT_MISMATCH))
        .isEqualTo(400);
    // ACCOUNT_REQUIRED=「根本没有身份」→ 401；ACCESS_DENIED=「身份有效但不属这条消息」→ 403。
    // 两者语义不同，若误配成 403，客户端会当成已登录无权限而错误地引导重新登录。
    assertThat(NotificationErrors.httpStatusOf(NotificationErrorCodes.ACCOUNT_REQUIRED))
        .isEqualTo(401);
  }

  @Test
  void unknownCodeIsRejectedNotSilentlyDowngraded() {
    assertThatThrownBy(() -> NotificationErrors.httpStatusOf("NOTIFICATION_NOT_A_REAL_CODE"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未登记");
  }

  @Test
  void ofCarriesErrorCodeAndDetailInMessage() {
    BizException e = NotificationErrors.of(NotificationErrorCodes.NOT_FOUND, 1L);
    assertThat(e.getErrorCode()).isEqualTo(NotificationErrorCodes.NOT_FOUND);
    assertThat(e.getMessage()).contains("NOTIFICATION_NOT_FOUND").contains("1");
  }
}
