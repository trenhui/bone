package com.bone.blueprint.infrastructure.messaging.idempotency;

import com.bone.blueprint.application.port.out.ConsumedEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link ConsumedEventPort} 的实现：靠 {@code (consumer_group, event_id)} 唯一键原子抢占。
 *
 * <p><b>谁调用我 / 为哪条链路服务</b>：仅 RocketMQ 消费端链路（{@code OrderPaidIntegrationEventConsumer} → 本类 → {@code
 * OrderPaidIntegrationListener}）。 带 {@code @ConditionalOnProperty(mq-enabled=true)}，默认不装配。
 *
 * <p><b>消费端幂等 vs 请求幂等</b>：本类是「消息消费不重复」，靠唯一键抢占；同模块 {@code IdempotencyPortAdapter} 是「API 请求可重放」， 靠快照
 * + TTL。两者唯一键、TTL、被谁消费都不同，命名相似但职责无交集。
 *
 * <p><b>为什么用 insert 而不是「先查后写」</b>：先查后写在并发重复投递下两个线程都会查到「不存在」，然后双双写入、 双双执行业务动作——恰好是幂等要防的场景。插入 +
 * 唯一键冲突由数据库保证只有一方成功。
 *
 * <p><b>为什么捕获 {@link DataIntegrityViolationException}</b>：它是 Spring 对完整性约束冲突的翻译（{@code
 * DuplicateKeyException} 是其子类），覆盖「唯一键冲突」与其他完整性失败。仅在此处把冲突翻译为「已处理」； 其他异常（如表不存在、连接失败）继续抛出，让 MQ
 * 重试而不是静默放过——静默放过等于丢事件。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "bone.blueprint.outbox.mq-enabled", havingValue = "true")
@RequiredArgsConstructor
public class ConsumedEventPortAdapter implements ConsumedEventPort {

  private final ConsumedEventRepository repository;

  @Override
  public boolean tryClaim(String consumerGroup, String topic, String eventId, long tenantId) {
    try {
      repository.insert(ConsumedEventRecord.claimed(consumerGroup, topic, eventId, tenantId));
      return true;
    } catch (DataIntegrityViolationException ex) {
      log.debug("事件重复投递，按 eventId 幂等跳过: consumerGroup={}, eventId={}", consumerGroup, eventId);
      return false;
    }
  }
}
