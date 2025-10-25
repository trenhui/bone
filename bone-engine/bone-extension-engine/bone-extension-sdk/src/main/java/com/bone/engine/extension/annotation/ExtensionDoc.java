package com.bone.engine.extension.annotation;

import java.lang.annotation.*;

/**
 * 扩展实现文档注解
 * <p>
 * 用于为扩展点实现提供详细的文档信息，支持自动生成API文档和开发工具提示
 * <strong>主要用途：</strong>
 * <ul>
 *   <li>提供扩展实现的详细描述和使用说明</li>
 *   <li>定义扩展实现的适用场景和边界条件</li>
 *   <li>指定扩展实现的配置说明和依赖关系</li>
 *   <li>提供性能考量和注意事项</li>
 *   <li>支持版本变更历史记录</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 2.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExtensionDoc {
    
    /**
     * 扩展实现标题
     */
    String title() default "";
    
    /**
     * 扩展实现详细描述
     */
    String description() default "";
    
    /**
     * 适用场景描述
     */
    String applicableScenarios() default "";
    
    /**
     * 适用场景（兼容旧版）
     */
    String scenarios() default "";
    
    /**
     * 与其他实现的差异
     */
    String differences() default "";
    
    /**
     * 实现细节说明
     */
    String implementationDetails() default "";
    
    /**
     * 配置依赖说明
     */
    String configurationDependencies() default "";
    
    /**
     * 性能考量
     */
    String performanceConsiderations() default "";
    
    /**
     * 性能考量（兼容旧版）
     */
    String performance() default "";
    
    /**
     * 资源消耗说明
     */
    String resourceUsage() default "";
    
    /**
     * 注意事项
     */
    String notes() default "";
    
    /**
     * 已知限制
     */
    String limitations() default "";
    
    /**
     * 推荐配置
     */
    String recommendedConfig() default "";
    
    /**
     * 版本信息
     */
    String version() default "";
    
    /**
     * 作者信息
     */
    String author() default "";
    
    /**
     * 创建日期
     */
    String createDate() default "";
    
    /**
     * 常见问题解答
     */
    FAQ[] faqs() default {};
    
    /**
     * 版本变更历史
     */
    Change[] changes() default {};
    
    /**
     * FAQ内部注解
     */
    @interface FAQ {
        /**
         * 问题
         */
        String question();
        
        /**
         * 答案
         */
        String answer();
    }
    
    /**
     * 变更记录内部注解
     */
    @interface Change {
        /**
         * 版本号
         */
        String version();
        
        /**
         * 变更内容
         */
        String content();
        
        /**
         * 变更日期
         */
        String date() default "";
        
        /**
         * 变更作者
         */
        String author() default "";
    }
}