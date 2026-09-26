package com.bone.masterdata.domain.model.record.valueobject;

public enum MasterDataRecordStatus {
  DRAFT("草稿"),
  PENDING_APPROVAL("待审批"),
  APPROVED("审批通过"),
  PUBLISHED("已发布"),
  SUPERSEDED("已被新版本取代"),
  ARCHIVED("已归档");

  private final String description;

  MasterDataRecordStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
