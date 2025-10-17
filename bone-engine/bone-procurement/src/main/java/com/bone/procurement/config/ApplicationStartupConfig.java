package com.bone.procurement.config;

import com.bone.procurement.service.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动配置类
 * 在应用启动时初始化必要的模拟数据
 */
@Component
public class ApplicationStartupConfig implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupConfig.class);
    
    private final SupplierService supplierService;
    
    public ApplicationStartupConfig(SupplierService supplierService) {
        this.supplierService = supplierService;
    }
    
    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.info("应用启动中，开始初始化模拟数据...");
        
        // 初始化供应商模拟数据
        supplierService.initMockData();
        
        logger.info("模拟数据初始化完成");
    }
}