package com.bone.procurement.service;

import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.procurement.entity.Supplier;
import com.bone.procurement.repository.SupplierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;
import java.math.BigDecimal;

/**
 * 供应商服务类
 * 处理供应商相关业务逻辑
 */
@Service
public class SupplierService {

    private static final Logger logger = LoggerFactory.getLogger(SupplierService.class);
    
    @Autowired
    private SupplierRepository supplierRepository;
    
    /**
     * 创建供应商
     */
    @Transactional
    public Supplier createSupplier(Supplier supplier) {
        logger.info("Creating new supplier with code: {}", supplier.getCode());
        
        // 检查编码是否已存在
        Criteria<Supplier> criteria = Criteria.<Supplier>create().eq("code", supplier.getCode());
        List<Supplier> existingSuppliers = supplierRepository.findByCriteria(criteria);
        if (!existingSuppliers.isEmpty()) {
            throw new IllegalArgumentException("供应商编码已存在");
        }
        
        // 设置默认值
        if (supplier.getSupplierLevel() == null) {
            supplier.setSupplierLevel("D级");
        }
        if (supplier.getCreditScore() == null) {
            supplier.setCreditScore(80);
        }
        if (supplier.getCooperationStatus() == null) {
            supplier.setCooperationStatus("待评估");
        }
        if (supplier.getTotalOrderAmount() == null) {
            supplier.setTotalOrderAmount(BigDecimal.ZERO);
        }
        if (supplier.getOrderCount() == null) {
            supplier.setOrderCount(0);
        }
        if (supplier.getAverageDeliveryRate() == null) {
            supplier.setAverageDeliveryRate(1.0);
        }
        if (supplier.getAverageQualityRate() == null) {
            supplier.setAverageQualityRate(1.0);
        }
        if (supplier.getComplaintCount() == null) {
            supplier.setComplaintCount(0);
        }
        
        Long savedId = supplierRepository.save(supplier);
        supplier.setId(savedId);
        logger.info("Supplier created successfully with ID: {}", savedId);
        return supplier;
    }
    
    /**
     * 根据ID获取供应商
     */
    public Supplier getSupplierById(Long id) {
        logger.info("Fetching supplier by ID: {}", id);
        return supplierRepository.findById(id);
    }
    
    /**
     * 根据编码获取供应商
     */
    public Supplier getSupplierByCode(String code) {
        logger.info("Fetching supplier by code: {}", code);
        Criteria<Supplier> criteria = Criteria.<Supplier>create().eq("code", code);
        try {
            return supplierRepository.findOneByCriteria(criteria);
        } catch (Exception e) {
            logger.warn("Error finding supplier by code: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取所有供应商
     */
    public List<Supplier> getAllSuppliers() {
        logger.info("Fetching all suppliers");
        Criteria<Supplier> criteria = Criteria.<Supplier>create();
        return supplierRepository.findByCriteria(criteria);
    }
    
    /**
     * 更新供应商信息
     */
    @Transactional
    public Supplier updateSupplier(Supplier supplier) {
        logger.info("Updating supplier with ID: {}", supplier.getId());
        
        // 检查供应商是否存在
        Supplier existingSupplier = supplierRepository.findById(supplier.getId());
        if (existingSupplier == null) {
            throw new IllegalArgumentException("供应商不存在");
        }
        
        // 执行更新
        boolean updated = supplierRepository.update(supplier);
        if (updated) {
            logger.info("Supplier updated successfully");
            return supplierRepository.findById(supplier.getId()); // 返回更新后的供应商信息
        }
        logger.error("Failed to update supplier with ID: {}", supplier.getId());
        return null;
    }
    
    /**
     * 删除供应商
     */
    @Transactional
    public boolean deleteSupplier(Long id, boolean forceDelete) {
        logger.info("Deleting supplier with ID: {}, force: {}", id, forceDelete);
        
        Supplier supplier = supplierRepository.findById(id);
        if (supplier == null) {
            return false; // 供应商不存在
        }
        
        if (!forceDelete) {
            // 检查是否有未完成的订单
            if (supplier.getOrderCount() > 0) {
                throw new IllegalStateException("该供应商有未完成的订单，无法删除");
            }
        }
        
        boolean deleted = supplierRepository.deleteById(id);
        if (deleted) {
            logger.info("Supplier deleted successfully");
        }
        return deleted;
    }
    
    /**
     * 根据名称模糊搜索供应商
     */
    public List<Supplier> searchSuppliersByName(String name) {
        logger.info("Searching suppliers by name: {}", name);
        Criteria<Supplier> criteria = Criteria.<Supplier>create().like("name", "%" + name + "%");
        return supplierRepository.findByCriteria(criteria);
    }
    
    /**
     * 根据供应商等级筛选
     */
    public List<Supplier> filterSuppliersByLevel(String level) {
        logger.info("Filtering suppliers by level: {}", level);
        Criteria<Supplier> criteria = Criteria.<Supplier>create().eq("supplierLevel", level);
        return supplierRepository.findByCriteria(criteria);
    }
    
    /**
     * 根据合作状态筛选
     */
    public List<Supplier> filterSuppliersByStatus(String status) {
        logger.info("Filtering suppliers by status: {}", status);
        Criteria<Supplier> criteria = Criteria.<Supplier>create().eq("cooperationStatus", status);
        return supplierRepository.findByCriteria(criteria);
    }
    
    /**
     * 初始化模拟数据
     */
    @Transactional
    public void initMockData() {
        logger.info("Initializing mock supplier data");
        
        // 检查是否已有数据
        Criteria<Supplier> countCriteria = Criteria.<Supplier>create();
        Long count = supplierRepository.countByCriteria(countCriteria);
        if (count > 0) {
            logger.info("Supplier data already exists, skipping initialization");
            return;
        }
        
        // 创建模拟供应商数据
        List<Supplier> mockSuppliers = new ArrayList<>();
        
        // 1. 创建A级供应商
        Supplier supplier1 = new Supplier();
        supplier1.setCode("SUP000001");
        supplier1.setName("优质材料有限公司");
        supplier1.setPhoneNumber("13800138001");
        supplier1.setContactPerson("张先生");
        supplier1.setEmail("contact@youzhicailiao.com");
        supplier1.setAddress("北京市朝阳区建国路88号");
        supplier1.setBusinessLicense("91110105MA001ABCDE");
        supplier1.setRegisterDate(LocalDate.of(2015, 6, 15));
        supplier1.setSupplierLevel("A级");
        supplier1.setCreditScore(95);
        supplier1.setCooperationStatus("合作中");
        supplier1.setLastCooperationDate(LocalDate.now().minusDays(15));
        supplier1.setTotalOrderAmount(new BigDecimal("5000000.00"));
        supplier1.setOrderCount(120);
        supplier1.setAverageDeliveryRate(0.98);
        supplier1.setAverageQualityRate(0.99);
        supplier1.setComplaintCount(2);
        supplier1.setPaymentTerms("货到付款");
        supplier1.setBankAccount("6222021001012345678");
        supplier1.setBankName("中国工商银行北京分行");
        supplier1.setTaxpayerNumber("91110105MA001ABCDE");
        mockSuppliers.add(supplier1);
        
        // 2. 创建B级供应商
        Supplier supplier2 = new Supplier();
        supplier2.setCode("SUP000002");
        supplier2.setName("诚信科技发展有限公司");
        supplier2.setPhoneNumber("13900139002");
        supplier2.setContactPerson("李女士");
        supplier2.setEmail("info@chengxinkeji.com");
        supplier2.setAddress("上海市浦东新区张江高科技园区博云路2号");
        supplier2.setBusinessLicense("91310115MA002FGHIJ");
        supplier2.setRegisterDate(LocalDate.of(2017, 8, 20));
        supplier2.setSupplierLevel("B级");
        supplier2.setCreditScore(85);
        supplier2.setCooperationStatus("合作中");
        supplier2.setLastCooperationDate(LocalDate.now().minusDays(30));
        supplier2.setTotalOrderAmount(new BigDecimal("2500000.00"));
        supplier2.setOrderCount(80);
        supplier2.setAverageDeliveryRate(0.95);
        supplier2.setAverageQualityRate(0.96);
        supplier2.setComplaintCount(4);
        supplier2.setPaymentTerms("月结30天");
        supplier2.setBankAccount("6222023101012345678");
        supplier2.setBankName("中国工商银行上海分行");
        supplier2.setTaxpayerNumber("91310115MA002FGHIJ");
        mockSuppliers.add(supplier2);
        
        // 保存模拟数据
        supplierRepository.batchSave(mockSuppliers);
        logger.info("Successfully initialized {} mock suppliers", mockSuppliers.size());
    }
    
    /**
     * 获取供应商统计信息
     */
    public Map<String, Object> getSupplierStatistics() {
        logger.info("Calculating supplier statistics");
        
        Criteria<Supplier> criteria = Criteria.<Supplier>create();
        List<Supplier> allSuppliers = supplierRepository.findByCriteria(criteria);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCount", allSuppliers.size());
        
        // 计算活跃供应商数量
        long activeCount = allSuppliers.stream()
                .filter(Supplier::isActive)
                .count();
        stats.put("activeCount", activeCount);
        
        return stats;
    }
    
    /**
     * 为了兼容PurchaseOrderService的调用
     */
    public Optional<Supplier> getSupplier(Long id) {
        logger.info("Fetching supplier by ID: {}", id);
        Supplier supplier = supplierRepository.findById(id);
        return Optional.ofNullable(supplier);
    }
}