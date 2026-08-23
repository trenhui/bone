package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** ApiResponse 统一响应模型测试 */
class ApiResponseTest {

  @Test
  void success_withMessageOnly() {
    // 重载语义：success(String) 命中 success(String message)，data 为空
    ApiResponse<String> resp = ApiResponse.success("hello");
    assertThat(resp.getSuccess()).isTrue();
    assertThat(resp.getCode()).isEqualTo(200);
    assertThat(resp.getData()).isNull();
    assertThat(resp.getMessage()).isEqualTo("hello");
    assertThat(resp.getTimestamp()).isNotBlank();
  }

  @Test
  void success_withMessageAndData() {
    ApiResponse<String> resp = ApiResponse.success("自定义消息", "data");
    assertThat(resp.getMessage()).isEqualTo("自定义消息");
    assertThat(resp.getData()).isEqualTo("data");
  }

  @Test
  void success_noArgs() {
    ApiResponse<Void> resp = ApiResponse.success();
    assertThat(resp.isSuccess()).isTrue();
    assertThat(resp.getData()).isNull();
  }

  @Test
  void error_withMessageUsesServerErrorCode() {
    ApiResponse<Void> resp = ApiResponse.error("出错了");
    assertThat(resp.isError()).isTrue();
    assertThat(resp.getCode()).isEqualTo(ResultCode.SERVER_ERROR.getCode());
    assertThat(resp.getMessage()).isEqualTo("出错了");
  }

  @Test
  void error_withCustomCodeAndMessage() {
    ApiResponse<Void> resp = ApiResponse.error(1001, "参数校验失败");
    assertThat(resp.isError()).isTrue();
    assertThat(resp.getCode()).isEqualTo(1001);
    assertThat(resp.getMessage()).isEqualTo("参数校验失败");
  }

  @Test
  void error_withResultCode() {
    ApiResponse<Void> resp = ApiResponse.error(ResultCode.NOT_FOUND);
    assertThat(resp.getCode()).isEqualTo(404);
    assertThat(resp.getMessage()).isEqualTo(ResultCode.NOT_FOUND.getMessage());
  }

  @Test
  void error_withErrorData() {
    ProblemDetail detail = new ProblemDetail();
    detail.setTitle("资源不存在");
    ApiResponse<ProblemDetail> resp = ApiResponse.error(404, "找不到", detail);
    assertThat(resp.getData()).isSameAs(detail);
  }

  @Test
  void page_quickFactory() {
    ApiResponse<PageResult<String>> resp = ApiResponse.page(List.of("a", "b"), 2L, 1, 10);
    assertThat(resp.isSuccess()).isTrue();
    assertThat(resp.getData().getRecords()).containsExactly("a", "b");
    assertThat(resp.getData().getTotal()).isEqualTo(2L);
  }

  @Test
  void deprecated_fail_keepsCompatibility() {
    ApiResponse<String> resp = ApiResponse.fail(500, "兼容旧调用", "data");
    assertThat(resp.isError()).isTrue();
    assertThat(resp.getCode()).isEqualTo(500);
    assertThat(resp.getData()).isEqualTo("data");
  }
}
