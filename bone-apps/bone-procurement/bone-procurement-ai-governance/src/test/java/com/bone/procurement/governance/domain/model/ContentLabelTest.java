package com.bone.procurement.governance.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.governance.domain.model.labeling.ContentLabel;
import com.bone.procurement.governance.domain.model.labeling.DeliveryDecision;
import org.junit.jupiter.api.Test;

/** F4 双标识单测：LABEL-01 缺隐式标识即阻断出域（evalset missingImplicitLabel_blocksDelivery）。 */
class ContentLabelTest {

  @Test
  void 双标识齐备允许出域() {
    ContentLabel label =
        new ContentLabel("draft://M7/1", "M7", "本内容由 AI 辅助起草", "ai_generated=true;model=v1", true);
    assertThat(label.hasExplicit()).isTrue();
    assertThat(label.hasImplicit()).isTrue();
    DeliveryDecision d = DeliveryDecision.decide(label);
    assertThat(d.released()).isTrue();
    assertThat(d.reasonCode()).isEqualTo(DeliveryDecision.REASON_RELEASED);
  }

  @Test
  void missingImplicitLabel_blocksDelivery() {
    ContentLabel label = new ContentLabel("report://M2/1", "M2", "本内容由 AI 辅助生成", "", true);
    DeliveryDecision d = DeliveryDecision.decide(label);
    assertThat(d.released()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(DeliveryDecision.REASON_MISSING_IMPLICIT);
  }

  @Test
  void 缺显式标识阻断出域() {
    ContentLabel label = new ContentLabel("answer://M5/1", "M5", "", "ai_generated=true", true);
    DeliveryDecision d = DeliveryDecision.decide(label);
    assertThat(d.released()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(DeliveryDecision.REASON_MISSING_EXPLICIT);
  }

  @Test
  void 双标识全缺阻断出域() {
    ContentLabel label = new ContentLabel("bid://M6/1", "M6", "", "", true);
    DeliveryDecision d = DeliveryDecision.decide(label);
    assertThat(d.released()).isFalse();
    assertThat(d.reasonCode()).isEqualTo(DeliveryDecision.REASON_BOTH_MISSING);
  }

  @Test
  void 非AI内容也需标识才出域() {
    ContentLabel label = new ContentLabel("doc://manual/1", "M2", "", "ai_generated=false", false);
    // aiGenerated=false 但缺显式标识仍阻断——标识义务按内容属性，不靠自报豁免。
    assertThat(DeliveryDecision.decide(label).released()).isFalse();
  }
}
