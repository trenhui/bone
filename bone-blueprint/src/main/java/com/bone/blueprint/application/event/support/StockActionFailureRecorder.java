package com.bone.blueprint.application.event.support;

import com.bone.blueprint.application.integration.event.OrderStockActionFailedIntegrationEvent;
import com.bone.blueprint.application.port.out.OrderOutboxPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存动作失败落 Outbox 的记录器（REQUIRES_NEW）。
 *
 * <p>库存预留/扣减在 {@code OrderCreatedEventHandler} / {@code OrderPaidEventHandler} 的
 * <strong>AFTER_COMMIT</strong> 上下文远程执行，该上下文<strong>无业务事务</strong>。要把失败事实落 Outbox（端口声明
 * MANDATORY，要求调用方已有事务）， 必须在本组件内新起一个独立短事务——与 {@code PaymentSucceededEventHandler} 确认订单支付用
 * REQUIRES_NEW 同形。
 *
 * <p><b>为何不直接在 handler 里写</b>：handler 是 AFTER_COMMIT「读 + 远程」的简单形态，引入事务会污染其职责；
 * 收口到一处记录器，两条库存链路共用，且未来加「失败重试 / 告警升级」也只改一处。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockActionFailureRecorder {

  private final OrderOutboxPort orderOutboxWriter;

  /**
   * 在独立事务中把库存动作失败事实写入 Outbox，使其可观测、可接告警/工单。
   *
   * <p>REQUIRES_NEW：调用方（AFTER_COMMIT handler）无事务，本方法自带事务以满足端口的 MANDATORY 约束。
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(OrderStockActionFailedIntegrationEvent event) {
    orderOutboxWriter.appendStockActionFailed(event);
  }
}
