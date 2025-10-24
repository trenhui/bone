package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.test.common.BaseDataSourceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * UserService单元测试，验证数据源切换功能
 */
public class UserServiceTest extends BaseDataSourceTest {
    
    @Mock
    private JdbcTemplate jdbcTemplate;
    
    @InjectMocks
    private UserService userService;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    public void testGetUserById() {
        // 测试默认数据源（主库）的方法调用
        Long userId = 1L;
        String expectedName = "Test User";
        
        when(jdbcTemplate.queryForObject(
                eq("SELECT name FROM user WHERE id = ?"),
                eq(new Object[]{userId}),
                eq(String.class)))
                .thenReturn(expectedName);
        
        // 执行方法
        String result = userService.getUserById(userId);
        
        // 验证结果
        assertEquals(expectedName, result);
        
        // 验证JdbcTemplate被正确调用
        verify(jdbcTemplate, times(1)).queryForObject(
                eq("SELECT name FROM user WHERE id = ?"),
                eq(new Object[]{userId}),
                eq(String.class));
    }
    
    @Test
    public void testGetUserCount() {
        // 测试使用@DS("slave")注解的方法调用
        int expectedCount = 10;
        
        when(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM user"),
                eq(Integer.class)))
                .thenReturn(expectedCount);
        
        // 模拟AOP拦截器的行为，设置数据源
        String originalDs = null;
        try {
            // 记录原始数据源（如果有）
            if (DataSourceContextHolder.hasDataSource()) {
                originalDs = DataSourceContextHolder.getCurrentLookupKey();
            }
            
            // 设置从库数据源
            DataSourceContextHolder.setDataSource("slave");
            
            // 执行方法
            int result = userService.getUserCount();
            
            // 验证结果
            assertEquals(expectedCount, result);
            
            // 验证JdbcTemplate被正确调用
            verify(jdbcTemplate, times(1)).queryForObject(
                    eq("SELECT COUNT(*) FROM user"),
                    eq(Integer.class));
            
            // 验证当前使用的是从库数据源
            assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
            
        } finally {

            // 恢复原始数据源（如果有）
            if (originalDs != null) {
                DataSourceContextHolder.setDataSource(originalDs);
            }
        }
    }
    
    @Test
    public void testCreateUser() {
        // 测试使用@DS("master")和@Transactional注解的方法调用
        Long userId = 1L;
        String username = "New User";
        
        // 模拟AOP拦截器的行为，设置数据源
        String originalDs = null;
        try {
            // 记录原始数据源（如果有）
            if (DataSourceContextHolder.hasDataSource()) {
                originalDs = DataSourceContextHolder.getCurrentLookupKey();
            }
            
            // 设置主库数据源
            DataSourceContextHolder.setDataSource("master");
            
            // 执行方法
            userService.createUser(userId, username);
            
            // 验证JdbcTemplate被正确调用
            verify(jdbcTemplate, times(1)).update(
                    eq("INSERT INTO user (id, name) VALUES (?, ?)"),
                    eq(userId), eq(username));
            
            // 验证当前使用的是主库数据源
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            
        } finally {

            // 恢复原始数据源（如果有）
            if (originalDs != null) {
                DataSourceContextHolder.setDataSource(originalDs);
            }
        }
    }
    
    @Test
    public void testNestedDataSourceExample() {
        // 测试嵌套数据源切换
        Long userId = 1L;
        String expectedName = "Test User";
        
        // 模拟内部查询结果
        when(jdbcTemplate.queryForObject(
                eq("SELECT name FROM user WHERE id = ?"),
                eq(new Object[]{userId}),
                eq(String.class)))
                .thenReturn(expectedName);
        
        // 模拟AOP拦截器的行为，设置外部方法的数据源
        String originalDs = null;
        try {
            // 记录原始数据源（如果有）
            if (DataSourceContextHolder.hasDataSource()) {
                originalDs = DataSourceContextHolder.getCurrentLookupKey();
            }
            
            // 设置主库数据源（模拟外部方法的@DS("master")）
            DataSourceContextHolder.setDataSource("master");
            
            // 执行方法
            userService.nestedDataSourceExample(userId);
            
            // 验证外层的更新操作使用主库
            verify(jdbcTemplate, times(1)).update(
                    eq("UPDATE user SET last_login = CURRENT_TIMESTAMP WHERE id = ?"),
                    eq(userId));
            
            // 验证内层的查询操作（在executeInDataSource中）
            verify(jdbcTemplate, times(1)).queryForObject(
                    eq("SELECT name FROM user WHERE id = ?"),
                    eq(new Object[]{userId}),
                    eq(String.class));
            
            // 验证最终使用的还是主库数据源（方法结束后）
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            
        } finally {

            // 恢复原始数据源（如果有）
            if (originalDs != null) {
                DataSourceContextHolder.setDataSource(originalDs);
            }
        }
    }
    

}