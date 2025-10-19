package com.bone.metadata.sdk.extension;

import com.bone.metadata.sdk.domain.exception.FieldAllocationException;
import com.bone.metadata.sdk.extension.handler.ExtensionStorageHandler;
import com.bone.metadata.sdk.domain.enums.ExtensionMode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展字段协调器，负责策略分发、缓存、事件发布和监控。
 */
@Service
public class ExtensionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ExtensionCoordinator.class);
    private final ApplicationContext context;
    private final Map<ExtensionMode, ExtensionStorageHandler> handlers = new ConcurrentHashMap<>();
    private final ExtensionMode defaultMode = ExtensionMode.RESERVED_COLUMNS;
    
    public ExtensionCoordinator(ApplicationContext context) {
        this.context = context;
    }

    public void fallbackToJson(ExtensionContext context) {
        getHandler(ExtensionMode.JSON).save(context);
    }

    @PostConstruct
    public void init() {
        Map<String, ExtensionStorageHandler> handlerBeans = context.getBeansOfType(ExtensionStorageHandler.class);
        handlerBeans.values().forEach(handler -> handlers.put(handler.getMode(), handler));
    }

    @Transactional
    public void save(ExtensionContext context) {
        try {
            ExtensionStorageHandler handler = getHandler(context.getMode());
            handler.save(context);
        } catch (FieldAllocationException e) {
            log.info("降级至JSON模式成功，实体: {}:{}", context.getEntityType(), context.getEntityId(), e);
            fallbackToJson(context);
        }
    }

    @Async
    @Transactional
    public CompletableFuture<Void> saveAsync(ExtensionContext context) {
        return CompletableFuture.runAsync(() -> save(context));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> load(ExtensionContext context) {

        ExtensionStorageHandler handler = getHandler(context.getMode());
        Map<String, Object> result = handler.load(context);
        if (result == null||result.isEmpty()) {
            //todo 只有降级后的才从JSONHandler取，这里要改造
            result = getHandler(ExtensionMode.JSON).load(context);
        }
        return result;
    }

    private ExtensionStorageHandler getHandler(ExtensionMode mode) {
        return handlers.getOrDefault(mode, handlers.get(defaultMode));
    }
}