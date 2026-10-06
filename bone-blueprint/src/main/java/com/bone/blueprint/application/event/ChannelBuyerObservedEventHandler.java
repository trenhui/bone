package com.bone.blueprint.application.event;

import com.bone.blueprint.application.ChannelBuyerApplicationService;
import com.bone.blueprint.domain.model.channelbuyer.event.ChannelBuyerObservedEvent;
import com.bone.core.tenant.context.TenantContextRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 渠道买家观测事件处理器 —— 提交后登记/累计「渠道买家 ↔ 内部客户」映射。
 *
 * <p><b>为何在 AFTER_COMMIT 而不是拉单事务内直接写</b>：拉单主事务只允许操作 {@code Order} 一个聚合（R9）， 映射是另一个聚合。 写在这里由 {@code
 * ChannelBuyerApplicationService#observeChannelBuyer} 以 {@code REQUIRES_NEW} 开独立事务完成。
 *
 * <p><b>必须显式切租户</b>：AFTER_COMMIT 线程没有 HTTP 请求上下文，{@code TenantContext} 为 {@code null}， 而 SDK 写路径按
 * ADR-0029 失败关闭（缺租户直接抛异常）。若不切租户，本处理器会每次都失败——而失败被下面的 catch 吞掉， 表现为「映射永远不建立」且无任何报错。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelBuyerObservedEventHandler {

  private final ChannelBuyerApplicationService channelBuyerApplicationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onChannelBuyerObserved(ChannelBuyerObservedEvent event) {
    if (event == null) {
      return;
    }
    // 判定用 < 0（外加 null），不是 <= 0：tenantId=0 是合法的平台租户，
    // 当成脏数据会让平台租户的渠道买家映射永不建立。
    if (event.tenantId() == null || event.tenantId() < 0) {
      log.error(
          "AFTER_COMMIT 线程缺少有效的 tenantId，渠道买家映射登记跳过（疑似事件数据脏）: channel={}, buyerId={}, orderId={}",
          event.channelCode(),
          event.channelBuyerId(),
          event.orderId());
      return;
    }
    if (event.channelBuyerId() == null || event.channelBuyerId().isBlank()) {
      // 无映射键：订单已落「未知客户」维度，这里无事可做，记 warn 便于回查渠道报文。
      log.warn("[{}] 渠道订单未携带买家ID，无法登记映射 | orderNoId={}", event.channelCode(), event.orderId());
      return;
    }
    TenantContextRunner.runAs(
        event.tenantId(), () -> channelBuyerApplicationService.observeChannelBuyer(event));
  }
}
