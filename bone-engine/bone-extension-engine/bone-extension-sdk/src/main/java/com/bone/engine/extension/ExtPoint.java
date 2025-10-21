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
 * @ExtPoint(
 *     name = "订单折扣计算",
 *     description = "不同场景下的订单折扣逻辑",
 *     type = "BUSINESS",
 *     version = "1.0.0",
 *     category = "交易",
 *     priority = 100
 * )
 * public interface OrderDiscountExtPoint {
 *     DiscountResult calculate(BizContext<Order> context);
 * }
 * 
 * // 2. 提供默认实现
 * @Extension(bizCode = "DEFAULT", isDefault = true)
 * public class DefaultOrderDiscount implements OrderDiscountExtPoint {
 *     @Override
 *     public DiscountResult calculate(BizContext<Order> context) {
 *         // 默认折扣实现
 *         return new DiscountResult();
 *     }
 * }
 * }
 * </pre>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtPoint {
    
    /**
     * 扩展点名称
     */
    String name() default "";
    
    /**
     * 扩展点描述
     */
    String description() default "";
    
    /**
     * 扩展点类型，默认为业务扩展
     */
    String type() default "BUSINESS";
    
    /**
     * 扩展点版本号
     */
    String version() default "1.0.0";
    
    /**
     * 是否启用
     */
    boolean enabled() default true;
    
    /**
     * 扩展点分类
     */
    String category() default "";
    
    /**
     * 默认优先级
     */
    int priority() default 100;
    
    /**
     * 废弃版本
     */
    String deprecatedSince() default "";
    
    /**
     * 计划移除版本
     */
    String deprecatedIn() default "";
    
    /**
     * 是否允许动态替换实现（默认允许）
     */
    boolean allowDynamicReplace() default true;
    
    /**
     * 是否启用缓存（默认启用）
     */
    boolean enableCache() default true;
}
