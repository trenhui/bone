package com.bone.claim.domain.model.config.event;

import com.bone.core.domain.DomainEvent;
import java.time.Instant;

/**
 * 决策图流程配置变更领域事件（M5a / E-5.4）。
 *
 * <p><b>为什么必须发事件</b>：{@code t_claim_flow_config} 的 {@code config_version} / {@code updated_at}
 * 只是同行持久化留痕，无法实时触发风控告警，也无法被审计 / 对账以统一契约订阅。「跳过 AI 评估即转人工」 这类配置会直接改变赔案结论档，属于一等领域事实，必须由事件总线广播。
 */
public record ClaimFlowConfigChangedEvent(
    long tenantId,
    long configId,
    String flowKey,
    String changeType,
    int configVersion,
    String afterConfigJson,
    String operator,
    Instant occurredAt)
    implements DomainEvent {

  public static final String TYPE_CREATED = "CREATED";
  public static final String TYPE_OVERWRITTEN = "OVERWRITTEN";
}
