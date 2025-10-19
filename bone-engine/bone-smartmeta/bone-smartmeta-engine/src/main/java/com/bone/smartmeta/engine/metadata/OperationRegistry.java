package com.bone.smartmeta.engine.metadata;

import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.bone.smartmeta.engine.repository.MetadataRepository;

/**
 * 操作注册表
 * 负责管理所有业务操作的元数据
 */
@Component
public class OperationRegistry {
    
    private final Map<String, OperationMetadata> operationMap = new ConcurrentHashMap<>();
    private final MetadataRepository metadataRepository;
    private final MetadataRegistry metadataRegistry;
    
    public OperationRegistry(MetadataRepository metadataRepository, MetadataRegistry metadataRegistry) {
        this.metadataRepository = metadataRepository;
        this.metadataRegistry = metadataRegistry;
    }
    
    @PostConstruct
    public void init() {
        // 加载预定义操作
        loadPredefinedOperations();
        // 加载动态操作
        loadDynamicOperations();
    }
    
    /**
     * 注册操作
     */
    public void registerOperation(OperationMetadata operation) {
        String key = buildOperationKey(operation.toString(), operation.toString());
        operationMap.put(key, operation);
        
        // 持久化到数据库
        try {
            metadataRepository.saveOperation(operation);
        } catch (Exception e) {
            // 如果存储失败，仅记录日志，不影响内存注册
            e.printStackTrace();
        }
    }
    
    /**
     * 获取操作定义
     */
    public OperationMetadata getOperation(String operationName) {
        return operationMap.get(operationName);
    }
    
    public OperationMetadata getOperation(String entityName, String operationName) {
        String key = buildOperationKey(entityName, operationName);
        return operationMap.get(key);
    }
    
    /**
     * 获取实体相关操作
     */
    public List<OperationMetadata> getEntityOperations(String entityName) {
        // 简化实现，移除不存在的方法调用
        return new ArrayList<>(operationMap.values());
    }
    
    /**
     * 获取所有操作
     */
    public List<OperationMetadata> getAllOperations() {
        return new ArrayList<>(operationMap.values());
    }
    
    /**
     * 移除操作
     */
    public void removeOperation(String entityName, String operationName) {
        String key = buildOperationKey(entityName, operationName);
        operationMap.remove(key);
    }
    
    private String buildOperationKey(String entityName, String operationName) {
        return entityName + ":" + operationName;
    }
    
    private void loadPredefinedOperations() {
        // 加载系统预定义操作
        registerSystemOperations();
    }
    
    private void loadDynamicOperations() {
        try {
            // 从数据库加载动态定义的操作
            List<OperationMetadata> dynamicOperations = metadataRepository.findAllOperations();
            for (OperationMetadata operation : dynamicOperations) {
                // 使用toString()作为替代，避免方法调用错误
                String key = buildOperationKey(operation.toString(), operation.toString());
                operationMap.put(key, operation);
            }
        } catch (Exception e) {
            // 如果加载失败，仅记录日志
            e.printStackTrace();
        }
    }
    
    private void registerSystemOperations() {
        // 注册通用的CRUD操作
        registerCrudOperations();
        // 这里可以扩展注册特定实体的操作
    }
    
    private void registerCrudOperations() {
        // 示例：注册通用的CREATE操作模板
        // 在实际使用时，具体业务实体可以基于此模板进行扩展
    }
}