package com.bone.blueprint.adapter.schedule;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.blueprint.application.command.handler.CancelOrderCommandHandler;
import com.bone.blueprint.domain.gateway.OrderReadPort;
import com.bone.blueprint.domain.gateway.TenantProvider;
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
 * domain 仓储**——与 {@code CloseExpiredPaymentJob} 同模式，§15）。
 *
 * <p>订单取消后由 {@code OrderCancelledEvent} 的 AFTER_COMMIT 订阅释放库存预留（最终一致）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CancelExpiredOrderJob {

  private final OrderReadPort orderReadPort;
  private final TenantProvider tenantProvider;
  private final CancelOrderCommandHandler cancelOrderCommandHandler;

  /** 超时阈值（分钟）：订单创建后超过该时长未支付即取消。 */
  private static final long ORDER_TIMEOUT_MINUTES = 30;

  @Scheduled(cron = "0 0/5 * * * ?") // 每5分钟执行一次
  public void cancelExpiredOrders() {
    long tenantId = tenantProvider.currentTenantId();
    Instant before = Instant.now().minusSeconds(ORDER_TIMEOUT_MINUTES * 60);
    List<OrderHeadRow> expired = orderReadPort.findCreatedExpiredBefore(tenantId, before);
    if (expired.isEmpty()) {
      return;
    }
    log.info("发现超时未支付订单: {} 个", expired.size());
    for (OrderHeadRow row : expired) {
      try {
        cancelOrderCommandHandler.handle(new CancelOrderCommand(row.getOrderId()));
      } catch (Exception e) {
        // 记录日志，继续处理下一笔（如状态已迁移导致 cancel 抛错，属预期跳过）
        log.error("取消超时订单失败: orderId={}", row.getOrderId(), e);
      }
    }
  }
}
