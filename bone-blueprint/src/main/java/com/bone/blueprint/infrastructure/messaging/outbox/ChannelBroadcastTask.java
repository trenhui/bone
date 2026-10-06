package com.bone.blueprint.infrastructure.messaging.outbox;

import com.bone.core.domain.AggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 渠道库存广播任务——<strong>基础设施持久化模型，非业务聚合</strong>。
 *
 * <p><b>为何与 {@code OrderOutboxRecord} 分表而不是复用 {@code bp_outbox}</b>：{@code bp_outbox}
 * 装的是「待发集成事件」（有信封、有 topic、有分区键）， 投递目标是 MQ；本表装的是「一次库存覆盖同步」，投递目标是<strong>渠道开放平台
 * HTTP</strong>，并额外需要重试上限、退避、死信与人工重试。 两种投递的目标系统、失败语义、运维手段都不同，混在一张表里会让「重试」这个动作含义模糊（重发 MQ
 * 事件？重推渠道库存？）。
 *
 * <p><b>为何在 infrastructure</b>：与 {@code OrderOutboxRecord} 同理——只有技术状态，无业务不变量。 真正的业务规则「哪些渠道该收到这次广播」
 * 属于 {@code ChannelProduct}（商品是否 ONLINE），由应用层判定后才入队。
 *
 * <p><b>target_stock 是覆盖值而非增量</b>：渠道库存接口（{@code taobao.quantity.update} / {@code
 * goods.update_stock} 等）都是「设置为某个数」， 传增量会导致渠道库存与实物库存越差越多。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bp_channel_broadcast_task")
public class ChannelBroadcastTask extends AggregateRoot<Long> {

  private Long tenantId;
  private Long productId;
  private String channelCode;
  private String channelProductId;
  private String productName;
  private Integer targetStock;
  private BroadcastTaskStatus status;
  private Integer retryCount;
  private Integer maxRetry;
  private Instant nextRetryAt;
  private String lastError;
  private Long mergedIntoId;

  /** 入队时间：合并同商品同渠道的旧任务时用它判断「谁更新」（后入队的库存值更新）。 */
  private Instant createdAt;

  /**
   * 行更新时间（映射 DB 的 {@code updated_at ON UPDATE CURRENT_TIMESTAMP}）。
   *
   * <p><b>它为什么必须映射</b>：卡死自愈靠「PROCESSING 且 updated_at 早于阈值」判定上次中继崩溃遗留。 若实体不映射这一列， 就只能用字符串字段名 {@code
   * "updatedAt"} 构造条件，SDK 会在运行期抛 {@code UndefinedFieldException}—— 且这个异常只在<b>调度第二轮之后</b>出现（第一次
   * pending 扫描不碰该字段），本地单测与编译都发现不了。
   */
  private Instant updatedAt;

  /** 入队一条待投递的库存广播任务。 */
  public static ChannelBroadcastTask pending(
      Long id,
      Long tenantId,
      Long productId,
      String channelCode,
      String channelProductId,
      String productName,
      int targetStock,
      int maxRetry) {
    ChannelBroadcastTask task = new ChannelBroadcastTask();
    task.setId(id);
    task.tenantId = tenantId != null ? tenantId : 0L;
    task.productId = productId;
    task.channelCode = channelCode;
    task.channelProductId = channelProductId;
    task.productName = productName;
    task.targetStock = targetStock;
    task.status = BroadcastTaskStatus.PENDING;
    task.retryCount = 0;
    task.maxRetry = maxRetry <= 0 ? 5 : maxRetry;
    task.nextRetryAt = Instant.now();
    task.createdAt = Instant.now();
    // 必须显式赋值：SDK 的 insert 会把实体字段一并写入，若 updatedAt 为 null 会撞
    // 「Column 'updated_at' cannot be null」（DB 的 ON UPDATE CURRENT_TIMESTAMP 只在 UPDATE 时生效，管不到
    // INSERT）。
    task.updatedAt = task.createdAt;
    return task;
  }

  /**
   * 抢占：PENDING → PROCESSING（CAS 由调用方经 SDK {@code updateByCriteria} 的 {@code WHERE status='PENDING'}
   * 保证）。
   */
  public void markClaimed() {
    this.status = BroadcastTaskStatus.PROCESSING;
  }

  /** 投递成功 → SENT。 */
  public void markSent() {
    this.status = BroadcastTaskStatus.SENT;
    this.lastError = null;
  }

  /** 被更新的任务合并掉（同商品同渠道只投最新库存）：标 SENT 并记下被合并到的任务ID。 */
  public void markMergedInto(Long newerTaskId) {
    this.status = BroadcastTaskStatus.SENT;
    this.mergedIntoId = newerTaskId;
  }

  /**
   * 投递失败。
   *
   * @param error 渠道原始失败原因（截断到 512，避免超长报文把行撑爆）
   * @param nextRetryAt 下次可重试时间；为 {@code null} 表示不再重试（直接死信）
   */
  public void markFailed(String error, Instant nextRetryAt) {
    this.retryCount = (retryCount == null ? 0 : retryCount) + 1;
    this.lastError =
        error == null ? null : (error.length() > 512 ? error.substring(0, 512) : error);
    if (nextRetryAt == null || (maxRetry != null && retryCount >= maxRetry)) {
      this.status = BroadcastTaskStatus.FAILED;
    } else {
      this.status = BroadcastTaskStatus.PENDING;
      this.nextRetryAt = nextRetryAt;
    }
  }

  /** 人工重试：死信/已合并的任务回到待投递。 */
  public void markRetriedManually() {
    this.status = BroadcastTaskStatus.PENDING;
    this.retryCount = 0;
    this.lastError = null;
    this.mergedIntoId = null;
    this.nextRetryAt = Instant.now();
  }
}
