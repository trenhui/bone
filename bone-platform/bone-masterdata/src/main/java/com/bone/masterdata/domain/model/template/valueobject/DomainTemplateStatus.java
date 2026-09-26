package com.bone.masterdata.domain.model.template.valueobject;

/** 域模板状态机：DRAFT → PUBLISHED → ARCHIVED（仅 PUBLISHED 可被租户实例化）。 */
public enum DomainTemplateStatus {
  DRAFT("草稿"),
  PUBLISHED("已发布"),
  ARCHIVED("已归档");

  private final String label;

  DomainTemplateStatus(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  /** 模板可被实例化的唯一前置状态。 */
  public boolean isInstantiable() {
    return this == PUBLISHED;
  }
}
