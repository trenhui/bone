package com.bone.claim.application.port.out;

/**
 * 结案报送保司的出站端口（真实 HTTP 出站，ADR-0021 二期）。
 *
 * <p>业务层只声明「把一条保司报送消息投递到外部系统」这一能力，投递介质由 {@code infrastructure.client} 的适配器决定：未配置端点时中继延续一期
 * 进程内事件总线，配置后走真实 HTTP POST 打保司端点。业务层与中继不感知 URL/鉴权。
 *
 * <p><b>契约</b>：失败即抛（非 2xx / 超时 / 连接失败），由 Outbox 中继统一做 retry_count 累加、退避与死信告警；端口内不得吞异常， 否则 Outbox 会假
 * SENT。
 */
public interface InsurerPushGatewayPort {

  /**
   * 一条待报送保司的消息（由 Outbox 记录装配，不暴露外部传输细节）。
   *
   * @param tenantId 来源租户 ID
   * @param aggregate 聚合名（如 ClaimCase）
   * @param aggregateId 聚合实例 ID（赔案 ID）
   * @param eventType 事件类型（INSURER_PUSH）
   * @param payloadJson 原始事件载荷 JSON
   */
  record InsurerPushMessage(
      Long tenantId, String aggregate, Long aggregateId, String eventType, String payloadJson) {}

  /**
   * 真实 HTTP 出站报送一条消息。
   *
   * @param message 报送消息（非 null）
   * @return 保司回执编号（端点响应 receipt；无则空串）
   * @throws RuntimeException 非 2xx / 超时 / 连接失败时抛出，交由 Outbox 重试或进死信
   */
  String push(InsurerPushMessage message);

  /** 是否已配置真实外部端点。默认 true：未提供该能力实现的端口（如测试桩/一期总线）可保持原派发语义。 HTTP 适配器覆盖为「端点非空」， 供中继决定是否改走 HTTP。 */
  default boolean isConfigured() {
    return true;
  }
}
