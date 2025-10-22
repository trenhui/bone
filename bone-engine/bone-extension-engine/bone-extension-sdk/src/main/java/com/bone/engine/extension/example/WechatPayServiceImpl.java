package com.bone.engine.extension.example;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 微信支付实现
 * <p>
 * 演示如何实现支付服务扩展点的另一种实现
 * </p>
 */
@Extension(
    name = "wechatPayService",
    description = "微信支付实现",
    bizCode = "ORDER",
    tenantCode = "DEFAULT",
    scenario = "NORMAL_PAY",
    paymentMethod = "WECHAT",
    priority = 6
)
@Component
public class WechatPayServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayServiceImpl.class);

    @Override
    public PaymentResult processPayment(BizContext<PaymentRequest> context) {
        // 暂时不调用getData()方法，直接使用默认值记录日志
        logger.info("Processing WeChat payment");
        
        // 模拟微信支付处理逻辑
        PaymentResult result = new PaymentResult();
        result.setPaymentId("WX" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        result.setStatus("SUCCESS");
        result.setMessage("微信支付成功");
        result.setPaidAmount(java.math.BigDecimal.valueOf(100.0)); // 转换为BigDecimal类型
        result.setPaidTime(System.currentTimeMillis());
        
        // 根据业务上下文进行特殊处理
        if ("MINI_APP_PAY".equals(context.getScenario())) {
            logger.info("Processing mini-app payment through WeChat");
            // 小程序支付特殊逻辑
        }
        
        return result;
    }
}