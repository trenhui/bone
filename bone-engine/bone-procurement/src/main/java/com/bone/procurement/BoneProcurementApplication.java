package com.bone.procurement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 采购模块应用程序主类
 * 集成bone-smartmeta引擎的最佳实践案例
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.bone.procurement"
    // 仅扫描本项目的包，避免扫描到外部冲突的类
})
public class BoneProcurementApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BoneProcurementApplication.class, args);
    }
}