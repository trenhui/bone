package com.bone.example.extension.payment;

import com.bone.engine.extension.ExtPoint;
import com.bone.engine.extension.annotation.ExtPointDoc;
import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.metadata.example.PaymentRequest;
import com.bone.example.extension.payment.ValidationResult;

/**
 * 支付扩展点接口
 * <p>
 * 定义支付处理相关的扩展能力，包括支付前验证、支付金额计算和支付后处理。
 * 提供统一的扩展点机制，允许不同业务场景定制化支付流程。
 */
@ExtPoint(
    name = "支付扩展点",
    description = "支付处理相关的扩展点，支持支付前验证、金额计算和支付后处理",
    version = "1.0.0"
)
@ExtPointDoc(
    title = "支付处理扩展点接口",
    domain = "支付系统",
    category = "交易处理",
    description = "该扩展点定义了支付处理的核心流程，包括支付前验证、金额计算和支付后处理三个关键环节，允许不同业务场景定制化支付逻辑。",
    usage = "实现该接口并通过@Extension注解注册，系统会根据业务上下文和优先级自动选择合适的扩展实现。",
    bestPractices = "1. 确保实现类线程安全\n2. 适当设置优先级以便正确选择扩展\n3. 实现isApplicable方法实现精准路由\n4. 添加完整的错误处理机制\n5. 对关键操作进行日志记录",
    notes = "各实现类应关注性能优化，尤其是在高频交易场景下"
)
public interface PaymentExtPoint {
    
    /**
     * 支付前验证
     * <p>
     * 在支付处理前验证支付请求的合法性和有效性。
     * 验证内容包括但不限于：参数完整性、用户权限、金额有效性等。
     * 
     * @param context 包含支付请求信息的业务上下文，非null
     * @return 验证结果对象，包含验证状态和错误信息
     */
    ValidationResult prePayValidate(final BizContext<PaymentRequest> context);
    
    /**
     * 计算支付金额
     * <p>
     * 根据支付请求计算最终的支付金额，包括手续费、税费、折扣等费用明细。
     * 确保所有金额计算使用BigDecimal以保证财务计算的精确性。
     * 
     * @param context 包含支付请求信息的业务上下文，非null
     * @return 支付计算结果对象，包含详细的费用明细
     */
    PaymentCalculationResult calculatePayment(final BizContext<PaymentRequest> context);
    
    /**
     * 支付后处理
     * <p>
     * 在支付完成后执行额外的处理逻辑，如订单状态更新、积分发放、通知发送等。
     * 
     * @param context 包含支付结果信息的业务上下文，非null
     */
    void postPayProcess(final BizContext<PaymentResult> context);
    
    /**
     * 获取扩展点优先级
     * <p>
     * 优先级值越小，优先级越高。不同实现类通过设置不同的优先级来控制执行顺序。
     * 
     * @return 优先级整数值，默认值为100
     */
    default int getPriority() {
        return 100; // 默认优先级
    }
    
    /**
     * 判断扩展点是否适用于当前上下文
     * <p>
     * 根据业务上下文决定是否应用该扩展实现。实现类应根据具体的业务规则进行精确判断。
     * 
     * @param context 业务上下文，可能为null
     * @return 是否适用于当前上下文
     */
    default boolean isApplicable(final BizContext<?> context) {
        return context != null; // 默认适用于非空上下文
    }
}