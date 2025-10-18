package com.bone.metadata.sdk.test.util;

import java.util.UUID;

/**
 * 测试工具类
 */
public class TestUtils {
    /**
     * 生成随机字符串
     * @return 随机字符串
     */
    public static String randomString() {
        return UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * 生成随机用户名
     * @return 随机用户名
     */
    public static String randomUsername() {
        return "user_" + randomString().substring(0, 8);
    }
    
    /**
     * 生成随机邮箱
     * @return 随机邮箱
     */
    public static String randomEmail() {
        return randomUsername() + "@test.com";
    }
}