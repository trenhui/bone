package com.bone.procurement.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 采购模块主应用类
 * 作为采购模块的启动入口
 */
@SpringBootApplication
@ComponentScan({
        "com.bone.procurement"
})
public class ProcurementApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ProcurementApplication.class, args);
    }
}