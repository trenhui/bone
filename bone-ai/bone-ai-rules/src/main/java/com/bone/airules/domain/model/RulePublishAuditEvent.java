package com.bone.airules.domain.model;

/**
 * 规则发布审计事件（G12 控制矩阵「发布审计 WORM」）：不可变，记录 who/when/what/verdict。
 *
 * <p>仓内默认 sink 只做结构化日志；长期 WORM 落盘由接入方注入实现，本事件是唯一载荷，不依赖具体存储。
 *
 * @param ruleCode 规则编码
 * @param versionNo 版本号
 * @param operator 发布操作人
 * @param decisionCode 判定码（ADMITTED 或某个 REJECTED_*）
 * @param admitted 是否放行
 * @param occurredAt 判定时间（ISO-8601）
 */
public record RulePublishAuditEvent(
    String ruleCode,
    int versionNo,
    String operator,
    String decisionCode,
    boolean admitted,
    String occurredAt) {}
