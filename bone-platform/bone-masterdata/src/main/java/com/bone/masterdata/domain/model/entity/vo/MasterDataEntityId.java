package com.bone.masterdata.domain.model.entity.vo;

import com.bone.core.exception.DomainException;
import java.util.UUID;

public record MasterDataEntityId(String value) {
  public MasterDataEntityId {
    if (value == null || value.isEmpty()) {
      throw new DomainException("主数据实体ID不能为空");
    }
    try {
      UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw new DomainException("主数据实体ID必须是有效的UUID格式");
    }
  }

  public static MasterDataEntityId of(String value) {
    return new MasterDataEntityId(value);
  }
}
