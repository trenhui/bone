package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.event.outbox.OrderOutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderOutboxRelayJob {

  private final OrderOutboxRelay orderOutboxRelay;

  @Scheduled(fixedDelayString = "${bone.blueprint.outbox.relay-delay-ms:5000}")
  public void relay() {
    int sent = orderOutboxRelay.relayPending();
    if (sent > 0) {
      log.info("Outbox 中继完成: sent={}", sent);
    }
  }
}
