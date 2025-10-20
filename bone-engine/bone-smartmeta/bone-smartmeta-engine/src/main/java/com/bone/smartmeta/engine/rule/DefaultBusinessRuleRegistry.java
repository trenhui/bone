package com.bone.smartmeta.engine.rule;

import com.bone.smartmeta.engine.model.BusinessRuleMetadata;
import com.bone.smartmeta.engine.DefaultBusinessRuleEngine;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 业务规则注册表默认实现
 * 提供业务规则的完整管理功能
 */
@Slf4j
public class DefaultBusinessRuleRegistry implements BusinessRuleRegistry {
    
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
        if (rule.getId() == null || rule.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("Rule ID cannot be null or empty");
        }
        if (rule.getEntityApiName() == null || rule.getEntityApiName().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity API name cannot be null or empty");
        }
        
        // 验证规则
        BusinessRuleEngine ruleEngine = new DefaultBusinessRuleEngine(null);
        if (!ruleEngine.validateRule(rule)) {
            throw new IllegalArgumentException("Invalid rule: " + rule.getName());
        }
        
        // 存储规则
        BusinessRuleMetadata oldRule = rulesById.put(rule.getId(), rule);
        
        // 更新实体索引
        updateEntityIndex(rule);
        
        // 更新事件索引
        updateEventIndex(rule);
        
        // 更新类型索引
        updateTypeIndex(rule);
        
        // 如果是更新，先通知旧规则的移除
        if (oldRule != null) {
            log.info("Updated rule: {}, Entity: {}", rule.getId(), rule.getEntityApiName());
            notifyRuleUpdated(rule);
        } else {
            log.info("Registered rule: {}, Entity: {}", rule.getId(), rule.getEntityApiName());
            notifyRuleRegistered(rule);
        }
    }
    
    @Override
    public void registerRules(List<BusinessRuleMetadata> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        
        List<BusinessRuleMetadata> validRules = new ArrayList<>();
        BusinessRuleEngine ruleEngine = new DefaultBusinessRuleEngine(null);
        
        // 预先验证所有规则
        for (BusinessRuleMetadata rule : rules) {
            try {
                if (rule != null && 
                    rule.getId() != null && !rule.getId().trim().isEmpty() &&
                    rule.getEntityApiName() != null && !rule.getEntityApiName().trim().isEmpty() &&
                    ruleEngine.validateRule(rule)) {
                    validRules.add(rule);
                } else {
                    log.warn("Skipping invalid rule: {}", rule != null ? rule.getId() : "null");
                }
            } catch (Exception e) {
                log.warn("Error validating rule: {}", rule != null ? rule.getId() : "null", e);
            }
        }
        
        // 批量注册有效规则
        for (BusinessRuleMetadata rule : validRules) {
            BusinessRuleMetadata oldRule = rulesById.put(rule.getId(), rule);
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
        if (rule == null || rule.getId() == null) {
            return false;
        }
        
        // 检查规则是否存在
        if (!rulesById.containsKey(rule.getId())) {
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
        
        log.info("Unregistered rule: {}, Entity: {}", ruleId, rule.getEntityApiName());
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
        String entityApiName = rule.getEntityApiName();
        rulesByEntity.computeIfAbsent(entityApiName, k -> new CopyOnWriteArrayList<>())
                .removeIf(r -> r.getId().equals(rule.getId()));
        rulesByEntity.get(entityApiName).add(rule);
    }
    
    /**
     * 移除时更新实体索引
     */
    private void updateEntityIndexOnRemove(BusinessRuleMetadata rule) {
        String entityApiName = rule.getEntityApiName();
        List<BusinessRuleMetadata> rules = rulesByEntity.get(entityApiName);
        if (rules != null) {
            rules.removeIf(r -> r.getId().equals(rule.getId()));
            if (rules.isEmpty()) {
                rulesByEntity.remove(entityApiName);
            }
        }
    }
    
    /**
     * 更新事件索引
     */
    private void updateEventIndex(BusinessRuleMetadata rule) {
        if (rule.getTriggerEvents() != null) {
            for (String eventType : rule.getTriggerEvents()) {
                String key = rule.getEntityApiName() + ":" + eventType;
                rulesByEvent.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                        .removeIf(r -> r.getId().equals(rule.getId()));
                rulesByEvent.get(key).add(rule);
            }
        }
    }
    
    /**
     * 移除时更新事件索引
     */
    private void updateEventIndexOnRemove(BusinessRuleMetadata rule) {
        if (rule.getTriggerEvents() != null) {
            for (String eventType : rule.getTriggerEvents()) {
                String key = rule.getEntityApiName() + ":" + eventType;
                List<BusinessRuleMetadata> rules = rulesByEvent.get(key);
                if (rules != null) {
                    rules.removeIf(r -> r.getId().equals(rule.getId()));
                    if (rules.isEmpty()) {
                        rulesByEvent.remove(key);
                    }
                }
            }
        }
    }
    
    /**
     * 更新类型索引
     */
    private void updateTypeIndex(BusinessRuleMetadata rule) {
        String key = rule.getEntityApiName() + ":" + rule.getRuleType().name();
        rulesByType.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
                .removeIf(r -> r.getId().equals(rule.getId()));
        rulesByType.get(key).add(rule);
    }
    
    /**
     * 移除时更新类型索引
     */
    private void updateTypeIndexOnRemove(BusinessRuleMetadata rule) {
        String key = rule.getEntityApiName() + ":" + rule.getRuleType().name();
        List<BusinessRuleMetadata> rules = rulesByType.get(key);
        if (rules != null) {
            rules.removeIf(r -> r.getId().equals(rule.getId()));
            if (rules.isEmpty()) {
                rulesByType.remove(key);
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