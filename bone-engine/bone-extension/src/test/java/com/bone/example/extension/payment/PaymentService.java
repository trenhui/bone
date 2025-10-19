package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.UUID;

/**
 * 支付服务
 * 负责协调支付流程，使用扩展点机制实现多租户支付处理差异化
 */
@Slf4j
public class PaymentService {
    
    // 常量定义
    private static final String TRANSACTION_PREFIX = "TXN";
    private static final int TRANSACTION_ID_LENGTH = 16;
    
    @Autowired
    private PaymentExtPoint paymentExtPoint;
    
    /**
     * 处理支付请求
     * @param request 支付请求
     * @param tenantCode 租户代码
     * @return 支付结果
     */
    public PaymentResult processPayment(PaymentRequest request, String tenantCode) {
        // 创建业务上下文
        BizContext<PaymentRequest> requestContext = createContext(request);
        requestContext.setBizCode(tenantCode);
        
        // 1. 支付前验证
        ValidationResult validation = paymentExtPoint.prePayValidate(requestContext);
        if (!validation.isSuccess()) {
            log.warn("Payment validation failed for tenant {}: {}", tenantCode, validation.getErrorCode());
            return buildValidationFailedResult(request, validation);
        }
        
        // 2. 计算支付金额
        PaymentCalculationResult calculationResult = paymentExtPoint.calculatePayment(requestContext);
        log.info("Payment calculated for user {}, final amount: {}", request.getUserId(), calculationResult.getFinalAmount());
        
        // 3. 创建支付结果
        PaymentResult result = buildPaymentResult(request, calculationResult);
        
        // 支付后处理
        BizContext<PaymentResult> resultContext = BizContext.create();
        resultContext.setData(result);
        resultContext.setBizCode(tenantCode);
        paymentExtPoint.postPayProcess(resultContext);
        
        return result;
    }
    
    /**
     * 构建验证失败的支付结果
     */
    private PaymentResult buildValidationFailedResult(PaymentRequest request, ValidationResult validation) {
        PaymentResult result = new PaymentResult();
        result.setSuccess(false);
        result.setErrorCode(validation.getErrorCode());
        result.setErrorMessage(validation.getErrorMessage());
        result.setUserId(request.getUserId());
        return result;
    }
    
    /**
     * 构建支付成功结果
     */
    private PaymentResult buildPaymentResult(PaymentRequest request, PaymentCalculationResult calculationResult) {
        PaymentResult result = new PaymentResult();
        result.setTransactionId(generateTransactionId());
        result.setSuccess(true);
        result.setFinalAmount(calculationResult.getFinalAmount());
        result.setUserId(request.getUserId());
        result.setPointsDeducted(request.getPointsToDeduct() > 0 ? request.getPointsToDeduct() : 0);
        return result;
    }
    
    /**
     * 生成交易ID
     */
    private String generateTransactionId() {
        return TRANSACTION_PREFIX + UUID.randomUUID().toString().replaceAll("-", "").substring(0, TRANSACTION_ID_LENGTH);
    }
    
    /**
     * 创建业务上下文
     */
    private BizContext<PaymentRequest> createContext(PaymentRequest request) {
        BizContext<PaymentRequest> context = BizContext.create();
        context.setData(request);
        return context;
    }
}