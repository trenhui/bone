package com.bone.metadata.sdk.metadata.cache;

import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 元数据缓存服务
 * 负责管理元数据的缓存逻辑，提高元数据查询性能
 * 实现了MetadataService接口，可作为装饰器使用
 */
@Component
public class CachingMetadataService implements MetadataService {

    private static final Logger logger = LoggerFactory.getLogger(CachingMetadataService.class);
    
    private final MetadataService delegate;
    
    public CachingMetadataService(MetadataService delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<FieldMetadata> findExtensionFields(AllocationContext context) {
        // 可以根据需要添加缓存逻辑
        return delegate.findExtensionFields(context);
    }

    @Override
    public List<FieldMetadata> findExtensionFieldsByNames(AllocationContext ctx, List<String> logicalNames) {
        if (logicalNames == null || logicalNames.isEmpty()) {
            return List.of();
        }

        // 构建缓存键
        List<String> sortedNames = logicalNames.stream().sorted().toList();
        String key = buildCacheKey(ctx, sortedNames);
        String coarseKey = buildCoarseCacheKey(ctx);

        // 先查精细缓存
        List<FieldMetadata> cached = FieldCache.getByCacheKey(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 缓存未命中，查委托服务
        List<FieldMetadata> newMetadata = delegate.findExtensionFieldsByNames(ctx, logicalNames);

        // 更新粗粒度缓存（合并）
        FieldCache.mergeFieldMetadataCache(coarseKey, newMetadata);

        // 写入精细粒度缓存并返回
        FieldCache.putToCache(key, newMetadata);
        return newMetadata;
    }

    @Override
    public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
        List<FieldMetadata> result = delegate.allocateAndPersistFields(fields);
        
        // 分配字段后清除相关缓存
        if (!result.isEmpty()) {
            FieldMetadata firstField = result.get(0);
            AllocationContext ctx = AllocationContext.of(
                firstField.getTenantId(),
                firstField.getAppCode(),
                firstField.getBizIdentityCode(),
                firstField.getEntityType()
            );
            String coarseKey = buildCoarseCacheKey(ctx);
            // 可以添加缓存失效逻辑
            logger.debug("Field allocated, cache may need invalidation for: {}", coarseKey);
        }
        
        return result;
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    @Override
    public <T> com.bone.metadata.sdk.domain.model.TableMetadata getTableMetadata(Class<T> entityClass) {
        return delegate.getTableMetadata(entityClass);
    }
    
    /**
     * 构建精细缓存键
     */
    private String buildCacheKey(AllocationContext ctx, List<String> sortedNames) {
        return String.join("|",
                Objects.toString(ctx.getTenantId(), ""),
                ctx.getAppCode(),
                ctx.getBizIdentityCode(),
                ctx.getEntityType(),
                String.join(",", sortedNames)
        );
    }
    
    /**
     * 构建粗粒度缓存键
     */
    private String buildCoarseCacheKey(AllocationContext ctx) {
        return ctx.getAppCode() + "." + ctx.getEntityType();
    }
    
    /**
     * 获取委托服务
     */
    public MetadataService getDelegate() {
        return delegate;
    }
}