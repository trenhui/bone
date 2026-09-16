package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.port.out.OrderOutboxRelayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继定时任务（adapter/schedule）。
 *
 * <p>按 E-3 R1 依赖向内原则，adapter 层直接依赖 {@code application/port/out} 技术出站端口（{@link
 * OrderOutboxRelayPort}），不经过 application 层中转。 v4.8 复核修正：删除原 {@code OrderOutboxRelayOrchestrator}
 * 中转层（纯技术转发，无业务编排，违反 E-5.3 「Orchestrator 禁承载纯技术轮询/中继」），Job 直注端口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayJob {

  private final OrderOutboxRelayPort orderOutboxRelayPort;

  @Scheduled(fixedDelayString = "${bone.blueprint.outbox.relay-delay-ms:5000}")
  public void relay() {
    int sent = orderOutboxRelayPort.relayPending();
    if (sent > 0) {
      log.info("Outbox 中继完成: sent={}", sent);
    }
  }
}
