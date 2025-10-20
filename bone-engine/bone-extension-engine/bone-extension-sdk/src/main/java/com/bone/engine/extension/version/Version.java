package com.bone.engine.extension.version;

import java.lang.annotation.*;

/**
 * 扩展点版本注解
 * <p>
 * 用于标记扩展点实现的版本信息，提供版本管理、兼容性控制和生命周期管理功能
 * 与@Extension注解配合使用，提供更详细的版本元数据
 *
 * @author renhui.trh
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Version {
    /**
     * 版本号，遵循语义化版本规范（Major.Minor.Patch）
     * @return 版本号字符串
     */
    String value() default "1.0.0";

    /**
     * 兼容的版本列表，指定此实现兼容的其他版本
     * @return 兼容版本数组
     */
    String[] compatibleWith() default {};

    /**
     * 发布日期，格式为yyyy-MM-dd
     * @return 发布日期字符串
     */
    String releaseDate() default "";

    /**
     * 是否已弃用
     * @return true表示已弃用，false表示正常
     */
    boolean deprecated() default false;

    /**
     * 从哪个版本开始弃用
     * @return 弃用起始版本号
     */
    String deprecatedSince() default "";

    /**
     * 替代版本，当此版本被弃用时，推荐使用的替代版本
     * @return 替代版本号
     */
    String replacement() default "";

    /**
     * 版本描述
     * @return 版本描述信息
     */
    String description() default "";
}