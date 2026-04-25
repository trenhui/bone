package com.bone.core.usecase;

import com.bone.core.usecase.Capability;
import lombok.Builder;
import lombok.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class HandlerRegistry {
    private final Map<String, CapabilityRegistration> capabilities = new ConcurrentHashMap<>();
    private final ApplicationContext applicationContext;
    
    public HandlerRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    @PostConstruct
    public void init() {
        Map<String, Object> handlers = applicationContext.getBeansWithAnnotation(Capability.class);
        handlers.forEach((name, handler) -> {
            Capability annotation = handler.getClass().getAnnotation(Capability.class);
            if (annotation != null) {
                CapabilityRegistration metadata = CapabilityRegistration.builder()
                    .name(annotation.name())
                    .description(annotation.description())
                    .inputSchema(annotation.inputSchema())
                    .outputSchema(annotation.outputSchema())
                    .idempotent(annotation.idempotent())
                    .cost(annotation.cost())
                    .retryable(annotation.retryable())
                    .timeout(annotation.timeout())
                    .build();
                capabilities.put(annotation.name(), metadata);
            }
        });
    }
    
    public List<CapabilityRegistration> getAllCapabilities() {
        return new ArrayList<>(capabilities.values());
    }
    
    public CapabilityRegistration getCapability(String name) {
        return capabilities.get(name);
    }
    
    @Value
    @Builder
    public static class CapabilityRegistration {
        String name;
        String description;
        String inputSchema;
        String outputSchema;
        boolean idempotent;
        int cost;
        boolean retryable;
        int timeout;
    }
}
