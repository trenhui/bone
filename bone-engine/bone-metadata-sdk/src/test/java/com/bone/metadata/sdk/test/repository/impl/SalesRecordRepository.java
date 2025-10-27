package com.bone.metadata.sdk.test.repository.impl;

import com.bone.metadata.sdk.test.domain.SalesRecord;
import java.util.*;
import java.math.BigDecimal;

/**
 * 简化的销售记录仓库类
 * 移除了所有外部依赖，实现基本的CRUD操作
 */
public class SalesRecordRepository {
    
    private final Map<Long, SalesRecord> dataStore = new HashMap<>();
    private long nextId = 1;
    
    /**
     * 保存销售记录
     */
    public SalesRecord save(SalesRecord record) {
        if (record.getId() == null) {
            record.setId(nextId++);
        }
        dataStore.put(record.getId(), record);
        return record;
    }
    
    /**
     * 根据ID查找销售记录
     */
    public Optional<SalesRecord> findById(Long id) {
        return Optional.ofNullable(dataStore.get(id));
    }
    
    /**
     * 查询所有销售记录
     */
    public List<SalesRecord> findAll() {
        return new ArrayList<>(dataStore.values());
    }
    
    /**
     * 删除销售记录
     */
    public void deleteById(Long id) {
        dataStore.remove(id);
    }
    
    /**
     * 根据类别查询销售记录
     */
    public List<SalesRecord> findByCategory(String category) {
        List<SalesRecord> result = new ArrayList<>();
        for (SalesRecord record : dataStore.values()) {
            if (category.equals(record.getCategory())) {
                result.add(record);
            }
        }
        return result;
    }
    
    /**
     * 获取销售总金额
     */
    public BigDecimal getTotalAmount() {
        BigDecimal total = BigDecimal.ZERO;
        for (SalesRecord record : dataStore.values()) {
            if (record.getAmount() != null) {
                total = total.add(record.getAmount());
            }
        }
        return total;
    }
}