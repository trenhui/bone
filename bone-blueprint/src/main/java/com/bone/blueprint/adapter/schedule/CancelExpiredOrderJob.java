package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.OrderApplicationService;
import com.bone.blueprint.application.command.CancelOrderCommand;
import com.bone.blueprint.domain.order.projection.OrderHeadProjection;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.tenant.context.TenantContextRunner;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 取消超时未支付订单定时任务。
 *
 * <p>定时扫描仍处于 CREATED 且创建时间早于超时阈值的订单，逐笔下发 {@link CancelOrderCommand}（经 {@link
 * OrderApplicationService} 执行）。
 *
 * <p><b>本类为何直连域仓储（本模块唯一的受控例外）</b>：扫描入口是全租户方法 {@link
 * OrderRepository#findExpiredOrdersAllTenants}（{@code @TenantScope(ALL)}），它是 ADR-0030 显式授权的
 * <strong>平台运维旁路</strong>——定时线程无请求上下文，按「当前租户」扫描会退化为平台租户 0。该方法按 ADR-0030 §2 目标形态即<strong>由定时 Job
 * 调用</strong>，并受 {@code all_tenants_scan_only_by_schedule} 与本模块 {@code ArchitectureTest}
 * 双重约束（仅本类被豁免）；<strong>其余入站适配器（web / rpc / messaging）不得复制此形态</strong>。
 *
 * <p>订单取消后由 {@code OrderCancelledEvent} 的 AFTER_COMMIT 订阅释放库存预留（最终一致）。
 *
 * <p><b>全租户扫描（E-2）</b>：定时任务线程无请求上下文，此前用 {@code TenantPort.currentTenantId()} 取到的只会是
 * <strong>降级后的平台租户 0</strong>——结果是除平台租户外的超时订单永不取消，而日志仍显示"扫描完成"。 现改为全租户读端口 {@code
 * findExpiredOrdersAllTenants}，并把扫描行的 {@code tenantId} <strong>显式携带</strong>进命令
 * （异步分支必须显式传租户，不能依赖线程上下文）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelExpiredOrderJob {

  private final OrderRepository orderRepository;
  private final OrderApplicationService orderApplicationService;

  /**
   * 超时阈值（分钟）：订单创建后超过该时长未支付即取消。
   *
   * <p><b>为何可配置</b>：超时窗口是<strong>运营策略</strong>（各业务线 / 渠道的窗口并不相同），硬编码为 {@code static final}
   * 意味着每次调整都要改代码、走一次发版，而本任务的行为完全由该阈值决定。
   */
  @Value("${bone.blueprint.schedule.order-timeout-minutes:30}")
  private long orderTimeoutMinutes;

  /** 扫描周期可配置（默认每 5 分钟扫一次）。 */
  @Scheduled(cron = "${bone.blueprint.schedule.cancel-expired-orders-cron:0 0/5 * * * ?}")
  public void cancelExpiredOrders() {
    Instant before = Instant.now().minusSeconds(orderTimeoutMinutes * 60);
    List<OrderHeadProjection> expired =
        orderRepository.findExpiredOrdersAllTenants(Timestamp.from(before));
    int cancelled = 0;
    int failed = 0;
    for (OrderHeadProjection row : expired) {
      try {
        // 调度线程无请求上下文：D2 起写路径改走 SDK update(entity)，租户由 TenantContext 提供，
        // 必须用 runAs 显式声明租户（ADR-0029 失败关闭），否则 MissingTenantContextException 被下方 catch 静默吞掉。
        TenantContextRunner.runAs(
            row.getTenantId(),
            () ->
                orderApplicationService.cancel(
                    new CancelOrderCommand(row.getOrderId(), row.getTenantId())));
        cancelled++;
      } catch (Exception e) {
        failed++;
        // 记录日志，继续处理下一笔（如状态已迁移导致 cancel 抛错，属预期跳过）
        log.error("取消超时订单失败: orderId={}, tenantId={}", row.getOrderId(), row.getTenantId(), e);
      }
    }

    // 必须区分「命中」与「实际完成」：只打命中数时，「命中 10 笔全部失败」与「全部成功」在日志上完全同形，
    // 一次远端故障或事务回滚会看起来像「扫描正常完成」。
    log.info(
        "[全租户扫描] 超时订单取消完成: 阈值={}min, 命中={} 笔, 成功={}, 失败={}（E-2 平台运维入口，README 已登记）",
        orderTimeoutMinutes,
        expired.size(),
        cancelled,
        failed);
  }
}
