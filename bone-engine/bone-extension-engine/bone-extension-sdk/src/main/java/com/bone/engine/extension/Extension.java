package com.bone.engine.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展点提供者注解
 * <p>
 * 用于标记实现了{@link ExtPoint}接口的具体实现类，并定义路由匹配条件
 * <strong>核心功能：</strong>允许为同一个扩展点接口提供多个实现，并根据业务上下文动态选择合适的实现
 * </p>
 * 
 * <h3>路由匹配机制：</h3>
 * <p>框架采用多级路由策略，按以下维度进行精确匹配：</p>
 * <ol>
 *   <li><strong>租户隔离：</strong>通过tenantCode或multiTenantCodes实现租户级别的扩展点隔离</li>
 *   <li><strong>业务域划分：</strong>通过bizCode或multiBizCodes区分不同业务域</li>
 *   <li><strong>用例区分：</strong>通过useCase区分同一业务域下的不同用例</li>
 *   <li><strong>场景细化：</strong>通过scenario进一步细化业务场景</li>
 *   <li><strong>环境适配：</strong>通过env指定扩展适用的环境</li>
 *   <li><strong>分组管理：</strong>通过group对扩展进行逻辑分组</li>
 *   <li><strong>动态匹配：</strong>通过condition支持复杂的动态条件判断</li>
 *   <li><strong>数据隔离：</strong>通过dataSource指定特定的数据源</li>
 *   <li><strong>版本控制：</strong>通过version支持扩展点的版本管理</li>
 * </ol>
 * 
 * <h3>匹配优先级规则：</h3>
 * <ol>
 *   <li>精确匹配（tenantCode、bizCode等）优先级高于表达式匹配</li>
 *   <li>精确匹配时，匹配的条件越多，优先级越高</li>
 *   <li>当多个实现都匹配时，优先级数值小的优先执行</li>
 *   <li>当没有完全匹配的实现时，会回退到部分匹配或默认实现</li>
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
 * }
 * </pre>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 * @see ExtPoint 扩展点接口注解
 * @see Extensions 扩展点容器注解
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Extensions.class)
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
     * 多租户代码
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
     * 场景代码
     */
    String scenario() default "";
    
    /**
     * 环境标识
     */
    String env() default "";
    
    /**
     * 分组标识
     */
    String group() default "";
    
    /**
     * 匹配条件表达式（Spring EL）
     */
    String condition() default "";
    
    /**
     * 优先级（越小优先级越高）
     */
    int priority() default 100;
    
    /**
     * 是否默认实现
     */
    boolean isDefault() default false;
    
    /**
     * 是否启用
     */
    boolean enabled() default true;
    
    /**
     * 数据源标识
     */
    String dataSource() default "";
    
    /**
     * 版本号
     */
    String version() default "1.0.0";
    
    /**
     * 生效开始时间（ISO 8601格式）
     */
    String startTime() default "";
    
    /**
     * 生效结束时间（ISO 8601格式）
     */
    String endTime() default "";
    
    /**
     * 实现者信息
     */
    String author() default "";
    
    /**
     * 配置参数定义（JSON格式）
     */
    String configSchema() default "";
}

/**
 * Extension注解的容器类，支持在一个类上标注多个Extension注解
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Extensions {
    Extension[] value();
}

