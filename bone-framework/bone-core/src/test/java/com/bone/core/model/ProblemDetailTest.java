package com.bone.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link ProblemDetail} 测试：RFC 7807 子集契约。
 *
 * <p>该类承载全局异常处理的失败详情，是 API 对外契约的一部分（字段名/默认值不可随意变更），故固化其行为。
 */
class ProblemDetailTest {

  @Test
  void of_populatesContractDefaults() {
    ProblemDetail p = ProblemDetail.of("BIZ-001", 400, "参数不合法");

    assertThat(p.getErrorCode()).isEqualTo("BIZ-001");
    assertThat(p.getStatus()).isEqualTo(400);
    assertThat(p.getDetail()).isEqualTo("参数不合法");
    // 约定：未显式设置 title 时回落为 errorCode，type 固定 about:blank（RFC 7807 默认）
    assertThat(p.getTitle()).isEqualTo("BIZ-001");
    assertThat(p.getType()).isEqualTo("about:blank");
  }

  /** errors 必须默认可写（非 null），否则全局异常处理器 addError 会 NPE。 */
  @Test
  void errorsListIsMutableByDefault() {
    ProblemDetail p = new ProblemDetail();

    assertThat(p.getErrors()).isNotNull().isEmpty();

    p.getErrors().add(fieldError("name", "不能为空", ""));

    assertThat(p.getErrors()).hasSize(1);
  }

  @Test
  void allFieldsRoundTrip() {
    ProblemDetail p = new ProblemDetail();
    p.setType("https://docs.bone/errors/BIZ-001");
    p.setTitle("业务校验失败");
    p.setStatus(422);
    p.setDetail("字段校验未通过");
    p.setInstance("/api/v1/orders");
    p.setErrorCode("BIZ-001");
    p.setTraceId("trace-abc");

    assertThat(p.getType()).isEqualTo("https://docs.bone/errors/BIZ-001");
    assertThat(p.getTitle()).isEqualTo("业务校验失败");
    assertThat(p.getStatus()).isEqualTo(422);
    assertThat(p.getDetail()).isEqualTo("字段校验未通过");
    assertThat(p.getInstance()).isEqualTo("/api/v1/orders");
    assertThat(p.getErrorCode()).isEqualTo("BIZ-001");
    assertThat(p.getTraceId()).isEqualTo("trace-abc");
  }

  /** equals/hashCode 为 Lombok 生成，基于全部字段——用于断言去重与集合比较。 */
  @Test
  void equalityIsValueBased() {
    ProblemDetail a = ProblemDetail.of("BIZ-001", 400, "bad");
    ProblemDetail b = ProblemDetail.of("BIZ-001", 400, "bad");
    ProblemDetail c = ProblemDetail.of("BIZ-002", 400, "bad");

    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a).isEqualTo(a);
    assertThat(a).isNotEqualTo("not a problem detail");
  }

  @Test
  void toStringContainsKeyFields() {
    String text = ProblemDetail.of("BIZ-001", 400, "bad").toString();

    assertThat(text).contains("BIZ-001").contains("400").contains("bad");
  }

  @Test
  void fieldErrorAccessorsAndEquality() {
    ProblemDetail.FieldError e1 = fieldError("age", "必须大于0", -1);
    ProblemDetail.FieldError e2 = fieldError("age", "必须大于0", -1);
    ProblemDetail.FieldError e3 = fieldError("age", "必须大于0", -2);

    assertThat(e1.getField()).isEqualTo("age");
    assertThat(e1.getMessage()).isEqualTo("必须大于0");
    assertThat(e1.getRejectedValue()).isEqualTo(-1);

    assertThat(e1).isEqualTo(e2).hasSameHashCodeAs(e2);
    assertThat(e1).isNotEqualTo(e3);
    assertThat(e1.toString()).contains("age");
  }

  /** 无参构造 + setter 组装，供需要精确控制字段的场景。 */
  @Test
  void setErrorsReplacesWholeList() {
    ProblemDetail p = ProblemDetail.of("BIZ-001", 400, "bad");
    p.setErrors(List.of(fieldError("a", "msg-a", null), fieldError("b", "msg-b", null)));

    assertThat(p.getErrors()).hasSize(2);
    assertThat(p.getErrors().get(0).getField()).isEqualTo("a");
  }

  private static ProblemDetail.FieldError fieldError(
      String field, String message, Object rejectedValue) {
    ProblemDetail.FieldError e = new ProblemDetail.FieldError();
    e.setField(field);
    e.setMessage(message);
    e.setRejectedValue(rejectedValue);
    return e;
  }
}
