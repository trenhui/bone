package com.bone.engine.extension.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展点提供者注解
 * <p>
 * 用于标记实现了{@link ExtPoint}接口的具体实现类，并定义路由匹配条件
 * <strong>核心功能：</strong>允许为同一个扩展点接口提供多个实现，并根据业务上下文动态选择合适的实现
 * </p>
 * <h3>增强功能：</h3>
 * <ul>
 *   <li><strong>多租户支持：</strong>支持配置多个租户代码，适配复杂的多租户场景</li>
 *   <li><strong>用户组标识：</strong>基于用户组进行更精细的路由匹配</li>
 *   <li><strong>标签机制：</strong>支持基于键值对标签的路由匹配，提供更灵活的匹配规则</li>
 *   <li><strong>动态条件：</strong>支持复杂的Spring EL表达式条件匹配</li>
 *   <li><strong>时间范围：</strong>支持按时间范围控制扩展生效</li>
 *   <li><strong>灰度发布：</strong>支持基于流量比例的灰度发布</li>
 *   <li><strong>权重路由：</strong>支持多实现按权重比例路由</li>
 *   <li><strong>参数约束：</strong>支持基于入参的精确匹配</li>
 * </ul>
 * 
 * <h3>路由匹配机制：</h3>
 * <p>框架采用多级路由策略，按以下维度进行精确匹配：</p>
 * <ol>
 *   <li><strong>租户隔离：</strong>通过tenantCode或multiTenantCodes实现租户级别的扩展点隔离</li>
 *   <li><strong>业务域划分：</strong>通过bizCode或multiBizCodes区分不同业务域</li>
 *   <li><strong>用例区分：</strong>通过useCase或multiUseCases区分同一业务域下的不同用例</li>
 *   <li><strong>场景细化：</strong>通过scenario或multiScenarios进一步细化业务场景</li>
 *   <li><strong>环境适配：</strong>通过env或multiEnvs指定扩展适用的环境</li>
 *   <li><strong>分组管理：</strong>通过group对扩展进行逻辑分组</li>
 *   <li><strong>动态匹配：</strong>通过condition支持复杂的动态条件判断</li>
 *   <li><strong>数据隔离：</strong>通过dataSource指定特定的数据源</li>
 *   <li><strong>版本控制：</strong>通过version支持扩展点的版本管理</li>
 *   <li><strong>时间范围：</strong>通过startTime和endTime控制扩展生效时间</li>
 *   <li><strong>灰度发布：</strong>通过trafficRate控制流量比例</li>
 *   <li><strong>参数约束：</strong>通过paramConstraints匹配特定参数值</li>
 * </ol>
 * 
 * <h3>匹配优先级规则：</h3>
 * <ol>
 *   <li>精确匹配（tenantCode、bizCode等）优先级高于表达式匹配</li>
 *   <li>精确匹配时，匹配的条件越多，优先级越高</li>
 *   <li>当多个实现都匹配时，优先级数值小的优先执行</li>
 *   <li>当没有完全匹配的实现时，会回退到部分匹配或默认实现</li>
 *   <li>权重路由模式下，按权重比例进行流量分配</li>
 * </ol>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 1. 默认实现（匹配所有场景）
 * @Extension(bizCode = "DEFAULT", isDefault = true)
 * public class DefaultPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 2. 租户级定制（仅匹配TENANT_A租户）
 * @Extension(tenantCode = "TENANT_A", bizCode = "PAYMENT", priority = 50)
 * public class TenantAPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 3. 支持多租户的实现
 * @Extension(multiTenantCodes = {"TENANT_B", "TENANT_C"}, bizCode = "PAYMENT", version = "2.0.0")
 * public class MultiTenantPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 4. 支持多匹配条件的实现（使用Repeatable）
 * @Extension(tenantCode = "TENANT_D", bizCode = "PAYMENT", scenario = "ONLINE")
 * @Extension(tenantCode = "TENANT_D", bizCode = "PAYMENT", scenario = "OFFLINE")
 * public class TenantDPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 5. 基于条件表达式的动态匹配
 * @Extension(
 *     bizCode = "PAYMENT",
 *     condition = "#context.getData().getAmount() > 1000",
 *     priority = 30
 * )
 * public class LargeOrderPaymentServiceImpl implements PaymentService {
 *     // 大额订单支付逻辑
 * }
 * 
 * // 6. 灰度发布实现
 * @Extension(
 *     bizCode = "PAYMENT",
 *     scenario = "NEW_FEATURE",
 *     trafficRate = 20, // 20%的流量
 *     priority = 20
 * )
 * public class PaymentServiceNewFeature implements PaymentService {
 *     // 新功能实现
 * }
 * 
 * // 7. 参数约束匹配
 * @Extension(
 *     bizCode = "PAYMENT",
 *     paramConstraints = {"paymentType=CREDIT_CARD", "currency=USD"},
 *     priority = 40
 * )
 * public class CreditCardUSDPaymentServiceImpl implements PaymentService {
 *     // 信用卡美元支付逻辑
 * }
 * }
 * </pre>
 *
 * @author Bone Engine Team
 * @version 2.1.0
 * @see ExtPoint 扩展点接口注解
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Extension {
    
    /**
     * 扩展名称
     */
    String name() default "";
    
    /**
     * 扩展描述
     */
    String description() default "";
    
    /**
     * 租户代码
     */
    String tenantCode() default "";
    
    /**
     * 多租户代码配置<br>
     * 支持配置多个租户代码，适用于需要为多个租户提供相同扩展实现的场景
     * 如果配置了此属性，则tenantCode属性将被忽略
     */
    String[] multiTenantCodes() default {};
    
    /**
     * 业务域代码
     */
    String bizCode() default "";
    
    /**
     * 多业务域代码
     */
    String[] multiBizCodes() default {};
    
    /**
     * 用例代码
     */
    String useCase() default "";
    
    /**
     * 多用例代码配置
     */
    String[] multiUseCases() default {};
    
    /**
     * 用户组标识<br>
     * 用于匹配业务上下文中的用户组信息，支持更精细的用户分组路由
     */
    String userGroup() default "";
    
    /**
     * 多用户组标识配置
     */
    String[] multiUserGroups() default {};
    
    /**
     * 场景代码
     */
    String scenario() default "";
    
    /**
     * 多场景代码配置
     */
    String[] multiScenarios() default {};
    
    /**
     * 环境标识
     */
    String env() default "";
    
    /**
     * 多环境标识配置
     */
    String[] multiEnvs() default {};
    
    /**
     * 标签配置<br>
     * 用于基于标签进行路由匹配，格式为 "key:value"，支持更灵活的匹配规则
     * 匹配规则：上下文中必须包含所有指定的标签键，且对应的值相等
     */
    String[] tags() default {};
    
    /**
     * 分组标识
     */
    String group() default "";
    
    /**
     * 参数约束配置<br>
     * 格式为 "paramName=paramValue"，用于匹配方法入参的特定值
     */
    String[] paramConstraints() default {};
    
    /**
     * 匹配条件表达式（Spring EL）<br>
     * 表达式中的变量可引用：
     * <ul>
     *   <li>context - 业务上下文对象</li>
     *   <li>tenantCode - 当前租户代码</li>
     *   <li>bizCode - 当前业务代码</li>
     *   <li>useCase - 当前用例代码</li>
     *   <li>scenario - 当前场景代码</li>
     *   <li>userGroup - 当前用户组</li>
     *   <li>params - 方法入参数组</li>
     * </ul>
     */
    String condition() default "";
    
    /**
     * 优先级（越小优先级越高）
     */
    int priority() default 100;
    
    /**
     * 路由权重，用于权重路由模式，默认100
     */
    int weight() default 100;
    
    /**
     * 流量比例，用于灰度发布，范围0-100
     */
    int trafficRate() default 100;
    
    /**
     * 是否默认实现
     */
    boolean isDefault() default false;
    
    /**
     * 是否启用
     */
    boolean enabled() default true;
    
    /**
     * 扩展实现版本<br>
     * 用于标识扩展实现的版本号，便于版本管理和升级
     */
    String version() default "1.0.0";
    
    /**
     * 数据源标识
     */
    String dataSource() default "";
    
    /**
     * 支付方式（用于支付相关扩展点）
     */
    String paymentMethod() default "";
    
    /**
     * 生效开始时间（ISO 8601格式）
     */
    String startTime() default "";
    
    /**
     * 生效结束时间（ISO 8601格式）
     */
    String endTime() default "";
    
    /**
     * 是否使用独立线程池执行
     */
    boolean asyncExecution() default false;
    
    /**
     * 最大执行超时时间（毫秒），优先级高于扩展点默认配置
     */
    long maxExecutionTime() default 0L;
}

