package com.bone.core.tenant.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** {@link TenantContextRunner} 契约测试：异步入口声明租户，且执行结束后不得残留上下文（线程池复用不得串租户）。 */
class TenantContextRunnerTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void runAs_exposesTenantInside_andRestoresNullAfterwards() {
    assertThat(TenantContext.getTenantId()).isNull();

    TenantContextRunner.runAs(42L, () -> assertThat(TenantContext.getTenantId()).isEqualTo("42"));

    // 离开作用域必须还原（异步线程原为 null ⇒ 等价于清空），否则线程池复用会串租户
    assertThat(TenantContext.getTenantId()).isNull();
  }

  @Test
  void callAs_returnsValue_andRestoresPreviousContext() {
    TenantContext.setTenantId(7L);

    String inside = TenantContextRunner.callAs(42L, TenantContext::getTenantId);

    assertThat(inside).isEqualTo("42");
    assertThat(TenantContext.getTenantId()).isEqualTo("7");
  }

  @Test
  void callAs_rejectsNullTenantInsteadOfSilentlyRunningWithoutContext() {
    assertThatThrownBy(() -> TenantContextRunner.callAs(null, TenantContext::getTenantId))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("tenantId must not be null");
  }

  @Test
  void runAs_restoresContextEvenWhenActionThrows() {
    TenantContext.setTenantId(7L);

    assertThatThrownBy(
            () ->
                TenantContextRunner.runAs(
                    42L,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(TenantContext.getTenantId()).isEqualTo("7");
  }
}
