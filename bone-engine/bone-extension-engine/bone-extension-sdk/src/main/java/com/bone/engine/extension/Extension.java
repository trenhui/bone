package com.bone.engine.extension;

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
 * // 3. 多租户定制（匹配多个租户）
 * @Extension(multiTenantCodes = {"TENANT_B", "TENANT_C"}, bizCode = "PAYMENT", priority = 60)
 * public class MultiTenantPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 4. 业务场景定制（匹配特定业务和场景）
 * @Extension(tenantCode = "TENANT_B", bizCode = "PAYMENT", scenario = "REFUND", env = "PROD")
 * public class TenantBRefundServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 5. 动态条件匹配（使用SpEL表达式）
 * @Extension(
 *     tenantCode = "TENANT_C", 
 *     condition = "#data.amount > 10000 && #bizCode == 'VIP_ORDER'",
 *     priority = 40
 * )
 * public class VipHighAmountServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * </pre>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 *   <li>总是为扩展点提供一个默认实现（isDefault=true）</li>
 *   <li>优先使用精确匹配，仅在需要复杂条件时使用表达式匹配</li>
 *   <li>为不同租户提供独立实现时，使用tenantCode或multiTenantCodes进行隔离</li>
 *   <li>表达式匹配时避免过于复杂的逻辑，影响性能</li>
 *   <li>使用适当的优先级（priority）控制执行顺序</li>
 *   <li>确保实现类的名称能够清晰表达其用途和适用场景</li>
 *   <li>生产环境中使用env属性限制扩展适用范围</li>
 * </ul>
 * 
 * @see ExtPoint 扩展点接口标记注解
 * @see BizContext 业务上下文对象
 * @see DefaultExtPointRouter 默认路由实现
 * @since 1.0.0
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Extension {
    /**
     * 扩展ID
     * <p>
     * 扩展的唯一标识，建议使用业务领域+功能模块+实现类型的命名方式
     * 如果不指定，将自动生成基于类名的唯一标识
     * </p>
     * 
     * @return 扩展ID
     */
    String id() default ExtPointConstants.EMPTY_STRING;
    
    /**
     * 租户编码
     * <p>
     * 用于多租户场景下的扩展点隔离，只有当请求的租户编码与该值匹配时才会选择此实现
     * 默认值表示匹配所有租户，适合通用实现
     * </p>
     * 
     * @return 租户编码
     */
    String tenantCode() default ExtPointConstants.DEFAULT_VALUE;
    
    /**
     * 多租户编码列表
     * <p>
     * 支持一个扩展实现匹配多个租户的场景，优先级高于tenantCode
     * 当指定了此属性时，tenantCode属性将被忽略
     * </p>
     * 
     * @return 租户编码列表
     */
    String[] multiTenantCodes() default {};

    /**
     * 业务编码
     * <p>
     * 用于区分不同业务域的扩展点实现，如"ORDER"、"PAYMENT"等
     * 建议为通用实现设置为"DEFAULT"，便于识别和维护
     * </p>
     * 
     * @return 业务编码
     */
    String bizCode() default ExtPointConstants.DEFAULT_VALUE;
    
    /**
     * 多业务编码列表
     * <p>
     * 支持一个扩展实现匹配多个业务域的场景，优先级高于bizCode
     * 当指定了此属性时，bizCode属性将被忽略
     * </p>
     * 
     * @return 业务编码列表
     */
    String[] multiBizCodes() default {};

    /**
     * 用例编码
     * <p>
     * 用于区分同一业务域下的不同用例场景
     * 如在"PAYMENT"业务域下，可能有"CREDIT_CARD"、"ALIPAY"等不同用例
     * </p>
     * 
     * @return 用例编码
     */
    String useCase() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 场景编码
     * <p>
     * 用于进一步细化业务场景
     * 如在支付用例下，可能有"NORMAL"、"PROMOTION"、"REFUND"等不同场景
     * </p>
     * 
     * @return 场景编码
     */
    String scenario() default ExtPointConstants.DEFAULT_VALUE;
    
    /**
     * 环境编码
     * <p>
     * 指定扩展实现适用的环境，如"DEV"、"TEST"、"PROD"等
     * 用于在不同环境中启用或禁用特定扩展
     * </p>
     * 
     * @return 环境编码
     */
    String env() default ExtPointConstants.DEFAULT_VALUE;
    
    /**
     * 分组名称
     * <p>
     * 用于对扩展进行逻辑分组，便于管理和版本控制
     * </p>
     * 
     * @return 分组名称
     */
    String group() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 动态匹配表达式
     * <p>
     * 使用Spring EL表达式语法进行复杂的动态条件匹配
     * 表达式可以访问上下文变量，实现更灵活的匹配逻辑
     * </p>
     * 
     * <p>可用变量：</p>
     * <ul>
     *   <li><code>#tenantCode</code>：当前租户编码</li>
     *   <li><code>#bizCode</code>：当前业务编码</li>
     *   <li><code>#useCase</code>：当前用例编码</li>
     *   <li><code>#scenario</code>：当前场景编码</li>
     *   <li><code>#data</code>：业务数据对象</li>
     *   <li><code>#context</code>：当前业务上下文对象，可以通过getAttribute方法获取属性</li>
     * </ul>
     * 
     * <p>表达式示例：</p>
     * <ul>
     *   <li><code>#tenantCode.startsWith('PREMIUM_')</code>：匹配高级租户</li>
     *   <li><code>#data.amount > 1000</code>：匹配大额交易</li>
     *   <li><code>#context.getAttribute('userId') != null && #context.getAttribute('userLevel') == 'VIP'</code>：匹配VIP用户</li>
     *   <li><code>#bizCode == 'ORDER' && #scenario == 'VIP'</code>：匹配VIP订单场景</li>
     * </ul>
     * 
     * @return 动态匹配表达式
     */
    String condition() default ExtPointConstants.EMPTY_STRING;
    
    /**
     * 动态匹配表达式（兼容旧版本）
     * <p>
     * 兼容旧版本的expression属性，与condition功能相同
     * 如果同时指定了condition和expression，condition优先级更高
     * </p>
     * 
     * @return 动态匹配表达式
     * @deprecated 建议使用condition属性
     */
    @Deprecated
    String expression() default ExtPointConstants.EMPTY_STRING;
    
    /**
     * 扩展点的版本号，遵循语义化版本规范
     * 
     * @return 版本号
     */
    String version() default "1.0.0";

    /**
     * 兼容的版本列表
     * 
     * @return 兼容版本数组
     */
    String[] compatibleWith() default {};
    
    /**
     * 扩展实现描述
     * <p>
     * 详细描述此扩展实现的功能、适用场景和特殊处理逻辑
     * </p>
     * 
     * @return 扩展实现描述
     */
    String description() default "";
    
    /**
     * 作者
     * <p>
     * 扩展实现的作者或开发团队
     * </p>
     * 
     * @return 作者信息
     */
    String author() default "";
    
    /**
     * 是否默认实现
     * <p>
     * 标记此实现是否为默认实现，当没有找到更匹配的实现时会使用默认实现
     * </p>
     * 
     * @return 是否默认实现
     */
    boolean isDefault() default false;
    
    /**
     * 是否推荐实现
     * <p>
     * 标记此实现是否为推荐实现，用于可视化平台推荐
     * </p>
     * 
     * @return 是否推荐实现
     */
    boolean isRecommended() default false;
    
    /**
     * 执行优先级
     * <p>
     * 当多个实现都匹配时的执行优先级，值越小优先级越高
     * </p>
     * 
     * @return 优先级值
     */
    int priority() default 100;
    
    /**
     * 依赖的其他扩展实现
     * <p>
     * 指定此扩展实现依赖的其他实现类全限定名
     * </p>
     * 
     * @return 依赖的实现类数组
     */
    String[] dependencies() default {};
    
    /**
     * 配置属性
     * <p>
     * 扩展实现的配置属性键值对，格式为"key=value"
     * </p>
     * 
     * @return 配置属性数组
     */
    String[] properties() default {};
}

