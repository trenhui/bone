package com.bone.engine.extension.example;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtensionDoc;
import com.bone.engine.extension.context.BizContext;
import java.math.BigDecimal;

/**
 * 支付服务扩展点示例
 * <p>
 * 演示如何定义扩展点接口
 * </p>
 */
@ExtPoint(name = "PaymentService", description = "支付服务扩展点")
@ExtensionDoc(
    title = "支付服务扩展点",
    description = "提供不同支付渠道的集成能力",
    usage = "用于处理各种支付场景的支付请求",
    params = {
        @ExtensionDoc.Param(
            name = "context",
            type = "BizContext<PaymentRequest>",
            description = "支付业务上下文，包含支付请求信息",
            required = true
        )
    },
    returnInfo = @ExtensionDoc.Return(
        type = "PaymentResult",
        description = "支付结果",
        errorCodes = {
            @ExtensionDoc.ErrorCode(
                code = "PAYMENT_FAILED",
                description = "支付失败"
            ),
            @ExtensionDoc.ErrorCode(
                code = "INVALID_PARAM",
                description = "参数无效"
            )
        }
    ),
    example = """
    // 使用示例
    @Autowired
    private PaymentService paymentService;
    
    public PaymentResult pay(PaymentRequest request) {
        BizContext<PaymentRequest> context = new BizContext.Builder<PaymentRequest>()
            .setBizCode("ORDER")
            .setTenantCode("TENANT001")
            .setScenario("NORMAL_PAY")
            .setBizData(request)
            .build();
        
        return paymentService.processPayment(context);
    }
    """,
    notes = "注意：实现类需要处理各种支付异常情况"
)
public interface PaymentService {
    
    /**
     * 处理支付请求
     * 
     * @param context 支付上下文
     * @return 支付结果
     */
    PaymentResult processPayment(BizContext<PaymentRequest> context);
    
    /**
     * 支付请求参数
     */
    class PaymentRequest {
        private String orderId;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String userId;
        
        // Getters and setters
        public String getOrderId() {
            return orderId;
        }
        
        public void setOrderId(String orderId) {
            this.orderId = orderId;
        }
        
        public BigDecimal getAmount() {
            return amount;
        }
        
        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }
        
        public String getCurrency() {
            return currency;
        }
        
        public void setCurrency(String currency) {
            this.currency = currency;
        }
        
        public String getPaymentMethod() {
            return paymentMethod;
        }
        
        public void setPaymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
        }
        
        public String getUserId() {
            return userId;
        }
        
        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
    
    /**
     * 支付结果
     */
    class PaymentResult {
        private String paymentId;
        private String status;
        private String message;
        private BigDecimal paidAmount;
        private long paidTime;
        
        // Getters and setters
        public String getPaymentId() {
            return paymentId;
        }
        
        public void setPaymentId(String paymentId) {
            this.paymentId = paymentId;
        }
        
        public String getStatus() {
            return status;
        }
        
        public void setStatus(String status) {
            this.status = status;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public BigDecimal getPaidAmount() {
            return paidAmount;
        }
        
        public void setPaidAmount(BigDecimal paidAmount) {
            this.paidAmount = paidAmount;
        }
        
        public long getPaidTime() {
            return paidTime;
        }
        
        public void setPaidTime(long paidTime) {
            this.paidTime = paidTime;
        }
    }
}