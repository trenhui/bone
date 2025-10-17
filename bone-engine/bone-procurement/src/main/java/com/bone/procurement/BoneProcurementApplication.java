package com.bone.procurement;

import com.bone.procurement.service.SupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.ComponentScan;

/**
 * 采购模块应用程序主类
 * 集成bone-smartmeta引擎和MySQL数据库的最佳实践案例
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.bone.procurement"
    // 扫描本项目所有包，包括repository
})
public class BoneProcurementApplication implements CommandLineRunner {
    
    private static final Logger logger = LoggerFactory.getLogger(BoneProcurementApplication.class);
    
    @Autowired
    private SupplierService supplierService;
    
    public static void main(String[] args) {
        logger.info("启动采购模块应用程序...");
        SpringApplication.run(BoneProcurementApplication.class, args);
        logger.info("采购模块应用程序启动完成");
    }
    
    @Override
    public void run(String... args) throws Exception {
        logger.info("执行初始化操作...");
        // 初始化供应商数据（仅在开发环境使用）
        if ("dev".equals(System.getProperty("spring.profiles.active", "dev"))) {
            logger.info("开发环境，初始化供应商数据");
            supplierService.initMockData();
        }
        logger.info("初始化操作完成");
    }
}