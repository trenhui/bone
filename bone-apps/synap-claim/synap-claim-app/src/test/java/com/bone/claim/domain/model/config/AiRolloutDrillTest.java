package com.bone.claim.domain.model.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 回滚演练纯领域判据：只降一档、资金权限不扩宽、零写、状态完整性。 */
class AiRolloutDrillTest {

  @Test
  @DisplayName("四档回退目标：只降一档，最低档回自身")
  void rollbackTargets() {
    assertThat(AiRolloutDrill.of(AiRolloutStage.AUTOMATED).rollbackTargetStage())
        .isEqualTo(AiRolloutStage.CANARY);
    assertThat(AiRolloutDrill.of(AiRolloutStage.CANARY).rollbackTargetStage())
        .isEqualTo(AiRolloutStage.ASSISTED);
    assertThat(AiRolloutDrill.of(AiRolloutStage.ASSISTED).rollbackTargetStage())
        .isEqualTo(AiRolloutStage.SHADOW);
    assertThat(AiRolloutDrill.of(AiRolloutStage.SHADOW).rollbackTargetStage())
        .isEqualTo(AiRolloutStage.SHADOW);
  }

  @Test
  @DisplayName("仅 SHADOW 为 atFloor")
  void atFloorOnlyAtShadow() {
    assertThat(AiRolloutDrill.of(AiRolloutStage.SHADOW).atFloor()).isTrue();
    for (AiRolloutStage s :
        new AiRolloutStage[] {
          AiRolloutStage.ASSISTED, AiRolloutStage.CANARY, AiRolloutStage.AUTOMATED
        }) {
      assertThat(AiRolloutDrill.of(s).atFloor()).isFalse();
    }
  }

  @Test
  @DisplayName("任一档回退都不扩宽自动出金权限，状态完整性恒成立")
  void neverWidensAutoSettlement() {
    for (AiRolloutStage s : AiRolloutStage.values()) {
      AiRolloutDrill.Report r = AiRolloutDrill.of(s);
      assertThat(r.autoSettlementAfterRollback() && !s.permitsAutoSettlement())
          .as("stage=%s", s)
          .isFalse();
      assertThat(r.rollbackTargetStage().rank()).isLessThanOrEqualTo(s.rank());
      assertThat(r.integrityIntact()).isTrue();
    }
  }

  @Test
  @DisplayName("模型参与要求按目标档重算（AUTOMATED→CANARY 仍要求，CANARY→ASSISTED 不再要求）")
  void modelParticipationRecomputedForTarget() {
    assertThat(
            AiRolloutDrill.of(AiRolloutStage.AUTOMATED).modelParticipationRequiredAfterRollback())
        .isTrue();
    assertThat(AiRolloutDrill.of(AiRolloutStage.CANARY).modelParticipationRequiredAfterRollback())
        .isFalse();
    assertThat(AiRolloutDrill.of(AiRolloutStage.ASSISTED).modelParticipationRequiredAfterRollback())
        .isFalse();
  }

  @Test
  @DisplayName("零写：mutations=0，禁改清单不可变且覆盖放量开关")
  void zeroMutationsAndImmutableForbiddenList() {
    AiRolloutDrill.Report r = AiRolloutDrill.of(AiRolloutStage.CANARY);
    assertThat(r.mutations()).isZero();
    assertThat(r.forbiddenMutations()).contains("放量开关与生效档位", "资金与支付");
    assertThatThrownBy(() -> r.forbiddenMutations().add("篡改"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> AiRolloutDrill.FORBIDDEN_MUTATIONS.add("篡改"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("报告保留当前档且给出非空依据")
  void basisAndCurrentPreserved() {
    AiRolloutDrill.Report r = AiRolloutDrill.of(AiRolloutStage.CANARY);
    assertThat(r.currentStage()).isEqualTo(AiRolloutStage.CANARY);
    assertThat(r.basis()).contains("ASSISTED");
    assertThat(AiRolloutDrill.of(AiRolloutStage.SHADOW).basis()).contains("SHADOW");
  }

  @Test
  @DisplayName("null 档位显式拒绝，不猜测")
  void nullRejected() {
    assertThatThrownBy(() -> AiRolloutDrill.of(null)).isInstanceOf(NullPointerException.class);
  }
}
