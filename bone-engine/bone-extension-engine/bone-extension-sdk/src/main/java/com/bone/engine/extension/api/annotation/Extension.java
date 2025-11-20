package com.bone.engine.extension.api.annotation;

import java.lang.annotation.*;

/**
 * 标记扩展点接口的具体实现
 *
 * 支持多维度路由匹配，包括租户、业务域、场景等。
 *
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Extension {

    /**
     * 扩展名称
     */
    String value() default "";

    /**
     * 扩展描述
     */
    String description() default "";

    // ========== 核心路由维度 ==========

    /**
     * 租户代码，支持通配符 *
     */
    String tenant() default "*";

    /**
     * 业务域代码
     */
    String biz() default "*";

    /**
     * 场景代码
     */
    String scenario() default "*";

    /**
     * 环境标识
     */
    String env() default "*";

    /**
     * 版本号
     */
    String version() default "1.0.0";

    // ========== 路由控制 ==========

    /**
     * 执行优先级，数值越小优先级越高
     */
    int order() default 100;

    /**
     * 路由权重，用于权重分配
     */
    int weight() default 100;

    /**
     * 流量比例（0-100），用于灰度发布
     */
    int traffic() default 100;

    /**
     * 是否默认实现
     */
    boolean primary() default false;

    /**
     * 是否启用
     */
    boolean enabled() default true;

    // ========== 高级配置 ==========

    /**
     * SpEL条件表达式
     *
     * 支持变量：context, tenant, biz, scenario, params
     */
    String condition() default "";

    /**
     * 标签匹配，格式：key=value
     */
    String[] tags() default {};

    /**
     * 生效开始时间（yyyy-MM-dd HH:mm:ss）
     */
    String startTime() default "";

    /**
     * 生效结束时间（yyyy-MM-dd HH:mm:ss）
     */
    String endTime() default "";

    /**
     * 是否异步执行
     */
    boolean async() default false;

    /**
     * 执行超时时间（秒），0表示使用扩展点默认配置
     */
    int timeout() default 0;
}