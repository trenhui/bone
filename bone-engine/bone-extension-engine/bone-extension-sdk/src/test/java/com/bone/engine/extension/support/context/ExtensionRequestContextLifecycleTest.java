package com.bone.engine.extension.support.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExtensionRequestContextLifecycleTest {

  private ExtensionRequestContextLifecycle lifecycle;

  @BeforeEach
  void setUp() {
    lifecycle = new ExtensionRequestContextLifecycle();
    ExtensionContextManager.clearContext();
  }

  @AfterEach
  void tearDown() {
    lifecycle.clear();
    ExtensionContextManager.clearContext();
  }

  @Test
  void testInitializeAndClearLifecycle() {
    assertThat(ExtensionContextManager.getCurrentContext()).isNull();

    lifecycle.initialize("tenant-test", "BIZ-001");

    BizContext<?> context = ExtensionContextManager.getCurrentContext();
    assertThat(context).isNotNull();
    assertThat(context.getTenant()).isEqualTo("tenant-test");
    assertThat(context.getBizCode()).isEqualTo("BIZ-001");

    lifecycle.clear();
    assertThat(ExtensionContextManager.getCurrentContext()).isNull();
  }

  @Test
  void testClearIsIdempotent() {
    lifecycle.clear();
    lifecycle.clear();
    assertThat(ExtensionContextManager.getCurrentContext()).isNull();
  }

  @Test
  void testInitializeThrowsOnNullContext() {
    assertThatThrownBy(() -> lifecycle.initialize(null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ExtensionContextManager.hasContext()).isFalse();
  }

  /**
   * 判别用例：验证 clear() 走的是 Scope.close()（恢复旧上下文）而非 clearContext()（直接丢弃）。若实现退化为丢弃 Scope 再单独
   * clear，本用例会因旧上下文未被恢复而失败。
   */
  @Test
  void testClearRestoresOuterContextViaScopeClose() {
    BizContext<?> outer =
        BizContext.builder().tenant("outer-tenant").bizCode("OUTER").scenario("GENERAL").build();
    // preHandle 未预清理时，外层上下文先存在；with() 会保存它作为 oldContext
    ExtensionContextManager.setCurrentContext(outer);

    lifecycle.initialize("inner-tenant", "INNER");
    assertThat(ExtensionContextManager.getCurrentContext().getTenant()).isEqualTo("inner-tenant");

    lifecycle.clear();
    assertThat(ExtensionContextManager.getCurrentContext()).isSameAs(outer);
  }

  /** 非法入参必须 fail-fast，且不得创建任何半初始化上下文，避免污染后续请求。 */
  @Test
  void testInitializeRejectsBlankTenantWithoutLeakingContext() {
    assertThatThrownBy(() -> lifecycle.initialize("  ", "BIZ"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(ExtensionContextManager.hasContext()).isFalse();
  }

  /** 连续 initialize 时先关闭旧 Scope，确保不串用、ThreadLocal 不残留。 */
  @Test
  void testReinitializeReplacesContext() {
    lifecycle.initialize("tenant-1", "BIZ-1");
    lifecycle.initialize("tenant-2", "BIZ-2");
    assertThat(ExtensionContextManager.getCurrentContext().getTenant()).isEqualTo("tenant-2");

    lifecycle.clear();
    assertThat(ExtensionContextManager.hasContext()).isFalse();
  }
}
