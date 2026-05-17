package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.store.ExtPointStore;
import com.bone.engine.extension.studio.domain.store.ExtensionStore;
import com.bone.engine.extension.studio.service.PluginExecutionLogService;
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

    @Autowired
    private ExtPointStore extPointStore;

    @Autowired
    private ExtensionStore extensionStore;

    @Autowired
    private PluginExecutionLogService executionLogService;

    @Override
    public void run(ApplicationArguments args) {
        if (!extPointStore.findAll().isEmpty()) {
            return;
        }
        log.info("初始化扩展管理示例数据…");

        ExtPoint pricing = point(
                "订单价格计算",
                "订单下单前价格扩展",
                "com.bone.example.extension.order.OrderPricingExtPoint",
                "order",
                "pricing");
        ExtPoint validation = point(
                "用户注册校验",
                "用户注册前置校验",
                "com.bone.example.extension.user.UserRegisterExtPoint",
                "user",
                "validation");
        extPointStore.save(pricing);
        extPointStore.save(validation);

        Extension defaultPricing = Extension.create(
                pricing.getId(),
                "默认价格扩展",
                "标准定价逻辑",
                "com.bone.example.extension.order.DefaultPricingExtension");
        defaultPricing.setPriority(100);
        defaultPricing.setConfig("{\"traffic\":100,\"defaultImpl\":true}");
        defaultPricing.enable();

        Extension vipPricing = Extension.create(
                pricing.getId(),
                "VIP 价格扩展",
                "会员折扣",
                "com.bone.example.extension.order.VipPricingExtension");
        vipPricing.setPriority(50);
        vipPricing.setTenantCode("VIP");
        vipPricing.setConfig("{\"traffic\":30}");
        vipPricing.disable();

        extensionStore.save(defaultPricing);
        extensionStore.save(vipPricing);

        executionLogService.record(
                defaultPricing, "INVOKE", "SUCCESS", "{\"orderId\":\"demo-001\"}", "{\"price\":99.0}", null, 42L);
        executionLogService.record(
                defaultPricing, "INVOKE", "SUCCESS", "{\"orderId\":\"demo-002\"}", "{\"price\":120.0}", null, 38L);
        executionLogService.record(
                vipPricing, "INVOKE", "FAILED", "{\"orderId\":\"demo-003\"}", null, "插件未部署", 5L);

        log.info("已初始化 {} 个扩展点、{} 个插件", extPointStore.count(), extensionStore.count());
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
