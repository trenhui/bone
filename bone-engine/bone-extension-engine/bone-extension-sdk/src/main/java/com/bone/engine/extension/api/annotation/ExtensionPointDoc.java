package com.bone.engine.extension.api.annotation;

import java.lang.annotation.*;

/**
 * 扩展点文档注解
 *
 * 为扩展点接口提供结构化文档信息，支持API文档自动生成。
 * 该注解仅在编译期生效，不会增加运行时内存开销。
 *
 * @since 1.0.0
 * @see ExtensionPointDoc
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExtensionPointDoc {

    /**
     * 扩展点业务名称（中文）
     * 用于在文档和管理界面中标识
     */
    String name() default "";

    /**
     * 扩展点所属业务领域
     * 例如：order, payment, inventory, member, messaging
     * 用于组织和分类扩展点
     */
    String domain() default "";

    /**
     * 扩展点功能分类
     * 例如：create, query, update, delete, calculate, validate, process
     * 用于进一步细分扩展点功能
     */
    String category() default "";

    /**
     * 详细描述
     * 说明扩展点的核心功能和作用
     */
    String description() default "";

    /**
     * 使用场景
     * 描述该扩展点适用的业务场景
     */
    String usage() default "";

    /**
     * 最佳实践
     * 提供使用该扩展点的最佳实践建议
     */
    String bestPractice() default "";

    /**
     * 代码示例
     * 提供使用该扩展点的代码示例
     */
    String example() default "";

    /**
     * 注意事项
     * 使用该扩展点需要注意的事项
     */
    String note() default "";

    /**
     * 性能说明
     * 描述该扩展点的性能特点和限制
     */
    String performance() default "";

    /**
     * 版本信息
     * 语义化版本号
     */
    String version() default "1.0.0";

    /**
     * 作者信息
     * 扩展点的设计者或维护团队
     */
    String author() default "";

    /**
     * 创建日期
     * 格式：yyyy-MM-dd
     */
    String created() default "";

    /**
     * 最后更新日期
     * 格式：yyyy-MM-dd
     */
    String updated() default "";

    /**
     * 参数说明
     */
    Param[] params() default {};

    /**
     * 返回值说明
     */
    Return returns() default @Return();

    /**
     * 常见问题
     */
    FAQ[] faqs() default {};

    /**
     * 变更记录
     */
    Change[] changes() default {};

    /**
     * 参数说明
     */
    @interface Param {
        /**
         * 参数名称
         */
        String name();

        /**
         * 参数类型
         */
        String type() default "";

        /**
         * 参数描述
         */
        String description() default "";

        /**
         * 是否必需
         */
        boolean required() default false;

        /**
         * 示例值
         */
        String example() default "";
    }

    /**
     * 返回值说明
     */
    @interface Return {
        /**
         * 返回类型
         */
        String type() default "";

        /**
         * 返回描述
         */
        String description() default "";

        /**
         * 成功示例
         */
        String successExample() default "";

        /**
         * 错误码
         */
        ErrorCode[] errorCodes() default {};
    }

    /**
     * 错误码定义
     */
    @interface ErrorCode {
        /**
         * 错误码
         */
        String code();

        /**
         * 错误描述
         */
        String description() default "";

        /**
         * 解决方案
         */
        String solution() default "";
    }

    /**
     * 常见问题
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
     * 变更记录
     */
    @interface Change {
        /**
         * 版本号
         */
        String version();

        /**
         * 变更描述
         */
        String description() default "";

        /**
         * 变更日期
         */
        String date() default "";
    }
}