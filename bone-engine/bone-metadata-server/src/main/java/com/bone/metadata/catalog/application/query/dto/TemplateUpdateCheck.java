package com.bone.metadata.catalog.application.query.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 模板跟随更新检查（技术方案 §5：租户确认式跟随，永不自动推送）。
 *
 * <p>{@code digest} 只含计数（sections/rules/actions 增减数），不返全量——列表只返摘要。
 */
@Data
@Builder
public class TemplateUpdateCheck {

  private boolean hasUpdate;

  /** 实例化时的模板版本（base 指针）。 */
  private String baseVersion;

  /** 模板当前版本。 */
  private String currentVersion;

  private int sectionAdded;

  private int sectionRemoved;

  private int ruleAdded;

  private int ruleRemoved;

  private int actionAdded;

  private int actionRemoved;

  /**
   * 无更新的原因（{@code TEMPLATE_ASSET_INCOMPLETE}=模板资产未补齐，跟随入口保持 disabled， 前端横幅明示不静默开放；{@code
   * NO_BASE_POINTER}=非模板实例，无需跟随）。
   */
  private String reason;
}
