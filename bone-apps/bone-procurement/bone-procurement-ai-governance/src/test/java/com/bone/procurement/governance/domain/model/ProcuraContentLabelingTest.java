package com.bone.procurement.governance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.labeling.ContentLabel;
import com.bone.procurement.governance.domain.model.labeling.DeliveryDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** evalset LABEL-01 源码落点：缺隐式标识不得出域，显式标识不能替代机器可读标识。 */
class ProcuraContentLabelingTest {

  @Test
  @DisplayName("missingImplicitLabel_blocksDelivery")
  void missingImplicitLabel_blocksDelivery() {
    ContentLabel label = new ContentLabel("report://M2/LABEL-01", "M2", "本内容由 AI 辅助生成", "", true);

    assertThat(label.hasExplicit()).isTrue();
    assertThat(label.hasImplicit()).isFalse();

    DeliveryDecision decision = DeliveryDecision.decide(label);

    assertThat(decision.released()).isFalse();
    assertThat(decision.reasonCode()).isEqualTo(DeliveryDecision.REASON_MISSING_IMPLICIT);
    assertThat(label.canRelease()).isFalse();
  }
}
