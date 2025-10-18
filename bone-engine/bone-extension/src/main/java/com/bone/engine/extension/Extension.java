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
 *   <li><strong>租户隔离：</strong>通过tenantCode实现租户级别的扩展点隔离</li>
 *   <li><strong>业务域划分：</strong>通过bizCode区分不同业务域</li>
 *   <li><strong>用例区分：</strong>通过useCase区分同一业务域下的不同用例</li>
 *   <li><strong>场景细化：</strong>通过scenario进一步细化业务场景</li>
 *   <li><strong>动态匹配：</strong>通过expression支持复杂的动态条件判断</li>
 * </ol>
 * 
 * <h3>匹配优先级规则：</h3>
 * <ol>
 *   <li>精确匹配（tenantCode、bizCode等）优先级高于表达式匹配</li>
 *   <li>精确匹配时，匹配的条件越多，优先级越高</li>
 *   <li>当没有完全匹配的实现时，会回退到部分匹配或默认实现</li>
 * </ol>
 * 
 * <h3>使用示例：</h3>
 * <pre>
 * {@code
 * // 1. 默认实现（匹配所有场景）
 * @Extension(bizCode = "DEFAULT")
 * public class DefaultPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 2. 租户级定制（仅匹配TENANT_A租户）
 * @Extension(tenantCode = "TENANT_A", bizCode = "PAYMENT")
 * public class TenantAPaymentServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 3. 业务场景定制（匹配特定业务和场景）
 * @Extension(tenantCode = "TENANT_B", bizCode = "PAYMENT", scenario = "REFUND")
 * public class TenantBRefundServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * 
 * // 4. 动态条件匹配（使用表达式）
 * @Extension(
 *     tenantCode = "TENANT_C", 
 *     expression = "#context.get(\"amount\") > 10000 && #bizCode == 'VIP_ORDER'"
 * )
 * public class VipHighAmountServiceImpl implements PaymentService {
 *     // 实现逻辑
 * }
 * }
 * </pre>
 * 
 * <h3>最佳实践：</h3>
 * <ul>
 *   <li>总是为扩展点提供一个默认实现（bizCode="DEFAULT"）</li>
 *   <li>优先使用精确匹配，仅在需要复杂条件时使用表达式匹配</li>
 *   <li>为不同租户提供独立实现时，使用tenantCode进行隔离</li>
 *   <li>表达式匹配时避免过于复杂的逻辑，影响性能</li>
 *   <li>确保实现类的名称能够清晰表达其用途和适用场景</li>
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
     * 租户编码
     * <p>
     * 用于多租户场景下的扩展点隔离，只有当请求的租户编码与该值匹配时才会选择此实现
     * 默认值（*）表示匹配所有租户，适合通用实现
     * </p>
     * 
     * @return 租户编码
     */
    String tenantCode() default ExtPointConstants.DEFAULT_VALUE;

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
     *   <li><code>#context</code>：当前业务上下文对象，可以通过get方法获取属性</li>
     * </ul>
     * 
     * <p>表达式示例：</p>
     * <ul>
     *   <li><code>#tenantCode.startsWith('PREMIUM_')</code>：匹配高级租户</li>
     *   <li><code>#context.get('amount') > 1000</code>：匹配大额交易</li>
     *   <li><code>#bizCode == 'ORDER' && #scenario == 'VIP'</code>：匹配VIP订单场景</li>
     * </ul>
     * 
     * @return 动态匹配表达式
     */
    String expression() default ExtPointConstants.EMPTY_STRING;
}

