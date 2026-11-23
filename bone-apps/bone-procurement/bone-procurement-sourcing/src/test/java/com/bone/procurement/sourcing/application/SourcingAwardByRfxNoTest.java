package com.bone.procurement.sourcing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.procurement.sourcing.domain.gateway.SourcingProjectRepository;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProject;
import com.bone.procurement.sourcing.domain.model.sourcing.SourcingProjectStatus;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 按 RFx 授标应用服务：存在→AWARDED，缺失→拒绝，空白操作人（AI/匿名）→拒绝且不落库。 */
class SourcingAwardByRfxNoTest {

  private final SourcingProjectRepository repository = mock(SourcingProjectRepository.class);
  private final SourcingApplicationService service = new SourcingApplicationService(repository);

  private SourcingProject closableProject() {
    SourcingProject p =
        SourcingProject.create(
            1L, "PRJ-1", "CAT-1", new BigDecimal("100"), "CNY", "STRATEGY", "owner-1");
    p.publish();
    p.closeBidding();
    return p;
  }

  @Test
  void 存在且具名操作人授标成功并读回状态() {
    SourcingProject project = closableProject();
    when(repository.findProjectByRfxNo(1L, "RFX-1")).thenReturn(Optional.of(project));

    service.awardByRfxNo(1L, "RFX-1", "alice");

    assertThat(project.status()).isEqualTo(SourcingProjectStatus.AWARDED);
    verify(repository).save(project);
  }

  @Test
  void 非对应RFx或跨租户拒绝且不落库() {
    when(repository.findProjectByRfxNo(1L, "MISSING")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.awardByRfxNo(1L, "MISSING", "alice"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RFx");
    verify(repository, never()).save(any());
  }

  @Test
  void 空白操作人拒绝且不落库() {
    assertThatThrownBy(() -> service.awardByRfxNo(1L, "RFX-1", "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工");
    verify(repository, never()).findProjectByRfxNo(any(), any());
    verify(repository, never()).save(any());
  }

  @Test
  void AI结论不得直接授标必须为具名人工() {
    SourcingProject project = closableProject();
    when(repository.findProjectByRfxNo(1L, "RFX-1")).thenReturn(Optional.of(project));

    // AI/系统主体没有具名人工操作人：空白与 null 均被拒绝，项目不得进入 AWARDED。
    assertThatThrownBy(() -> service.awardByRfxNo(1L, "RFX-1", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("人工");
    assertThat(project.status()).isEqualTo(SourcingProjectStatus.BIDDING_CLOSED);
    verify(repository, never()).save(any());
  }
}
