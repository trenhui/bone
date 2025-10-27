package com.bone.metadata.sdk.metadata;

import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.support.config.MetadataSdkProperties;
import com.bone.metadata.sdk.domain.enums.DeploymentMode;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.scope.refresh.RefreshScopeRefreshedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class DelegatingMetadataService implements MetadataService, ApplicationContextAware {

    private static final Logger logger = LoggerFactory.getLogger(DelegatingMetadataService.class.getName());
    
    // 当前激活的服务实例（原子引用保证线程安全）
    private final AtomicReference<MetadataService> activeDelegate = new AtomicReference<>();
    private final AtomicReference<DeploymentMode> currentMode = new AtomicReference<>();

    private ApplicationContext applicationContext;
    private final MetadataSdkProperties properties;

    public DelegatingMetadataService(ApplicationContext applicationContext,
                                     MetadataSdkProperties properties) {
        this.applicationContext = applicationContext;
        this.properties = properties;
    }

    /**
     * 应用启动后初始化
     */
    @PostConstruct
    public void initialize() {
        refreshActiveDelegate();
    }

    /**
     * 监听配置刷新事件
     */
    @EventListener(RefreshScopeRefreshedEvent.class)
    public void onConfigurationRefresh(RefreshScopeRefreshedEvent event) {
        logger.info("Metadata service configuration refreshed");
        refreshActiveDelegate();
    }

    /**
     * 刷新激活的服务实例
     */
    private synchronized void refreshActiveDelegate() {
        DeploymentMode targetMode = getEffectiveMode();

        // 如果模式未变化则跳过初始化
        if (activeDelegate.get() != null && currentMode.get() == targetMode) {
            return;
        }

        try {
            MetadataService newDelegate = applicationContext.getBean(targetMode.equals(DeploymentMode.REMOTE) ? "remoteMetadataService" : "embeddedMetadataService", MetadataService.class);
            activeDelegate.set(newDelegate);
            currentMode.set(targetMode);
            logger.info("Metadata service is now running in " + targetMode + " mode");
        } catch (Exception e) {
            handleDelegateException(targetMode, e);
        }
    }

    /**
     * 处理服务初始化异常（带自动恢复）
     */
    private void handleDelegateException(DeploymentMode mode, Exception ex) {
        logger.error("Failed to initialize {} metadata service", mode, ex);

        DeploymentMode fallbackMode = (mode == DeploymentMode.REMOTE)
                ? DeploymentMode.EMBEDDED
                : DeploymentMode.REMOTE;

        try {
            logger.warn("Attempting fallback to {} mode", fallbackMode);
            MetadataService fallbackService =  applicationContext.getBean("embeddedMetadataService", MetadataService.class);
            activeDelegate.set(fallbackService);
            currentMode.set(fallbackMode);
        } catch (Exception fallbackEx) {
            logger.error("Critical failure: Fallback to {} mode failed", fallbackMode, fallbackEx);
            throw new IllegalStateException("Unable to initialize metadata service", fallbackEx);
        }
    }

    /**
     * 获取当前有效的部署模式
     */
    private DeploymentMode getEffectiveMode() {
        // 由于@Data注解生成的getter方法可能有问题，暂时硬编码返回默认模式
        return DeploymentMode.EMBEDDED;
    }

    // ======== 公共访问方法 ========

    /**
     * 获取当前激活的委托服务实例
     *
     * @return 当前激活的MetadataService实例
     */
    public MetadataService getActiveDelegate() {
        ensureInitialized();
        return activeDelegate.get();
    }

    /**
     * 获取当前部署模式
     *
     * @return 当前激活的部署模式
     */
    public DeploymentMode getCurrentMode() {
        ensureInitialized();
        return currentMode.get();
    }

    /**
     * 确保服务已初始化
     */
    private void ensureInitialized() {
        if (activeDelegate.get() == null) {
            synchronized (this) {
                if (activeDelegate.get() == null) {
                    refreshActiveDelegate();
                }
            }
        }
    }

    // ======== 元数据服务接口实现 ========

    @Override
    public List<FieldMetadata> findExtensionFields(AllocationContext context) {
        return getDelegate().findExtensionFields(context);
    }

    @Override
    public List<FieldMetadata> findExtensionFieldsByNames(AllocationContext ctx, List<String> logicalNames) {
        return getDelegate().findExtensionFieldsByNames(ctx, logicalNames);
    }

    @Override
    public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
        return getDelegate().allocateAndPersistFields(fields);
    }

    @Override
    public boolean isHealthy() {
        MetadataService delegate = activeDelegate.get();
        return delegate != null && delegate.isHealthy();
    }
    
    @Override
    public <T> TableMetadata getTableMetadata(Class<T> entityClass) {
        return activeDelegate.get().getTableMetadata(entityClass);
    }

    // ======== 内部委托方法 ========

    /**
     * 获取当前代理的服务（带初始化检查）
     */
    private MetadataService getDelegate() {
        ensureInitialized();
        return activeDelegate.get();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
}