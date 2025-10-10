package com.bone.procurement.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 预算服务
 */
@Service
public class BudgetService {
    
    /**
     * 检查预算可用性
     */
    public void checkBudgetAvailability(String departmentId, BigDecimal amount) {
        // 实际实现应该查询部门预算
        // 这里提供一个基本的模拟实现
        if (departmentId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("部门ID和金额参数无效");
        }
    }
    
    /**
     * 预留预算
     */
    public void reserveBudget(String departmentId, BigDecimal amount, String orderId) {
        // 实际实现应该更新部门预算
        // 这里提供一个基本的模拟实现
        if (departmentId == null || amount == null || orderId == null) {
            throw new IllegalArgumentException("参数无效");
        }
    }
}