package com.bone.metadata.catalog.application.query.dto;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 发布预览的界面摘要区块（技术方案 §4：publish-preview 加 {@code ui} 区块）。
 *
 * <p>只读摘要（计数 + 阻断问题），供 {@code PublishPreviewModal} 渲染"界面 diff"，与结构 statements 同屏。
 */
@Data
@Builder
public class UiSchemaSummary {

  private boolean hasSchema;

  private int sections;

  private int linkageRules;

  private int submitRules;

  private int actions;

  private List<EntityValidationIssue> blockingIssues;
}
