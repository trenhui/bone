package com.bone.claim.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 特性开关配置变更领域事件（G3 / 架构评审后续）。
 *
 * <p><b>为什么要独立成事件而不是只在 remark 里写</b>：{@code t_feature_toggle} 的 {@code remark} / {@code
 * config_json.lastRollback} 已经是「谁把档位降了、依据什么」的<b>持久化</b>留痕（与开关同行同生共死，不会出现「开关表说
 * AUTOMATED、审计表说昨天降过档」的分叉）； 但持久化留痕无法<b>实时</b>触发风控告警，也无法被审计 / 对账系统以统一契约订阅。本事件把「配置变更」作为一等领域事实送上事件总线，
 * 使风控告警、审计订阅、跨系统对账都能以统一契约消费，而不是各自去解析 remark 字符串。
 *
 * <p><b>范围</b>：当前仅在 {@code AiRolloutApplicationService.autoRollback}（资金侧放量档位自动收紧）发射——
 * 这是与「自动出金」直接相关的配置变更，最该被风控告警盯防。任何新增的开关写入口（如运营端改 {@code stage}/灰度比例）都应复用本事件，而非各自补一个语义含糊的日志。
 *
 * <p><b>审计与告警的分工</b>：本事件负责「实时广播 + 可被订阅」；持久化审计仍由 {@code remark} + {@code config_json.lastRollback} +
 * ERROR 日志承担（零新增 DDL）。若日后要求「配置变更可库内查询的审计流水」， 应新增 {@code t_feature_toggle_audit} 表 + 订阅器落库（属 L3
 * DDL，需架构师审批，不在本次范围）。
 */
public record FeatureToggleChangedEvent(
    long tenantId,
    long toggleId,
    String featureKey,
    String changeType,
    String beforeStage,
    String afterStage,
    String beforeConfigJson,
    String afterConfigJson,
    String reason,
    String operator,
    Instant occurredAt)
    implements DomainEvent {

  /** 变更类型：错误预算击穿自动降档（系统自收紧，最需风控可见）。 */
  public static final String TYPE_AUTO_ROLLBACK = "AUTO_ROLLBACK";
}
