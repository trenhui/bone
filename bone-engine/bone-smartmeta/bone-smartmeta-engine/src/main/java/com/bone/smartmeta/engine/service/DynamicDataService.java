package com.bone.smartmeta.engine.service;

import java.util.List;
import java.util.Map;

/**
 * 动态数据服务
 * 提供基于元数据的动态数据操作接口
 */
public interface DynamicDataService {
    
    /**
     * 根据ID获取实体数据
     */
    Map<String, Object> getById(String entityName, String entityId);
    
    /**
     * 创建实体数据
     */
    Map<String, Object> create(String entityName, Map<String, Object> data);
    
    /**
     * 更新实体数据
     */
    Map<String, Object> update(String entityName, String entityId, Map<String, Object> data);
    
    /**
     * 删除实体数据
     */
    boolean delete(String entityName, String entityId);
    
    /**
     * 查询实体数据列表
     */
    List<Map<String, Object>> query(String entityName, Map<String, Object> queryParams);
    
    /**
     * 批量查询实体数据
     */
    List<Map<String, Object>> queryByIds(String entityName, List<String> ids);
    
    /**
     * 分页查询实体数据
     */
    Map<String, Object> queryPage(String entityName, Map<String, Object> queryParams,
                                int page, int size);
    
    /**
     * 批量创建实体数据
     */
    List<Map<String, Object>> batchCreate(String entityName, List<Map<String, Object>> dataList);
    
    /**
     * 批量更新实体数据
     */
    List<Map<String, Object>> batchUpdate(String entityName, List<Map<String, Object>> dataList);
    
    /**
     * 批量删除实体数据
     */
    int batchDelete(String entityName, List<String> ids);
    
    /**
     * 执行数据操作（如存储过程、函数等）
     */
    Object executeOperation(String entityName, String operationName, Map<String, Object> params);
    
    /**
     * 验证数据格式
     */
    Map<String, Object> validateData(String entityName, Map<String, Object> data);
    
    /**
     * 获取实体统计数据
     */
    Map<String, Object> getStatistics(String entityName, Map<String, Object> queryParams);
}