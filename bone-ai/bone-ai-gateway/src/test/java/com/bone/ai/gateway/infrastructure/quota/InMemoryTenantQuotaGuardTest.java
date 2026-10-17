package com.bone.ai.gateway.infrastructure.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.ai.gateway.common.AiGatewayErrorCodes;
import com.bone.core.exception.BizException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 租户配额：窗口内计数、超限拒绝、跨窗口恢复、租户隔离、缺租户放行。 */
class InMemoryTenantQuotaGuardTest {

  @Test
  void allows_up_to_limit_then_rejects() {
    AtomicLong now = new AtomicLong(1_000_000L);
    InMemoryTenantQuotaGuard guard = new InMemoryTenantQuotaGuard(2, 60_000, now::get);

    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
    assertThatThrownBy(() -> guard.acquire("1"))
        .isInstanceOf(BizException.class)
        .satisfies(
            e ->
                assertThat(((BizException) e).getErrorCode())
                    .isEqualTo(AiGatewayErrorCodes.QUOTA_EXCEEDED));
    assertThat(guard.used("1")).isEqualTo(2);
  }

  @Test
  void resets_in_next_window() {
    AtomicLong now = new AtomicLong(1_000_000L);
    InMemoryTenantQuotaGuard guard = new InMemoryTenantQuotaGuard(1, 60_000, now::get);

    guard.acquire("1");
    assertThatThrownBy(() -> guard.acquire("1")).isInstanceOf(BizException.class);

    now.addAndGet(60_000); // 进入下一窗口
    assertThatCode(() -> guard.acquire("1")).doesNotThrowAnyException();
  }

  @Test
  void tenants_are_isolated() {
    AtomicLong now = new AtomicLong(1_000_000L);
    InMemoryTenantQuotaGuard guard = new InMemoryTenantQuotaGuard(1, 60_000, now::get);

    guard.acquire("1");
    // 租户 2 不受租户 1 消耗影响
    assertThatCode(() -> guard.acquire("2")).doesNotThrowAnyException();
  }

  @Test
  void missing_tenant_is_not_blocked() {
    AtomicLong now = new AtomicLong(1_000_000L);
    InMemoryTenantQuotaGuard guard = new InMemoryTenantQuotaGuard(1, 60_000, now::get);

    // 配额是容量治理，不该退化成「没租户就不许调用」的可用性故障
    assertThatCode(() -> guard.acquire(null)).doesNotThrowAnyException();
    assertThatCode(() -> guard.acquire("  ")).doesNotThrowAnyException();
  }
}
