package com.bone.engine.extension.annotation;

import com.bone.engine.extension.config.ExtensionAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 启用扩展点框架注解
 * <p>
 * 用于在Spring Boot应用中启用扩展点框架功能
 * <strong>核心功能：</strong>自动导入扩展点相关配置，启用代理工厂、路由引擎等核心组件
 * </p>
 * 
 * <h3>使用方式：</h3>
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
 * @author Bone Engine Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import(ExtensionAutoConfiguration.class)
public @interface EnableExtPoints {
    
    /**
     * 是否启用自动扫描
     */
    boolean enableAutoScan() default true;
    
    /**
     * 扫描的基础包路径
     */
    String[] basePackages() default {};
    
    /**
     * 是否启用缓存
     */
    boolean enableCache() default true;
    
    /**
     * 是否启用事件发布
     */
    boolean enableEvents() default true;
}