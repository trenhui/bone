package com.bone.engine.extension.api.annotation;

import java.lang.annotation.*;

/**
 * 扩展实现文档注解
 *
 * 为扩展点实现提供详细的文档信息，支持自动文档生成。
 * 该注解仅在编译期生效，不会增加运行时内存开销。
 *
 * @since 1.0.0
 * @see ExtensionDoc
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface ExtensionDoc {

    /**
     * 扩展实现业务名称（中文）
     */
    String name() default "";

    /**
     * 所属业务领域
     */
    String domain() default "";

    /**
     * 实现分类
     * 例如：standard, custom, test, mock, integration, thirdparty
     */
    String category() default "";

    /**
     * 详细描述
     */
    String description() default "";

    /**
     * 适用场景
     */
    String scenario() default "";

    /**
     * 实现特点
     */
    String feature() default "";

    /**
     * 配置说明
     */
    String configuration() default "";

    /**
     * 性能说明
     */
    String performance() default "";

    /**
     * 注意事项
     */
    String note() default "";

    /**
     * 使用限制
     */
    String limitation() default "";

    /**
     * 版本信息
     */
    String version() default "1.0.0";

    /**
     * 作者信息
     */
    String author() default "";

    /**
     * 创建日期
     */
    String created() default "";

    /**
     * 最后更新日期
     */
    String updated() default "";

    /**
     * 常见问题
     */
    FAQ[] faqs() default {};

    /**
     * 变更记录
     */
    Change[] changes() default {};

    /**
     * 与标准实现的差异
     */
    String differences() default "";

    /**
     * 依赖组件
     */
    String dependencies() default "";

    /**
     * 扩展能力
     * 描述该实现扩展了哪些能力
     */
    String capabilities() default "";

    /**
     * 常见问题
     */
    @interface FAQ {
        String question();
        String answer();
    }

    /**
     * 变更记录
     */
    @interface Change {
        String version();
        String description() default "";
        String date() default "";
    }
}