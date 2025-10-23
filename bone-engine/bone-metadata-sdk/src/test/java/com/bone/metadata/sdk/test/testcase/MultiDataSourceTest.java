package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多数据源单元测试
 * 测试动态数据源切换、注解拦截器、上下文持有者等核心功能
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
public class MultiDataSourceTest {
    
    @Autowired
    private DataSourceManager dataSourceManager;
    
    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;
    
    @Autowired
    @Qualifier("slaveJdbcTemplate")
    private JdbcTemplate slaveJdbcTemplate;
    
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试隔离
        DataSourceContextHolder.clearAll();
        
        // 在主数据源中插入测试数据
        masterJdbcTemplate.update("DELETE FROM user WHERE id = 100");
        masterJdbcTemplate.update("INSERT INTO user (id, name) VALUES (100, 'master_user')");
        
        // 在从数据源中插入测试数据
        slaveJdbcTemplate.update("DELETE FROM user WHERE id = 100");
        slaveJdbcTemplate.update("INSERT INTO user (id, name) VALUES (100, 'slave_user')");
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试DataSourceContextHolder的基本功能
     */
    @Test
    public void testDataSourceContextHolder() {
        // 初始状态下没有数据源
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
        assertFalse(DataSourceContextHolder.hasDataSource());
        
        // 设置数据源
        DataSourceContextHolder.setDataSource("master");
        assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
        assertTrue(DataSourceContextHolder.hasDataSource());
        
        // 嵌套设置数据源
        DataSourceContextHolder.setDataSource("slave");
        assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
        
        // 清理数据源（恢复到上一个）
        String cleared = DataSourceContextHolder.clearDataSource();
        assertEquals("slave", cleared);
        assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
        
        // 再次清理（完全清空）
        cleared = DataSourceContextHolder.clearDataSource();
        assertEquals("master", cleared);
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
        assertFalse(DataSourceContextHolder.hasDataSource());
    }
    
    /**
     * 测试DataSourceManager的withDataSource方法
     */
    @Test
    public void testDataSourceManager_withDataSource() {
        // 使用master数据源
        String result1 = dataSourceManager.withDataSource("master", () -> {
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            return masterJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = 100", String.class);
        });
        assertEquals("master_user", result1);
        
        // 使用slave数据源
        String result2 = dataSourceManager.withDataSource("slave", () -> {
            assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
            return slaveJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = 100", String.class);
        });
        assertEquals("slave_user", result2);
        
        // 验证方法执行后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试DataSourceManager的withMaster和withSlave便捷方法
     */
    @Test
    public void testDataSourceManager_convenienceMethods() {
        // 测试withMaster
        String masterResult = dataSourceManager.withMaster(() -> {
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            return "master_result";
        });
        assertEquals("master_result", masterResult);
        
        // 测试withSlave
        String slaveResult = dataSourceManager.withSlave(() -> {
            assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
            return "slave_result";
        });
        assertEquals("slave_result", slaveResult);
        
        // 验证方法执行后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试嵌套数据源切换功能
     */
    @Test
    public void testNestedDataSourceSwitching() {
        String result = dataSourceManager.withDataSource("master", () -> {
            // 第一层：master
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            
            // 嵌套切换到slave
            String nestedResult = dataSourceManager.withDataSource("slave", () -> {
                // 第二层：slave
                assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
                return "nested_slave";
            });
            
            // 返回第一层：master
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            assertEquals("nested_slave", nestedResult);
            
            return "outer_master";
        });
        
        // 验证完全退出后上下文被清理
        assertEquals("outer_master", result);
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试无返回值的数据源操作
     */
    @Test
    public void testDataSourceManager_withConsumer() {
        final String[] capturedValue = {null};
        
        // 测试withDataSource的Consumer版本
        dataSourceManager.withDataSource("master", v -> {
            assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
            capturedValue[0] = "consumer_executed";
        });
        
        assertEquals("consumer_executed", capturedValue[0]);
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试DS注解的模拟功能
     * 注意：这里只是模拟测试，完整的注解功能需要在实际应用中测试
     */
    @Test
    public void testDSAnnotationFunctionality() {
        // 调用带有模拟DS注解行为的方法
        String result = testWithMasterAnnotation();
        assertEquals("master_user", result);
        
        // 验证数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 模拟带有@DS("master")注解的方法
     * 实际项目中这个方法应该直接标注@DS注解
     */
    private String testWithMasterAnnotation() {
        // 模拟注解行为
        return dataSourceManager.withDataSource("master", () -> {
            return masterJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = 100", String.class);
        });
    }
    
    /**
     * 测试异常情况下的数据源清理
     */
    @Test
    public void testDataSourceCleanupOnException() {
        // 确保即使发生异常，数据源上下文也会被清理
        Exception exception = assertThrows(RuntimeException.class, () -> {
            dataSourceManager.withDataSource("master", () -> {
                // 设置了数据源
                assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
                // 抛出异常
                throw new RuntimeException("Test exception");
            });
        });
        
        assertEquals("Test exception", exception.getMessage());
        // 验证异常后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
}