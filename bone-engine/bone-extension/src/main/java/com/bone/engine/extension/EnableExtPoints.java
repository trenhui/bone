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
 * 使用此注解在Spring Boot应用中启用扩展点功能，支持自定义仓库实现和路由策略
 * 
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
 * @author renhui.trh 2023-10-30
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({ExtPointRegister.class})
public @interface EnableExtPoints {
    /**
     * 指定扩展点仓库实现类
     * <p>
     * 默认使用内存仓库实现，可以自定义为Redis、Nacos等分布式实现
     * 
     * @return 扩展点仓库实现类
     */
    Class<? extends ExtPointRepository> extPointRepository() default MemExtPointRepository.class;

    /**
     * 指定扩展点路由策略实现类
     * <p>
     * 默认使用基于租户和业务标识的路由策略，可以自定义更复杂的路由规则
     * 
     * @return 扩展点路由策略实现类
     */
    Class<? extends ExtPointRouter> extPointRouter() default DefaultExtPointRouter.class;
    
    /**
     * 指定需要扫描的扩展点包路径
     * <p>
     * 可以指定多个包路径，默认扫描当前类所在的包及其子包
     * 
     * @return 需要扫描的包路径数组
     */
    String[] basePackages() default {};
    
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
