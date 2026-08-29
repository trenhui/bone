package com.bone.metadata.engine.runtime.config;

import com.bone.metadata.engine.ports.registry.MetadataRegistry;
import com.bone.metadata.engine.runtime.metadata.processor.CompositeMetadataProcessor;
import com.bone.metadata.engine.runtime.metadata.processor.MetadataProcessor;
import com.bone.metadata.engine.runtime.repository.InMemoryMetadataRepository;
import com.bone.metadata.engine.runtime.repository.MetadataRepository;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 引擎运行时自动装配配置。
 *
 * <p>为 {@code MetadataEngine} 等遗留引擎组件的必需依赖提供默认 bean（内存实现），使 runtime 作为库被宿主 （如 {@code
 * bone-metadata-server}）组件扫描时能完整装配，不依赖外部 starter。
 *
 * <p>说明：
 *
 * <ul>
 *   <li>{@code ports.registry.MetadataRegistry} 无独立实现类，此处提供内存匿名实现。
 *   <li>{@code runtime.repository.MetadataRepository} / {@code
 *       runtime.metadata.processor.MetadataProcessor} 分别由 {@code InMemoryMetadataRepository} /
 *       {@code CompositeMetadataProcessor} 实现。
 * </ul>
 *
 * <p>所有 bean 均带 {@code @ConditionalOnMissingBean}，宿主可自行覆盖为真实实现。
 */
@Configuration
public class RuntimeEngineAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public MetadataRegistry portsMetadataRegistry() {
    return new MetadataRegistry() {
      private final Map<String, Object> registry = new ConcurrentHashMap<>();

      @Override
      public void registerMetadata(Object metadata) {
        if (metadata != null) {
          registry.put(
              metadata.getClass().getSimpleName() + "#" + System.identityHashCode(metadata),
              metadata);
        }
      }

      @Override
      public Object findMetadata(String entityName) {
        return registry.get(entityName);
      }

      @Override
      public boolean unregisterMetadata(String entityName) {
        return registry.remove(entityName) != null;
      }

      @Override
      public Iterable<String> getAllEntityNames() {
        return new ArrayList<>(registry.keySet());
      }

      @Override
      public void clear() {
        registry.clear();
      }
    };
  }

  @Bean
  @ConditionalOnMissingBean
  public MetadataRepository metadataRepository() {
    return new InMemoryMetadataRepository();
  }

  @Bean
  @ConditionalOnMissingBean
  public MetadataProcessor metadataProcessor(ApplicationEventPublisher eventPublisher) {
    return new CompositeMetadataProcessor(new ArrayList<>(), eventPublisher);
  }
}
