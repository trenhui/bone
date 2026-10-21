package com.bone.masterdata.domain.model.entity.valueobject;

/**
 * 主数据实体生命周期状态（UC-MD-T7 发布审批流）。
 *
 * <p>状态机（L2/L3 工作流实体）：
 *
 * <pre>
 *   DRAFT --submitForReview--> PENDING_REVIEW --approve--> APPROVED --publish--> PUBLISHED
 *                                  |                                                     |
 *                                  +--reject(reason)--> DRAFT                            +--retire--> RETIRED
 * </pre>
 *
 * L1 轻量实体无审批流：DRAFT 可直发 PUBLISHED（publish 门禁对 workflowEnabled=false 放行）。
 */
public enum MasterDataEntityStatus {
  DRAFT("草稿"),
  PENDING_REVIEW("待评审"),
  APPROVED("已审批"),
  PUBLISHED("已发布"),
  DISABLED("已停用"),
  RETIRED("已退役");

  private final String description;

  MasterDataEntityStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }
}
