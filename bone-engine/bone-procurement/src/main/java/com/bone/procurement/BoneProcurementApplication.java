package com.bone.procurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 采购模块应用程序主类
 */
@SpringBootApplication
public class BoneProcurementApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(BoneProcurementApplication.class);
    
    public static void main(String[] args) {
        logger.info("启动采购模块应用程序...");
        SpringApplication.run(BoneProcurementApplication.class, args);
        logger.info("采购模块应用程序启动完成");
    }
}