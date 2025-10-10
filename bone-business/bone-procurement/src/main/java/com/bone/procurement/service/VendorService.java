package com.bone.procurement.service;

import org.springframework.stereotype.Service;

/**
 * 供应商服务
 */
@Service
public class VendorService {
    
    /**
     * 验证供应商是否存在且活跃
     */
    public void validateActiveVendor(String vendorId) {
        // 实际实现应该查询供应商信息
        // 这里提供一个基本的模拟实现
        if (vendorId == null || vendorId.trim().isEmpty()) {
            throw new IllegalArgumentException("供应商ID不能为空");
        }
    }
}