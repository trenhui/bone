package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.order.read.OrderHeadRow;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 取消超时未支付订单定时任务。
 *
 * <p>定时扫描仍处于 CREATED 且创建时间早于超时阈值的订单，逐笔下发 {@link CancelOrderCommand}（**经应用层 Handler 执行，adapter 不直连
 * domain 仓储**）。
 *
 * <p>订单取消后由 {@code OrderCancelledEvent} 的 AFTER_COMMIT 订阅释放库存预留（最终一致）。
 *
 * <p><b>全租户扫描（E-4.4）</b>：定时任务线程无请求上下文，此前用 {@code TenantProvider.currentTenantId()} 取到的只会是
 * <strong>降级后的平台租户 0</strong>——结果是除平台租户外的超时订单永不取消，而日志仍显示"扫描完成"。 现改为全租户读端口 {@code
 * findCreatedExpiredBeforeAllTenants}，并把扫描行的 {@code tenantId} <strong>显式携带</strong>进命令
 * （异步分支必须显式传租户，不能依赖线程上下文）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelExpiredOrderJob {

  private final OrderReadPort orderReadPort;
  private final CancelOrderCommandHandler cancelOrderCommandHandler;

  /** 超时阈值（分钟）：订单创建后超过该时长未支付即取消。 */
  private static final long ORDER_TIMEOUT_MINUTES = 30;

  @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行一次
  public void cancelExpiredOrders() {
    Instant before = Instant.now().minusSeconds(ORDER_TIMEOUT_MINUTES * 60);
    List<OrderHeadRow> expired = orderReadPort.findCreatedExpiredBeforeAllTenants(before);
    log.info(
        "[全租户扫描] 超时未支付订单扫描完成: 阈值={}min, 命中={} 笔（E-4.4 平台运维入口，README 已登记）",
        ORDER_TIMEOUT_MINUTES,
        expired.size());
    for (OrderHeadRow row : expired) {
      try {
        cancelOrderCommandHandler.handle(
            new CancelOrderCommand(row.getOrderId(), row.getTenantId()));
      } catch (Exception e) {
        // 记录日志，继续处理下一笔（如状态已迁移导致 cancel 抛错，属预期跳过）
        log.error("取消超时订单失败: orderId={}, tenantId={}", row.getOrderId(), row.getTenantId(), e);
      }
    }
  }
}
