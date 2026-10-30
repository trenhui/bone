package com.bone.metadata.sdk.metadata;

import com.bone.metadata.sdk.domain.enums.DataType;
import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.support.cache.FieldCache;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

public class EmbeddedMetadataService implements MetadataService {
  private final ColumnAllocator allocator;
  private final FieldMetadataRepository fieldMetadataRepository;

  @Autowired
  public EmbeddedMetadataService(
      ColumnAllocator allocator, FieldMetadataRepository fieldMetadataRepository) {
    this.allocator = allocator;
    this.fieldMetadataRepository = fieldMetadataRepository;
  }

  public List<FieldMetadata> findExtensionFields(AllocationContext ctx) {
    return fieldMetadataRepository.findByContext(ctx);
  }

  public List<FieldMetadata> findExtensionFieldsByNames(
      AllocationContext ctx, List<String> logicalNames) {
    if (logicalNames == null || logicalNames.isEmpty()) {
      return List.of();
    }
    // 纵深防御：Web 层的 @Valid 只能挡住 HTTP 入站，而本类是 SDK 公共 API，
    // 进程内调用方同样可能传残缺上下文。缺 tenantId 时下面拼缓存键会 NPE ⇒ 500，
    // 故在这里以明确的契约错误失败，而不是让空指针泄漏成内部错误。
    requireUsableContext(ctx);

    List<String> sortedNames = logicalNames.stream().sorted().toList();
    String key =
        String.join(
            "|",
            ctx.getTenantId().toString(),
            ctx.getAppCode(),
            ctx.getBizIdentityCode(),
            ctx.getEntityType(),
            String.join(",", sortedNames));

    String cacheKey = ctx.getAppCode() + "." + ctx.getEntityType();

    // 先查精细缓存
    List<FieldMetadata> cached = FieldCache.getByCacheKey(key);
    if (cached != null && !cached.isEmpty()) {
      return cached;
    }

    // 缓存未命中，查数据库
    List<FieldMetadata> newMetadata =
        fieldMetadataRepository.findByContextAndNames(ctx, sortedNames);

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
      rollbackFor = Exception.class)
  public List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields) {
    if (fields == null || fields.isEmpty()) {
      return Collections.emptyList();
    }

    // 1. 按数据类型分组
    Map<DataType, List<FieldMetadata>> groupedFields =
        fields.stream().collect(Collectors.groupingBy(f -> DataType.valueOf(f.getDataType())));

    List<FieldMetadata> results = new ArrayList<>();
    FieldMetadata fieldMetadata = fields.get(0);
    AllocationContext ctx =
        AllocationContext.of(
            fieldMetadata.getTenantId(),
            fieldMetadata.getAppCode(),
            fieldMetadata.getBizIdentityCode(),
            fieldMetadata.getEntityType());
    // 2. 按数据类型批量分配
    groupedFields.forEach(
        (dataType, fieldGroup) -> {

          // 分配物理列名
          List<String> columns = allocator.allocate(ctx, dataType, fieldGroup.size());

          // 更新字段元数据
          IntStream.range(0, fieldGroup.size())
              .forEach(
                  i -> {
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

  /**
   * 校验上下文足以构成缓存键与查询路由：tenantId / appCode / entityType 三个分区键缺一不可。
   *
   * <p>{@code bizIdentityCode} 允许为空（{@code AllocationContext.of(appCode, entityType)} 的两参工厂本就不带它，且
   * {@code String.join} 对 null 元素无 NPE）。tenantId 是唯一会被 {@code .toString()} 直接解引用的键，缺它会当场
   * NPE；appCode/entityType 缺失虽然不会抛异常， 但会让缓存键退化成 {@code "null.null"}——不同实体互串缓存，属更隐蔽的正确性问题，一并挡掉。
   */
  private static void requireUsableContext(AllocationContext ctx) {
    if (ctx == null || ctx.getTenantId() == null) {
      throw new IllegalArgumentException(
          "findExtensionFieldsByNames 需要 AllocationContext.tenantId（缓存键与查询分区依赖它）");
    }
    if (ctx.getAppCode() == null || ctx.getAppCode().isBlank()) {
      throw new IllegalArgumentException(
          "findExtensionFieldsByNames 需要 AllocationContext.appCode（缓存键与查询分区依赖它）");
    }
    if (ctx.getEntityType() == null || ctx.getEntityType().isBlank()) {
      throw new IllegalArgumentException(
          "findExtensionFieldsByNames 需要 AllocationContext.entityType（缓存键与查询分区依赖它）");
    }
  }

  private void validateField(FieldMetadata field) {
    if (field.getColumnName() != null && !field.getColumnName().isBlank()) {
      throw new IllegalStateException(
          String.format(
              "Field [%s] already allocated column [%s]", field.getName(), field.getColumnName()));
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
