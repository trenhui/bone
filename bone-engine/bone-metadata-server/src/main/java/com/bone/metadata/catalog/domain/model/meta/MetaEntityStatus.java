package com.bone.metadata.catalog.domain.model.meta;

/** 元数据实体生命周期状态，对应 meta_entity.status */
public enum MetaEntityStatus {
  DRAFT(0),
  PUBLISHED(1),
  ARCHIVED(2);

  private final int code;

  MetaEntityStatus(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static MetaEntityStatus fromCode(int code) {
    for (MetaEntityStatus s : values()) {
      if (s.code == code) {
        return s;
      }
    }
    throw new IllegalArgumentException("未知实体状态: " + code);
  }
}
