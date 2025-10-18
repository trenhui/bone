package com.bone.metadata.sdk.test.testcase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 简单的Spring测试类，用于验证Spring上下文初始化
 */
@SpringBootTest(classes = SimpleTestConfig.class)
@ActiveProfiles("test")
public class SimpleSpringTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testSpringContext() {
        // 只验证Spring上下文能正常初始化并注入JdbcTemplate
        assertNotNull(jdbcTemplate, "JdbcTemplate should be injected");
        System.out.println("Spring context initialized successfully with JdbcTemplate");
    }
}