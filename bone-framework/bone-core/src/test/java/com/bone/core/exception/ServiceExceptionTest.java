package com.bone.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.core.enums.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * {@link ServiceException} 与 {@link ErrorCode} 测试。
 *
 * <p>ServiceException 覆写了 {@code getMessage()}（取 {@code message} 字段而非 Throwable 构造入参），
 * 全局异常处理器据此回写响应体，语义一旦变化会导致错误信息丢失，故固化。
 */
class ServiceExceptionTest {

  @Test
  void constructsFromErrorCode() {
    ErrorCode code = new ErrorCode(1001, "业务校验失败");

    ServiceException ex = new ServiceException(code);

    assertThat(ex.getCode()).isEqualTo(1001);
    assertThat(ex.getMessage()).isEqualTo("业务校验失败");
  }

  @Test
  void constructsFromCodeAndMessage() {
    ServiceException ex = new ServiceException(500, "服务异常");

    assertThat(ex.getCode()).isEqualTo(500);
    assertThat(ex.getMessage()).isEqualTo("服务异常");
  }

  /** 无参构造供 JSON / RPC 反序列化使用，字段默认为 null。 */
  @Test
  void noArgConstructorLeavesFieldsNull() {
    ServiceException ex = new ServiceException();

    assertThat(ex.getCode()).isNull();
    assertThat(ex.getMessage()).isNull();
  }

  /**
   * setter 由 Lombok {@code @Data} 生成，返回 {@code void}，不支持链式。
   *
   * <p>本项目未启用 {@code @Accessors(chain = true)}，也没有 {@code lombok.config} 的 {@code
   * lombok.accessors.chain} 配置，全仓无链式 setter 用法；反序列化后补全字段须逐字段赋值。 若将来确需链式，应统一通过 {@code lombok.config}
   * 开启，而非在单个类上加 {@code @Accessors} 造成风格不一致。
   */
  @Test
  void settersAssignFields() {
    ServiceException ex = new ServiceException();
    ex.setCode(404);
    ex.setMessage("未找到");

    assertThat(ex.getCode()).isEqualTo(404);
    assertThat(ex.getMessage()).isEqualTo("未找到");
  }

  @Test
  void messageFieldOverridesThrowableMessage() {
    ServiceException ex = new ServiceException(400, "自定义消息");

    assertThat(ex).isInstanceOf(RuntimeException.class).hasMessage("自定义消息");
  }

  @Test
  void errorCodeEqualityAndToString() {
    ErrorCode a = new ErrorCode(1001, "x");
    ErrorCode b = new ErrorCode(1001, "x");
    ErrorCode c = new ErrorCode(1002, "x");

    assertThat(a.getCode()).isEqualTo(1001);
    assertThat(a.getMsg()).isEqualTo("x");
    assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    assertThat(a).isNotEqualTo(c);
    assertThat(a.toString()).contains("1001");
  }

  /**
   * 固化既有陷阱：本类标注 {@code @EqualsAndHashCode(callSuper = true)}，而 {@code RuntimeException} 未覆写 {@code
   * equals}（沿用 Object 身份比较），因此两个字段完全相同的 ServiceException 也<b>不相等</b>。
   *
   * <p>影响：不能用 {@code assertEquals(expectedEx, actualEx)} 断言业务异常，须逐个断言 {@code getCode()} 与 {@code
   * getMessage()}。若确实需要按值比较，应移除 {@code callSuper = true}。
   */
  @Test
  void serviceExceptionEqualityDegradesToIdentity() {
    ServiceException a = new ServiceException(500, "e");
    ServiceException b = new ServiceException(500, "e");

    assertThat(a).isEqualTo(a);
    assertThat(a).isNotEqualTo(b);
    assertThat(a).isNotEqualTo(new ServiceException(501, "e"));
  }
}
