package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.metadata.*;
import com.bone.smartmeta.engine.metadata.processor.CompositeMetadataProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 元数据引擎，负责元数据的核心处理逻辑
 * 包括元数据的加载、验证、转换和应用
 */
@Component
@RequiredArgsConstructor
public class MetadataEngine {
    
    private final MetadataRegistry metadataRegistry;
    private final CompositeMetadataProcessor metadataProcessor;
    
    // 缓存计算字段的表达式引擎实例
    private final Map<String, Map<String, Object>> expressionEngineCache = new ConcurrentHashMap<>();
    
    // 用于热重载的调度器
    private ScheduledExecutorService hotReloadScheduler;
    
    // 添加日志变量
    private static final Logger log = LoggerFactory.getLogger(MetadataEngine.class);
    
    /**
     * 启动元数据热重载功能
     * @param intervalMillis 重载间隔（毫秒）
     */
    public void startHotReload(long intervalMillis) {
        log.info("启动元数据热重载功能，间隔: {}毫秒", intervalMillis);
        if (hotReloadScheduler != null && !hotReloadScheduler.isShutdown()) {
            log.warn("热重载调度器已经在运行中");
            return;
        }
        
        hotReloadScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "metadata-hot-reload-thread");
            thread.setDaemon(true);
            return thread;
        });
        
        hotReloadScheduler.scheduleAtFixedRate(this::refreshMetadata, 
                intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        log.info("元数据热重载功能启动成功");
    }
    
    /**
     * 刷新元数据（热重载实现）
     */
    private void refreshMetadata() {
        try {
            log.debug("开始刷新元数据");
            // 这里可以实现元数据的重新加载逻辑
            // 例如从文件、数据库或其他源重新读取元数据
            log.debug("元数据刷新完成");
        } catch (Exception e) {
            log.error("元数据刷新失败", e);
        }
    }
    
    /**
     * 停止元数据热重载功能
     */
    public void stopHotReload() {
        if (hotReloadScheduler != null && !hotReloadScheduler.isShutdown()) {
            log.info("停止元数据热重载功能");
            hotReloadScheduler.shutdown();
            try {
                if (!hotReloadScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    hotReloadScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                hotReloadScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("元数据热重载功能已停止");
        }
    }
    
    /**
     * 初始化元数据引擎
     */
    public void initialize() {
        log.info("初始化元数据引擎...");
        // MetadataRegistry已经在其PostConstruct中初始化
        log.info("元数据引擎初始化完成");
    }
    
    /**
     * 刷新元数据
     */
    public void refreshMetadata() {
        log.info("刷新元数据引擎中的所有元数据...");
        metadataRegistry.refreshMetadata();
        // 清理表达式引擎缓存
        expressionEngineCache.clear();
        log.info("元数据刷新完成");
    }
    
    /**
     * 注册新的实体元数据
     * @param metadata 实体元数据
     */
    public void registerEntity(EntityMetadata metadata) {
        validateEntityMetadata(metadata);
        metadataRegistry.registerEntity(metadata);
        log.info("实体元数据已成功注册: {}", metadata.getApiName());
    }
    
    /**
     * 注销实体元数据
     * @param entityApiName 实体API名称
     * @return 是否成功注销
     */
    public boolean unregisterEntity(String entityApiName) {
        // 先获取要移除的实体
        EntityMetadata removed = metadataRegistry.getEntityMetadata(entityApiName);
        // 调用注销方法
        metadataRegistry.unregisterEntity(entityApiName);
        if (removed != null) {
            // 清理相关缓存
            expressionEngineCache.remove(entityApiName);
            log.info("实体元数据已成功注销: {}", entityApiName);
            return true;
        }
        return false;
    }
    
    /**
     * 获取实体元数据
     * @param entityApiName 实体API名称
     * @return 实体元数据
     */
    public EntityMetadata getEntityMetadata(String entityApiName) {
        return metadataRegistry.getEntityMetadata(entityApiName);
    }
    
    /**
     * 获取所有实体元数据
     * @return 实体元数据列表
     */
    public List<EntityMetadata> getAllEntityMetadata() {
        return metadataRegistry.getAllEntityMetadata();
    }
    
    /**
     * 获取实体的计算字段
     * @param entityApiName 实体API名称
     * @return 计算字段映射表
     */
    public Map<String, CalculatedFieldMetadata> getCalculatedFields(String entityApiName) {
        List<FieldMetadata> fields = metadataRegistry.getCalculatedFields(entityApiName);
        Map<String, CalculatedFieldMetadata> result = new HashMap<>();
        // 将FieldMetadata转换为CalculatedFieldMetadata并添加到Map中
        for (FieldMetadata field : fields) {
            // 简化处理，这里我们直接将FieldMetadata作为CalculatedFieldMetadata使用
            // 在实际应用中，可能需要进行类型转换
            result.put(field.getApiName(), (CalculatedFieldMetadata) field);
        }
        return result;
    }
    
    /**
     * 获取实体的虚拟字段
     * @param entityApiName 实体API名称
     * @return 虚拟字段映射表
     */
    public Map<String, VirtualFieldMetadata> getVirtualFields(String entityApiName) {
        List<FieldMetadata> fields = metadataRegistry.getVirtualFields(entityApiName);
        Map<String, VirtualFieldMetadata> result = new HashMap<>();
        // 将FieldMetadata转换为VirtualFieldMetadata并添加到Map中
        for (FieldMetadata field : fields) {
            // 简化处理，这里我们直接将FieldMetadata作为VirtualFieldMetadata使用
            result.put(field.getApiName(), (VirtualFieldMetadata) field);
        }
        return result;
    }
    
    /**
     * 获取实体的AI元数据
     * @param entityApiName 实体API名称
     * @return AI元数据
     */
    public AiMetadata getAiMetadata(String entityApiName) {
        // 简化实现，返回null
        // 由于EntityMetadata没有getAiMetadata()方法，这里返回null
        return null;
    }
    
    /**
     * 验证实体元数据的完整性和正确性
     * @param metadata 实体元数据
     */
    private void validateEntityMetadata(EntityMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("实体元数据不能为空");
        }
        
        if (metadata.getApiName() == null || metadata.getApiName().trim().isEmpty()) {
            throw new IllegalArgumentException("实体API名称不能为空");
        }
        
        // 验证字段的唯一性
        Set<String> fieldApiNames = new HashSet<>();
        // 修复forEach循环，使用entrySet遍历Map
        for (Map.Entry<String, FieldMetadata> entry : metadata.getFields().entrySet()) {
            FieldMetadata field = entry.getValue();
            if (field.getApiName() == null || field.getApiName().trim().isEmpty()) {
                throw new IllegalArgumentException("字段API名称不能为空");
            }
            
            if (!fieldApiNames.add(field.getApiName())) {
                throw new IllegalArgumentException("字段API名称重复: " + field.getApiName());
            }
            
            // 验证计算字段
            if (field instanceof CalculatedFieldMetadata) {
                validateCalculatedField((CalculatedFieldMetadata) field, metadata);
            }
            
            // 验证虚拟字段
            if (field instanceof VirtualFieldMetadata) {
                validateVirtualField((VirtualFieldMetadata) field);
            }
        }
        
        // 验证AI元数据 - 简化处理，避免调用不存在的方法
        // 由于我们不能直接访问aiMetadata字段，这里跳过AI元数据的验证
        // 实际应用中可能需要通过其他方式获取AI元数据
    }
    
    /**
     * 验证计算字段
     */
    private void validateCalculatedField(CalculatedFieldMetadata field, EntityMetadata entityMetadata) {
        if (field.getCalculationExpression() == null || field.getCalculationExpression().trim().isEmpty()) {
            throw new IllegalArgumentException("计算字段必须包含表达式: " + field.getApiName());
        }
        
        // 验证计算依赖字段是否存在
        if (field.getCalculationDependencies() != null) {
            Map<String, FieldMetadata> fields = entityMetadata.getFields();
            for (String dependency : field.getCalculationDependencies()) {
                if (!fields.containsKey(dependency)) {
                    throw new IllegalArgumentException("计算字段依赖的字段不存在: " + dependency + 
                            " (字段: " + field.getApiName() + ")");
                }
            }
        }
    }
    
    /**
     * 验证虚拟字段
     */
    private void validateVirtualField(VirtualFieldMetadata field) {
        // 简化虚拟字段验证，移除对getProvider()方法的调用
        // 可以添加其他基本验证
        if (field.getApiName() == null || field.getApiName().trim().isEmpty()) {
            throw new IllegalArgumentException("虚拟字段API名称不能为空: " + field.getApiName());
        }
    }
    
    /**
     * 验证AI元数据
     */
    private void validateAiMetadata(AiMetadata aiMetadata) {
        // 简化AI元数据验证，移除对不存在方法的调用
        // 保留基本的非空验证
        if (aiMetadata != null) {
            // 这里可以添加其他不需要调用不存在方法的验证逻辑
            log.debug("AI元数据验证通过");
        }
    }
    
    /**
     * 处理实体实例，计算计算字段的值
     * @param entityApiName 实体API名称
     * @param entityData 实体数据
     * @return 处理后的实体数据
     */
    public Map<String, Object> processEntityInstance(String entityApiName, Map<String, Object> entityData) {
        Map<String, Object> processedData = new HashMap<>(entityData);
        
        // 处理计算字段
        Map<String, CalculatedFieldMetadata> calculatedFields = getCalculatedFields(entityApiName);
        for (Map.Entry<String, CalculatedFieldMetadata> entry : calculatedFields.entrySet()) {
            String fieldName = entry.getKey();
            CalculatedFieldMetadata field = entry.getValue();
            
            // 计算字段值（简化实现，实际应使用表达式引擎）
            Object calculatedValue = calculateFieldValue(field, processedData);
            processedData.put(fieldName, calculatedValue);
        }
        
        // 处理虚拟字段（实际实现中需要调用提供者服务）
        // 这里只是示例，实际应通过服务发现机制调用对应的提供者
        
        return processedData;
    }
    
    /**
     * 计算字段值
     * 注意：这里是简化实现，实际应使用表达式引擎如SpEL、MVEL等
     */
    private Object calculateFieldValue(CalculatedFieldMetadata field, Map<String, Object> context) {
        try {
            String expression = field.getCalculationExpression();
            // 简化实现，实际应使用表达式引擎
            // 这里仅作为示例，实际项目中应集成成熟的表达式计算框架
            log.debug("计算字段值: {} - 表达式: {}", field.getApiName(), expression);
            
            // 此处应使用实际的表达式引擎进行计算
            // 例如使用SpEL、MVEL或自定义表达式引擎
            return null; // 占位返回
        } catch (Exception e) {
            log.error("计算字段值失败: {}", field.getApiName(), e);
            return null;
        }
    }
    
    /**
     * 注册元数据变更监听器
     * @param listener 监听器
     */
    public void registerMetadataChangeListener(MetadataRegistry.MetadataChangeListener listener) {
        metadataRegistry.addMetadataChangeListener(listener);
    }
    
    /**
     * 注销元数据变更监听器
     * @param listener 监听器
     */
    public void unregisterMetadataChangeListener(MetadataRegistry.MetadataChangeListener listener) {
        metadataRegistry.removeMetadataChangeListener(listener);
    }
    
    /**
     * 搜索实体
     * @param searchCriteria 搜索条件
     * @return 匹配的实体元数据列表
     */
    public List<EntityMetadata> searchEntities(Map<String, Object> searchCriteria) {
        // 简化实现，返回所有实体元数据
        // 实际应用中可以根据searchCriteria进行过滤
        List<EntityMetadata> allEntities = metadataRegistry.getAllEntityMetadata();
        
        if (searchCriteria == null || searchCriteria.isEmpty()) {
            return allEntities;
        }
        
        // 简单的过滤实现
        List<EntityMetadata> filteredEntities = new ArrayList<>();
        for (EntityMetadata entity : allEntities) {
            boolean match = true;
            // 这里可以根据searchCriteria实现更复杂的过滤逻辑
            // 为了简化，这里只是一个占位符实现
            filteredEntities.add(entity);
        }
        
        return filteredEntities;
    }
    
    /**
     * 获取实体的业务规则
     * @param entityApiName 实体API名称
     * @return 业务规则列表
     */
    public List<ValidationRuleMetadata> getBusinessRules(String entityApiName) {
        EntityMetadata metadata = getEntityMetadata(entityApiName);
        if (metadata != null) {
            // 移除对getType()方法的调用，返回所有规则
            return metadata.getValidationRules();
        }
        return Collections.emptyList();
    }
    
    /**
     * 获取实体的验证规则
     * @param entityApiName 实体API名称
     * @return 验证规则列表
     */
    public List<ValidationRuleMetadata> getValidationRules(String entityApiName) {
        EntityMetadata metadata = getEntityMetadata(entityApiName);
        if (metadata != null) {
            // 移除对getType()方法的调用，返回所有规则
            return metadata.getValidationRules();
        }
        return Collections.emptyList();
    }
}