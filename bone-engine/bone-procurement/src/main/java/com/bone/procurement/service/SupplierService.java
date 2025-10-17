package com.bone.procurement.service;

import com.bone.procurement.entity.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 供应商管理服务
 * 提供供应商信息的CRUD操作和业务逻辑处理
 */
@Service
public class SupplierService {

    private static final Logger logger = LoggerFactory.getLogger(SupplierService.class);
    
    private final Map<Long, Supplier> suppliers = new HashMap<>();
    private Long nextId = 1L;

    /**
     * 初始化模拟数据
     */
    public void initMockData() {
        // 在服务启动时手动调用此方法
        
        logger.info("初始化供应商模拟数据");
        
        Supplier supplier1 = new Supplier();
        supplier1.setId(nextId++);
        supplier1.setCode("SUP001");
        supplier1.setName("创新科技有限公司");
        supplier1.setPhoneNumber("13800138001");
        supplier1.setContactPerson("张经理");
        supplier1.setEmail("contact@innovtech.com");
        supplier1.setAddress("北京市海淀区科技园路1号");
        supplier1.setBusinessLicense("91110108MA00123456");
        supplier1.setRegisterDate(LocalDate.of(2015, 5, 15));
        supplier1.setSupplierLevel("A级");
        supplier1.setCreditScore(95);
        supplier1.setCooperationStatus("活跃");
        supplier1.setLastCooperationDate(LocalDate.now().minusDays(10));
        supplier1.setTotalOrderAmount(new BigDecimal("5000000.00"));
        supplier1.setOrderCount(120);
        supplier1.setAverageDeliveryRate(0.98);
        supplier1.setAverageQualityRate(0.99);
        supplier1.setComplaintCount(2);
        supplier1.setOverallScore(97.5);
        supplier1.setCooperationYears(8);
        supplier1.setRiskLevel("低");
        
        Supplier supplier2 = new Supplier();
        supplier2.setId(nextId++);
        supplier2.setCode("SUP002");
        supplier2.setName("诚信贸易公司");
        supplier2.setPhoneNumber("13900139002");
        supplier2.setContactPerson("李总");
        supplier2.setEmail("sales@chengxin.com");
        supplier2.setAddress("上海市浦东新区贸易大道88号");
        supplier2.setBusinessLicense("91310115MA00654321");
        supplier2.setRegisterDate(LocalDate.of(2018, 3, 22));
        supplier2.setSupplierLevel("B级");
        supplier2.setCreditScore(82);
        supplier2.setCooperationStatus("活跃");
        supplier2.setLastCooperationDate(LocalDate.now().minusDays(5));
        supplier2.setTotalOrderAmount(new BigDecimal("3500000.00"));
        supplier2.setOrderCount(85);
        supplier2.setAverageDeliveryRate(0.95);
        supplier2.setAverageQualityRate(0.96);
        supplier2.setComplaintCount(5);
        supplier2.setOverallScore(88.0);
        supplier2.setCooperationYears(5);
        supplier2.setRiskLevel("中");
        
        Supplier supplier3 = new Supplier();
        supplier3.setId(nextId++);
        supplier3.setCode("SUP003");
        supplier3.setName("快速配送中心");
        supplier3.setPhoneNumber("13700137003");
        supplier3.setContactPerson("王主管");
        supplier3.setEmail("delivery@expresscenter.com");
        supplier3.setAddress("广州市天河区物流园B区12栋");
        supplier3.setBusinessLicense("91440106MA00789012");
        supplier3.setRegisterDate(LocalDate.of(2020, 7, 10));
        supplier3.setSupplierLevel("C级");
        supplier3.setCreditScore(75);
        supplier3.setCooperationStatus("合作中");
        supplier3.setLastCooperationDate(LocalDate.now().minusDays(2));
        supplier3.setTotalOrderAmount(new BigDecimal("1800000.00"));
        supplier3.setOrderCount(200);
        supplier3.setAverageDeliveryRate(0.99);
        supplier3.setAverageQualityRate(0.93);
        supplier3.setComplaintCount(8);
        supplier3.setOverallScore(82.0);
        supplier3.setCooperationYears(3);
        supplier3.setRiskLevel("中");
        
        suppliers.put(supplier1.getId(), supplier1);
        suppliers.put(supplier2.getId(), supplier2);
        suppliers.put(supplier3.getId(), supplier3);
        
        logger.info("成功初始化{}个供应商数据", suppliers.size());
    }

    /**
     * 根据ID获取供应商信息
     * @param id 供应商ID
     * @return 供应商信息
     */
    public Optional<Supplier> getSupplier(Long id) {
        logger.info("查询供应商信息，ID: {}", id);
        return Optional.ofNullable(suppliers.get(id));
    }
    
    /**
     * 保存供应商信息
     * @param supplier 供应商信息
     * @return 保存后的供应商信息
     */
    public Supplier saveSupplier(Supplier supplier) {
        if (supplier.getId() == null) {
            supplier.setId(nextId++);
            logger.info("新增供应商，ID: {}, 名称: {}", supplier.getId(), supplier.getName());
        } else {
            logger.info("更新供应商，ID: {}, 名称: {}", supplier.getId(), supplier.getName());
        }
        suppliers.put(supplier.getId(), supplier);
        return supplier;
    }
    
    /**
     * 删除供应商
     * @param id 供应商ID
     * @return 是否删除成功
     */
    public boolean deleteSupplier(Long id) {
        logger.info("删除供应商，ID: {}", id);
        return suppliers.remove(id) != null;
    }
    
    /**
     * 获取所有供应商
     * @return 供应商列表
     */
    public Map<Long, Supplier> getAllSuppliers() {
        logger.info("获取所有供应商信息，共{}个", suppliers.size());
        return new HashMap<>(suppliers);
    }
}