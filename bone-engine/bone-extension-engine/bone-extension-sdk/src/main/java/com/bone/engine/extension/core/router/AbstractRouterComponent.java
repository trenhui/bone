package com.bone.engine.extension.core.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 路由组件抽象基类，提供RouterComponent接口的基础实现
 * 管理组件的生命周期状态和通用方法
 *
 * @author Trae AI
 */
public abstract class AbstractRouterComponent implements RouterComponent {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final AtomicBoolean initialized = new AtomicBoolean(false);
    protected final AtomicBoolean shutDown = new AtomicBoolean(false);
    
    @Override
    public final void initialize() {
        if (!initialized.get() && !shutDown.get()) {
            synchronized (this) {
                if (!initialized.get() && !shutDown.get()) {
                    try {
                        logger.info("Initializing {} component...", getComponentName());
                        doInitialize();
                        initialized.set(true);
                        logger.info("{} component initialized successfully.", getComponentName());
                    } catch (Exception e) {
                        logger.error("Failed to initialize {} component: {}", getComponentName(), e.getMessage(), e);
                        throw new RuntimeException("Component initialization failed: " + getComponentName(), e);
                    }
                }
            }
        }
    }
    
    @Override
    public final void shutdown() {
        if (initialized.get() && !shutDown.get()) {
            synchronized (this) {
                if (initialized.get() && !shutDown.get()) {
                    try {
                        logger.info("Shutting down {} component...", getComponentName());
                        doShutdown();
                        shutDown.set(true);
                        initialized.set(false);
                        logger.info("{} component shut down successfully.", getComponentName());
                    } catch (Exception e) {
                        logger.error("Error during shutdown of {} component: {}", getComponentName(), e.getMessage(), e);
                    }
                }
            }
        }
    }
    
    @Override
    public boolean isAvailable() {
        return initialized.get() && !shutDown.get();
    }
    
    /**
     * 子类实现具体的初始化逻辑
     */
    protected abstract void doInitialize() throws Exception;
    
    /**
     * 子类实现具体的关闭逻辑
     */
    protected abstract void doShutdown();
    
    /**
     * 检查组件是否已初始化，如果没有则抛出异常
     */
    protected void ensureInitialized() {
        if (!isAvailable()) {
            throw new IllegalStateException("Component " + getComponentName() + " is not initialized or has been shut down");
        }
    }
    
    /**
     * 组件名称，子类必须提供
     */
    @Override
    public abstract String getComponentName();
}