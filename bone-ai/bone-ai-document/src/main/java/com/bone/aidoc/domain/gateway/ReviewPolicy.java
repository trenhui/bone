package com.bone.aidoc.domain.gateway;

import com.bone.aidoc.domain.model.FieldReviewContext;
import com.bone.aidoc.domain.model.ReviewDecision;

/**
 * 复核路由策略端口（ADR-0046 五层可信度的最终裁决层 + §四：按票种/字段/风险分级标定，禁全局阈值）。
 *
 * <p>综合字段证据（OCR 置信度）、质量分与风险等级，对单字段裁决： {@code AUTO_PASS} / {@code SECONDARY_RECOGNIZE} / {@code
 * MANUAL_REVIEW}。 设计铁律：多模型结果一致 ≠ 正确；高风险字段不得仅凭 OCR 置信度自动放行； 阈值须由真实标注数据按票种/字段/风险分别标定，不得硬编码统一 0.95。
 *
 * <p>骨架提供 {@code DefaultReviewPolicy}（L1~L5 组合逻辑），阈值由配置注入、可覆盖。
 */
public interface ReviewPolicy {

  /** 对单字段做出复核路由决策。 */
  ReviewDecision decide(FieldReviewContext ctx);
}
