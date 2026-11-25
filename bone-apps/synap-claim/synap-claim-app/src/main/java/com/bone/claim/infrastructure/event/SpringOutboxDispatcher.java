package com.bone.claim.infrastructure.event;

import com.bone.claim.application.port.out.InsurerPushGatewayPort;
import com.bone.claim.application.port.out.InsurerPushGatewayPort.InsurerPushMessage;
import com.bone.claim.domain.model.event.GenericOutboxEvent;
import com.bone.claim.domain.port.out.OutboxPort;
import com.bone.claim.domain.port.out.OutboxRecord;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OutboxPort 落地实现（投递介质按事件类型路由）。
 *
 * <p>默认把发件箱载荷（JSON）反序列化为 {@link GenericOutboxEvent} 后经进程内事件总线派发——一期 afterCommit 已即时投送原始类型事件， 此处仅作
 * afterCommit 失败时的兜底重投。
 *
 * <p><b>INSURER_PUSH 真实出站（ADR-0021 二期）</b>：结案报送保司是跨系统事实，不再只进进程内总线。当 {@code
 * InsurerPushGatewayPortAdapter#isConfigured()} 为真（配了保司端点）时，该类事件改走真实 HTTP POST；未配置则回落进程内总线，
 * 保持一期行为不变。路由只认事件类型，业务层无需改动，失败仍由 {@code OutboxApplicationService} 统一重试/死信。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringOutboxDispatcher implements OutboxPort {

  static final String EVENT_INSURER_PUSH = "INSURER_PUSH";

  private final DomainEventPublisher domainEventPublisher;
  private final ObjectMapper objectMapper;
  private final InsurerPushGatewayPort insurerPushGatewayPort;

  @Override
  public void dispatch(OutboxRecord record) {
    if (EVENT_INSURER_PUSH.equals(record.eventType()) && insurerPushGatewayPort.isConfigured()) {
      String receipt =
          insurerPushGatewayPort.push(
              new InsurerPushMessage(
                  record.tenantId(),
                  record.aggregate(),
                  record.aggregateId(),
                  record.eventType(),
                  record.payload()));
      log.info("保司报送经 HTTP 出站完成: claimId={} receipt={}", record.aggregateId(), receipt);
      return;
    }
    publishInProcess(record);
  }

  /** 一期行为：发件箱载荷经进程内领域事件总线派发。 */
  private void publishInProcess(OutboxRecord record) {
    try {
      @SuppressWarnings("unchecked")
      java.util.Map<String, Object> payload =
          objectMapper.readValue(record.payload(), java.util.Map.class);
      domainEventPublisher.publish(
          new GenericOutboxEvent(
              record.eventType(), record.aggregate(), record.aggregateId(), payload));
    } catch (Exception e) {
      throw new RuntimeException("outbox dispatch failed: " + record, e);
    }
  }
}
