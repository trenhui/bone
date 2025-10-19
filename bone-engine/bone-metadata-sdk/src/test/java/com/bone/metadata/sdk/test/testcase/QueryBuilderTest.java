package com.bone.metadata.sdk.test.testcase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库连接测试类
 */
@SpringBootTest(classes = QueryBuilderTestConfig.class)
@ActiveProfiles("test")
@Transactional
public class QueryBuilderTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试Spring上下文初始化和数据库连接
     */
    @Test
    public void testContextInitialization() {
        System.out.println("测试Spring上下文初始化");
        // 验证Spring上下文能正确注入JdbcTemplate
        assertNotNull(jdbcTemplate);
        System.out.println("JdbcTemplate注入成功");
        
        // 验证数据库连接正常并测试基本查询
        try {
            // 查询用户表中的记录数
            Integer userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            System.out.println("用户表记录数: " + userCount);
            assertEquals(3, userCount);
            
            // 查询角色表中的记录数
            Integer roleCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
            System.out.println("角色表记录数: " + roleCount);
            assertEquals(2, roleCount);
            
            System.out.println("数据库连接和查询测试成功！");
        } catch (Exception e) {
            System.err.println("数据库操作异常: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}