package com.bone.metadata.sdk.test.simple;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.test.domain.User;

/**
 * 简单测试类，直接验证QueryBuilder的基本功能
 */
public class SimpleQueryBuilderTest {
    
    public static void main(String[] args) {
        try {
            System.out.println("开始测试QueryBuilder.from方法...");
            Object result = QueryBuilder.from(User.class);
            System.out.println("测试成功！QueryBuilder.from返回对象: " + result.getClass().getName());
        } catch (Exception e) {
            System.err.println("测试失败！错误信息: " + e.getMessage());
            e.printStackTrace();
        }
    }
}