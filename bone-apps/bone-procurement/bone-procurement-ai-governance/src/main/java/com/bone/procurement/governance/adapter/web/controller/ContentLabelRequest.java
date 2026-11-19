package com.bone.procurement.governance.adapter.web.controller;

import com.bone.procurement.governance.domain.model.labeling.ContentLabel;
import jakarta.validation.constraints.NotBlank;

/** 内容双标识出域校验请求体。 */
public record ContentLabelRequest(
    @NotBlank String contentRef,
    @NotBlank String capabilityCode,
    String explicitLabel,
    String implicitMetadata,
    boolean aiGenerated) {

  public ContentLabel toLabel() {
    return new ContentLabel(
        contentRef, capabilityCode, explicitLabel, implicitMetadata, aiGenerated);
  }
}
