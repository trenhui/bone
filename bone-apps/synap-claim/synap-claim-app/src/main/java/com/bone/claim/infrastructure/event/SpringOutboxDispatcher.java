package com.bone.claim.infrastructure.event;

import com.bone.claim.domain.model.event.GenericOutboxEvent;
import com.bone.claim.domain.port.out.OutboxPort;
import com.bone.claim.domain.port.out.OutboxRecord;
import com.bone.core.domain.event.DomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * OutboxPort 进程内落地实现（一期兜底）。
 *
 * <p>把发件箱载荷（JSON）反序列化为 {@link GenericOutboxEvent} 后经进程内事件总线派发。一期 afterCommit 已即时投送原始类型事件， 此处仅作
 * afterCommit 失败时的兜底重投；二期接 MQ 后替换为发往消息中间件，调用方无需改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringOutboxDispatcher implements OutboxPort {

  private final DomainEventPublisher domainEventPublisher;
  private final ObjectMapper objectMapper;

  @Override
  public void dispatch(OutboxRecord record) {
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
