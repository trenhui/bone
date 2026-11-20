package com.bone.procurement.governance.application;

import com.bone.procurement.governance.domain.model.capability.OwnerRef;

/** 登记能力命令。 */
public record RegisterCapabilityCommand(
    String code, String displayName, String ownerUserId, String ownerDisplayName) {

  public OwnerRef owner() {
    return OwnerRef.of(ownerUserId, ownerDisplayName);
  }
}
