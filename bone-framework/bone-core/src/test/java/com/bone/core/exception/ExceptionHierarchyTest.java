package com.bone.core.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** 异常体系测试：BizException 工厂方法、SystemException 继承链 */
class ExceptionHierarchyTest {

  @Test
  void bizException_defaultCodeIs500() {
    BizException ex = BizException.of("库存不足");
    assertThat(ex.getCode()).isEqualTo(500);
    assertThat(ex.getMessage()).isEqualTo("库存不足");
  }

  @Test
  void bizException_customCodeAndCause() {
    IllegalStateException cause = new IllegalStateException("底层原因");
    BizException ex = BizException.of(409, "状态冲突", cause);
    assertThat(ex.getCode()).isEqualTo(409);
    assertThat(ex.getCause()).isSameAs(cause);
  }

  @Test
  void bizException_constructorWithMessage() {
    BizException ex = new BizException("直接构造");
    assertThat(ex.getCode()).isEqualTo(BizException.DEFAULT_ERROR_CODE);
  }

  @Test
  void bizException_isRuntimeException() {
    assertThat(BizException.class.getSuperclass()).isEqualTo(RuntimeException.class);
  }

  @Test
  void systemException_inheritsInfrastructureChain() {
    SystemException ex = SystemException.of("数据库连接失败");
    assertThat(ex).isInstanceOf(InfrastructureException.class);
    assertThat(ex).isInstanceOf(RuntimeException.class);
    assertThat(ex.getMessage()).isEqualTo("数据库连接失败");
  }

  @Test
  void domainException_isRuntimeException() {
    assertThat(DomainException.class.getSuperclass()).isEqualTo(RuntimeException.class);
  }

  @Test
  void exceptionsAreThrowableAndCatchable() {
    assertThatThrownBy(
            () -> {
              throw BizException.of("业务失败");
            })
        .isInstanceOf(BizException.class)
        .hasMessage("业务失败");

    assertThatThrownBy(
            () -> {
              throw SystemException.of("系统失败");
            })
        .isInstanceOf(SystemException.class)
        .hasMessage("系统失败");
  }
}
