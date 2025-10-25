package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QueryBuilder基础功能测试类
 * 极简测试，只验证from方法能返回非空对象
 */
public class QueryBuilderTest {

    /**
     * 极简测试，只验证QueryBuilder.from方法能返回非空对象
     * 使用String.class作为测试类，避免依赖User类
     */
    @Test
    public void testFromMethodReturnsObject() {
        Object result = QueryBuilder.from(String.class);
        assertNotNull(result, "QueryBuilder.from()应该返回非空对象");
    }
}