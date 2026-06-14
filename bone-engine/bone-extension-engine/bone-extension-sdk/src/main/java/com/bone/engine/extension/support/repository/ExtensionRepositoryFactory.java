package com.bone.engine.extension.support.repository;

import com.bone.engine.extension.api.spi.ExtensionRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * ExtensionRepository 工厂
 *
 * <p>设计优化： 1. 作为Spring Bean管理，避免静态变量持有ApplicationContext 2. 使用依赖注入而非静态访问 3. 线程安全的缓存实现
 */
@Slf4j
@Component
public class ExtensionRepositoryFactory implements ApplicationContextAware {

  private ApplicationContext ctx;

  private final Map<String, ExtensionRepository> cache = new ConcurrentHashMap<>();

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.ctx = applicationContext;
  }

  /** 创建仓库实例（支持注解自定义） */
  public ExtensionRepository create(Class<? extends ExtensionRepository> repoClass) {
    String key = repoClass.getName();
    return cache.computeIfAbsent(
        key,
        k -> {
          try {
            // 优先从 Spring 容器获取（支持 @Component）
            return ctx.getBean(repoClass);
          } catch (Exception ignored) {
            try {
              // 反射创建
              return repoClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
              throw new IllegalStateException("Cannot create ExtensionRepository: " + repoClass, e);
            }
          }
        });
  }

  /** 创建 Bean 实例（通用工具） */
  public <T> T createBean(Class<T> clazz) {
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

  /** 获取默认仓库实现 优先级：Nacos > Redis > InMemory */
  public ExtensionRepository getDefault() {
    return cache.computeIfAbsent(
        "default",
        k -> {
          try {
            return ctx.getBean("nacosExtensionRepository", ExtensionRepository.class);
          } catch (Exception ignored) {
            log.debug("NacosExtensionRepository not available, trying RedisExtensionRepository");
          }
          try {
            return ctx.getBean("redisExtensionRepository", ExtensionRepository.class);
          } catch (Exception ignored) {
            log.debug("RedisExtensionRepository not available, using InMemoryExtensionRepository");
          }
          return new InMemoryExtensionRepository();
        });
  }

  /** 清理缓存（主要用于测试） */
  public void clearCache() {
    cache.clear();
    log.info("ExtensionRepositoryFactory cache cleared");
  }
}
