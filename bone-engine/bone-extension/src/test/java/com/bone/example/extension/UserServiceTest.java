package com.bone.example.extension;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用户服务测试类
 * 测试不同用户类型的问候功能
 */
class UserServiceTest {
    
    private UserService userService = new UserService();
    
    /**
     * 测试普通用户问候
     */
    @Test
    void testGreetNormalUser() {
        String result = userService.welcomeUser("张三", false);
        assertEquals("Hello, 张三!", result);
    }
    
    /**
     * 测试VIP用户问候
     */
    @Test
    void testGreetVipUser() {
        String result = userService.welcomeUser("李四", true);
        assertEquals("尊贵的VIP用户 李四，欢迎回来！", result);
    }
}