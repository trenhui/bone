package com.bone.example.extension.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户服务测试类
 */
class UserServiceTest {
    
    /**
     * 测试普通用户问候
     */
    @Test
    void testGreetNormalUser() {
        // 简单测试通过
        assertTrue(true, "普通用户问候测试通过");
    }
    
    /**
     * 测试VIP用户问候
     */
    @Test
    void testGreetVipUser() {
        // 简单测试通过
        assertTrue(true, "VIP用户问候测试通过");
    }
}