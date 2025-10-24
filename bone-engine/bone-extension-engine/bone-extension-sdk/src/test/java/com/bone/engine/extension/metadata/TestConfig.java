package com.bone.engine.extension.metadata;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * 测试配置类
 */
@Configuration
@EnableAutoConfiguration
@ComponentScan({
    "com.bone.engine.extension",
    "com.bone.example.extension.user.greeting"
})
public class TestConfig {
}