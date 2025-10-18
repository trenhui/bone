package com.bone.engine.extension;

import com.bone.engine.extension.register.ExtPointRegister;
import com.bone.engine.extension.repository.ExtPointRepository;
import com.bone.engine.extension.repository.MemExtPointRepository;
import com.bone.engine.extension.route.ExtPointRouter;
import com.bone.engine.extension.route.DefaultExtPointRouter;
import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用扩展点框架的核心注解
 * <p>
 * 在Spring Boot应用程序的主配置类上使用此注解，以启用扩展点功能。
 * 此注解会触发自动扫描和注册扩展点接口及其实现类的过程，并配置相应的代理机制。
 * </p>
 * 
 * <h3>功能特性：</h3>
 * <ul>
 *   <li>自动扫描并注册带有{@code @ExtPoint}注解的接口和类</li>
 *   <li>自动扫描并注册带有{@code @ExtProvider}注解的实现类</li>
 *   <li>支持基于业务上下文的动态路由</li>
 *   <li>支持多租户隔离</li>
 *   <li>支持自定义扩展点仓库实现（内存、Redis、Nacos等）</li>
 *   <li>支持自定义路由策略</li>
 * </ul>
 * 
 * <h3>基础用法示例：</h3>
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableExtPoints
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }
 * </pre>
 * 
 * <h3>自定义配置示例：</h3>
 * <pre>
 * {@code
 * @SpringBootApplication
 * @EnableExtPoints(
 *     basePackages = "com.example.business.extension",
 *     extensionRepository = RedisExtPointRepository.class,
 *     extensionRouter = CustomExtPointRouter.class
 * )
 * public class Application {
 *     public static void main(String[] args) {
 *         SpringApplication.run(Application.class, args);
 *     }
 * }
 * }
 * </pre>
 * 
 * @see ExtPoint 扩展点标记注解
 * @see ExtProvider 扩展提供者注解
 * @see ExtPointRegister 扩展点注册器
 * @since 1.0.0
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({ExtPointRegister.class})
public @interface EnableExtPoints {
    /**
     * 扫描的基础包路径
     * <p>
     * 指定要扫描的包，框架会在这些包中查找带有{@code @ExtPoint}和{@code @ExtProvider}注解的类
     * 如果未指定，默认扫描注解所在类的包及其子包
     * </p>
     * 
     * @return 基础包路径数组
     */
    String[] basePackages() default {};
    
    /**
     * 指定扩展点仓库实现类
     * <p>
     * 扩展点仓库负责存储和管理所有已注册的扩展提供者实例
     * 默认使用内存仓库实现，适用于单体应用
     * 分布式场景下可自定义为Redis、Nacos等分布式实现
     * </p>
     * 
     * @return 扩展点仓库实现类
     */
    Class<? extends ExtPointRepository> extensionRepository() default MemExtPointRepository.class;

    /**
     * 指定扩展点路由策略实现类
     * <p>
     * 扩展点路由策略负责根据业务上下文选择合适的扩展提供者实现
     * 默认使用多级路由策略，按租户ID、业务标识和条件表达式进行匹配
     * 可自定义实现复杂的路由算法以满足特殊业务需求
     * </p>
     * 
     * @return 扩展点路由策略实现类
     */
    Class<? extends ExtPointRouter> extPointRouter() default DefaultExtPointRouter.class;
    
    /**
     * 是否启用缓存
     * <p>
     * 启用缓存可以提高扩展点查找性能，默认开启
     * 
     * @return 是否启用缓存
     */
    boolean enableCache() default true;
    
    /**
     * 缓存过期时间（秒）
     * <p>
     * 当enableCache=true时有效，默认60秒
     * 
     * @return 缓存过期时间
     */
    int cacheExpireSeconds() default 60;
}
