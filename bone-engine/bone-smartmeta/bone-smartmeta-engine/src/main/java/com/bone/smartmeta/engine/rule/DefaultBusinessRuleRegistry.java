package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
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
            throw new IllegalArgumentException("规则不能为空");
        }
        
        // 使用反射方法获取属性值
        String apiName = getRuleApiName(rule);
        if (apiName == null || apiName.trim().isEmpty()) {
            // 如果没有apiName，尝试使用name作为备选
            apiName = getRuleName(rule);
            if (apiName == null || apiName.trim().isEmpty()) {
                throw new IllegalArgumentException("Rule name cannot be null or empty");
            }
        }
        
        // 获取其他属性值
        String ruleName = getRuleName(rule);
        String ruleId = getRuleId(rule);
        String entityApiName = null;
        try {
            // 尝试通过反射获取entityApiName
            Field field = rule.getClass().getDeclaredField("entityApiName");
            field.setAccessible(true);
            Object value = field.get(rule);
            if (value instanceof String) {
                entityApiName = (String) value;
            }
        } catch (Exception ignored) {
            // 如果没有entityApiName字段，则忽略
        }
        
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
    
    
    @Override
    public void registerRules(List<BusinessRuleMetadata> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        List<BusinessRuleMetadata> validRules = new ArrayList<>();
        
        // 预先验证所有规则
        for (BusinessRuleMetadata rule : rules) {
            try {
                // 使用反射方法获取属性
                String ruleIdentifier = getRuleId(rule) != null ? getRuleId(rule) : 
                                       (getRuleName(rule) != null ? getRuleName(rule) : 
                                       (getRuleApiName(rule) != null ? getRuleApiName(rule) : null));
                
                if (rule != null && ruleIdentifier != null && !ruleIdentifier.trim().isEmpty()) {
                    validRules.add(rule);
                    log.debug("Adding valid rule: {}", ruleIdentifier);
                } else {
                    log.warn("Skipping invalid rule: {}", ruleIdentifier != null ? ruleIdentifier : "null");
                }
            } catch (Exception e) {
                // 使用反射方法获取属性值
                String ruleIdentifier = rule != null && getRuleId(rule) != null ? getRuleId(rule) : "null";
                log.warn("Error validating rule: {}", ruleIdentifier, e);
            }
        }
        
        // 批量注册有效规则
        for (BusinessRuleMetadata rule : validRules) {
            // 使用反射方法获取属性值
            String ruleId = getRuleId(rule) != null ? getRuleId(rule) : 
                           (getRuleApiName(rule) != null ? getRuleApiName(rule) : getRuleName(rule));
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
        
        // 使用反射方法获取属性值
        String ruleId = getRuleId(rule) != null ? getRuleId(rule) : 
                       (getRuleApiName(rule) != null ? getRuleApiName(rule) : getRuleName(rule));
                        
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
        
        String entityApiName = null;
        try {
            // 尝试通过反射获取entityApiName
            Field field = rule.getClass().getDeclaredField("entityApiName");
            field.setAccessible(true);
            Object value = field.get(rule);
            if (value instanceof String) {
                entityApiName = (String) value;
            }
        } catch (Exception ignored) {
            // 如果没有entityApiName字段，则忽略
        }
        
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
    
    // 测试中使用的方法
    public List<BusinessRuleMetadata> getRulesByEventType(String entityApiName, String eventType) {
        if (entityApiName == null || eventType == null) {
            return Collections.emptyList();
        }
        
        // 构建事件键
        String eventKey = entityApiName + ":" + eventType;
        List<BusinessRuleMetadata> rules = rulesByEvent.get(eventKey);
        
        if (rules != null) {
            // 过滤出激活的规则
            return rules.stream()
                    .filter(rule -> isRuleActive(rule))
                    .collect(Collectors.toUnmodifiableList());
        }
        
        return Collections.emptyList();
    }
    
    // 测试中使用的方法
    public List<BusinessRuleMetadata> getRulesByEntityAndType(String entityApiName, String ruleType) {
        if (entityApiName == null || ruleType == null) {
            return Collections.emptyList();
        }
        
        // 构建类型键
        String typeKey = entityApiName + ":" + ruleType;
        List<BusinessRuleMetadata> rules = rulesByType.get(typeKey);
        
        if (rules != null) {
            // 过滤出激活的规则
            return rules.stream()
                    .filter(rule -> isRuleActive(rule))
                    .collect(Collectors.toUnmodifiableList());
        }
        
        return Collections.emptyList();
    }
    
    // 测试中使用的方法
    public List<BusinessRuleMetadata> getRulesByType(String ruleType) {
        if (ruleType == null) {
            return Collections.emptyList();
        }
        
        // 从所有规则中查找指定类型的规则
        return rulesById.values().stream()
                .filter(rule -> {
                    String ruleRuleType = getRuleType(rule);
                    return ruleRuleType != null && ruleType.equals(ruleRuleType);
                })
                .filter(rule -> isRuleActive(rule))
                .collect(Collectors.toUnmodifiableList());
        }
    
    // updateRule方法已在上面实现，无需重复定义
    
    // 测试中使用的方法
    public void deleteRule(String ruleId) {
        if (ruleId == null) {
            return;
        }
        
        BusinessRuleMetadata removedRule = rulesById.remove(ruleId);
        if (removedRule != null) {
            // 重新构建索引
            rebuildIndexes();
        }
    }
    
    // 测试中使用的方法
    public void activateRule(String ruleId) {
        BusinessRuleMetadata rule = rulesById.get(ruleId);
        if (rule != null) {
            setRuleActive(rule, true);
            updateRule(rule);
        }
    }
    
    // 测试中使用的方法
    public void deactivateRule(String ruleId) {
        BusinessRuleMetadata rule = rulesById.get(ruleId);
        if (rule != null) {
            setRuleActive(rule, false);
            updateRule(rule);
        }
    }
    
    // 重建所有索引
    private void rebuildIndexes() {
        rulesByEntity.clear();
        rulesByEvent.clear();
        rulesByType.clear();
        
        for (BusinessRuleMetadata rule : rulesById.values()) {
            updateEntityIndex(rule);
            updateEventIndex(rule);
            updateTypeIndex(rule);
        }
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
        String entityApiName = null;
        try {
            // 尝试通过反射获取entityApiName
            Field field = rule.getClass().getDeclaredField("entityApiName");
            field.setAccessible(true);
            Object value = field.get(rule);
            if (value instanceof String) {
                entityApiName = (String) value;
            }
        } catch (Exception ignored) {
            // 如果没有entityApiName字段，则忽略
        }
        
        if (entityApiName != null) {
            // 使用反射方法获取属性值
            String ruleId = getRuleId(rule) != null ? getRuleId(rule) : 
                           (getRuleApiName(rule) != null ? getRuleApiName(rule) : getRuleName(rule));
            rulesByEntity.computeIfAbsent(entityApiName, k -> new CopyOnWriteArrayList<>())
                    .removeIf(r -> {
                        String existingRuleId = getRuleId(r) != null ? getRuleId(r) : 
                                               (getRuleApiName(r) != null ? getRuleApiName(r) : getRuleName(r));
                        return existingRuleId != null && existingRuleId.equals(ruleId);
                    });
            rulesByEntity.get(entityApiName).add(rule);
        }
    }
    
    /**
     * 移除时更新实体索引
     */
    private void updateEntityIndexOnRemove(BusinessRuleMetadata rule) {
        String entityApiName = null;
        try {
            // 尝试通过反射获取entityApiName
            Field field = rule.getClass().getDeclaredField("entityApiName");
            field.setAccessible(true);
            Object value = field.get(rule);
            if (value instanceof String) {
                entityApiName = (String) value;
            }
        } catch (Exception ignored) {
            // 如果没有entityApiName字段，则忽略
        }
        
        if (entityApiName != null) {
            List<BusinessRuleMetadata> rules = rulesByEntity.get(entityApiName);
            if (rules != null) {
                // 使用反射方法获取属性值
                String ruleId = getRuleId(rule) != null ? getRuleId(rule) : 
                               (getRuleApiName(rule) != null ? getRuleApiName(rule) : getRuleName(rule));
                rules.removeIf(r -> {
                    String existingRuleId = getRuleId(r) != null ? getRuleId(r) : 
                                           (getRuleApiName(r) != null ? getRuleApiName(r) : getRuleName(r));
                    return existingRuleId != null && existingRuleId.equals(ruleId);
                });
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
        try {
            // 使用反射获取id字段
            try {
                Field idField = BusinessRuleMetadata.class.getDeclaredField("id");
                idField.setAccessible(true);
                String id = (String) idField.get(rule);
                if (id != null) {
                    return id;
                }
            } catch (Exception e) {
                // 忽略异常，继续尝试其他字段
            }
            
            // 回退使用apiName和name
            try {
                Field apiNameField = BusinessRuleMetadata.class.getDeclaredField("apiName");
                apiNameField.setAccessible(true);
                String apiName = (String) apiNameField.get(rule);
                if (apiName != null) {
                    return apiName;
                }
            } catch (Exception e) {
                // 忽略异常，继续尝试
            }
            
            try {
                Field nameField = BusinessRuleMetadata.class.getDeclaredField("name");
                nameField.setAccessible(true);
                String name = (String) nameField.get(rule);
                return name;
            } catch (Exception e) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getRuleId(BusinessRuleMetadata rule) {
        try {
            Field idField = BusinessRuleMetadata.class.getDeclaredField("id");
            idField.setAccessible(true);
            return (String) idField.get(rule);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getRuleApiName(BusinessRuleMetadata rule) {
        try {
            Field apiNameField = BusinessRuleMetadata.class.getDeclaredField("apiName");
            apiNameField.setAccessible(true);
            return (String) apiNameField.get(rule);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getRuleName(BusinessRuleMetadata rule) {
        try {
            Field nameField = BusinessRuleMetadata.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(rule);
        } catch (Exception e) {
            return null;
        }
    }
    
    private String getRuleType(BusinessRuleMetadata rule) {
        try {
            Field ruleTypeField = BusinessRuleMetadata.class.getDeclaredField("ruleType");
            ruleTypeField.setAccessible(true);
            return (String) ruleTypeField.get(rule);
        } catch (Exception e) {
            return "default";
        }
    }
    
    private boolean isRuleActive(BusinessRuleMetadata rule) {
        try {
            Field activeField = BusinessRuleMetadata.class.getDeclaredField("active");
            activeField.setAccessible(true);
            return (Boolean) activeField.get(rule);
        } catch (Exception e) {
            return true; // 默认返回true表示激活状态
        }
    }
    
    private void setRuleActive(BusinessRuleMetadata rule, boolean active) {
        try {
            Field activeField = BusinessRuleMetadata.class.getDeclaredField("active");
            activeField.setAccessible(true);
            activeField.set(rule, active);
        } catch (Exception e) {
            // 忽略设置失败的情况
        }
    }
    
    /**
     * 更新事件索引
     */
    private void updateEventIndex(BusinessRuleMetadata rule) {
        try {
            // 使用反射获取triggerEvents字段
            List<String> triggerEvents = null;
            try {
                Field triggerEventsField = BusinessRuleMetadata.class.getDeclaredField("triggerEvents");
                triggerEventsField.setAccessible(true);
                triggerEvents = (List<String>) triggerEventsField.get(rule);
            } catch (Exception e) {
                // 如果获取失败，使用空列表
                triggerEvents = new ArrayList<>();
            }
            
            if (triggerEvents != null && !triggerEvents.isEmpty()) {
                String entityApiName = null;
                try {
                    // 尝试通过反射获取entityApiName
                    Field field = rule.getClass().getDeclaredField("entityApiName");
                    field.setAccessible(true);
                    Object value = field.get(rule);
                    if (value instanceof String) {
                        entityApiName = (String) value;
                    }
                } catch (Exception ignored) {
                    // 如果没有entityApiName字段，则忽略
                }
                
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
            // 使用反射获取triggerEvents字段
            List<String> triggerEvents = null;
            try {
                Field triggerEventsField = BusinessRuleMetadata.class.getDeclaredField("triggerEvents");
                triggerEventsField.setAccessible(true);
                triggerEvents = (List<String>) triggerEventsField.get(rule);
            } catch (Exception e) {
                // 如果获取失败，使用空列表
                triggerEvents = new ArrayList<>();
            }
            
            if (triggerEvents != null && !triggerEvents.isEmpty()) {
                String entityApiName = null;
                try {
                    // 尝试通过反射获取entityApiName
                    Field field = rule.getClass().getDeclaredField("entityApiName");
                    field.setAccessible(true);
                    Object value = field.get(rule);
                    if (value instanceof String) {
                        entityApiName = (String) value;
                    }
                } catch (Exception ignored) {
                    // 如果没有entityApiName字段，则忽略
                }
                
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
            String entityApiName = null;
            try {
                // 尝试通过反射获取entityApiName
                Field field = rule.getClass().getDeclaredField("entityApiName");
                field.setAccessible(true);
                Object value = field.get(rule);
                if (value instanceof String) {
                    entityApiName = (String) value;
                }
            } catch (Exception ignored) {
                // 如果没有entityApiName字段，则忽略
            }
            
            // 使用反射获取ruleType字段值
            String ruleType = null;
            try {
                Field ruleTypeField = BusinessRuleMetadata.class.getDeclaredField("ruleType");
                ruleTypeField.setAccessible(true);
                ruleType = (String) ruleTypeField.get(rule);
            } catch (Exception e) {
                ruleType = "default";
            }
            
            String ruleId = getRuleIdentifier(rule);
            
            if (entityApiName != null && ruleType != null && ruleId != null) {
                String key = entityApiName + ":" + ruleType;
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
            String entityApiName = null;
            try {
                // 尝试通过反射获取entityApiName
                Field field = rule.getClass().getDeclaredField("entityApiName");
                field.setAccessible(true);
                Object value = field.get(rule);
                if (value instanceof String) {
                    entityApiName = (String) value;
                }
            } catch (Exception ignored) {
                // 如果没有entityApiName字段，则忽略
            }
            
            // 使用反射获取ruleType字段值
            String ruleType = null;
            try {
                Field ruleTypeField = BusinessRuleMetadata.class.getDeclaredField("ruleType");
                ruleTypeField.setAccessible(true);
                ruleType = (String) ruleTypeField.get(rule);
            } catch (Exception e) {
                ruleType = "default";
            }
            
            String ruleId = getRuleIdentifier(rule);
            
            if (entityApiName != null && ruleType != null && ruleId != null) {
                String key = entityApiName + ":" + ruleType;
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