package com.bone.blueprint.application.port.out;

import java.time.Instant;

/**
 * 渠道库存广播任务的查询端口（技术出站端口）。
 *
 * <p><b>为何任务表在 infrastructure
 * 却还要一个查询端口</b>：广播任务是<strong>技术投递状态</strong>（PENDING/PROCESSING/SENT/FAILED）， 没有业务不变量，按 Bone
 * 的分层约定不能进 {@code domain}；但 application 需要把任务状态展示给运营、并提供人工重试入口， 而 application<strong>不能反向依赖
 * infrastructure</strong>（P0-1 依赖方向）。 因此这里声明一个只读端口 + 一个与持久化无关的视图 record。
 *
 * <p>写入方向不需要端口的对应查询方法：写入只发生在「入队」这一个动作里，由 {@link ChannelBroadcastOutboxPort} 承担。
 */
public interface ChannelBroadcastQueryPort {

  /** 任务视图（与持久化模型解耦，字段以运营需要为准，不与表结构一一对应）。 */
  record TaskView(
      String id,
      String productId,
      String channelCode,
      String channelProductId,
      String productName,
      Integer targetStock,
      String status,
      Integer retryCount,
      Instant nextRetryAt,
      String lastError,
      Long mergedIntoId,
      Instant createdAt) {}

  /**
   * 按状态分页查询（带真实总数）。
   *
   * @param status 状态字符串（{@code PENDING/PROCESSING/SENT/FAILED}）；为空则不过滤
   */
  com.bone.core.model.PageResult<TaskView> pageByStatus(
      Long tenantId, String status, int page, int size);

  /** 单条查询（人工重试前校验用）。 */
  TaskView findById(Long tenantId, String id);
}
