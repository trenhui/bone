package com.bone.engine.extension.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * Bone扩展引擎管理台应用主类
 */
@SpringBootApplication(scanBasePackages = "com.bone.engine.extension.studio")
public class ExtensionStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtensionStudioApplication.class, args);
    }

}