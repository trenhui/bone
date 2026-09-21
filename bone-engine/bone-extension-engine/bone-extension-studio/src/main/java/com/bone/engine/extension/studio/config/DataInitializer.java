package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.application.command.handler.PluginExecutionLogCommandApplicationService;
import com.bone.engine.extension.studio.domain.gateway.ExtPointReadPort;
import com.bone.engine.extension.studio.domain.gateway.ExtensionReadPort;
import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** 开发环境示例数据，便于前后端联调。 */
@Component
public class DataInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

  @Autowired private ExtPointRepository extPointRepository;

  @Autowired private ExtPointReadPort extPointReadPort;

  @Autowired private ExtensionRepository extensionRepository;

  @Autowired private ExtensionReadPort extensionReadPort;

  @Autowired private PluginExecutionLogCommandApplicationService executionLogCommandHandler;

  @Override
  public void run(ApplicationArguments args) {
    if (!extPointReadPort.findAll().isEmpty()) {
      return;
    }
    log.info("初始化扩展管理示例数据…");

    ExtPoint pricing =
        point(
            "订单价格计算",
            "订单下单前价格扩展",
            "com.bone.example.extension.order.OrderPricingExtPoint",
            "order",
            "pricing");
    ExtPoint validation =
        point(
            "用户注册校验",
            "用户注册前置校验",
            "com.bone.example.extension.user.UserRegisterExtPoint",
            "user",
            "validation");
    extPointRepository.save(pricing);
    extPointRepository.save(validation);

    Extension defaultPricing =
        Extension.create(
            pricing.getId(),
            "默认价格扩展",
            "标准定价逻辑",
            "com.bone.example.extension.order.DefaultPricingExtension");
    defaultPricing.setPriority(100);
    defaultPricing.setConfig("{\"traffic\":100,\"defaultImpl\":true}");
    defaultPricing.enable();

    Extension vipPricing =
        Extension.create(
            pricing.getId(),
            "VIP 价格扩展",
            "会员折扣",
            "com.bone.example.extension.order.VipPricingExtension");
    vipPricing.setPriority(50);
    vipPricing.setTenantCode("VIP");
    vipPricing.setConfig("{\"traffic\":30}");
    vipPricing.disable();

    extensionRepository.save(defaultPricing);
    extensionRepository.save(vipPricing);

    executionLogCommandHandler.record(
        defaultPricing,
        "INVOKE",
        "SUCCESS",
        "{\"orderId\":\"demo-001\"}",
        "{\"price\":99.0}",
        null,
        42L);
    executionLogCommandHandler.record(
        defaultPricing,
        "INVOKE",
        "SUCCESS",
        "{\"orderId\":\"demo-002\"}",
        "{\"price\":120.0}",
        null,
        38L);
    executionLogCommandHandler.record(
        vipPricing, "INVOKE", "FAILED", "{\"orderId\":\"demo-003\"}", null, "插件未部署", 5L);

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
