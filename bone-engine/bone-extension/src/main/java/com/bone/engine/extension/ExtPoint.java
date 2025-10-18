package com.bone.engine.extension;

import java.lang.annotation.*;

/**
 * 扩展点接口标记注解，用于标识可扩展的接口
 * <p>
 * 被此注解标记的接口将启用扩展点机制，支持通过{@link ExtProvider}注解实现不同维度的扩展
 * 扩展点接口通常定义在核心模块中，由各业务模块提供具体实现
 * <p>
 * 使用示例：
 * <pre>
 * {@code @ExtPoint}
 * public interface PaymentService {
 *     PaymentResult pay(PaymentRequest request);
 * }
 * </pre>
 * <p>
 * 对应的扩展实现：
 * <pre>
 * {@code @ExtProvider(tenantCode = "TENANT_A", bizCode = "ORDER")}
 * public class TenantAPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * </pre>
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 * @see ExtProvider 扩展点提供者注解
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ExtPoint {
}
