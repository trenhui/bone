package com.bone.example.extension.user.greeting;

import com.bone.engine.extension.context.BizContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 问候扩展点使用示例测试类
 * 演示如何在实际场景中使用问候扩展点（纯Java方式）
 */
@DisplayName("问候扩展点使用场景测试")
public class GreetingExtPointUsageTest {

    private GreetingExtPoint greetingExtPoint;
    
    /**
     * 测试准备阶段，初始化测试对象
     */
    @BeforeEach
    void setUp() {
        // 直接创建一个简单的扩展点实现
        greetingExtPoint = context -> {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Hello, " + userName + "!";
        };
    }
    
    /**
     * 测试基本问候功能
     */
    @Test
    @DisplayName("测试基本问候功能")
    void testBasicGreeting() {
        // Given - 已在setUp中准备好测试对象
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userName", "testUser");
        
        BizContext<String> context = BizContext.<String>builder()
                .data("testUser")
                .bizCode("standard")
                .attributes(attributes)
                .build();
        
        // When
        String result = greetingExtPoint.greet(context);
        
        // Then
        assertNotNull(greetingExtPoint, "扩展点实例不应为null");
        assertNotNull(result, "问候结果不应为null");
        assertTrue(result.contains("testUser"), "问候语应该包含用户名");
        System.out.println("Basic greeting result: " + result);
    }
    
    /**
     * 测试使用场景：多用户问候
     */
    @Test
    @DisplayName("测试多用户问候场景")
    void testMultiUserGreeting() {
        // Given
        String[] users = {"testUser1", "testUser2", "testUser3"};
        
        // When & Then
        for (String user : users) {
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("userName", user);
            
            BizContext<String> context = BizContext.<String>builder()
                    .data(user)
                    .bizCode("standard")
                    .attributes(attributes)
                    .build();
                    
            String result = greetingExtPoint.greet(context);
            assertNotNull(result, "用户" + user + "的问候结果不应为null");
            assertTrue(result.contains(user), "用户" + user + "的问候结果应包含用户名");
        }
    }
    
    /**
     * 测试扩展点可替换性
     */
    @Test
    @DisplayName("测试扩展点实现的可替换性")
    void testExtPointReplaceability() {
        // Given - 使用自定义实现替换默认实现
        GreetingExtPoint customExtPoint = context -> {
            if (context == null) {
                throw new IllegalArgumentException("Context cannot be null");
            }
            String userName = context.getAttribute("userName") != null ? 
                context.getAttribute("userName").toString() : "Guest";
            return "Welcome, " + userName + "!";
        };
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userName", "testUser");
        
        BizContext<String> context = BizContext.<String>builder()
                .data("testUser")
                .bizCode("standard")
                .attributes(attributes)
                .build();
        
        // When
        String result = customExtPoint.greet(context);
        
        // Then
        assertNotNull(result, "自定义实现的问候结果不应为null");
        assertTrue(result.contains("Welcome"), "自定义实现应返回预期的欢迎消息");
        assertTrue(result.contains("testUser"), "自定义实现应包含用户名");
    }
}
