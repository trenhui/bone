package com.bone.blueprint.application.port.out;

/**
 * 渠道库存广播 Outbox 写入端口——<strong>技术出站端口，声明于 {@code application/port/out}，实现于 infrastructure</strong>（与
 * {@code OrderOutboxPort} 同构：技术写不进 {@code domain/gateway}）。
 *
 * <p><b>解决什么</b>：原先库存变更后在业务事务内<strong>同步循环</strong>调用各渠道的库存同步接口。三个致命问题：
 *
 * <ol>
 *   <li><b>事务被网络 IO 拖长</b>：4 个渠道串行 HTTP，最坏情况（超时+重试）库存事务持有行锁数十秒， 连接池被同步 IO 占满；
 *   <li><b>两侧不一致无法区分</b>：渠道 A 已改成功、渠道 B 超时 → 事务回滚但渠道 A 的库存已变， 没有任何机制能把它对齐回来；
 *   <li><b>失败无处可查</b>：只留一行日志，渠道库存长期停留在旧值，直到用户在其他渠道下单失败才被发现。
 * </ol>
 *
 * <p>改为 Outbox 后：库存变更与「待广播」同事务提交（原子），中继异步投递（不占业务事务）， 失败自动退避重试、耗尽转死信， 并提供人工重试入口。
 *
 * <p><b>调用约束（关键）</b>：实现类用 {@code Propagation.MANDATORY} 强制「必须在业务写事务内调用」。 若允许无事务调用，
 * 容器会悄悄新起事务，出现「库存已提交但广播任务没入队」——而库存已变、渠道未变正是超卖的直接成因。
 */
public interface ChannelBroadcastOutboxPort {

  /**
   * 入队一条库存广播任务（<b>须与库存变更同事务</b>）。
   *
   * @param tenantId 租户ID
   * @param productId 内部商品ID
   * @param channelCode 渠道码
   * @param channelProductId 渠道侧商品ID（必须已上架成功才有值；为空表示该渠道还没上架成功，中继会跳过并记原因）
   * @param productName 商品名快照（失败时便于运营定位）
   * @param targetStock 目标库存（覆盖值，非增量）
   */
  void appendStockBroadcast(
      Long tenantId,
      Long productId,
      String channelCode,
      String channelProductId,
      String productName,
      int targetStock);

  /**
   * 把一条失败/被合并的任务放回待投递队列（运营人工重试）。
   *
   * <p><b>为什么人工重试要走端口而不是让 Controller 直接改表</b>：状态机（哪些状态可回队列、重试计数怎么清零）是投递组件的内部约定， Controller
   * 直改表等于把这份约定复制到 Web 层，改状态机时必漏。
   *
   * @param taskId 任务ID
   * @return 是否成功放回（false 表示任务不存在或当前状态不允许重试）
   */
  boolean requeueFailed(Long tenantId, String taskId);
}
