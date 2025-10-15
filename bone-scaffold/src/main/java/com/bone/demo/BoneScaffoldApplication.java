package com.bone.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Bone脚手架应用程序入口
 */
@SpringBootApplication
@ComponentScan("com.bone.demo")
public class BoneScaffoldApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BoneScaffoldApplication.class, args);
        System.out.println("==================================");
        System.out.println("Bone脚手架应用启动成功！");
        System.out.println("访问地址：http://localhost:8080");
        System.out.println("API文档：http://localhost:8080/swagger-ui.html");
        System.out.println("==================================");
    }
}