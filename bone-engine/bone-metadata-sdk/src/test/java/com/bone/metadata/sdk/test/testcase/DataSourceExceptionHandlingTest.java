package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据源异常处理独立测试类
 * 专注于DataSourceContextHolder的异常处理和上下文清理功能
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("数据源异常处理测试")
public class DataSourceExceptionHandlingTest {
    
    /**
     * 测试前置准备 - 确保完全隔离的测试环境
     */
    @BeforeEach
    public void setUp() {
        DataSourceContextHolder.clearAll();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "测试前数据源上下文必须为空");
    }
    
    /**
     * 测试后置清理 - 确保不污染其他测试
     */
    @AfterEach
    public void tearDown() {
        DataSourceContextHolder.clearAll();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "测试后数据源上下文必须为空");
    }
    
    /**
     * 测试运行时异常情况下的上下文清理
     */
    @Test
    @Order(1)
    public void shouldCleanupContext_whenRuntimeExceptionOccurs() {
        String testDataSource = "master";
        
        try {
            DataSourceContextHolder.setDataSource(testDataSource);
            assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey(), "DataSource should be set correctly");
            throw new RuntimeException("Test exception");
        } catch (RuntimeException e) {
            assertEquals("Test exception", e.getMessage(), "Exception message should match");
        } finally {
            DataSourceContextHolder.clearDataSource();
            assertNull(DataSourceContextHolder.getCurrentLookupKey(), "Context should be cleared after exception");
        }
    }
    
    /**
     * 测试嵌套数据源上下文的正确处理
     */
    @Test
    @Order(2)
    public void shouldCleanupNestedDataSourceContextsCorrectly() {
        String outerDataSource = "master";
        String innerDataSource = "slave";
        
        try {
            DataSourceContextHolder.setDataSource(outerDataSource);
            assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey(), "嵌套操作-外层数据源应该被正确设置为: " + outerDataSource);
            
            try {
                DataSourceContextHolder.setDataSource(innerDataSource);
                assertEquals(innerDataSource, DataSourceContextHolder.getCurrentLookupKey(), "嵌套操作-内层数据源应该被正确设置为: " + innerDataSource);
            } finally {
                DataSourceContextHolder.clearDataSource();
                assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey(), "嵌套操作-清除内层后应该返回到外层数据源: " + outerDataSource);
            }
        } finally {
            DataSourceContextHolder.clearDataSource();
            assertNull(DataSourceContextHolder.getCurrentLookupKey(), "嵌套操作-所有上下文都应该被最终清除");
        }
    }
    
    /**
     * 测试clearAll方法的有效性
     */
    @Test
    @Order(3)
    public void shouldClearAllContexts_whenClearAllIsCalled() {
        DataSourceContextHolder.setDataSource("ds1");
        DataSourceContextHolder.setDataSource("ds2");
        DataSourceContextHolder.setDataSource("ds3");
        
        assertEquals("ds3", DataSourceContextHolder.getCurrentLookupKey(), "Most recent data source should be active");
        
        DataSourceContextHolder.clearAll();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "clearAll should remove all context entries");
    }
    
    /**
     * 测试空状态下的操作
     */
    @Test
    @Order(4)
    public void shouldHandleEmptyStateOperationsGracefully() {
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "Initial state should be null");
        
        // 空状态下clearDataSource会返回null
        String cleared = DataSourceContextHolder.clearDataSource();
        assertNull(cleared, "clearDataSource should return null in empty state");
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "State should remain null after clearDataSource");
        
        DataSourceContextHolder.clearAll();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "State should remain null after clearAll");
    }
    
    /**
     * 测试重复设置相同数据源
     */
    @Test
    @Order(5)
    public void shouldHandleRepeatedDataSourceSetting() {
        String dataSource = "master";
        
        DataSourceContextHolder.setDataSource(dataSource);
        DataSourceContextHolder.setDataSource(dataSource);
        
        assertEquals(dataSource, DataSourceContextHolder.getCurrentLookupKey(), "DataSource should be set correctly after repeated calls");
        
        DataSourceContextHolder.clearDataSource();
        assertEquals(dataSource, DataSourceContextHolder.getCurrentLookupKey(), "Previous data source should still be active after first clear");
        
        DataSourceContextHolder.clearDataSource();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "Context should be completely cleared after second clear");
    }
    
    /**
     * 测试null值处理
     */
    @Test
    @Order(6)
    public void shouldHandleNullDataSourceValuesWithProperException() {
        // 测试setDataSource(null)会抛出NullPointerException
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            DataSourceContextHolder.setDataSource(null);
        }, "设置null数据源时应该抛出NullPointerException");
        assertTrue(exception.getMessage().contains("Data source cannot be null"), "异常消息应该包含预期文本: Data source cannot be null");
    }
    
    /**
     * 测试异常情况下的资源泄漏防护
     */
    @Test
    @Order(7)
    public void shouldPreventResourceLeakageAfterExceptions() {
        for (int i = 0; i < 3; i++) {
            try {
                DataSourceContextHolder.setDataSource("ds_" + i);
                if (i == 1) {
                    throw new RuntimeException("Simulated failure");
                }
            } catch (RuntimeException e) {
                assertEquals("Simulated failure", e.getMessage(), "Exception message should match the simulated failure");
            } finally {
                DataSourceContextHolder.clearDataSource();
                assertNull(DataSourceContextHolder.getCurrentLookupKey(), "Context should be cleared in finally block even after exception");
            }
        }
        
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "No resource leakage should occur after multiple operations");
    }
}
