package com.bone.procurement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 极简的独立应用程序，不依赖Spring Boot，仅用于测试运行环境
 */
public class MinimalApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(MinimalApplication.class);
    
    public static void main(String[] args) {
        logger.info("启动极简应用程序...");
        System.out.println("极简应用程序启动成功！");
        logger.info("极简应用程序启动完成！");
        System.out.println("应用程序正在运行...");
        try {
            // 让应用程序保持运行一段时间
            Thread.sleep(30000);
        } catch (InterruptedException e) {
            logger.error("应用程序被中断", e);
        }
    }
}