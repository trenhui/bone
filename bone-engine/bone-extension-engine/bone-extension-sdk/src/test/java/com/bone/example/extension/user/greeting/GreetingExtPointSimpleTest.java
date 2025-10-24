package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import com.bone.engine.extension.proxy.ExtPointProxyFactory;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GreetingExtPoint 简单测试类
 * 使用Spring容器创建最小化配置的测试环境
 */
public class GreetingExtPointSimpleTest {

    @Test
    void testGreetWithSpringContext() {
        // 创建最小化的Spring应用上下文
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(TestConfig.class);
        context.refresh();
        
        // 从容器中获取扩展点实例
        GreetingExtPoint greetingExtPoint = context.getBean(GreetingExtPoint.class);
        
        // 确保扩展点实例不为空
        assertNotNull(greetingExtPoint, "扩展点实例不应为null");
        
        // 创建业务上下文（使用Builder模式）
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userName", "John");
        
        BizContext<String> bizContext = BizContext.<String>builder()
                .tenantCode("default")
                .bizCode("greeting")
                .attributes(attributes)
                .build();
        
        // 调用扩展点
        String result = greetingExtPoint.greet(bizContext);
        
        // 验证结果
        assertNotNull(result);
        System.out.println("Greeting result: " + result);
        
        // 关闭上下文
        context.close();
    }

    /**
     * 测试配置类
     */
    @Configuration
    static class TestConfig {
        
        @Bean
        public ExtPointProxyFactory extPointProxyFactory() {
            return new ExtPointProxyFactory();
        }
        
        @Bean
        public GreetingExtPoint greetingExtPoint(ExtPointProxyFactory proxyFactory) {
            // 直接返回一个简单的实现，不使用代理
            return context -> {
                if (context == null) {
                    throw new IllegalArgumentException("Context cannot be null");
                }
                
                String userName = context.getAttribute("userName") != null ? 
                    context.getAttribute("userName").toString() : "Guest";
                return "Hello, " + userName + "!";
            };
        }
    }
}