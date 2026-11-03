package com.bone.claim.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.claim.application.ClaimCaseApplicationService;
import com.bone.claim.application.port.out.TenantPort;
import com.bone.claim.domain.model.claim.valueobject.ClaimStatus;
import com.bone.claim.domain.repository.ClaimCaseRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 分页入参收窄的判据测试。
 *
 * <p><b>为什么要单独钉住</b>：{@code size} 越界本身不产生任何错误 —— 它只是让一次查询多读几万行，功能上「看起来正常」。 这类缺陷无法靠人工回归发现，只能在
 * 入口断言「仓储收到的到底是什么值」。
 *
 * <p><b>为什么钉在服务层而不是控制器</b>：{@code ReviewTaskController} 把裸 {@code page}/{@code size} 直接透传给 {@link
 * ClaimCaseApplicationService#page}，绕过了带 {@code @Valid} 的 {@code ClaimPageReq}。 收窄若只写在控制器，这条路径就是缺口。
 */
@ExtendWith(MockitoExtension.class)
class PageBoundsClampTest {

  @Mock private ClaimCaseRepository claimCaseRepository;
  @Mock private TenantPort tenantPort;
  private ClaimCaseApplicationService service;

  @BeforeEach
  void setUp() {
    // 只装配本测试用到的两个依赖；page() 不触碰其余端口
    service =
        new ClaimCaseApplicationService(
            claimCaseRepository, null, null, tenantPort, null, null, null);
  }

  @Test
  @DisplayName("每页条数超过上限时收敛到框架上限，不下发给数据库")
  void clampsOversizedPageSize() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    service.page(List.of(ClaimStatus.PENDING_ADJUST), null, 1, 10_000_000);

    verify(claimCaseRepository)
        .findClaimPage(eq(1L), eq(List.of(ClaimStatus.PENDING_ADJUST)), isNull(), eq(1), eq(100));
  }

  @Test
  @DisplayName("页码小于 1 时收敛为 1（否则 offset 为负）")
  void clampsNonPositivePage() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    service.page(List.of(ClaimStatus.PENDING_ADJUST), null, 0, 20);

    verify(claimCaseRepository)
        .findClaimPage(eq(1L), eq(List.of(ClaimStatus.PENDING_ADJUST)), isNull(), eq(1), eq(20));
  }

  @Test
  @DisplayName("合法入参原样透传（收窄不得改变正常语义）")
  void keepsValidBoundsUntouched() {
    when(tenantPort.currentTenantId()).thenReturn(1L);

    service.page(List.of(ClaimStatus.PENDING_ADJUST), "APP", 3, 50);

    verify(claimCaseRepository)
        .findClaimPage(eq(1L), eq(List.of(ClaimStatus.PENDING_ADJUST)), eq("APP"), eq(3), eq(50));
  }

  @Test
  @DisplayName("PageBounds.of 的边界值：非正数收敛为 1，超限收敛为上限")
  void pageBoundsBoundaryValues() {
    assertThat(PageBounds.of(0, 0)).isEqualTo(new PageBounds(1, 1));
    assertThat(PageBounds.of(-5, -1)).isEqualTo(new PageBounds(1, 1));
    assertThat(PageBounds.of(2, 10_000)).isEqualTo(new PageBounds(2, 100));
    assertThat(PageBounds.of(1, 100)).isEqualTo(new PageBounds(1, 100));
  }
}
