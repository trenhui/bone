package com.bone.blueprint.infrastructure.gateway.inventory;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 库存 ACL 出站适配器（Mock 实现，供样板工程本地演示）。
 *
 * <p><b>为何包名是 {@code gateway/inventory} 而不是 {@code
 * gateway/mock}</b>：包路径表达<strong>被隔离的外部系统</strong>（E-10.2 的 {@code domain/gateway →
 * infrastructure/gateway}），Mock 与否由类名承载（E-13.3 规定占位实现统一 {@code Mock}
 * 前缀）。按实现性质分包会把类名已经说过的信息在包路径重复一遍，并与同级的 {@code gateway/payment} 形成两套分类标准。
 *
 * <p><b>样板占位警告（生产前必须替换）</b>：当前为 Mock 实现，仅用于本地跑通下单→支付→库存流程演示， 并非真实库存服务对接——{@link #checkStock}
 * 恒放行（不校验真实库存），{@code reserve/confirm/release} 为无操作。 接入真实库存服务前必须：① 实现远程客户端（Feign / HTTP）并做协议转换；②
 * 外部错误语义/空值在此边界转为领域异常（E-10），禁止把外部 {@code null}/异常穿透到 domain；③ 补充<b>契约测试</b>（打桩外部响应，验证翻译与错误语义隔离，E-10
 * 强制项）。
 *
 * <p><b>启动告警</b>：prod profile 下直接阻断启动（与 {@code MockPaymentGatewayAdapter} / {@code
 * MockPaymentSignaturePortAdapter} 同模式）。
 */
@Slf4j
@Component
public class MockInventoryGatewayAdapter
    implements InventoryGateway, EnvironmentAware, InitializingBean {

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public void afterPropertiesSet() {
    boolean prod = environment != null && environment.acceptsProfiles("prod");
    String message =
        "库存服务当前装配的是 Mock 实现（checkStock 恒放行、reserve/confirm/release 为无操作）；生产环境必须替换为真实库存网关";
    if (prod) {
      throw new IllegalStateException("[配置事故] " + message + " — 已阻断 prod 启动");
    } else {
      log.warn("{}", message);
    }
  }

  /**
   * 占位实现为何必须打日志：{@code reserve/confirm/release} 都是<strong>空方法</strong>，若再不打日志， 本地演示「下单 → 预留 → 支付 →
   * 扣减」时就<strong>既无数据变化、也无任何输出</strong>——外部完全无法区分「链路正常执行」与「链路根本没被触发」。 这正是 {@code
   * OrderItemInventoryExecutor} 里明令禁止的失败形态：看起来正常运行，其实什么都没做。
   *
   * <p>故三个占位方法统一以 INFO 留痕「动作 + 关键参数 + 本次不做真实库存变更」，让演示链路可观测、
   * 也让替换真实实现时有一致的对照基线。接真实库存服务后，这些日志应替换/下沉为客户端调用日志。
   */
  private void traceNoop(String action, Long orderId, Long productId, Integer quantity) {
    log.info(
        "[MockInventory] {}（占位，不产生真实库存变更）: orderId={}, productId={}, quantity={}",
        action,
        orderId,
        productId,
        quantity);
  }

  @Override
  public boolean checkStock(Long productId, Integer quantity) {
    // 占位：真实实现对库存服务发起校验，此处仅做参数非空放行，便于本地演示。
    return productId != null && quantity != null && quantity > 0;
  }

  @Override
  public void reserveStock(Long orderId, Long productId, Integer quantity) {
    traceNoop("reserveStock 预留库存", orderId, productId, quantity);
  }

  @Override
  public void confirmStock(Long orderId, Long productId, Integer quantity) {
    traceNoop("confirmStock 确认扣减", orderId, productId, quantity);
  }

  @Override
  public void releaseStock(Long orderId) {
    traceNoop("releaseStock 释放预留", orderId, null, null);
  }
}
