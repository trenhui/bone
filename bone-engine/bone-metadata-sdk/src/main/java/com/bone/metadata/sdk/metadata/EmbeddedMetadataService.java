package com.bone.metadata.sdk.metadata;

import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.support.cache.FieldCache;
import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import com.bone.metadata.sdk.domain.model.ColumnMetadata;

public class EmbeddedMetadataService implements MetadataService {
    private final ColumnAllocator allocator;
    private final FieldMetadataRepository fieldMetadataRepository;

    @Autowired
    public EmbeddedMetadataService(ColumnAllocator allocator, FieldMetadataRepository fieldMetadataRepository) {
        this.allocator = allocator;
        this.fieldMetadataRepository = fieldMetadataRepository;
    }
    
    @Override
    public <T> TableMetadata getTableMetadata(Class<T> entityClass) {
        // 将类名转换为表名（简单实现：转为小写）
        String tableName = entityClass.getSimpleName().toLowerCase();
        // 创建空的列元数据列表
        List<ColumnMetadata> columns = Collections.emptyList();
        // 使用正确的构造函数创建TableMetadata
        return new TableMetadata(tableName, columns);
    }

    public List<FieldMetadata> findExtensionFields(AllocationContext ctx) {
        return fieldMetadataRepository.findByContext(ctx);
    }

    public List<FieldMetadata> findExtensionFieldsByNames(AllocationContext ctx, List<String> logicalNames) {
        if (logicalNames == null || logicalNames.isEmpty()) {
            return List.of();
        }

        List<String> sortedNames = logicalNames.stream().sorted().toList();
        String key = String.join("|",
                ctx.getTenantId().toString(),
                ctx.getAppCode(),
                ctx.getBizIdentityCode(),
                ctx.getEntityType(),
                String.join(",", sortedNames)
        );

        String cacheKey = ctx.getAppCode() + "." + ctx.getEntityType();

        // 先查精细缓存
        List<FieldMetadata> cached = FieldCache.getByCacheKey(key);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 缓存未命中，查数据库
        List<FieldMetadata> newMetadata = fieldMetadataRepository.findByContextAndNames(ctx, sortedNames);

        // 更新粗粒度缓存（合并）
        FieldCache.mergeFieldMetadataCache(cacheKey, newMetadata);

        // 写入精细粒度缓存并返回
        FieldCache.putToCache(key, newMetadata);
        return newMetadata;
    }


    /**
     * 预分配并持久化扩展字段元数据。
     *
     * @param fields 字段的数据类型
     * @return 新增的 FieldMetadata 列表
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class
    )
    public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
        if (fields == null || fields.isEmpty()) {
            return Collections.emptyList();
        }

        // 1. 按数据类型分组
        Map<DataType, List<FieldMetadata>> groupedFields = fields.stream()
                .collect(Collectors.groupingBy(f -> DataType.valueOf(f.getDataType())));


        List<FieldMetadata> results = new ArrayList<>();
        FieldMetadata fieldMetadata = fields.get(0);
        AllocationContext ctx = AllocationContext.of(fieldMetadata.getTenantId(), fieldMetadata.getAppCode(), fieldMetadata.getBizIdentityCode(), fieldMetadata.getEntityType());
        // 2. 按数据类型批量分配
        groupedFields.forEach((dataType, fieldGroup) -> {

            // 分配物理列名
            List<String> columns = allocator.allocate(ctx, dataType, fieldGroup.size());

            // 更新字段元数据
            IntStream.range(0, fieldGroup.size()).forEach(i -> {
                FieldMetadata field = fieldGroup.get(i);
                validateField(field);
                field.setColumnName(columns.get(i));
                field.setExtension(true);
            });

            results.addAll(fieldGroup);
        });

        // 3. 批量保存
        fieldMetadataRepository.batchSave(results);
        return results;
    }


    private void validateField(FieldMetadata field) {
        if (field.getColumnName() != null && !field.getColumnName().isBlank()) {
            throw new IllegalStateException(
                    String.format("Field [%s] already allocated column [%s]",
                            field.getName(), field.getColumnName()));
        }
    }

    /**
     * 健康检查方法
     *
     * @return 元数据服务是否健康
     */
    @Override
    public boolean isHealthy() {
        return true;
    }
}