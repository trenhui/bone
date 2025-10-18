package com.bone.engine.extension;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 扩展点提供者注解，用于标记扩展点接口的具体实现类
 * <p>
 * 通过该注解可以定义扩展点实现的匹配条件，支持：
 * <ul>
 *   <li>精确匹配：tenantCode、bizCode、useCase、scenario</li>
 *   <li>表达式匹配：使用SpEL表达式进行动态匹配</li>
 * </ul>
 * <p>
 * 当有多个实现匹配时，精确匹配优先级高于表达式匹配
 *
 * @author renhui.trh 2023-11-1
 * @since 1.0.0
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ExtProvider {
    /**
     * 租户编码，用于多租户场景的扩展点隔离
     * <p>
     * 默认值表示匹配所有租户
     */
    String tenantCode() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 业务编码，用于区分不同业务域的扩展点实现
     * <p>
     * 默认值表示匹配所有业务域
     */
    String bizCode() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 用例编码，用于区分不同业务用例的扩展点实现
     * <p>
     * 默认值表示匹配所有用例
     */
    String useCase() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 场景编码，用于区分不同业务场景的扩展点实现
     * <p>
     * 默认值表示匹配所有场景
     */
    String scenario() default ExtPointConstants.DEFAULT_VALUE;

    /**
     * 动态匹配表达式，使用Spring EL表达式语法
     * <p>
     * 表达式中可以使用以下变量：
     * <ul>
     *   <li>tenantCode：当前租户编码</li>
     *   <li>bizCode：当前业务编码</li>
     *   <li>useCase：当前用例编码</li>
     *   <li>scenario：当前场景编码</li>
     *   <li>context：当前业务上下文对象</li>
     * </ul>
     * <p>
     * 示例："#tenantCode.startsWith('TENANT_') && #bizCode == 'ORDER'"
     * <p>
     * 默认为空字符串，表示不使用表达式匹配
     */
    String expression() default ExtPointConstants.EMPTY_STRING;
}

