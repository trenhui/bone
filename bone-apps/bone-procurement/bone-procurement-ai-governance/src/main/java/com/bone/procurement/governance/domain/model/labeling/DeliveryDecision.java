package com.bone.procurement.governance.domain.model.labeling;

import java.util.Objects;

/** 内容出域裁决：缺隐式或显式标识一律 BLOCKED，不伪造「可出域」。 */
public record DeliveryDecision(String contentRef, boolean released, String reasonCode) {

  public static final String REASON_RELEASED = "LABELS_COMPLETE";
  public static final String REASON_MISSING_IMPLICIT = "MISSING_IMPLICIT_LABEL";
  public static final String REASON_MISSING_EXPLICIT = "MISSING_EXPLICIT_LABEL";
  public static final String REASON_BOTH_MISSING = "LABELS_MISSING";

  public DeliveryDecision {
    Objects.requireNonNull(contentRef, "contentRef");
    Objects.requireNonNull(reasonCode, "reasonCode");
  }

  /** LABEL-01：缺隐式标识即阻断出域（优先报隐式，因它是机器可读的强校验）。 */
  public static DeliveryDecision decide(ContentLabel label) {
    Objects.requireNonNull(label, "label");
    if (!label.hasImplicit() && !label.hasExplicit()) {
      return new DeliveryDecision(label.contentRef(), false, REASON_BOTH_MISSING);
    }
    if (!label.hasImplicit()) {
      return new DeliveryDecision(label.contentRef(), false, REASON_MISSING_IMPLICIT);
    }
    if (!label.hasExplicit()) {
      return new DeliveryDecision(label.contentRef(), false, REASON_MISSING_EXPLICIT);
    }
    return new DeliveryDecision(label.contentRef(), true, REASON_RELEASED);
  }
}
