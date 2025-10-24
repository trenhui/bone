package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GreetingExtPoint 简单测试类
 * 使用纯Java方式测试扩展点功能
 */
@DisplayName("问候扩展点简单功能测试")
public class GreetingExtPointSimpleTest {

    /**
     * 测试正常情况下的问候功能
     */
    @Test
    @DisplayName("测试基本问候功能包含用户名")
    void testGreet() {
        // Given - 准备测试环境
        GreetingExtPoint greetingExtPoint = context -> {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Hello, " + userName + "!";
        };
        
        // 创建业务上下文（使用Builder模式）
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userName", "John");
        
        BizContext<String> bizContext = BizContext.<String>builder()
                .tenantCode("default")
                .bizCode("greeting")
                .attributes(attributes)
                .build();
        
        // When - 调用被测试方法
        String result = greetingExtPoint.greet(bizContext);
        
        // Then - 验证结果
        assertNotNull(greetingExtPoint, "扩展点实例不应为null");
        assertNotNull(result, "问候结果不应为null");
        assertTrue(result.contains("John"), "问候语应该包含用户名John");
        System.out.println("Greeting result: " + result);
    }
    
    /**
     * 测试使用默认用户名的情况
     */
    @Test
    @DisplayName("测试未指定用户名时使用默认值")
    void testGreetWithDefaultUsername() {
        // Given
        GreetingExtPoint greetingExtPoint = context -> {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Hello, " + userName + "!";
        };
        
        // 创建没有用户名属性的上下文
        BizContext<String> bizContext = BizContext.<String>builder()
                .tenantCode("default")
                .bizCode("greeting")
                .attributes(new HashMap<>())
                .build();
        
        // When
        String result = greetingExtPoint.greet(bizContext);
        
        // Then
        assertNotNull(result, "问候结果不应为null");
        assertTrue(result.contains("Guest"), "未指定用户名时应使用默认值Guest");
    }
    
    /**
     * 测试空上下文的异常处理
     */
    @Test
    @DisplayName("测试空上下文的异常处理")
    void testGreetWithNullContext() {
        // Given
        GreetingExtPoint greetingExtPoint = context -> {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Hello, " + userName + "!";
        };
        
        // When & Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> greetingExtPoint.greet(null),
                "当上下文为null时应抛出IllegalArgumentException");
        assertTrue(exception.getMessage().contains("Context cannot be null"));
    }

}