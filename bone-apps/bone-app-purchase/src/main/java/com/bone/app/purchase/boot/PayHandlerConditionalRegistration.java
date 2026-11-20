package com.bone.app.purchase.boot;

import com.bone.app.purchase.PurchaseOrderPayHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 存量付款处理器的唯一注册入口（D-06 硬隔离）。
 *
 * <p>仅当 {@code procura.purchase-pay-handler.enabled=true} 时才注册 {@link PurchaseOrderPayHandler}； 缺省
 * / 未配置 / false 时该 bean 不存在，运行期动作无法按名解析到处理器，实现 fail-closed。 配合 {@link
 * PurchaseComponentScanConfiguration} 对该类的扫描排除，确保不存在第二条注册路径。
 */
@Configuration(proxyBeanMethods = false)
public class PayHandlerConditionalRegistration {

  @Bean("purchaseOrderPayHandler")
  @ConditionalOnProperty(
      name = "procura.purchase-pay-handler.enabled",
      havingValue = "true",
      matchIfMissing = false)
  public PurchaseOrderPayHandler purchaseOrderPayHandler() {
    return new PurchaseOrderPayHandler();
  }
}
