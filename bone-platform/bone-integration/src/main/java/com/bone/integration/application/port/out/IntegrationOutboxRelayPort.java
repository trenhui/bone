package com.bone.integration.application.port.out;

/**
 * Outbox 中继出站端口（CORE-02 依赖倒置）。
 *
 * <p>adapter/schedule 的 {@code IntegrationOutboxRelayJob} 经此端口触发中继，不再直注 {@code
 * infrastructure.messaging.outbox.IntegrationOutboxRelay} 实现——实现可替换、可独立单测，且受 {@code
 * ArchitectureTest#outbox_relay_job_must_use_domain_port} 守护。
 */
public interface IntegrationOutboxRelayPort {

  /**
   * 中继一批 PENDING 的 Outbox 记录到 MQ。
   *
   * @return 成功投递（标记 SENT）的条数
   */
  int relayBatch();
}
