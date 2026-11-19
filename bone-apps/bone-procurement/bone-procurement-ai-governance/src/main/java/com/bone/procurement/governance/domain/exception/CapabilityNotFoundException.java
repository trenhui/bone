package com.bone.procurement.governance.domain.exception;

/** 能力未登记：治理查询/变更针对未登记能力时抛出，调用方映射为 404，不放行任何推理。 */
public class CapabilityNotFoundException extends RuntimeException {

  public CapabilityNotFoundException(String code) {
    super("AI 能力未登记: " + code);
  }
}
