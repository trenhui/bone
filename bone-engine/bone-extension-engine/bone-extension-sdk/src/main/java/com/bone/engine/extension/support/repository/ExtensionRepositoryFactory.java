package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.spi.ExtensionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExtensionRepository 工厂
 */
@Slf4j
public class ExtensionRepositoryFactory implements ApplicationContextAware {

    private static ApplicationContext ctx;

    private static final Map<String, ExtensionRepository> CACHE = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
    }

    /** 创建仓库实例（支持注解自定义） */
    public static ExtensionRepository create(Class<?> repoClass) {
        String key = repoClass.getName();
        return CACHE.computeIfAbsent(key, k -> {
            try {
                // 优先从 Spring 容器获取（支持 @Component）
                return (ExtensionRepository) ctx.getBean(repoClass);
            } catch (Exception ignored) {
                try {
                    // 反射创建
                    return (ExtensionRepository) repoClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new IllegalStateException("Cannot create ExtensionRepository: " + repoClass, e);
                }
            }
        });
    }

    /** 创建 Bean 实例（通用工具） */
    public static <T> T createBean(Class<T> clazz) {
        try {
            return ctx.getBean(clazz);
        } catch (Exception ignored) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalStateException("Cannot create bean: " + clazz, e);
            }
        }
    }

    public static ExtensionRepository getDefault() {
        return CACHE.computeIfAbsent("default", k -> {
            // 自动装配优先级：Nacos > Redis > InMemory
            try {
                return ctx.getBean("nacosExtensionRepository", ExtensionRepository.class);
            } catch (Exception ignored) {}
            try {
                return ctx.getBean("redisExtensionRepository", ExtensionRepository.class);
            } catch (Exception ignored) {}
            return new InMemoryExtensionRepository();
        });
    }
}