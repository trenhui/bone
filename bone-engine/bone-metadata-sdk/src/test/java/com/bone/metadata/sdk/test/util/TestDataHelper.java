package com.bone.metadata.sdk.test.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 简化的测试数据工具类
 * 移除了所有外部依赖
 */
public class TestDataHelper {
    
    /**
     * 模拟清理测试数据
     */
    public static void cleanTestData() {
        // 简化版本，仅输出日志
        System.out.println("清理测试数据");
    }
    
    /**
     * 模拟设置权限测试数据
     */
    public static void setUpPermissionTestData() {
        System.out.println("设置权限测试数据");
    }
    
    /**
     * 模拟设置角色测试数据
     */
    public static void setUpRoleTestData() {
        System.out.println("设置角色测试数据");
    }
    
    /**
     * 模拟设置用户测试数据
     */
    public static void setUpUserTestData() {
        System.out.println("设置用户测试数据");
    }
    
    /**
     * 模拟设置用户角色关联测试数据
     */
    public static void setUpUserRoleTestData() {
        System.out.println("设置用户角色关联测试数据");
    }
    
    /**
     * 模拟设置角色权限关联测试数据
     */
    public static void setUpRolePermissionTestData() {
        System.out.println("设置角色权限关联测试数据");
    }
    
    /**
     * 创建测试参数映射
     */
    private static Map<String, Object> createPermissionParams(Long id, String bizIdentityCode, 
                                                             String permName, String permCode) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", id);
        params.put("biz_identity_code", bizIdentityCode);
        params.put("perm_name", permName);
        params.put("perm_code", permCode);
        params.put("create_time", LocalDateTime.now());
        return params;
    }
    
    /**
     * 生成测试ID
     */
    public static Long generateTestId() {
        return System.currentTimeMillis();
    }
}