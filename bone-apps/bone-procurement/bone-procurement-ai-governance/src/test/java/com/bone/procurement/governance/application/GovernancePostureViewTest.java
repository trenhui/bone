package com.bone.procurement.governance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.application.GovernancePostureView.CapabilityPostureEntry;
import com.bone.procurement.governance.domain.model.capability.PostureVerdict;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** 治理态势视图：字段白名单守护（禁 reason 原文/AI output/PII），计数真实派生。 */
class GovernancePostureViewTest {

  @Test
  void 能力明细字段白名单不含敏感原文() {
    Set<String> fields =
        java.util.Arrays.stream(CapabilityPostureEntry.class.getRecordComponents())
            .map(c -> c.getName())
            .collect(Collectors.toSet());
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "code",
            "displayName",
            "state",
            "decisionAllowed",
            "decisionReasonCode",
            "driftVerdict",
            "oversightVerdict",
            "requiresOwnerReview");
    assertThat(fields).noneMatch(f -> f.toLowerCase().contains("output"));
    assertThat(fields).noneMatch(f -> f.toLowerCase().contains("pii"));
    assertThat(fields).noneMatch(f -> f.equals("reason") || f.equals("aiConclusion"));
  }

  @Test
  void 计数从明细真实派生且不声明模型健康() {
    CapabilityPostureEntry attention =
        new CapabilityPostureEntry(
            "M1",
            "辅助评标",
            "AVAILABLE",
            true,
            "ALLOWED_ADVISORY_ONLY",
            "NO_ACTIONABLE_SIGNAL",
            "REVIEW_REQUIRED",
            true);
    CapabilityPostureEntry undetermined =
        new CapabilityPostureEntry(
            "M2",
            "供应商推荐",
            "UNKNOWN",
            false,
            "CAPABILITY_INDETERMINATE",
            "UNDETERMINED",
            "NO_ACTIONABLE_SIGNAL",
            false);
    GovernancePostureView view =
        GovernancePostureView.from(PostureVerdict.UNDETERMINED, List.of(attention, undetermined));
    assertThat(view.attentionCount()).isEqualTo(1);
    assertThat(view.undeterminedCount()).isEqualTo(1);
    assertThat(view.modelHealthClaim()).isEqualTo(GovernancePostureView.NO_MODEL_HEALTH_CLAIM);
    assertThat(view.modelHealthClaim()).isEqualTo("NONE");
  }
}
