package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.BizContexts;
import com.bone.engine.extension.ExtensionExecutor;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Resource;
import java.util.UUID;

/**
 * 支付服务
 * 负责协调支付流程，使用扩展点机制实现多租户支付处理差异化
 */
@Slf4j
public class PaymentService {
    
    @Resource
    private ExtensionExecutor extensionExecutor;
    
    /**
     * 处理支付请求
     * @param request 支付请求
     * @param tenantCode 租户代码
     * @return 支付结果
     */
    public PaymentResult processPayment(PaymentRequest request, String tenantCode) {
        try {
            // 创建并设置业务上下文
            try (BizContexts.ContextManager manager = BizContexts.use()) {
                // 设置租户和业务域
                manager.setTenantCode(tenantCode);
                manager.setBizDomain("PAYMENT_SERVICE");
                
                // 1. 支付前验证
                ValidationResult validation = extensionExecutor.execute(PaymentExtPoint.class, 
                    extPoint -> extPoint.prePayValidate(BizContexts.getContext(request)));
                
                if (!validation.isSuccess()) {
                    log.warn("Payment validation failed for tenant {}: {}", tenantCode, validation.getErrorCode());
                    return PaymentResult.builder()
                        .success(false)
                        .errorCode(validation.getErrorCode())
                        .errorMessage(validation.getErrorMessage())
                        .userId(request.getUserId())
                        .build();
                }
                
                // 2. 计算支付金额
                PaymentCalculationResult calculationResult = extensionExecutor.execute(PaymentExtPoint.class,
                    extPoint -> extPoint.calculatePayment(BizContexts.getContext(request)));
                
                // 3. 执行实际支付（这里简化处理，实际项目中会调用支付网关）
                boolean paymentSuccess = executePayment(calculationResult);
                
                // 4. 创建支付结果
                PaymentResult result = PaymentResult.builder()
                    .transactionId(generateTransactionId())
                    .success(paymentSuccess)
                    .finalAmount(calculationResult.getFinalAmount())
                    .userId(request.getUserId())
                    .pointsDeducted(request.getPointsToDeduct() != null ? request.getPointsToDeduct() : 0)
                    .build();
                
                // 5. 支付后处理
                if (paymentSuccess) {
                    extensionExecutor.execute(PaymentExtPoint.class,
                        extPoint -> extPoint.postPayProcess(BizContexts.getContext(result)));
                }
                
                return result;
            }
        } catch (Exception e) {
            log.error("Payment processing failed for tenant {}", tenantCode, e);
            return PaymentResult.builder()
                .success(false)
                .errorCode("PAYMENT_ERROR")
                .errorMessage("支付处理过程中出现异常")
                .userId(request.getUserId())
                .build();
        }
    }
    
    /**
     * 执行实际支付
     * 这里是模拟实现，实际项目中会调用支付网关
     */
    private boolean executePayment(PaymentCalculationResult result) {
        // 模拟支付成功
        return true;
    }
    
    /**
     * 生成交易ID
     */
    private String generateTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
    }
}