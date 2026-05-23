package com.bone.core.capability;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * 扫描带 {@link Capability} 的 Bean，供 Flow / AI 等编排发现。
 *
 * <p>使用 {@link AopUtils#getTargetClass(Object)} 与 {@link AnnotationUtils#findAnnotation} 处理
 * CGLIB / JDK 代理：若 Handler 同时带 {@code @Transactional}，Spring 生成代理类时
 * {@code handler.getClass().getAnnotation(...)} 返回 {@code null}，将导致能力丢失。
 */
@Slf4j
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
        handlers.forEach((beanName, handler) -> {
            Class<?> targetClass = AopUtils.getTargetClass(handler);
            Capability annotation = AnnotationUtils.findAnnotation(targetClass, Capability.class);
            if (annotation == null) {
                log.warn("Handler {} declared as Capability bean but annotation missing on target {}",
                        beanName, targetClass.getName());
                return;
            }
            CapabilityRegistration previous = capabilities.putIfAbsent(annotation.name(),
                    toRegistration(annotation, targetClass));
            if (previous != null) {
                log.warn("Duplicate capability name '{}' on {} ignored (already registered by {})",
                        annotation.name(), targetClass.getName(), previous.getDeclaringClass());
            }
        });
    }

    public List<CapabilityRegistration> getAllCapabilities() {
        return new ArrayList<>(capabilities.values());
    }

    public Optional<CapabilityRegistration> findCapability(String name) {
        return Optional.ofNullable(capabilities.get(name));
    }

    /** 兼容旧调用（返回 nullable）。新代码请使用 {@link #findCapability(String)}。 */
    public CapabilityRegistration getCapability(String name) {
        return capabilities.get(name);
    }

    private static CapabilityRegistration toRegistration(Capability annotation, Class<?> targetClass) {
        return CapabilityRegistration.builder()
                .name(annotation.name())
                .description(annotation.description())
                .inputSchema(annotation.inputSchema())
                .outputSchema(annotation.outputSchema())
                .idempotent(annotation.idempotent())
                .cost(annotation.cost())
                .retryable(annotation.retryable())
                .timeout(annotation.timeout())
                .declaringClass(targetClass.getName())
                .build();
    }

    /** 仅供测试 / 监控读取已注册能力快照。 */
    public Map<String, CapabilityRegistration> snapshot() {
        return Collections.unmodifiableMap(capabilities);
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
        String declaringClass;
    }
}
