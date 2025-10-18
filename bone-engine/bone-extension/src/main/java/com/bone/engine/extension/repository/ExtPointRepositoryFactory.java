package com.bone.engine.extension.repository;

import com.bone.core.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展点仓库工厂，负责创建和管理不同类型的扩展点仓库实例
 *
 * @author renhui.trh 2023-10-30
 */
public class ExtPointRepositoryFactory {
    private static final Logger log = LoggerFactory.getLogger(ExtPointRepositoryFactory.class);
    // 缓存已创建的仓库实例
    private static final Map<String, ExtPointRepository> REPOSITORY_CACHE = new ConcurrentHashMap<>();
    // 默认仓库类型
    private static final Class<? extends ExtPointRepository> DEFAULT_REPOSITORY_CLASS = MemExtPointRepository.class;

    /**
     * 创建默认的扩展点仓库实例
     * 
     * @return 默认的扩展点仓库实例（内存仓库）
     */
    public static ExtPointRepository createExtPointRepository() {
        return createExtPointRepository(DEFAULT_REPOSITORY_CLASS);
    }

    /**
     * 根据指定类型创建扩展点仓库实例
     * 
     * @param repositoryClass 仓库类型
     * @return 扩展点仓库实例
     */
    public static ExtPointRepository createExtPointRepository(Class<?> repositoryClass) {
        Assert.notNull(repositoryClass, "Repository class must not be null");
        
        String className = repositoryClass.getSimpleName();
        log.debug("Creating extension repository of type: {}", className);
        
        return REPOSITORY_CACHE.computeIfAbsent(className, key -> {
            try {
                // 优先尝试通过反射创建实例（更灵活）
                if (ExtPointRepository.class.isAssignableFrom(repositoryClass)) {
                    return (ExtPointRepository) repositoryClass.getDeclaredConstructor().newInstance();
                } else {
                    log.warn("Repository class {} does not implement ExtPointRepository interface", className);
                    // 回退到硬编码的创建方式
                    return createRepositoryByType(className);
                }
            } catch (Exception e) {
                log.error("Failed to create repository of type {} using reflection, falling back to default", 
                        className, e);
                // 反射失败时回退到硬编码方式
                return createRepositoryByType(className);
            }
        });
    }
    
    /**
     * 通过类型名称创建仓库实例（硬编码方式）
     */
    private static ExtPointRepository createRepositoryByType(String className) {
        switch (className) {
            case "MemExtPointRepository":
                return new MemExtPointRepository();
            case "RedisExtPointRepository":
                return new RedisExtPointRepository();
            case "NacosExtPointRepository":
                return new NacosExtPointRepository();
            default:
                log.warn("Unsupported repository type: {}, using MemExtPointRepository as default", className);
                return new MemExtPointRepository();
        }
    }
    
    /**
     * 获取仓库实例的数量
     * 
     * @return 仓库实例数量
     */
    public static int getRepositoryCount() {
        return REPOSITORY_CACHE.size();
    }
    
    /**
     * 清除仓库缓存
     */
    public static void clearRepositoryCache() {
        REPOSITORY_CACHE.clear();
        log.info("Cleared extension repository cache");
    }
}
