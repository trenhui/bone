package com.bone.aitrace.domain.gateway;

/**
 * LLM 用量计量出口端口（spend tracking 消费端契约）。
 *
 * <p><b>为何独立于证据链</b>：{@code LlmUsageRecorded} 与 {@code LlmCallRecorded} 是两类事实——前者是「花了多少」（可聚合的数值），
 * 后者是「发生了什么」（单次留证）。前者要按租户 / 模型 / 时间窗聚合出账期口径，落库表形态与成本模型强相关， 不宜硬绑在证据快照上，故独立成端口由使用方按自己的计费口径实现。
 *
 * <p>默认实现 {@code StructuredLoggingLlmUsageSink} 输出结构化日志（零 DDL、零新依赖），满足「按租户/模型聚合日志算账」的 最低可用形态；接
 * Micrometer 打点或 {@code llm_usage_daily} 聚合表属依赖 / DDL 变更（AGENTS L3，需架构师审批）， 届时以 {@code @Bean}
 * 覆盖本端口即可，调用侧零改动。
 */
public interface LlmUsageSink {

  /** 记录一次模型调用用量（租户可为空——网关 POC 单租户口径，缺失不阻断）。 */
  void record(LlmUsageRecord record);

  /**
   * 一次调用的用量事实（跨模块解耦：不直接依赖 gateway 的事件类型）。
   *
   * @param tenantId 租户（可能为 null）
   * @param model 模型标识
   * @param promptTokens 输入 token
   * @param completionTokens 输出 token
   * @param totalTokens 合计 token
   * @param traceId 全链追踪 ID（与证据链同一键——「这笔 token 属于哪次业务动作」的对账依据，可为空）
   */
  record LlmUsageRecord(
      String tenantId,
      String model,
      int promptTokens,
      int completionTokens,
      int totalTokens,
      String traceId) {}
}
