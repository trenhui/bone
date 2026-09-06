package com.bone.integration.adapter.schedule;

import com.bone.integration.application.config.IntegrationOutboxProperties;
import com.bone.integration.infrastructure.messaging.outbox.IntegrationOutboxRelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Outbox 发件进程（INT-10）：独立调度中继至 MQ。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class IntegrationOutboxRelayJob {

  private final IntegrationOutboxProperties properties;
  private final IntegrationOutboxRelay outboxRelay;

  @Scheduled(fixedDelayString = "${bone.integration.outbox.relay-interval-ms:5000}")
  public void relayPending() {
    if (!properties.isEnabled()) {
      return;
    }
    int sent = outboxRelay.relayBatch();
    if (sent > 0) {
      log.debug("Outbox 中继完成: sent={}", sent);
    }
  }
}
