package com.bone.airules.domain.model;

import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 规则版本快照（不可变）：表达式 + 灰度 + 命中标记的版本化记录，供判定路径回放与版本 diff。
 *
 * <p>规则级兼容性检查（版本 diff 同回归绿灯才允许发布）由治理面在发布动作中强制执行。
 */
@Getter
public class RuleAssetVersion {

  private final String ruleId;
  private final int versionNo;
  private final String expression; // QLExpress DSL 文本
  private final boolean hitBlock; // RULE_HIT_BLOCK：命中时网关短路不调 LLM
  private final int grayPercent; // 灰度比例 0-100
  private final LocalDateTime createdAt;
  private final String createdBy;

  private RuleAssetVersion(
      String ruleId,
      int versionNo,
      String expression,
      boolean hitBlock,
      int grayPercent,
      String createdBy) {
    this.ruleId = ruleId;
    this.versionNo = versionNo;
    this.expression = expression;
    this.hitBlock = hitBlock;
    this.grayPercent = grayPercent;
    this.createdBy = createdBy;
    this.createdAt = LocalDateTime.now();
  }

  public static RuleAssetVersion of(
      String ruleId,
      int versionNo,
      String expression,
      boolean hitBlock,
      int grayPercent,
      String createdBy) {
    return new RuleAssetVersion(ruleId, versionNo, expression, hitBlock, grayPercent, createdBy);
  }
}
