package com.bone.studio.generator.infrastructure.handler;

import com.bone.core.usecase.Capability;
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
    private final Map<String, CapabilityMetadata> capabilities = new ConcurrentHashMap<>();
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
                CapabilityMetadata metadata = CapabilityMetadata.builder()
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

    public List<CapabilityMetadata> getAllCapabilities() {
        return new ArrayList<>(capabilities.values());
    }

    public CapabilityMetadata getCapability(String name) {
        return capabilities.get(name);
    }
}

class CapabilityMetadata {
    private String name;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private boolean idempotent;
    private int cost;
    private boolean retryable;
    private int timeout;

    public CapabilityMetadata(String name, String description, String inputSchema, String outputSchema, 
                              boolean idempotent, int cost, boolean retryable, int timeout) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.outputSchema = outputSchema;
        this.idempotent = idempotent;
        this.cost = cost;
        this.retryable = retryable;
        this.timeout = timeout;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInputSchema() {
        return inputSchema;
    }

    public String getOutputSchema() {
        return outputSchema;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public int getCost() {
        return cost;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public int getTimeout() {
        return timeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String inputSchema;
        private String outputSchema;
        private boolean idempotent;
        private int cost;
        private boolean retryable;
        private int timeout;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder inputSchema(String inputSchema) {
            this.inputSchema = inputSchema;
            return this;
        }

        public Builder outputSchema(String outputSchema) {
            this.outputSchema = outputSchema;
            return this;
        }

        public Builder idempotent(boolean idempotent) {
            this.idempotent = idempotent;
            return this;
        }

        public Builder cost(int cost) {
            this.cost = cost;
            return this;
        }

        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public CapabilityMetadata build() {
            return new CapabilityMetadata(name, description, inputSchema, outputSchema, idempotent, cost, retryable, timeout);
        }
    }
}