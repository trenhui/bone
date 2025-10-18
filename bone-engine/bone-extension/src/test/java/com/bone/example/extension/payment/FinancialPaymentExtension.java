package com.bone.example.extension.payment;

import com.bone.engine.extension.BizContext;
import com.bone.engine.extension.Extension;
import com.bone.example.extension.result.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;

/**
 * 金融服务租户支付实现
 * 支持复杂的费率计算、手续费分配和合规检查
 */
@Extension(tenantCode = "FINANCIAL_TENANT", priority = 100)
@Slf4j
public class FinancialPaymentExtension implements PaymentExtPoint {
    
    // 为了演示，这里模拟服务依赖
    private final ComplianceService complianceService = new ComplianceService();
    private final FeeService feeService = new FeeService();
    
    @Override
    public ValidationResult prePayValidate(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        
        // 合规性检查
        if (!complianceService.checkTransaction(request.getUserId(), request.getAmount())) {
            return ValidationResult.fail("COMPLIANCE_VIOLATION", "交易不符合合规要求");
        }
        
        // 风控检查
        RiskLevel riskLevel = complianceService.assessRisk(request);
        if (riskLevel == RiskLevel.HIGH) {
            return ValidationResult.fail("HIGH_RISK", "交易风险等级过高");
        }
        
        return ValidationResult.success();
    }
    
    @Override
    public PaymentCalculationResult calculatePayment(BizContext<PaymentRequest> context) {
        PaymentRequest request = context.getData();
        
        // 计算手续费
        BigDecimal fee = feeService.calculateFee(request.getAmount(), request.getPaymentMethod());
        
        // 计算税率
        BigDecimal tax = calculateTax(request.getAmount(), "DEFAULT");
        
        return PaymentCalculationResult.builder()
            .originalAmount(request.getAmount())
            .finalAmount(request.getAmount().add(fee).add(tax))
            .feeAmount(fee)
            .taxAmount(tax)
            .currency("CNY")
            .build();
    }
    
    @Override
    public void postPayProcess(BizContext<PaymentResult> context) {
        // 金融特有的支付后处理逻辑
        // 包括：清算、对账标记、合规记录等
        complianceService.recordTransaction(context.getData());
    }
    
    private BigDecimal calculateTax(BigDecimal amount, String taxType) {
        // 根据税务类型计算税额
        return amount.multiply(new BigDecimal("0.06")); // 默认6%税率
    }
    
    // 风险等级枚举
    enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
    
    // 模拟服务类
    static class ComplianceService {
        public boolean checkTransaction(String userId, BigDecimal amount) {
            // 模拟合规检查
            return true;
        }
        
        public RiskLevel assessRisk(PaymentRequest request) {
            // 模拟风险评估
            return RiskLevel.LOW;
        }
        
        public void recordTransaction(PaymentResult result) {
            // 模拟记录交易
            log.info("Recording compliance transaction: {}", result.getTransactionId());
        }
    }
    
    static class FeeService {
        public BigDecimal calculateFee(BigDecimal amount, String paymentMethod) {
            // 模拟计算手续费
            return amount.multiply(new BigDecimal("0.005")); // 0.5%手续费
        }
    }
}