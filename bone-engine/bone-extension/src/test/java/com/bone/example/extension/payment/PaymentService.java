package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 支付服务
 * 负责协调支付流程，使用扩展点机制实现多租户支付处理差异化
 */
@Slf4j
public class PaymentService {
    
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
            PaymentResult result = new PaymentResult();
            result.setSuccess(false);
            result.setErrorCode(validation.getErrorCode());
            result.setErrorMessage(validation.getErrorMessage());
            result.setUserId(request.getUserId());
            return result;
        }
        
        // 2. 计算支付金额
        PaymentCalculationResult calculationResult = paymentExtPoint.calculatePayment(requestContext);
        log.info("Payment calculated for user {}, final amount: {}", request.getUserId(), calculationResult.getFinalAmount());
        
        // 3. 创建支付结果
        PaymentResult result = new PaymentResult();
        result.setTransactionId(generateTransactionId());
        result.setSuccess(true);
        result.setFinalAmount(calculationResult.getFinalAmount());
        result.setUserId(request.getUserId());
        Integer pointsToDeduct = request.getPointsToDeduct();
        result.setPointsDeducted(pointsToDeduct != null ? pointsToDeduct : 0);
        
        // 支付后处理
        BizContext<PaymentResult> resultContext = BizContext.create();
        resultContext.setData(result);
        resultContext.setBizCode(tenantCode);
        paymentExtPoint.postPayProcess(resultContext);
        
        return result;
    }
    

    
    /**
     * 生成交易ID
     */
    private String generateTransactionId() {
        return "TXN" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 16);
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