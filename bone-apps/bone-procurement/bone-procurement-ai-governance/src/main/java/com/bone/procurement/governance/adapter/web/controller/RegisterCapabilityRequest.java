package com.bone.procurement.governance.adapter.web.controller;

import com.bone.procurement.governance.application.RegisterCapabilityCommand;
import jakarta.validation.constraints.NotBlank;

/** 登记能力请求体。 */
public record RegisterCapabilityRequest(
    @NotBlank String code,
    @NotBlank String displayName,
    @NotBlank String ownerUserId,
    String ownerDisplayName) {

  public RegisterCapabilityCommand toCommand() {
    return new RegisterCapabilityCommand(code, displayName, ownerUserId, ownerDisplayName);
  }
}
