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

  /**
   * 重载歧义固化：{@code success("x")} 命中 {@code success(String message)}（data 为空）， 只有非 String 实参才会走
   * {@code success(T data)}。误用会导致「数据被当成消息」的静默错误。
   */
  @Test
  void success_withNonStringArgumentHitsDataOverload() {
    ApiResponse<Integer> resp = ApiResponse.success(42);

    assertThat(resp.getData()).isEqualTo(42);
    assertThat(resp.getMessage()).isEqualTo(ResultCode.SUCCESS.getMessage());
    assertThat(resp.getSuccess()).isTrue();
  }

  /** {@code error(int, String)} 与 {@code error(Integer, String)} 并存：装箱实参才命中后者。 */
  @Test
  void error_withBoxedIntegerHitsIntegerOverload() {
    ApiResponse<Void> resp = ApiResponse.error(Integer.valueOf(1001), "装箱码");

    assertThat(resp.getCode()).isEqualTo(1001);
    assertThat(resp.getMessage()).isEqualTo("装箱码");
    assertThat(resp.isError()).isTrue();
  }

  @Test
  void error_withMessageAndErrorData() {
    ApiResponse<ProblemDetail> resp = ApiResponse.error("失败", ProblemDetail.of("E1", 400, "bad"));

    assertThat(resp.isError()).isTrue();
    assertThat(resp.getCode()).isEqualTo(ResultCode.SERVER_ERROR.getCode());
    assertThat(resp.getData().getErrorCode()).isEqualTo("E1");
  }

  /** ResultCode 重载以显式 message 为准，不回落枚举默认消息。 */
  @Test
  void error_withResultCodeMessageAndDataOverridesDefaultMessage() {
    ApiResponse<ProblemDetail> resp =
        ApiResponse.error(ResultCode.NOT_FOUND, "覆盖默认消息", ProblemDetail.of("E2", 404, "missing"));

    assertThat(resp.getCode()).isEqualTo(404);
    assertThat(resp.getMessage()).isEqualTo("覆盖默认消息");
    assertThat(resp.getData().getErrorCode()).isEqualTo("E2");
  }

  @Test
  void page_wrapsExistingPageResult() {
    PageResult<String> pageResult = PageResult.of(List.of("a"), 10L, 1, 5);

    ApiResponse<PageResult<String>> resp = ApiResponse.page(pageResult);

    assertThat(resp.isSuccess()).isTrue();
    assertThat(resp.getData()).isSameAs(pageResult);
  }

  /** success 为 null（反序列化不完整等）时按失败处理，避免把未知状态误判为成功。 */
  @Test
  void nullSuccessTreatedAsError() {
    ApiResponse<Void> resp = new ApiResponse<>();

    assertThat(resp.getSuccess()).isNull();
    assertThat(resp.isSuccess()).isFalse();
    assertThat(resp.isError()).isTrue();
  }

  @Test
  void equalityAndToString() {
    ApiResponse<String> a = ApiResponse.success("m", "d");
    a.setTimestamp("fixed");
    ApiResponse<String> b = ApiResponse.success("m", "d");
    b.setTimestamp("fixed");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(ApiResponse.error("m"));
    assertThat(a.toString()).contains("d");
  }
}
