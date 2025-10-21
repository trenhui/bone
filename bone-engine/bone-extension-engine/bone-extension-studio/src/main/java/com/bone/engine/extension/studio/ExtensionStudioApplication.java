package com.bone.engine.extension.studio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Bone扩展引擎管理台应用主类
 */
@SpringBootApplication
@ComponentScan({
    "com.bone.engine.extension.studio",
    "com.bone.engine.extension" // 扫描扩展引擎的核心组件
})
public class ExtensionStudioApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExtensionStudioApplication.class, args);
    }

}