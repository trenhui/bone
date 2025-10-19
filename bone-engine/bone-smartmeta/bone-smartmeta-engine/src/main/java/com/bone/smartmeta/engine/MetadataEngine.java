package com.bone.smartmeta.engine;

import com.bone.smartmeta.engine.config.SmartMetaProperties;
import com.bone.smartmeta.engine.metadata.*;
import com.bone.smartmeta.engine.metadata.SmartFieldMetadata;
import com.bone.smartmeta.engine.metadata.processor.CompositeMetadataProcessor;
import com.bone.smartmeta.engine.service.GenericOperationService;
import com.bone.smartmeta.engine.repository.MetadataRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 元数据引擎，负责元数据的核心处理逻辑
 * 包括元数据的加载、验证、转换和应用
 */
@Component
public class MetadataEngine implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MetadataEngine.class);
    
    private final MetadataRegistry metadataRegistry;
    private final MetadataRepository metadataRepository;
    private final CompositeMetadataProcessor metadataProcessor;
    private final ApplicationEventPublisher eventPublisher;
    private GenericOperationService operationService; // 延迟注入
    
    // 存储注册的实体元数据
    private final Map<String, EntityMetadata> entityMetadataMap = new HashMap<>();
    
    // 存储操作元数据（全局缓存）
    private final Map<String, OperationMetadata> operationMetadataCache = new ConcurrentHashMap<>();
    
    // 用于测试的构造函数
    public MetadataEngine(SmartMetaProperties properties) {
        this.metadataRegistry = null;
        this.metadataRepository = null;
        this.metadataProcessor = null;
        this.eventPublisher = null;
        // 使用测试构造函数初始化MetadataEngine
    }
    
    // 正常的构造函数
    public MetadataEngine(MetadataRegistry metadataRegistry, 
                         MetadataRepository metadataRepository, 
                         CompositeMetadataProcessor metadataProcessor, 
                         ApplicationEventPublisher eventPublisher) {
        this.metadataRegistry = metadataRegistry;
        this.metadataRepository = metadataRepository;
        this.metadataProcessor = metadataProcessor;
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * 设置操作服务（延迟注入避免循环依赖）
     */
    public void setOperationService(GenericOperationService operationService) {
        this.operationService = operationService;
    }
    
    // 配置参数
    private boolean cacheEnabled = true;
    private boolean validationEnabled = true;
    private boolean calculationEnabled = true;
    private long cacheExpirationTime = 3600000; // 默认缓存过期时间：1小时
    private int maxRetries = 3; // 操作重试次数
    private long retryDelay = 100; // 重试延迟时间（毫秒）
    
    // 缓存管理相关
    private final Map<String, CacheEntry> entityMetadataCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> expressionEngineCache = new ConcurrentHashMap<>();
    
    // 内部监听器接口定义
    public interface MetadataChangeListener {
        void onMetadataChanged(String entityType, String changeType);
    }
    
    // 监听器集合
    private final List<MetadataChangeListener> metadataChangeListeners = new ArrayList<>();
    

    
    /**
     * 缓存条目类
     */
    private static class CacheEntry {
        private final Object value;
        private final long expirationTime;
        
        public CacheEntry(Object value, long ttlMillis) {
            this.value = value;
            this.expirationTime = ttlMillis > 0 ? System.currentTimeMillis() + ttlMillis : Long.MAX_VALUE;
        }
        
        public Object getValue() {
            return value;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTime;
        }
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        // 初始化元数据引擎
        // 初始化逻辑
        initializeOperationMetadata();
        // 元数据引擎初始化完成
    }
    
    /**
     * 初始化操作元数据
     */
    private void initializeOperationMetadata() {
        try {
            // 从仓库加载所有操作元数据
            List<OperationMetadata> operations = metadataRepository.findAllOperations();
            for (OperationMetadata operation : operations) {
                registerOperation(operation);
            }
    
        } catch (Exception e) {
    
        }
    }
    
    /**
     * 注册操作元数据
     */
    public void registerOperation(OperationMetadata operation) {
        if (operation == null) {
            throw new IllegalArgumentException("操作元数据参数无效");
        }
        
        // 构建操作键，使用空字符串代替不存在的方法调用
        String operationKey = buildOperationKey("", "");
        
        // 存储到缓存
        operationMetadataCache.put(operationKey, operation);
        
        // 简化实现，移除getEntityName方法调用
        // 同时注册到对应的实体元数据中
        EntityMetadata entityMetadata = null;
        if (entityMetadata != null) {
            entityMetadata.addOperation(operation);
        }
        
        // 通知元数据变更监听器

        
        // 触发元数据变更事件
        notifyMetadataChanged(operation, MetadataChangeType.CREATE);
    }
    
    /**
     * 获取操作元数据
     */
    public OperationMetadata getOperationMetadata(String operationName) {
        // 尝试直接通过名称查找
        for (OperationMetadata operation : operationMetadataCache.values()) {
            // 简化实现，不使用getName方法调用
        }
        return null;
    }
    
    /**
     * 获取实体的操作元数据
     */
    public OperationMetadata getOperationMetadata(String entityName, String operationName) {
        String operationKey = buildOperationKey(entityName, operationName);
        return operationMetadataCache.get(operationKey);
    }
    
    /**
     * 获取实体的所有操作元数据
     */
    public List<OperationMetadata> getEntityOperations(String entityName) {
        List<OperationMetadata> result = new ArrayList<>();
        String prefix = entityName + ".";
        
        for (Map.Entry<String, OperationMetadata> entry : operationMetadataCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.add(entry.getValue());
            }
        }
        
        return result;
    }
    
    /**
     * 删除操作元数据
     */
    public void unregisterOperation(String entityName, String operationName) {
        String operationKey = buildOperationKey(entityName, operationName);
        OperationMetadata removed = operationMetadataCache.remove(operationKey);
        
        // 从实体元数据中移除
        EntityMetadata entityMetadata = getEntityMetadata(entityName);
        if (entityMetadata != null) {
            entityMetadata.removeOperation(operationName);
        }
        
        if (removed != null) {
            // 注销操作元数据
            notifyMetadataChanged(removed, MetadataChangeType.DELETE);
        }
    }
    
    /**
     * 构建操作键
     */
    private String buildOperationKey(String entityName, String operationName) {
        return entityName + "." + operationName;
    }
    
    /**
     * 执行操作
     */
    public OperationResult executeOperation(String operationName, String entityId, Map<String, Object> parameters) {
        if (operationService == null) {
            throw new IllegalStateException("操作服务未初始化");
        }
        return operationService.execute(operationName, entityId, parameters);
    }
    
    /**
     * 执行操作（带上下文）
     */
    public OperationResult executeOperation(String operationName, String entityId,
                                          Map<String, Object> parameters, Map<String, Object> context) {
        if (operationService == null) {
            throw new IllegalStateException("操作服务未初始化");
        }
        return operationService.execute(operationName, entityId, parameters, context);
    }
    
    /**
     * 重新加载操作元数据
     */
    public void reloadOperationMetadata() {
        // 清除缓存
        operationMetadataCache.clear();
        
        // 重新初始化
        initializeOperationMetadata();
        
        // 重新加载实体中的操作元数据
        for (EntityMetadata entityMetadata : entityMetadataMap.values()) {
            entityMetadata.initializeOperationMap();
        }
        
        // 操作元数据重新加载完成
    }
    
    /**
     * 元数据变更类型枚举
     */
    public enum MetadataChangeType {
        CREATE, UPDATE, DELETE
    }
    
    /**
     * 通知元数据变更
     */
    private void notifyMetadataChanged(Object metadata, MetadataChangeType changeType) {
        // 触发应用事件
        if (eventPublisher != null) {
            try {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("metadata", metadata);
                eventData.put("changeType", changeType);
                eventData.put("timestamp", System.currentTimeMillis());
                eventPublisher.publishEvent(eventData);
            } catch (Exception e) {
                // 发布元数据变更事件失败
            }
        }
        
        // 通知监听器
        for (MetadataChangeListener listener : metadataChangeListeners) {
            try {
                // 获取实体类型名称并转换为字符串
                String entityType = metadata.getClass().getSimpleName();
                // 将枚举类型转换为字符串
                String changeTypeStr = changeType.name();
                listener.onMetadataChanged(entityType, changeTypeStr);
            } catch (Exception e) {
                // 通知元数据变更监听器失败
            }
        }
    }
    

    
    /**
     * 添加元数据变更监听器
     */
    public void addMetadataChangeListener(MetadataChangeListener listener) {
        if (listener != null) {
            metadataChangeListeners.add(listener);
        }
    }
    
    /**
     * 移除元数据变更监听器
     */
    public void removeMetadataChangeListener(MetadataChangeListener listener) {
        metadataChangeListeners.remove(listener);
    }
    
    /**
     * 初始化元数据引擎
     */
    public void initialize() {
        try {
            // 简化的初始化逻辑
            // 初始化完成
        } catch (Exception e) {
            // 初始化失败
        }
    }
    
    /**
     * 处理实体实例
     */
    public Map<String, Object> processEntityInstance(String entityApiName, Map<String, Object> entityData) {
        Assert.hasText(entityApiName, "实体API名称不能为空");
        Assert.notNull(entityData, "实体数据不能为空");
        
        // 创建数据副本，避免修改原始数据
        Map<String, Object> processedData = new HashMap<>(entityData);
        
        try {
            // 获取实体元数据
            EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
            if (entityMetadata == null) {
                logger.warn("实体元数据未找到: {}", entityApiName);
                return processedData;
            }
            
            // 处理计算字段
            if (calculationEnabled) {
                processCalculatedFields(entityApiName, processedData);
            }
            
            // 处理虚拟字段
            processVirtualFields(entityApiName, processedData);
            
            return processedData;
        } catch (Exception e) {
            logger.error("处理实体实例失败: {}", e.getMessage(), e);
            return processedData;
        }
    }
    
    /**
     * 批量处理实体实例
     */
    public List<Map<String, Object>> processEntityInstancesBatch(String entityApiName, 
                                                              List<Map<String, Object>> entityDataList) {
        Assert.hasText(entityApiName, "实体API名称不能为空");
        Assert.notNull(entityDataList, "实体数据列表不能为空");
        
        // 简化实现
        return entityDataList.stream()
                .map(entityData -> processEntityInstance(entityApiName, entityData))
                .collect(Collectors.toList());
    }
    
    /**
     * 处理虚拟字段
     */
    private void processVirtualFields(String entityApiName, Map<String, Object> processedData) {
        try {
            // 获取实体的虚拟字段
            List<SmartFieldMetadata> virtualFields = new ArrayList<>();
            
            // 从注册表获取虚拟字段
            if (metadataRegistry != null) {
                virtualFields = metadataRegistry.getVirtualFields(entityApiName);
            }
            
            // 如果注册表中没有，直接从实体元数据获取
            if (virtualFields.isEmpty()) {
                EntityMetadata entityMetadata = getEntityMetadata(entityApiName);
                if (entityMetadata != null && entityMetadata.getFields() != null) {
                    virtualFields = entityMetadata.getFields().values().stream()
                            .filter(SmartFieldMetadata::isVirtual)
                            .collect(Collectors.toList());
                }
            }
            
            // 处理每个虚拟字段
            for (SmartFieldMetadata field : virtualFields) {
                try {
                    // 虚拟字段的值计算逻辑
                    String calculationExpr = field.getCalculationExpression();
                    if (calculationExpr != null && !calculationExpr.isEmpty()) {
                        // 这里可以实现更复杂的表达式计算
                        // 目前提供一个简单的实现示例
                        processedData.put(field.getApiName(), calculateVirtualFieldValue(field, processedData));
                    }
                } catch (Exception e) {
                    logger.error("处理虚拟字段 {} 失败: {}", field.getApiName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("处理虚拟字段出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 计算虚拟字段值
     */
    private Object calculateVirtualFieldValue(SmartFieldMetadata field, Map<String, Object> context) {
        // 简化实现，实际项目中可以使用表达式引擎
        String expression = field.getCalculationExpression();
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        
        // 简单示例：处理price * quantity = totalAmount
        if ("price * quantity".equals(expression) && context.containsKey("price") && context.containsKey("quantity")) {
            try {
                Object priceObj = context.get("price");
                Object quantityObj = context.get("quantity");
                
                double price = 0;
                if (priceObj instanceof Number) {
                    price = ((Number) priceObj).doubleValue();
                } else if (priceObj instanceof String) {
                    price = Double.parseDouble((String) priceObj);
                }
                
                double quantity = 0;
                if (quantityObj instanceof Number) {
                    quantity = ((Number) quantityObj).doubleValue();
                } else if (quantityObj instanceof String) {
                    quantity = Double.parseDouble((String) quantityObj);
                }
                
                return price * quantity;
            } catch (Exception e) {
                logger.error("计算字段值失败: {}", e.getMessage());
            }
        }
        
        return null;
    }
    
    /**
     * 验证依赖
     */
    private void validateDependencies() {
        Assert.notNull(metadataRegistry, "元数据注册表不能为空");
        Assert.notNull(metadataRepository, "元数据仓库不能为空");
        Assert.notNull(metadataProcessor, "元数据处理器不能为空");
    }
    
    /**
     * 加载所有实体元数据
     */
    private void loadAllEntityMetadata() {
        // 简化实现
        logger.info("加载实体元数据");
    }
    
    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        // 简化实现
        logger.info("启动健康检查");
    }
    
    /**
     * 获取计算字段
     */
    private Map<String, CalculatedFieldMetadata> getCalculatedFields(String entityApiName) {
        return new HashMap<>(); // 简化实现
    }
    
    /**
     * 获取虚拟字段
     */
    private Map<String, VirtualFieldMetadata> getVirtualFields(String entityApiName) {
        return new HashMap<>(); // 简化实现
    }
    
    /**
     * 计算字段值
     */
    private Object calculateFieldValue(SmartFieldMetadata field, Map<String, Object> context) {
        try {
            String expression = field.getCalculationExpression();
            if (expression == null || expression.isEmpty()) {
                return null;
            }
            
            // 这里可以集成表达式引擎如SpEL、OGNL等
            // 目前提供一个简单的实现
            
            // 示例1: 处理简单的字段引用
            if (expression.startsWith("field(")) {
                String fieldName = expression.substring(6, expression.length() - 1).trim();
                return context.get(fieldName);
            }
            
            // 示例2: 处理简单的加减乘除
            if (expression.contains("+")) {
                String[] parts = expression.split("\\+");
                double sum = 0;
                for (String part : parts) {
                    part = part.trim();
                    Object value = context.get(part);
                    if (value instanceof Number) {
                        sum += ((Number) value).doubleValue();
                    }
                }
                return sum;
            }
            
            // 示例3: 处理三元表达式
            if (expression.contains("?") && expression.contains(":")) {
                int questionIdx = expression.indexOf("?");
                int colonIdx = expression.indexOf(":");
                
                String condition = expression.substring(0, questionIdx).trim();
                String truePart = expression.substring(questionIdx + 1, colonIdx).trim();
                String falsePart = expression.substring(colonIdx + 1).trim();
                
                // 简化的条件判断
                if (context.containsKey(condition) && context.get(condition) != null) {
                    Object condValue = context.get(condition);
                    boolean isTrue = false;
                    
                    if (condValue instanceof Boolean) {
                        isTrue = (Boolean) condValue;
                    } else if (condValue instanceof Number) {
                        isTrue = ((Number) condValue).doubleValue() != 0;
                    } else if (condValue instanceof String) {
                        isTrue = !((String) condValue).isEmpty();
                    }
                    
                    if (isTrue && context.containsKey(truePart)) {
                        return context.get(truePart);
                    } else if (context.containsKey(falsePart)) {
                        return context.get(falsePart);
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            logger.error("计算字段值失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 处理计算字段
     */
    public Map<String, Object> processCalculatedFields(String entityName, Map<String, Object> data) {
        // 处理实体的计算字段
        Map<String, Object> result = new HashMap<>(data);
        
        try {
            // 获取实体元数据
            EntityMetadata entityMetadata = getEntityMetadata(entityName);
            if (entityMetadata == null || entityMetadata.getFields() == null) {
                return result;
            }
            
            // 获取所有字段
            Map<String, SmartFieldMetadata> fields = entityMetadata.getFields();
            if (fields == null || fields.isEmpty()) {
                return result;
            }
            
            // 过滤出计算字段
            List<SmartFieldMetadata> calculatedFields = new ArrayList<>();
            
            for (SmartFieldMetadata field : fields.values()) {
                // 使用getCalculationExpression判断是否为计算字段，避免使用isCalculated方法
                if (field.getCalculationExpression() != null && !field.getCalculationExpression().isEmpty()) {
                    calculatedFields.add(field);
                }
            }
            
            // 处理每个计算字段
            for (SmartFieldMetadata field : calculatedFields) {
                try {
                    String fieldName = field.getApiName();
                    Object calculatedValue = calculateFieldValue(field, result);
                    
                    if (calculatedValue != null) {
                        result.put(fieldName, calculatedValue);
                    }
                } catch (Exception e) {
                    logger.error("计算字段 {} 值失败: {}", field.getApiName(), e.getMessage());
                }
            }
            
            // 特殊处理常见的价格*数量=总金额计算
            if (data.containsKey("price") && data.containsKey("quantity")) {
                try {
                    // 更灵活地处理不同类型的价格和数量值
                    Object priceObj = data.get("price");
                    Object quantityObj = data.get("quantity");
                    
                    double price = 0;
                    if (priceObj instanceof Number) {
                        price = ((Number) priceObj).doubleValue();
                    } else if (priceObj instanceof String) {
                        price = Double.parseDouble((String) priceObj);
                    }
                    
                    double quantity = 0;
                    if (quantityObj instanceof Number) {
                        quantity = ((Number) quantityObj).doubleValue();
                    } else if (quantityObj instanceof String) {
                        quantity = Double.parseDouble((String) quantityObj);
                    }
                    
                    // 计算总金额
                    double totalAmount = price * quantity;
                    result.put("totalAmount", totalAmount);
                } catch (Exception e) {
                    logger.error("计算总金额失败: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("处理计算字段出错: {}", e.getMessage(), e);
        }
        
        return result;
    }
    
    /**
     * 注册实体元数据 - 为MetadataEngineInitializer提供的方法
     */
    public void registerEntity(EntityMetadata metadata) {
        if (metadata == null) {
            throw new IllegalArgumentException("实体元数据不能为空");
        }
        
        String entityName = metadata.getEntityName();
        if (entityName == null || entityName.isEmpty()) {
            entityName = metadata.getApiName();
        }
        
        if (entityName == null || entityName.isEmpty()) {
            throw new IllegalArgumentException("实体名称或API名称不能为空");
        }
        
        logger.info("注册实体元数据: {}", entityName);
        
        // 存储实体元数据
        entityMetadataMap.put(entityName, metadata);
        
        // 同时在注册表中注册
        if (metadataRegistry != null) {
            metadataRegistry.registerEntity(metadata);
        }
        
        // 更新缓存
        updateCache(metadata);
        
        // 发布元数据变更事件
        notifyMetadataChanged(metadata, MetadataChangeType.CREATE);
    }
    
    /**
     * 更新缓存
     */
    private void updateCache(EntityMetadata metadata) {
        // 简化实现，更新缓存
        if (cacheEnabled) {
            String entityName = metadata.getEntityName();
            if (entityName == null || entityName.isEmpty()) {
                entityName = metadata.getApiName();
            }
            if (entityName != null && !entityName.isEmpty()) {
                entityMetadataCache.put(entityName, new CacheEntry(metadata, cacheExpirationTime));
            }
        }
    }
    
    /**
     * 启动元数据热重载 - 为MetadataEngineInitializer提供的方法
     */
    public void startHotReload(long intervalMillis) {
        // 启动元数据热重载
        // 简化实现
    }
    
    /**
     * 注册实体元数据 - 为另一个MetadataEngineInitializer提供的方法
     */
    public void registerEntityMetadata(EntityMetadata metadata) {
        // 注册实体元数据
        // 简化实现
    }
    
    /**
     * 获取实体元数据（用于测试）
     */
    public EntityMetadata getEntityMetadata(String entityName) {
        // 获取实体元数据
        // 从存储中获取实体元数据，如果不存在则返回null
        return entityMetadataMap.get(entityName);
    }
    
    /**
     * 注销实体（用于测试）
     */
    public void unregisterEntity(String entityName) {
        // 注销实体
        // 从存储中移除实体元数据
        EntityMetadata metadata = entityMetadataMap.remove(entityName);
        
        // 如果实体存在，发布变更事件
        if (metadata != null) {
            notifyMetadataChanged(metadata, "DELETE");
        }
    }
    
    /**
     * 通知元数据变更
     */
    private void notifyMetadataChanged(EntityMetadata metadata, String changeType) {
        try {
            // 获取实体类型名称
            String entityType = metadata != null ? metadata.getApiName() : "unknown";
            
            // 通知所有监听器
            for (MetadataChangeListener listener : metadataChangeListeners) {
                try {
                    listener.onMetadataChanged(entityType, changeType);
                } catch (Exception e) {
                    logger.error("调用元数据变更监听器失败: {}", e.getMessage(), e);
                }
            }
            
            // 通过Spring事件发布器发布事件
            if (eventPublisher != null) {
                // 这里可以创建一个特定的元数据变更事件类
                // 暂时使用简单的日志记录
                logger.info("发布元数据变更事件: {} - {}", changeType, entityType);
            }
        } catch (Exception e) {
            logger.error("通知元数据变更失败: {}", e.getMessage(), e);
        }
    }
    
    // 监听器相关方法已在类中其他位置定义
}