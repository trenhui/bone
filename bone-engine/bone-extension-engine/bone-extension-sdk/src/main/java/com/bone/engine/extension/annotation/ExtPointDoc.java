package com.bone.engine.extension.annotation;

import java.lang.annotation.*;

/**
 * 扩展点文档注解
 * <p>
 * 用于为扩展点提供详细的文档信息，支持自动生成API文档和开发工具提示
 * <strong>主要用途：</strong>
 * <ul>
 *   <li>提供扩展点的详细描述和使用说明</li>
 *   <li>定义扩展点的使用场景和最佳实践</li>
 *   <li>指定扩展点的参数说明和验证规则</li>
 *   <li>配置扩展点的返回值说明和错误码定义</li>
 *   <li>支持示例代码和常见问题解答</li>
 *   <li>提供性能建议和注意事项</li>
 *   <li>支持版本变更历史记录</li>
 * </ul>
 * </p>
 *
 * @author Bone Engine Team
 * @version 2.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExtPointDoc {
    
    /**
     * 扩展点标题
     */
    String title() default "";
    
    /**
     * 扩展点所属领域
     * 用于对扩展点进行领域归类，例如：订单、支付、会员等
     */
    String domain() default "";
    
    /**
     * 扩展点分类
     * 用于对扩展点进行更细粒度的分类，例如：创建、查询、更新等
     */
    String category() default "";
    
    /**
     * 扩展点详细描述
     */
    String description() default "";
    
    /**
     * 使用场景说明
     */
    String usage() default "";
    
    /**
     * 最佳实践说明
     */
    String bestPractices() default "";
    
    /**
     * 参数说明列表
     */
    Param[] params() default {};
    
    /**
     * 返回值说明
     */
    Return returnInfo() default @Return();
    
    /**
     * 使用示例
     */
    String example() default "";
    
    /**
     * 高级示例（复杂场景）
     */
    String advancedExample() default "";
    
    /**
     * 注意事项
     */
    String notes() default "";
    
    /**
     * 性能建议
     */
    String performanceTips() default "";
    
    /**
     * 常见问题解答
     */
    FAQ[] faqs() default {};
    
    /**
     * 版本变更历史
     */
    Change[] changes() default {};
    
    /**
     * 创建者信息
     */
    String creator() default "";
    
    /**
     * 创建日期
     */
    String createDate() default "";
    
    /**
     * 最后更新者
     */
    String lastUpdater() default "";
    
    /**
     * 最后更新日期
     */
    String lastUpdateDate() default "";
    
    /**
     * 参数说明注解
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
         * 是否必填
         */
        boolean required() default false;
        
        /**
         * 默认值
         */
        String defaultValue() default "";
        
        /**
         * 验证规则
         */
        String validationRules() default "";
        
        /**
         * 参数示例
         */
        String example() default "";
    }
    
    /**
     * 返回值说明注解
     */
    @interface Return {
        
        /**
         * 返回类型
         */
        String type() default "";
        
        /**
         * 返回值描述
         */
        String description() default "";
        
        /**
         * 错误码列表
         */
        ErrorCode[] errorCodes() default {};
        
        /**
         * 成功示例
         */
        String successExample() default "";
    }
    
    /**
     * 错误码说明注解
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
        
        /**
         * 解决方案建议
         */
        String solution() default "";
    }
    
    /**
     * 常见问题解答注解
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
     * 版本变更历史注解
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
        String date();
        
        /**
         * 变更者
         */
        String author() default "";
    }
}