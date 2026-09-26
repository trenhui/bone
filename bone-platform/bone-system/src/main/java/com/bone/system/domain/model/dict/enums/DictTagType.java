package com.bone.system.domain.model.dict.enums;

/**
 * 字典项的展示语义色：与前端 antd Tag 的色板对齐（{@code default/info/success/warning/error}）。
 *
 * <p>为什么要落在字典而不是前端写死：同一个「已驳回」在订单页是红色、在审批页是橙色， 写死在页面里就等于把一份值域知识复制了 N 份；落在字典则由值域 owner 统一口径。
 */
public enum DictTagType {
  DEFAULT("default"),
  INFO("info"),
  SUCCESS("success"),
  WARNING("warning"),
  ERROR("error");

  private final String code;

  DictTagType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 未知取值退化为 {@link #DEFAULT}，不因展示配置写错而让整条字典读不出来。 */
  public static DictTagType ofCode(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT;
    }
    for (DictTagType t : values()) {
      if (t.code.equalsIgnoreCase(value.trim())) {
        return t;
      }
    }
    return DEFAULT;
  }
}
