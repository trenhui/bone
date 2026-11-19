package com.bone.procurement.governance.adapter.web.controller;

import com.bone.procurement.governance.application.ChangeCapabilityStateCommand;

/** 能力状态变更请求体：action ∈ enable/disable/degrade/undetermined，操作人不接受自报。 */
public record CapabilityStateRequest(String action, String reason) {

  public ChangeCapabilityStateCommand toCommand(String code) {
    return new ChangeCapabilityStateCommand(code, reason);
  }
}
