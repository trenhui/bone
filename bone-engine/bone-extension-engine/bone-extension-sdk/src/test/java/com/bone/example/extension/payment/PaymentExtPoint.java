package com.bone.example.extension.payment;

import com.bone.engine.extension.api.annotation.ExtPoint;
import com.bone.engine.extension.api.annotation.ExtPointDoc;
import com.bone.engine.extension.support.context.BizContext;
import com.bone.example.extension.result.ValidationResult;

/**
 * 支付扩展点接口
 * <p>
 * 定义支付处理相关的扩展能力，包括支付前验证、支付金额计算和支付后处理。
 * 提供统一的扩展点机制，允许不同业务场景定制化支付流程。
 */
@ExtPoint(
    name = "支付扩展点",
    description = "支付处理相关的扩展点，支持支付前验证、金额计算和支付后处理"
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
     * @param context 支付上下文
     * @return 验证结果
     */
    ValidationResult prePayValidate(BizContext<PaymentTestRequest> context);
    
    /**
     * 计算支付金额
     * @param context 支付上下文
     * @return 支付计算结果
     */
    PaymentCalculationResult calculatePayment(BizContext<PaymentTestRequest> context);
    
    /**
     * 支付后处理
     * @param context 支付结果上下文
     */
    void postPayProcess(BizContext<PaymentResult> context);
    
    /**
     * 获取扩展点优先级
     * @return 优先级值
     */
    int getPriority();
    
    /**
     * 判断扩展点是否适用于当前请求
     * @param context 业务上下文
     * @return 是否适用
     */
    boolean isApplicable(BizContext<?> context);
}
