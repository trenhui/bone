package com.bone.smartmeta.engine.registry;

/**
 * 元数据注册表接口
 * 负责元数据的注册、查找和管理功能
 */
public interface MetadataRegistry {
    
    /**
     * 注册元数据
     * 
     * @param metadata 元数据对象
     */
    void registerMetadata(Object metadata);
    
    /**
     * 根据实体名称查找元数据
     * 
     * @param entityName 实体名称
     * @return 元数据对象，如果不存在则返回null
     */
    Object findMetadata(String entityName);
    
    /**
     * 注销元数据
     * 
     * @param entityName 实体名称
     * @return 是否注销成功
     */
    boolean unregisterMetadata(String entityName);
    
    /**
     * 获取所有注册的元数据实体名称
     * 
     * @return 实体名称集合
     */
    Iterable<String> getAllEntityNames();
    
    /**
     * 清空注册表
     */
    void clear();
}