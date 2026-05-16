package com.bone.blueprint.application.capability;

import com.bone.blueprint.application.annotation.Capability;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/** 扫描带 {@link Capability} 的 Bean，供编排侧发现（可选能力，见 Bone-DDD §20）。 */
@Component
public class HandlerRegistry {

  private final Map<String, CapabilityMetadata> capabilities = new ConcurrentHashMap<>();
  private final ApplicationContext applicationContext;

  public HandlerRegistry(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @PostConstruct
  public void init() {
    Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(Capability.class);
    handlers.forEach(
        (name, handler) -> {
          Capability annotation = handler.getClass().getAnnotation(Capability.class);
          if (annotation != null) {
            CapabilityMetadata metadata =
                new CapabilityMetadata(
                    annotation.name(),
                    annotation.description(),
                    annotation.inputSchema(),
                    annotation.outputSchema(),
                    annotation.idempotent(),
                    annotation.cost(),
                    annotation.retryable(),
                    annotation.timeout());
            capabilities.put(annotation.name(), metadata);
          }
        });
  }

  public List<CapabilityMetadata> listCapabilities() {
    return new ArrayList<>(capabilities.values());
  }

  @Value
  public static class CapabilityMetadata {
    String name;
    String description;
    String inputSchema;
    String outputSchema;
    boolean idempotent;
    int cost;
    boolean retryable;
    int timeout;

    public CapabilityMetadata(
        String name,
        String description,
        String inputSchema,
        String outputSchema,
        boolean idempotent,
        int cost,
        boolean retryable,
        int timeout) {
      this.name = name;
      this.description = description;
      this.inputSchema = inputSchema;
      this.outputSchema = outputSchema;
      this.idempotent = idempotent;
      this.cost = cost;
      this.retryable = retryable;
      this.timeout = timeout;
    }
  }
}
