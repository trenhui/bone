package com.bone.engine.extension;

import java.lang.annotation.*;

/**
 * 扩展点接口标记注解
 * <p>
 * 用于标识可被扩展的接口，框架将为被此注解标记的接口创建动态代理并启用扩展点机制
 * <strong>设计意图：</strong>实现核心业务逻辑与特定场景定制化逻辑的解耦，支持通过配置动态切换实现
 * </p>
 * 
 * <h3>扩展点设计模式说明：</h3>
 * <ul>
 *   <li><strong>约定优于配置：</strong>通过注解约定扩展点接口和实现类</li>
 *   <li><strong>关注点分离：</strong>核心逻辑与定制逻辑分离，便于维护</li>
 *   <li><strong>动态路由：</strong>根据运行时上下文自动选择合适的扩展实现</li>
 *   <li><strong>开闭原则：</strong>对扩展开放，对修改关闭</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 1. 定义扩展点接口
 * @ExtPoint
 * public interface PaymentService {
 *     PaymentResult pay(PaymentRequest request, BizContext<?> context);
 * }
 * 
 * // 2. 提供默认实现
 * @ExtProvider(bizCode = "DEFAULT")
 * public class DefaultPaymentServiceImpl implements PaymentService {
 *     @Override
 *     public PaymentResult pay(PaymentRequest request, BizContext<?> context) {
 *         // 默认支付实现
 *         return new PaymentResult();
 *     }
 * }
 * 
 * // 3. 提供租户A的定制实现
 * @ExtProvider(tenantCode = "TENANT_A", bizCode = "ORDER")
 * public class TenantAPaymentServiceImpl implements PaymentService {
 *     @Override
 *     public PaymentResult pay(PaymentRequest request, BizContext<?> context) {
 *         // 租户A特定的支付实现
 *         return new PaymentResult();
 *     }
 * }
 * 
 * // 4. 在业务代码中注入并使用
 * @Service
 * public class OrderService {
 *     @Autowired
 *     private PaymentService paymentService;
 *     
 *     public void processOrder(Order order) {
 *         BizContext<?> context = new BizContext.Builder()
 *             .setTenantCode(order.getTenantCode())
 *             .setBizCode("ORDER")
 *             .build();
 *             
 *         // 框架会根据上下文自动路由到合适的实现
 *         PaymentResult result = paymentService.pay(new PaymentRequest(order), context);
 *     }
 * }
 * }
 * </pre>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 *   <li>扩展点接口应保持简洁，聚焦单一职责</li>
 *   <li>接口方法应接受{@code BizContext<?>}作为参数，以支持上下文路由</li>
 *   <li>扩展点接口通常定义在核心模块，实现类定义在具体业务模块</li>
 *   <li>为每个扩展点提供默认实现，提高系统健壮性</li>
 * </ul>
 * 
 * @see ExtProvider 扩展点提供者注解
 * @see BizContext 业务上下文对象
 * @see EnableExtPoints 启用扩展点框架注解
 * @since 1.0.0
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ExtPoint {
}
