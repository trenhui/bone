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
    bizCode = {"ORDER", "MEMBERSHIP"},
    tenantCode = {"DEFAULT", "TENANT002"},
    scenario = {"NORMAL_PAY", "MINI_APP_PAY"},
    paymentMethod = "WECHAT",
    priority = 6
)
@Component
public class WechatPayServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(WechatPayServiceImpl.class);

    @Override
    public PaymentResult processPayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getBizData();
        logger.info("Processing WeChat payment for order: {}, amount: {}", 
                request.getOrderId(), request.getAmount());
        
        // 模拟微信支付处理逻辑
        PaymentResult result = new PaymentResult();
        result.setPaymentId("WX" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        result.setStatus("SUCCESS");
        result.setMessage("微信支付成功");
        result.setPaidAmount(request.getAmount());
        result.setPaidTime(System.currentTimeMillis());
        
        // 根据业务上下文进行特殊处理
        if ("MINI_APP_PAY".equals(context.getScenario())) {
            logger.info("Processing mini-app payment through WeChat for order: {}", request.getOrderId());
            // 小程序支付特殊逻辑
        }
        
        return result;
    }
}