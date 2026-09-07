package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.orchestration.OrderOutboxRelayOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayJob {

  private final OrderOutboxRelayOrchestrator orderOutboxRelayOrchestrator;

  @Scheduled(fixedDelayString = "${bone.blueprint.outbox.relay-delay-ms:5000}")
  public void relay() {
    int sent = orderOutboxRelayOrchestrator.relay();
    if (sent > 0) {
      log.info("Outbox 中继完成: sent={}", sent);
    }
  }
}
