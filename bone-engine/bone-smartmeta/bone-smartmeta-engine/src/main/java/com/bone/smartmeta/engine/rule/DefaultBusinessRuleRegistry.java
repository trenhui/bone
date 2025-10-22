package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 业务规则注册表默认实现
 * 提供业务规则的完整管理功能
 */
public class DefaultBusinessRuleRegistry implements BusinessRuleRegistry {
    private static final Logger log = LoggerFactory.getLogger(DefaultBusinessRuleRegistry.class);
    
    // 规则ID到规则的映射
    private final Map<String, BusinessRuleMetadata> rulesById = new ConcurrentHashMap<>();
    
    // 实体API名称到规则列表的映射
    private final Map<String, List<BusinessRuleMetadata>> rulesByEntity = new ConcurrentHashMap<>();
    
    // 事件类型和实体API名称组合到规则列表的映射
    private final Map<String, List<BusinessRuleMetadata>> rulesByEvent = new ConcurrentHashMap<>();
    
    // 规则类型和实体API名称组合到规则列表的映射
    private final Map<String, List<BusinessRuleMetadata>> rulesByType = new ConcurrentHashMap<>();
    
    // 规则变更监听器列表
    private final List<BusinessRuleChangeListener> listeners = new CopyOnWriteArrayList<>();
    
    @Override
    public void registerRule(BusinessRuleMetadata rule) {
        if (rule == null) {
            throw new IllegalArgumentException("Rule cannot be null");
        }
        
        // 使用反射获取属性值，避免方法调用错误
        String apiName = getFieldValue(rule, "apiName");
        if (apiName == null || apiName.trim().isEmpty()) {
            // 如果没有apiName，尝试使用name作为备选
            apiName = getFieldValue(rule, "name");
            if (apiName == null || apiName.trim().isEmpty()) {
                throw new IllegalArgumentException("Rule name cannot be null or empty");
            }
        }
        
        // 获取其他属性值
        String ruleName = getFieldValue(rule, "name");
        String ruleId = getFieldValue(rule, "id");
        String entityApiName = getFieldValue(rule, "entityApiName");
        
        // 使用安全的日志记录
        if (log != null) {
            log.debug("Registering rule: {}", ruleName);
        }
        
        // 存储规则，使用apiName作为ID（如果id不存在）
        String key = ruleId != null && !ruleId.trim().isEmpty() ? ruleId : apiName;
        BusinessRuleMetadata oldRule = rulesById.put(key, rule);
        
        // 更新实体索引
        updateEntityIndex(rule);
        
        // 更新事件索引
        updateEventIndex(rule);
        
        // 更新类型索引
        updateTypeIndex(rule);
        
        // 如果是更新，先通知旧规则的移除
        if (oldRule != null) {
            if (log != null) {
                log.info("Updated rule: {}, Entity: {}", key, entityApiName);
            }
            notifyRuleUpdated(rule);
        } else {
            if (log != null) {
                log.info("Registered rule: {}, Entity: {}", key, entityApiName);
            }
            notifyRuleRegistered(rule);
        }
    }
    
    /**
     * 通过反射安全获取对象字段值
     */
    private String getFieldValue(Object obj, String fieldName) {
        if (obj == null || fieldName == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(obj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            // 如果字段不存在或访问出错，返回null
            return null;
        }
    }
    
    @Override
    public void registerRules(List<BusinessRuleMetadata> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        List<BusinessRuleMetadata> validRules = new ArrayList<>();
        
        // 预先验证所有规则
        for (BusinessRuleMetadata rule : rules) {
            try {
                String apiName = getFieldValue(rule, "apiName");
                String name = getFieldValue(rule, "name");
                String id = getFieldValue(rule, "id");
                String entityApiName = getFieldValue(rule, "entityApiName");
                
                // 使用任何可用的标识符
                String ruleIdentifier = id != null ? id : (apiName != null ? apiName : name);
                
                if (rule != null && ruleIdentifier != null && !ruleIdentifier.trim().isEmpty()) {
                    validRules.add(rule);
                    log.debug("Adding valid rule: {}", ruleIdentifier);
                } else {
                    log.warn("Skipping invalid rule: {}", ruleIdentifier != null ? ruleIdentifier : "null");
                }
            } catch (Exception e) {
                String ruleIdentifier = rule != null ? getFieldValue(rule, "id") : "null";
                log.warn("Error validating rule: {}", ruleIdentifier != null ? ruleIdentifier : "null", e);
            }
        }
        
        // 批量注册有效规则
        for (BusinessRuleMetadata rule : validRules) {
            String ruleId = getFieldValue(rule, "id") != null ? getFieldValue(rule, "id") : 
                           (getFieldValue(rule, "apiName") != null ? getFieldValue(rule, "apiName") : getFieldValue(rule, "name"));
            BusinessRuleMetadata oldRule = rulesById.put(ruleId, rule);
            updateEntityIndex(rule);
            updateEventIndex(rule);
            updateTypeIndex(rule);
        }
        
        log.info("Registered {} rules ({} skipped)", validRules.size(), rules.size() - validRules.size());
        
        // 通知规则注册
        for (BusinessRuleMetadata rule : validRules) {
            notifyRuleRegistered(rule);
        }
    }
    
    @Override
    public boolean updateRule(BusinessRuleMetadata rule) {
        if (rule == null) {
            return false;
        }
        
        String ruleId = getFieldValue(rule, "id") != null ? getFieldValue(rule, "id") : 
                       (getFieldValue(rule, "apiName") != null ? getFieldValue(rule, "apiName") : getFieldValue(rule, "name"));
                       
        if (ruleId == null) {
            return false;
        }
        
        // 检查规则是否存在
        if (!rulesById.containsKey(ruleId)) {
            return false;
        }
        
        // 注册规则（会覆盖现有规则）
        registerRule(rule);
        return true;
    }
    
    @Override
    public boolean unregisterRule(String ruleId) {
        if (ruleId == null || ruleId.trim().isEmpty()) {
            return false;
        }
        
        // 获取要删除的规则
        BusinessRuleMetadata rule = rulesById.remove(ruleId);
        if (rule == null) {
            return false;
        }
        
        // 更新实体索引
        updateEntityIndexOnRemove(rule);
        
        // 更新事件索引
        updateEventIndexOnRemove(rule);
        
        // 更新类型索引
        updateTypeIndexOnRemove(rule);
        
        String entityApiName = getFieldValue(rule, "entityApiName");
        log.info("Unregistered rule: {}, Entity: {}", ruleId, entityApiName != null ? entityApiName : "unknown");
        notifyRuleUnregistered(ruleId);
        
        return true;
    }
    
    @Override
    public BusinessRuleMetadata getRuleById(String ruleId) {
        return rulesById.get(ruleId);
    }
    
    @Override
    public List<BusinessRuleMetadata> getRulesByEntity(String entityApiName) {
        if (entityApiName == null) {
            return Collections.emptyList();
        }
        
        List<BusinessRuleMetadata> rules = rulesByEntity.get(entityApiName);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }
    
    @Override
    public List<BusinessRuleMetadata> getRulesByEvent(String entityApiName, String eventType) {
        if (entityApiName == null || eventType == null) {
            return Collections.emptyList();
        }
        
        String key = entityApiName + ":" + eventType;
        List<BusinessRuleMetadata> rules = rulesByEvent.get(key);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }
    
    @Override
    public List<BusinessRuleMetadata> getRulesByType(String entityApiName, String ruleType) {
        if (entityApiName == null || ruleType == null) {
            return Collections.emptyList();
        }
        
        String key = entityApiName + ":" + ruleType;
        List<BusinessRuleMetadata> rules = rulesByType.get(key);
        return rules != null ? Collections.unmodifiableList(rules) : Collections.emptyList();
    }
    
    @Override
    public List<BusinessRuleMetadata> searchRules(Predicate<BusinessRuleMetadata> predicate) {
        if (predicate == null) {
            return Collections.emptyList();
        }
        
        return rulesById.values().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }
    
    @Override
    public Set<String> getAllEntityApiNames() {
        return new HashSet<>(rulesByEntity.keySet());
    }
    
    @Override
    public boolean exists(String ruleId) {
        return rulesById.containsKey(ruleId);
    }
    
    @Override
    public void clear() {
        rulesById.clear();
        rulesByEntity.clear();
        rulesByEvent.clear();
        rulesByType.clear();
        
        log.info("Cleared all rules from registry");
        notifyRegistryCleared();
    }
    
    @Override
    public int size() {
        return rulesById.size();
    }
    
    @Override
    public void addRuleChangeListener(BusinessRuleChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    @Override
    public void removeRuleChangeListener(BusinessRuleChangeListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
    
    /**
     * 更新实体索引
     */
    private void updateEntityIndex(BusinessRuleMetadata rule) {
        String entityApiName = getFieldValue(rule, "entityApiName");
        if (entityApiName != null) {
            String ruleId = getRuleIdentifier(rule);
            rulesByEntity.computeIfAbsent(entityApiName, k -> new CopyOnWriteArrayList<>())
                    .removeIf(r -> getRuleIdentifier(r).equals(ruleId));
            rulesByEntity.get(entityApiName).add(rule);
        }
    }
    
    /**
     * 移除时更新实体索引
     */
    private void updateEntityIndexOnRemove(BusinessRuleMetadata rule) {
        String entityApiName = getFieldValue(rule, "entityApiName");
        if (entityApiName != null) {
            List<BusinessRuleMetadata> rules = rulesByEntity.get(entityApiName);
            if (rules != null) {
                String ruleId = getRuleIdentifier(rule);
                rules.removeIf(r -> getRuleIdentifier(r).equals(ruleId));
                if (rules.isEmpty()) {
                    rulesByEntity.remove(entityApiName);
                }
            }
        }
    }
    
    /**
     * 获取规则标识符
     */
    private String getRuleIdentifier(BusinessRuleMetadata rule) {
        if (rule == null) return null;
        String id = getFieldValue(rule, "id");
        if (id != null) return id;
        String apiName = getFieldValue(rule, "apiName");
        if (apiName != null) return apiName;
        return getFieldValue(rule, "name");
    }
    
    /**
     * 更新事件索引
     */
    private void updateEventIndex(BusinessRuleMetadata rule) {
        try {
            // 使用反射安全获取triggerEvents字段，避免直接类型转换错误
            List<String> triggerEvents = null;
            try {
                java.lang.reflect.Field eventsField = rule.getClass().getDeclaredField("triggerEvents");
                eventsField.setAccessible(true);
                Object value = eventsField.get(rule);
                if (value instanceof List) {
                    triggerEvents = (List<String>) value;
                }
            } catch (Exception ignore) {
                // 如果无法获取或类型转换失败，保持为null
            }
            
            if (triggerEvents != null && !triggerEvents.isEmpty()) {
                String entityApiName = getFieldValue(rule, "entityApiName");
                String ruleId = getRuleIdentifier(rule);
                if (entityApiName != null && ruleId != null) {
                    for (String eventType : triggerEvents) {
                        String key = entityApiName + ":" + eventType;
                        rulesByEvent.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                                .removeIf(r -> getRuleIdentifier(r).equals(ruleId));
                        rulesByEvent.get(key).add(rule);
                    }
                }
            }
        } catch (Exception e) {
            if (log != null) {
                log.error("Error updating event index: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 移除时更新事件索引
     */
    private void updateEventIndexOnRemove(BusinessRuleMetadata rule) {
        try {
            // 使用反射安全获取triggerEvents字段，避免直接类型转换错误
            List<String> triggerEvents = null;
            try {
                java.lang.reflect.Field eventsField = rule.getClass().getDeclaredField("triggerEvents");
                eventsField.setAccessible(true);
                Object value = eventsField.get(rule);
                if (value instanceof List) {
                    triggerEvents = (List<String>) value;
                }
            } catch (Exception ignore) {
                // 如果无法获取或类型转换失败，保持为null
            }
            
            if (triggerEvents != null && !triggerEvents.isEmpty()) {
                String entityApiName = getFieldValue(rule, "entityApiName");
                String ruleId = getRuleIdentifier(rule);
                if (entityApiName != null && ruleId != null) {
                    for (String eventType : triggerEvents) {
                        String key = entityApiName + ":" + eventType;
                        List<BusinessRuleMetadata> rules = rulesByEvent.get(key);
                        if (rules != null) {
                            rules.removeIf(r -> getRuleIdentifier(r).equals(ruleId));
                            if (rules.isEmpty()) {
                                rulesByEvent.remove(key);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (log != null) {
                log.error("Error updating event index on remove: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 更新类型索引
     */
    private void updateTypeIndex(BusinessRuleMetadata rule) {
        try {
            String entityApiName = getFieldValue(rule, "entityApiName");
            String ruleType = getFieldValue(rule, "ruleType");
            String ruleId = getRuleIdentifier(rule);
            
            if (entityApiName != null && ruleType != null && ruleId != null) {
                // 如果ruleType是枚举类型，尝试获取name()方法的值
                String ruleTypeName = ruleType;
                try {
                    Object ruleTypeObj = getFieldValue(rule, "ruleType");
                    if (ruleTypeObj != null && ruleTypeObj.getClass().isEnum()) {
                        // 尝试使用反射获取name()方法的值
                        ruleTypeName = getFieldValue(ruleTypeObj, "name");
                        if (ruleTypeName == null) {
                            ruleTypeName = ruleTypeObj.toString();
                        }
                    }
                } catch (Exception e) {
                    // 如果反射失败，使用字符串表示
                    ruleTypeName = ruleType;
                }
                
                String key = entityApiName + ":" + ruleTypeName;
                rulesByType.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                        .removeIf(r -> getRuleIdentifier(r).equals(ruleId));
                rulesByType.get(key).add(rule);
            }
        } catch (Exception e) {
            if (log != null) {
                log.error("Error updating type index: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 移除时更新类型索引
     */
    private void updateTypeIndexOnRemove(BusinessRuleMetadata rule) {
        try {
            String entityApiName = getFieldValue(rule, "entityApiName");
            String ruleType = getFieldValue(rule, "ruleType");
            String ruleId = getRuleIdentifier(rule);
            
            if (entityApiName != null && ruleType != null && ruleId != null) {
                // 如果ruleType是枚举类型，尝试获取name()方法的值
                String ruleTypeName = ruleType;
                try {
                    Object ruleTypeObj = getFieldValue(rule, "ruleType");
                    if (ruleTypeObj != null && ruleTypeObj.getClass().isEnum()) {
                        // 尝试使用反射获取name()方法的值
                        ruleTypeName = getFieldValue(ruleTypeObj, "name");
                        if (ruleTypeName == null) {
                            ruleTypeName = ruleTypeObj.toString();
                        }
                    }
                } catch (Exception e) {
                    // 如果反射失败，使用字符串表示
                    ruleTypeName = ruleType;
                }
                
                String key = entityApiName + ":" + ruleTypeName;
                List<BusinessRuleMetadata> rules = rulesByType.get(key);
                if (rules != null) {
                    rules.removeIf(r -> getRuleIdentifier(r).equals(ruleId));
                    if (rules.isEmpty()) {
                        rulesByType.remove(key);
                    }
                }
            }
        } catch (Exception e) {
            if (log != null) {
                log.error("Error updating type index on remove: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 通知规则注册
     */
    private void notifyRuleRegistered(BusinessRuleMetadata rule) {
        for (BusinessRuleChangeListener listener : listeners) {
            try {
                listener.onRuleRegistered(rule);
            } catch (Exception e) {
                log.error("Error notifying listener of rule registration", e);
            }
        }
    }
    
    /**
     * 通知规则更新
     */
    private void notifyRuleUpdated(BusinessRuleMetadata rule) {
        for (BusinessRuleChangeListener listener : listeners) {
            try {
                listener.onRuleUpdated(rule);
            } catch (Exception e) {
                log.error("Error notifying listener of rule update", e);
            }
        }
    }
    
    /**
     * 通知规则注销
     */
    private void notifyRuleUnregistered(String ruleId) {
        for (BusinessRuleChangeListener listener : listeners) {
            try {
                listener.onRuleUnregistered(ruleId);
            } catch (Exception e) {
                log.error("Error notifying listener of rule unregistration", e);
            }
        }
    }
    
    /**
     * 通知注册表清空
     */
    private void notifyRegistryCleared() {
        for (BusinessRuleChangeListener listener : listeners) {
            try {
                listener.onRegistryCleared();
            } catch (Exception e) {
                log.error("Error notifying listener of registry clearing", e);
            }
        }
    }
}