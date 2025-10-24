package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GreetingExtPoint 使用示例测试类
 * 展示如何正确注入和使用扩展点
 */
@SpringBootTest(classes = GreetingExtPointUsageTest.TestConfig.class)
public class GreetingExtPointUsageTest {

    @Autowired
    private GreetingExtPoint greetingExtPoint;

    @Test
    void testGreet() {
        // 确保扩展点被注入
        assertNotNull(greetingExtPoint, "GreetingExtPoint 应该被自动注入");
        
        // 创建业务上下文
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userName", "testUser");
        
        BizContext<String> context = BizContext.<String>builder()
                .data("testUser")
                .bizCode("standard")
                .attributes(attributes)
                .build();
        
        // 调用扩展点
        String result = greetingExtPoint.greet(context);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains("testUser"), "问候语应该包含用户名");
    }

    /**
     * 测试配置类
     */
    @Configuration
    static class TestConfig {
        
        @Bean
        public GreetingExtPoint greetingExtPoint() {
            // 直接提供一个简单的实现用于测试
            return context -> {
                String userName = context.getAttribute("userName") != null ? 
                    context.getAttribute("userName").toString() : "Guest";
                return "Hello, " + userName + "!";
            };
        }
    }
}
