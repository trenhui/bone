package com.bone.engine.extension.api.annotation;

import com.bone.engine.extension.core.register.ExtensionPointRegister;
import com.bone.engine.extension.core.router.DefaultExtPointRouter;
import com.bone.engine.extension.support.repository.InMemoryExtensionRepository;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用 Bone Engine 扩展点框架
 *
 * 在Spring Boot应用中启用企业级插件化框架，支持智能路由、多租户隔离和动态扩展能力。
 *
 * @see ExtensionPoint
 * @see Extension
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ExtensionPointRegister.class)

public @interface EnableExtensionPoints {

    /**
     * 扫描扩展点实现的基础包路径
     *
     * 未指定时默认扫描注解所在包及其子包
     */
    String[] basePackages() default {};

    Class<?> extensionRepository() default InMemoryExtensionRepository.class;

    Class<?> extensionRouter() default DefaultExtPointRouter.class;

    /**
     * 自定义路由器实现类
     *
     * 必须实现 ExtensionRouter 接口，优先级高于内置路由器
     */
    String customRouter() default "";

    /**
     * 自定义执行器实现类
     *
     * 必须实现 ExtensionExecutor 接口，优先级高于内置执行器
     */
    String customExecutor() default "";

    /**
     * 是否启用路由缓存
     *
     * 默认启用以提升性能，开发环境可禁用
     */
    boolean cacheEnabled() default true;

    /**
     * 是否启用监控指标
     *
     * 默认启用指标收集，支持Prometheus集成
     */
    boolean metricsEnabled() default true;

    /**
     * 路由策略
     *
     * 支持：default(按精度), score（评分）、priority（优先级）、weight（权重）、first（首次匹配）
     */
    String routingStrategy() default "default";

    /**
     * 是否启用严格模式
     *
     * 严格模式下未找到匹配扩展点会抛出异常
     */
    boolean strictMode() default false;

    /**
     * 执行超时时间（秒）
     *
     * 扩展点方法执行的超时时间，0表示不超时
     */
    int timeout() default 30;
}