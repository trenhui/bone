package com.bone.engine.extension.annotation;

import java.lang.annotation.*;

/**
 * 扩展点文档注解
 * <p>
 * 用于为扩展点提供详细的文档信息，支持自动生成API文档和开发工具提示
 * <strong>主要用途：</strong>
 * <ul>
 *   <li>提供扩展点的详细描述</li>
 *   <li>定义扩展点的使用场景</li>
 *   <li>指定扩展点的参数说明</li>
 *   <li>配置扩展点的返回值说明</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtensionDoc {
    
    /**
     * 扩展点标题
     */
    String title() default "";
    
    /**
     * 扩展点详细描述
     */
    String description() default "";
    
    /**
     * 使用场景说明
     */
    String usage() default "";
    
    /**
     * 参数说明
     */
    Param[] params() default {};
    
    /**
     * 返回值说明
     */
    Return returnInfo() default @Return();
    
    /**
     * 示例代码
     */
    String example() default "";
    
    /**
     * 注意事项
     */
    String notes() default "";
    
    /**
     * 创建者
     */
    String creator() default "";
    
    /**
     * 创建日期
     */
    String createDate() default "";
    
    /**
     * 参数说明内部注解
     */
    @interface Param {
        
        /**
         * 参数名称
         */
        String name();
        
        /**
         * 参数类型
         */
        String type();
        
        /**
         * 参数描述
         */
        String description();
        
        /**
         * 是否必需
         */
        boolean required() default false;
        
        /**
         * 默认值
         */
        String defaultValue() default "";
    }
    
    /**
     * 返回值说明内部注解
     */
    @interface Return {
        
        /**
         * 返回值类型
         */
        String type() default "";
        
        /**
         * 返回值描述
         */
        String description() default "";
        
        /**
         * 可能的错误码
         */
        ErrorCode[] errorCodes() default {};
    }
    
    /**
     * 错误码说明内部注解
     */
    @interface ErrorCode {
        
        /**
         * 错误码
         */
        String code();
        
        /**
         * 错误描述
         */
        String description();
    }
}