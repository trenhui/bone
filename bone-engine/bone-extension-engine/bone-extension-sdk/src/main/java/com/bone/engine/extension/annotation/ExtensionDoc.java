package com.bone.engine.extension.annotation;

import java.lang.annotation.*;

/**
 * 扩展实现文档注解
 * <p>
 * 用于为扩展实现类提供详细的文档信息，支持自动生成API文档和开发工具提示
 * <strong>主要用途：</strong>
 * <ul>
 *   <li>提供扩展实现的详细描述和适配场景</li>
 *   <li>定义实现细节和与其他实现的差异</li>
 *   <li>指定性能特征和资源消耗</li>
 *   <li>配置作者和版本信息</li>
 *   <li>提供注意事项和使用建议</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExtensionDoc {
    
    /**
     * 扩展实现详细描述
     */
    String description() default "";
    
    /**
     * 适用场景说明
     */
    String scenarios() default "";
    
    /**
     * 实现细节说明
     */
    String implementationDetails() default "";
    
    /**
     * 与其他实现的差异
     */
    String differences() default "";
    
    /**
     * 性能特征描述
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
     * 作者信息
     */
    String author() default "";
    
    /**
     * 创建日期
     */
    String createDate() default "";
    
    /**
     * 最后修改日期
     */
    String lastModifiedDate() default "";
    
    /**
     * 测试覆盖情况
     */
    String testCoverage() default "";
    
    /**
     * 已知限制
     */
    String limitations() default "";
    
    /**
     * 推荐配置
     */
    String recommendedConfig() default "";
}