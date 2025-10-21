package com.bone.engine.extension.example;

import com.bone.engine.extension.Extension;
import com.bone.engine.extension.context.BizContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 支付宝支付实现
 * <p>
 * 演示如何实现支付服务扩展点
 * </p>
 */
@Extension(
    name = "alipayPaymentService",
    description = "支付宝支付实现",
    bizCode = {"ORDER", "REFUND"},
    tenantCode = {"DEFAULT", "TENANT001"},
    scenario = {"NORMAL_PAY", "APP_PAY"},
    paymentMethod = "ALIPAY",
    priority = 5
)
@Component
public class AlipayServiceImpl implements PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(AlipayServiceImpl.class);

    @Override
    public PaymentResult processPayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getBizData();
        logger.info("Processing Alipay payment for order: {}, amount: {}", 
                request.getOrderId(), request.getAmount());
        
        // 模拟支付宝支付处理逻辑
        PaymentResult result = new PaymentResult();
        result.setPaymentId("ALI" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        result.setStatus("SUCCESS");
        result.setMessage("支付宝支付成功");
        result.setPaidAmount(request.getAmount());
        result.setPaidTime(System.currentTimeMillis());
        
        // 根据业务上下文进行特殊处理
        if ("REFUND".equals(context.getBizCode())) {
            logger.info("Processing refund through Alipay for order: {}", request.getOrderId());
            // 退款特殊逻辑
        }
        
        return result;
    }
}