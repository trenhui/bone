package com.bone.procurement.service;

import com.bone.procurement.entity.Supplier;
import com.bone.smartmeta.engine.MetadataEngine;
import com.bone.smartmeta.engine.ExpressionEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 供应商服务类
 * 演示如何使用bone-smartmeta引擎进行供应商元数据管理和评估
 */
@Service
public class SupplierService {
    
    private static final Logger logger = Logger.getLogger(SupplierService.class.getName());
    
    private final MetadataEngine metadataEngine;
    private final ExpressionEngine expressionEngine;
    
    // 模拟数据存储
    private final Map<Long, Supplier> supplierRepository = new HashMap<>();
    private long nextId = 1;
    
    @Autowired
    public SupplierService(MetadataEngine metadataEngine, ExpressionEngine expressionEngine) {
        this.metadataEngine = metadataEngine;
        this.expressionEngine = expressionEngine;
        
        // 初始化一些模拟数据
        initMockData();
    }
    
    /**
     * 初始化模拟供应商数据
     */
    private void initMockData() {
        // 注册实体元数据
        metadataEngine.registerEntity(Supplier.class);
        
        // 创建一些默认供应商
        Supplier supplier1 = new Supplier();
        supplier1.setId(nextId++);
        supplier1.setName("得力办公用品有限公司");
        supplier1.setCode("DL-001");
        supplier1.setContactPerson("张经理");
        supplier1.setContactPhone("13800138001");
        supplier1.setEmail("contact@deli.com");
        supplier1.setAddress("上海市浦东新区张江高科技园区");
        supplier1.setEnabled(true);
        supplier1.setRating(4.8);
        supplier1.setRegistrationDate(LocalDateTime.now().minusYears(3));
        supplier1.setBusinessLicense("91310115MA1H9YKR9K");
        
        Supplier supplier2 = new Supplier();
        supplier2.setId(nextId++);
        supplier2.setName("华为技术有限公司");
        supplier2.setCode("HW-001");
        supplier2.setContactPerson("李总监");
        supplier2.setContactPhone("13900139002");
        supplier2.setEmail("sales@huawei.com");
        supplier2.setAddress("广东省深圳市南山区科技园");
        supplier2.setEnabled(true);
        supplier2.setRating(4.9);
        supplier2.setRegistrationDate(LocalDateTime.now().minusYears(5));
        supplier2.setBusinessLicense("91440300746645251H");
        
        Supplier supplier3 = new Supplier();
        supplier3.setId(nextId++);
        supplier3.setName("腾讯科技(深圳)有限公司");
        supplier3.setCode("TX-001");
        supplier3.setContactPerson("王总");
        supplier3.setContactPhone("13700137003");
        supplier3.setEmail("procurement@tencent.com");
        supplier3.setAddress("广东省深圳市南山区高新科技园");
        supplier3.setEnabled(true);
        supplier3.setRating(4.7);
        supplier3.setRegistrationDate(LocalDateTime.now().minusYears(4));
        supplier3.setBusinessLicense("91440300708461136T");
        
        // 保存到模拟仓库
        supplierRepository.put(supplier1.getId(), supplier1);
        supplierRepository.put(supplier2.getId(), supplier2);
        supplierRepository.put(supplier3.getId(), supplier3);
        
        logger.info("初始化供应商数据完成，共创建 " + supplierRepository.size() + " 个供应商");
    }
    
    /**
     * 根据ID获取供应商
     */
    public Supplier getSupplier(Long supplierId) {
        Supplier supplier = supplierRepository.get(supplierId);
        if (supplier != null) {
            // 重新计算计算字段
            calculateSupplierFields(supplier);
        }
        return supplier;
    }
    
    /**
     * 创建新供应商
     */
    public Supplier createSupplier(Supplier supplier) {
        logger.info("创建供应商: " + supplier.getName());
        
        // 设置创建时间
        supplier.setCreationDate(LocalDateTime.now());
        
        // 如果未设置启用状态，默认为启用
        if (supplier.getEnabled() == null) {
            supplier.setEnabled(true);
        }
        
        // 保存供应商
        synchronized (this) {
            supplier.setId(nextId++);
            supplierRepository.put(supplier.getId(), supplier);
        }
        
        // 计算字段值
        calculateSupplierFields(supplier);
        
        logger.info("供应商创建成功，ID: " + supplier.getId());
        return supplier;
    }
    
    /**
     * 评估供应商综合得分
     */
    public double evaluateSupplierScore(Supplier supplier) {
        // 构建上下文
        Map<String, Object> context = new HashMap<>();
        context.put("supplier", supplier);
        
        // 使用表达式引擎计算综合得分
        // 评分规则：评分占50%，合作年限占30%，是否启用占20%
        String expression = "(${supplier.rating} / 5.0 * 50) + " +
                          "(Math.min(${supplier.cooperationYears}, 5) / 5.0 * 30) + " +
                          "(${supplier.enabled} ? 20 : 0)";
        
        try {
            return expressionEngine.evaluateExpression(expression, context, Double.class);
        } catch (Exception e) {
            logger.warning("评估供应商得分失败: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * 计算供应商的计算字段
     */
    private void calculateSupplierFields(Supplier supplier) {
        // 计算合作年限
        if (supplier.getRegistrationDate() != null) {
            long years = LocalDateTime.now().getYear() - supplier.getRegistrationDate().getYear();
            supplier.setCooperationYears((int) years);
        } else {
            supplier.setCooperationYears(0);
        }
        
        // 计算综合得分
        double compositeScore = evaluateSupplierScore(supplier);
        supplier.setCompositeScore(compositeScore);
        
        // 评估供应商等级
        evaluateSupplierLevel(supplier);
    }
    
    /**
     * 评估供应商等级
     */
    private void evaluateSupplierLevel(Supplier supplier) {
        double score = supplier.getCompositeScore();
        String level;
        
        if (score >= 90) {
            level = "A级-战略供应商";
        } else if (score >= 80) {
            level = "B级-核心供应商";
        } else if (score >= 70) {
            level = "C级-合格供应商";
        } else {
            level = "D级-观察供应商";
        }
        
        supplier.setSupplierLevel(level);
    }
    
    /**
     * 启用/禁用供应商
     */
    public Supplier toggleSupplierStatus(Long supplierId, boolean enabled) {
        Supplier supplier = getSupplier(supplierId);
        if (supplier == null) {
            throw new RuntimeException("供应商不存在: " + supplierId);
        }
        
        supplier.setEnabled(enabled);
        
        // 重新计算字段值
        calculateSupplierFields(supplier);
        
        logger.info("供应商状态已更新: " + supplier.getName() + " - " + (enabled ? "启用" : "禁用"));
        return supplier;
    }
}