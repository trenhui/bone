package com.bone.integration.domain.model.flow.valueobject;

import com.bone.core.exception.DomainException;
import java.util.UUID;

public record FlowId(String value) {
  public FlowId {
    if (value == null || value.isEmpty()) {
      throw new DomainException("流程ID不能为空");
    }
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new DomainException("流程ID必须是有效的UUID格式");
    }
  }

  public static FlowId of(String value) {
    return new FlowId(value);
  }
}
