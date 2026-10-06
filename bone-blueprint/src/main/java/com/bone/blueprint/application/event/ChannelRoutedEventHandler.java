package com.bone.blueprint.application.event;

import com.bone.blueprint.application.port.out.ChannelExtensionPort;
import com.bone.blueprint.domain.model.channel.Channel;
import com.bone.blueprint.domain.model.channel.event.ChannelRoutedEvent;
import com.bone.blueprint.domain.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 渠道路由可观测投影 —— 把 {@link ChannelRoutedEvent} 落成 {@code bp_channel} 上的 {@code extImplCode} / {@code
 * lastSyncAt}。
 *
 * <p><b>为何必须是「提交后 + 独立事务」，而不是在业务事务里顺手写渠道</b>：拉单事务已持久化 {@code Order}、上架事务已持久化 {@code
 * ChannelProduct}，再写 {@code Channel} 就成了一事务两聚合（R9）。拆到本处理器后：
 *
 * <ul>
 *   <li>业务事务只管自己的聚合，锁范围与失败域都不再被可观测字段扩大；
 *   <li>可观测写入失败（渠道被并发删除、数据库抖动）只丢一条投影，不会把已成功的订单/上架一起回滚——
 *       <strong>这正是「可观测」应有的失败语义</strong>，反过来（为了写渠道而回滚订单）是本末倒置。
 * </ul>
 *
 * <p><b>为何用 REQUIRES_NEW</b>：AFTER_COMMIT 阶段外层事务已结束，必须新开事务才能真正落库； 且新事务与业务事务互不牵连，一方回滚不影响另一方。
 *
 * <p><b>租户ID取自事件而非线程上下文</b>：提交后线程变量可能被回收，显式从事件取值可避免投影写到错误的租户。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRoutedEventHandler {

  private final ChannelRepository channelRepository;
  private final ChannelExtensionPort channelExtensionPort;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handle(ChannelRoutedEvent event) {
    if (event == null || event.channelCode() == null) {
      return;
    }
    long tenantId = event.tenantId() == null ? 0L : event.tenantId();
    Channel channel = channelRepository.findByCode(tenantId, event.channelCode());
    if (channel == null) {
      log.warn(
          "[{}] 渠道不存在，跳过路由投影 | tenantId={} | useCase={}",
          event.channelCode(),
          tenantId,
          event.useCase());
      return;
    }
    channel.markRoutedImpl(
        channelExtensionPort.resolveImplCode(event.channelCode(), event.useCase()));
    channel.markSynced(event.occurredAt() == null ? java.time.Instant.now() : event.occurredAt());
    channelRepository.update(channel);
    log.debug(
        "[{}] 渠道路由投影已刷新 | impl={} | useCase={}",
        event.channelCode(),
        channel.getExtImplCode(),
        event.useCase());
  }
}
