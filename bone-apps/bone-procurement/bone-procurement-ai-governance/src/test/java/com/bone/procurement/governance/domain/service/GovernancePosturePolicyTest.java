package com.bone.procurement.governance.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.capability.DriftVerdict;
import com.bone.procurement.governance.domain.model.capability.OversightVerdict;
import com.bone.procurement.governance.domain.model.capability.PostureVerdict;
import com.bone.procurement.governance.domain.service.GovernancePosturePolicy.PostureItemVerdict;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 治理态势聚合策略：不可判定优先、其次需关注、否则全清；不接受客户端阈值。 */
class GovernancePosturePolicyTest {

  private final GovernancePosturePolicy policy = new GovernancePosturePolicy();

  @Test
  void 空清单全清() {
    assertThat(policy.aggregate(List.of())).isEqualTo(PostureVerdict.ALL_CLEAR);
  }

  @Test
  void 分项不可判定优先于其他需求的需关注() {
    List<PostureItemVerdict> items =
        List.of(
            new PostureItemVerdict(
                true, DriftVerdict.REVIEW_REQUIRED, OversightVerdict.NO_ACTIONABLE_SIGNAL),
            new PostureItemVerdict(
                true, DriftVerdict.UNDETERMINED, OversightVerdict.NO_ACTIONABLE_SIGNAL));
    assertThat(policy.aggregate(items)).isEqualTo(PostureVerdict.UNDETERMINED);
  }

  @Test
  void 存在需关注分项则整体需关注() {
    List<PostureItemVerdict> items =
        List.of(
            new PostureItemVerdict(
                true, DriftVerdict.NO_ACTIONABLE_SIGNAL, OversightVerdict.REVIEW_REQUIRED),
            new PostureItemVerdict(
                true, DriftVerdict.NO_ACTIONABLE_SIGNAL, OversightVerdict.NO_ACTIONABLE_SIGNAL));
    assertThat(policy.aggregate(items)).isEqualTo(PostureVerdict.ATTENTION_REQUIRED);
  }

  @Test
  void 全部可判定且无信号则全清() {
    List<PostureItemVerdict> items =
        List.of(
            new PostureItemVerdict(
                true, DriftVerdict.NO_ACTIONABLE_SIGNAL, OversightVerdict.NO_ACTIONABLE_SIGNAL),
            new PostureItemVerdict(
                false, DriftVerdict.NO_ACTIONABLE_SIGNAL, OversightVerdict.NO_ACTIONABLE_SIGNAL));
    assertThat(policy.aggregate(items)).isEqualTo(PostureVerdict.ALL_CLEAR);
  }
}
