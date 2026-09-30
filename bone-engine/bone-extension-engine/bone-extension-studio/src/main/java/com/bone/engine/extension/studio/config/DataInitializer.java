package com.bone.engine.extension.studio.config;

import com.bone.core.tenant.context.TenantContext;
import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.extension.Extension;
import com.bone.engine.extension.studio.domain.model.extpoint.ExtPoint;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发环境示例数据，便于前后端联调。
 *
 * <p><b>种子即真实契约</b>：扩展点 interfaceName / 插件 className 指向 bone-blueprint 样板工程中真实存在的
 * {@code @ExtensionPoint} 接口与 {@code @Extension} 实现类——studio 页面展示的扩展点与插件必须能在数据面 （bone-blueprint
 * 进程内经 bone-extension-sdk 注册的代理与路由）真实命中，而不是历史上指向不存在的 {@code com.bone.example.*} 演示类。执行日志不再伪造：真实
 * INVOKE 日志由 blueprint 的 {@code StudioExecutionLogReporter} 上报产生。
 *
 * <p><b>开关</b>：{@code bone.extension.studio.seed.enabled=false} 时跳过（生产环境 application-prod.yml
 * 已配置），此前该配置无人读取属死配置。
 */
@Component
@ConditionalOnProperty(
    name = "bone.extension.studio.seed.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  @Autowired private ExtPointRepository extPointRepository;

  @Autowired private ExtPointReadPort extPointReadPort;

  @Autowired private ExtensionRepository extensionRepository;

  @Autowired private ExtensionReadPort extensionReadPort;

  @Override
  public void run(ApplicationArguments args) {
    // 启动期没有请求上下文，metadata 模式的租户内聚表强制要求租户上下文；
    // 示例数据固定落在演示租户（DEMO=1001），与本地联调登录账号保持一致。
    TenantContext.setTenantId(1001L);
    try {
      seedDemoData();
    } finally {
      TenantContext.clear();
    }
  }

  private void seedDemoData() {
    if (!extPointReadPort.findAll().isEmpty()) {
      return;
    }
    log.info("初始化扩展管理示例数据（真实契约，指向 bone-blueprint 数据面）…");

    // 真实扩展点：blueprint infrastructure/extension/order/ExtensionOrderPriceCalculator（@ExtensionPoint）
    ExtPoint pricing =
        point(
            "订单价格计算",
            "订单下单前价格扩展（blueprint 定价策略）",
            "com.bone.blueprint.infrastructure.extension.order.ExtensionOrderPriceCalculator",
            "order",
            "pricing");
    extPointRepository.save(pricing);

    // 真实实现：blueprint @Extension 类（DefaultOrderPriceCalculator / VipOrderPriceCalculator），
    // 路由维度与注解声明一致（bizCode=ecommerce / useCase=order / scenario=vip）。
    Extension defaultPricing =
        Extension.create(
            pricing.getId(),
            "标准定价扩展",
            "标准定价逻辑（无折扣）",
            "com.bone.blueprint.infrastructure.extension.order.DefaultOrderPriceCalculator");
    defaultPricing.setPriority(100);
    defaultPricing.setBizCode("ecommerce");
    defaultPricing.setUseCase("order");
    defaultPricing.setScenario("standard");
    defaultPricing.setConfig("{\"traffic\":100,\"defaultImpl\":true}");
    defaultPricing.enable();

    Extension vipPricing =
        Extension.create(
            pricing.getId(),
            "VIP 定价扩展",
            "VIP 客户折扣（折扣率优先取元数据定价规则中心）",
            "com.bone.blueprint.infrastructure.extension.order.VipOrderPriceCalculator");
    vipPricing.setPriority(50);
    vipPricing.setBizCode("ecommerce");
    vipPricing.setUseCase("order");
    vipPricing.setScenario("vip");
    vipPricing.setConfig("{\"traffic\":100}");
    vipPricing.enable();

    extensionRepository.save(defaultPricing);
    extensionRepository.save(vipPricing);

    log.info("已初始化 {} 个扩展点、{} 个插件", extPointReadPort.count(), extensionReadPort.count());
  }

  private static ExtPoint point(
      String name, String description, String interfaceName, String domain, String category) {
    ExtPoint extPoint = new ExtPoint();
    extPoint.setName(name);
    extPoint.setDescription(description);
    extPoint.setInterfaceName(interfaceName);
    extPoint.setDomain(domain);
    extPoint.setCategory(category);
    extPoint.setEnabled(true);
    return extPoint;
  }
}
