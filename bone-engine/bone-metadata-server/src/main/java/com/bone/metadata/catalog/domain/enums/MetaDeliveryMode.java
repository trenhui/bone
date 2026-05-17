package com.bone.metadata.catalog.domain.enums;

/** 元数据实体交付模式，对齐 PRD META-002 / META-002B。 */
public enum MetaDeliveryMode {
  /** 模式 A：生成式交付（studio-generator） */
  GENERATIVE(0),
  /** 模式 B：运行时元数据面（bone-metadata-engine） */
  RUNTIME(1);

  private final int code;

  MetaDeliveryMode(int code) {
    this.code = code;
  }

  public int getCode() {
    return code;
  }

  public static MetaDeliveryMode fromCode(Integer code) {
    if (code == null) {
      return GENERATIVE;
    }
    for (MetaDeliveryMode m : values()) {
      if (m.code == code) {
        return m;
      }
    }
    throw new IllegalArgumentException("未知 delivery_mode: " + code);
  }
}
