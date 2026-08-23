package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ResultCode 响应状态码枚举测试 */
class ResultCodeTest {

  @Test
  void successCodeIs200() {
    assertThat(ResultCode.SUCCESS.getCode()).isEqualTo(200);
  }

  @Test
  void httpStatusCodesAlignWithSpec() {
    assertThat(ResultCode.BAD_REQUEST.getCode()).isEqualTo(400);
    assertThat(ResultCode.UNAUTHORIZED.getCode()).isEqualTo(401);
    assertThat(ResultCode.FORBIDDEN.getCode()).isEqualTo(403);
    assertThat(ResultCode.NOT_FOUND.getCode()).isEqualTo(404);
    assertThat(ResultCode.SERVER_ERROR.getCode()).isEqualTo(500);
  }

  @Test
  void businessCodesStartFrom1000() {
    assertThat(ResultCode.BUSINESS_ERROR.getCode()).isEqualTo(1000);
    assertThat(ResultCode.VALIDATION_ERROR.getCode()).isEqualTo(1001);
    assertThat(ResultCode.DATA_ACCESS_ERROR.getCode()).isEqualTo(1002);
  }

  @Test
  void getValue_knownCode() {
    assertThat(ResultCode.getValue(404)).isEqualTo(ResultCode.NOT_FOUND);
    assertThat(ResultCode.getValue(200)).isEqualTo(ResultCode.SUCCESS);
  }

  @Test
  void getValue_unknownCodeReturnsNull() {
    assertThat(ResultCode.getValue(9999)).isNull();
  }

  @Test
  void allMessagesAreNonBlank() {
    for (ResultCode code : ResultCode.values()) {
      assertThat(code.getMessage()).isNotBlank();
    }
  }
}
