package com.bone.ai.gateway.domain.event;

import com.bone.ai.gateway.domain.LlmUsage;
import com.bone.core.domain.DomainEvent;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * LLM 用量计量事件（AI 中台 §4.1 网关治理面 · spend tracking 发布端契约）。
 *
 * <p>与 {@link LlmCallRecorded}（证据链）分离关注点：调用日志证明"发生了什么"，本事件聚合"花了多少 token / 成本"。 业界对位 LiteLLM /
 * Portkey 的 spend tracking——按租户 / 模型聚合 token，供预算 / 配额 / 成本红线消费。
 *
 * <p>一期网关为库形态（进程内门面），gateway 只发布计量契约、不感知谁聚合；集中式网关演进时（跨产品集中配额 / 供应商 ≥3）由 订阅方接入分布式计数器，发布端契约不变。
 *
 * <p><b>带 traceId（2026-10-17 复核修复）</b>：此前计量事件与证据链无法关联，审计答不出「这笔 token 消耗属于哪次业务动作」—— 对账只能按时间窗凑。现与
 * {@link LlmCallRecorded} 共用同一 traceId，可直接 join 出「单次业务动作的模型花费」。
 *
 * @param tenantId 租户（可为空，POC 单租户）
 * @param model 模型档位
 * @param usage 本次 token 用量
 * @param traceId 全链追踪 ID（与证据链同一键；缺失时消费者可按 eventId 去重）
 * @param occurredAt 发生时刻
 */
public record LlmUsageRecorded(
    String tenantId, String model, LlmUsage usage, String traceId, LocalDateTime occurredAt)
    implements DomainEvent {

  /** 无 traceId 的构造（POC / 单测兼容；生产请用带 traceId 的重载以便对账）。 */
  public LlmUsageRecorded(String tenantId, String model, LlmUsage usage, LocalDateTime occurredAt) {
    this(tenantId, model, usage, null, occurredAt);
  }

  public static LlmUsageRecorded of(String tenantId, String model, LlmUsage usage) {
    return new LlmUsageRecorded(tenantId, model, usage, null, LocalDateTime.now());
  }

  public static LlmUsageRecorded of(String tenantId, String model, LlmUsage usage, String traceId) {
    return new LlmUsageRecorded(tenantId, model, usage, traceId, LocalDateTime.now());
  }

  /** 事件 UUID（域事件可回放标识，供计量幂等去重）。 */
  public String eventId() {
    return "llm-usage-" + UUID.randomUUID();
  }
}
