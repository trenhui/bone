package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.ConsumedEventPort;
import com.bone.blueprint.domain.integration.event.OrderPaidIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 消费「订单已支付」集成事件的应用用例（入站适配器 {@code OrderPaidIntegrationMqListener} → 本类）。
 *
 * <p><b>为什么消费者要有应用用例，而不是把逻辑写在监听器里</b>：幂等抢占必须与业务动作处在<strong>同一个事务</strong>。
 * 若先抢占、之后才在一个独立事务里执行业务动作，一旦业务动作失败，MQ 重试会被自己刚写的幂等记录挡住——重复消费变成了
 * <strong>丢事件</strong>（比重复消费更严重）。事务边界是应用层职责（E-3.5），入站适配器只做协议解析与异常翻译。
 *
 * <p><b>真实下游系统在此实现业务动作</b>：本样板只记录消费结果（示例的下游无需写本上下文数据）。把动作放在这里， 就能与 {@link
 * ConsumedEventPort#tryClaim} 共享事务——失败一起回滚，重试可重新处理。
 *
 * <p>注意：消费「订单已支付」事件不得反向再次确认订单或再次发起支付（会与订单上下文的聚合状态机冲突）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPaidConsumptionApplicationService {

  private final ConsumedEventPort consumedEventPort;

  /**
   * 幂等消费一次集成事件。
   *
   * @param eventId 信封 eventId（幂等键，来自信封而非载荷）
   * @param event 反序列化后的集成事件
   * @param consumerGroup 消费组：同一 eventId 允许被不同消费组各消费一次
   * @param topic 来源 Topic
   * @return {@code true} 本次真正消费；{@code false} 重复投递已跳过
   */
  @Transactional
  public boolean consume(
      String eventId, OrderPaidIntegrationEvent event, String consumerGroup, String topic) {
    long tenantId = event.tenantId() != null ? event.tenantId() : 0L;
    if (!consumedEventPort.tryClaim(consumerGroup, topic, eventId, tenantId)) {
      return false;
    }
    // 真实下游在此执行业务动作（写自己的库 / 发通知 / 入对账系统），与本抢占同事务提交
    log.info(
        "[Biz] 消费 OrderPaidIntegrationEvent: eventId={}, orderId={}, tenantId={}, amount={},"
            + " schemaVersion={}",
        eventId,
        event.orderId(),
        tenantId,
        event.amount(),
        event.schemaVersion());
    return true;
  }
}
