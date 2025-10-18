package com.bone.metadata.sdk.metadata.api;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.domain.model.TableMetadata;

import java.util.List;

/**
 * 元数据服务统一接口
 */
public interface MetadataService {

    /**
     * 获取指定上下文的所有扩展字段
     *
     * @param context 分配上下文
     * @return 扩展字段元数据列表
     */
    List<FieldMetadata> findExtensionFields(AllocationContext context);

    /**
     * 按逻辑字段名获取扩展字段元数据
     *
     * @param context      分配上下文
     * @param logicalNames 逻辑字段名列表
     * @return 匹配的扩展字段元数据
     */
    List<FieldMetadata> findExtensionFieldsByNames(AllocationContext context, List<String> logicalNames);

    /**
     * 分配并持久化扩展字段
     *
     * @param fields 待分配的字段元数据
     * @return 已分配物理列的字段元数据
     */
    List<FieldMetadata> allocateAndPersistFields(List<FieldMetadata> fields);

    /**
     * 健康检查方法
     *
     * @return 元数据服务是否健康
     */
    boolean isHealthy();
    
    /**
     * 获取指定实体类的表元数据
     * 
     * @param entityClass 实体类
     * @param <T> 实体类型
     * @return 表元数据
     */
    <T> TableMetadata getTableMetadata(Class<T> entityClass);
}