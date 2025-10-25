package com.bone.procurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 极简应用程序入口类，不加载任何自动配置
 */
@SpringBootApplication
public class SimpleApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(SimpleApplication.class);
    
    public static void main(String[] args) {
        logger.info("启动简单应用程序...");
        SpringApplication.run(SimpleApplication.class, args);
        logger.info("简单应用程序启动完成！");
    }
}