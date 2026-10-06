package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.blueprint.application.port.out.ChannelBroadcastOutboxPort;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.query.criteria.Criteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ChannelBroadcastOutboxPort} 的基础设施实现：在<strong>业务事务内</strong>写入广播任务。
 *
 * <p><b>为何用 MANDATORY</b>：「库存已变」与「待广播」必须原子提交。用默认 {@code REQUIRED} 时， 若将来有人从无事务上下文调用，
 * 容器会悄悄新起一个事务——库存提交了但广播任务没入队，且没有任何报错， 结果是渠道库存永久停在旧值直到超卖。 MANDATORY 把这条约束从注释升级为容器级保证（无事务调用直接抛异常）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBroadcastOutboxPortAdapter implements ChannelBroadcastOutboxPort {

  private final ChannelBroadcastTaskRepository broadcastTaskRepository;
  private final com.bone.blueprint.infrastructure.config.ChannelBroadcastProperties properties;

  @Transactional(propagation = Propagation.MANDATORY)
  @Override
  public void appendStockBroadcast(
      Long tenantId,
      Long productId,
      String channelCode,
      String channelProductId,
      String productName,
      int targetStock) {
    if (channelCode == null || channelCode.isBlank()) {
      // 没有渠道码就无法路由到扩展点，入队等于制造一条永远投递不了又看不出原因的死信。
      log.warn("库存广播入队跳过：渠道码为空 | productId={} | tenantId={}", productId, tenantId);
      return;
    }
    ChannelBroadcastTask task =
        ChannelBroadcastTask.pending(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            productId,
            channelCode,
            channelProductId,
            productName,
            targetStock,
            properties.getMaxRetry());
    broadcastTaskRepository.insert(task);
    log.info(
        "库存广播已入队（异步投递）| product={} | channel={} | targetStock={} | taskId={}",
        productId,
        channelCode,
        targetStock,
        task.getId());
  }

  /**
   * 人工重试：CAS 把 FAILED（或被合并的 SENT）翻回 PENDING。
   *
   * <p><b>为何带 {@code WHERE status=原状态} 的 CAS</b>：不做条件更新的话，运营连点两次、或与中继的终态标记并发， 会把已被中继判死的任务复活成
   * PENDING 并清零重试计数 —— 死信被无限重试，限流类失败会变成永动机。
   *
   * <p>不要求 MANDATORY：人工重试是运维动作，本身就是独立事务（由 application 层的 {@code @Transactional} 提供）。
   */
  @Transactional
  @Override
  public boolean requeueFailed(Long tenantId, String taskId) {
    if (taskId == null || taskId.isBlank()) {
      return false;
    }
    long id;
    try {
      id = Long.parseLong(taskId.trim());
    } catch (NumberFormatException ex) {
      return false;
    }
    ChannelBroadcastTask task = broadcastTaskRepository.findById(id);
    if (task == null || task.getTenantId() == null || !task.getTenantId().equals(tenantId)) {
      return false;
    }
    if (task.getStatus() == BroadcastTaskStatus.PENDING
        || task.getStatus() == BroadcastTaskStatus.PROCESSING) {
      // 已在队列中：重复点「重试」不应清零重试计数（那等于让退避失效、绕过退避直接再打渠道）。
      return false;
    }
    BroadcastTaskStatus from = task.getStatus();
    task.markRetriedManually();
    Integer updated =
        broadcastTaskRepository.updateByCriteria(
            task,
            Criteria.<ChannelBroadcastTask>create()
                .eq(ChannelBroadcastTask::getId, id)
                .eq(ChannelBroadcastTask::getTenantId, tenantId)
                .eq(ChannelBroadcastTask::getStatus, from)
                .disableTenantFilter());
    return updated != null && updated > 0;
  }
}
