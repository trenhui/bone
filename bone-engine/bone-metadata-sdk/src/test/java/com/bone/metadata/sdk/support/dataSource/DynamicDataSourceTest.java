package com.bone.metadata.sdk.support.dataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DynamicDataSource单元测试
 */
public class DynamicDataSourceTest {
    
    private DynamicDataSource dynamicDataSource;
    private DataSource masterDataSource;
    private DataSource slaveDataSource;
    private DataSource testDataSource;
    
    @BeforeEach
    public void setUp() {
        // 创建测试数据源
        masterDataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:master_test;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        
        slaveDataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:slave_test;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        
        testDataSource = DataSourceBuilder.create()
                .url("jdbc:h2:mem:test_db;DB_CLOSE_DELAY=-1")
                .username("sa")
                .password("")
                .driverClassName("org.h2.Driver")
                .build();
        
        // 初始化数据源表结构用于测试
        initDataSource(masterDataSource, "master_table");
        initDataSource(slaveDataSource, "slave_table");
        
        // 创建动态数据源
        dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        dynamicDataSource.setPrimary("master");
        dynamicDataSource.setStrict(false);
        dynamicDataSource.afterPropertiesSet();
        
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    private void initDataSource(DataSource dataSource, String tableName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " (id INT PRIMARY KEY, name VARCHAR(100))");
        jdbcTemplate.execute("INSERT INTO " + tableName + " VALUES (1, '" + tableName + "_data')");
    }
    
    @Test
    public void testDefaultDataSourceRouting() {
        // 测试默认数据源路由（主库）
        DataSourceContextHolder.clearAll();
        
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(masterDataSource, currentDs, "Should use master datasource by default");
        
        // 验证能够访问主库的数据
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dynamicDataSource);
        String result = jdbcTemplate.queryForObject("SELECT name FROM master_table WHERE id = 1", String.class);
        assertEquals("master_table_data", result);
    }
    
    @Test
    public void testSpecifiedDataSourceRouting() {
        // 测试指定数据源路由（从库）
        DataSourceContextHolder.setDataSource("slave");
        
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(slaveDataSource, currentDs, "Should use specified slave datasource");
        
        // 验证能够访问从库的数据
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dynamicDataSource);
        String result = jdbcTemplate.queryForObject("SELECT name FROM slave_table WHERE id = 1", String.class);
        assertEquals("slave_table_data", result);
    }
    
    @Test
    public void testNonExistentDataSource_LenientMode() {
        // 测试非严格模式下访问不存在的数据源（应该回退到默认数据源）
        dynamicDataSource.setStrict(false);
        DataSourceContextHolder.setDataSource("non_existent");
        
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(masterDataSource, currentDs, "Should fallback to master in lenient mode");
    }
    
    @Test
    public void testNonExistentDataSource_StrictMode() {
        // 测试严格模式下访问不存在的数据源（应该抛出异常）
        dynamicDataSource.setStrict(true);
        DataSourceContextHolder.setDataSource("non_existent");
        
        assertThrows(RuntimeException.class, () -> {
            dynamicDataSource.getCurrentDataSource();
        }, "Should throw exception in strict mode when datasource not found");
    }
    
    @Test
    public void testAddDataSourceAtRuntime() {
        // 测试运行时添加数据源
        String newDsName = "test_db";
        dynamicDataSource.addDataSource(newDsName, testDataSource);
        
        // 验证新数据源可用
        DataSourceContextHolder.setDataSource(newDsName);
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(testDataSource, currentDs, "Should be able to use newly added datasource");
    }
    
    @Test
    public void testRemoveDataSourceAtRuntime() {
        // 测试运行时移除数据源
        dynamicDataSource.removeDataSource("slave");
        
        // 在非严格模式下，应该回退到默认数据源
        dynamicDataSource.setStrict(false);
        DataSourceContextHolder.setDataSource("slave");
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(masterDataSource, currentDs, "Should fallback to master after removing datasource");
    }
    
    @Test
    public void testNestedDataSourceSwitching() {
        // 测试嵌套数据源切换
        DataSourceContextHolder.setDataSource("master");
        DataSourceContextHolder.setDataSource("slave");
        
        // 当前应该使用最内层的数据源
        DataSource currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(slaveDataSource, currentDs, "Should use innermost datasource in nested calls");
        
        // 清理一层，应该回到上一个数据源
        DataSourceContextHolder.clearDataSource();
        currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(masterDataSource, currentDs, "Should restore to previous datasource after clear");
        
        // 再清理一层，应该使用默认数据源
        DataSourceContextHolder.clearDataSource();
        currentDs = dynamicDataSource.getCurrentDataSource();
        assertEquals(masterDataSource, currentDs, "Should use default datasource after all clears");
    }
}