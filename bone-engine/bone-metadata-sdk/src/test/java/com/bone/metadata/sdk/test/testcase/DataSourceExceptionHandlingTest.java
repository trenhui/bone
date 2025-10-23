package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 数据源异常处理测试
 * 验证在各种异常情况下数据源上下文的正确清理和恢复机制
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
public class DataSourceExceptionHandlingTest {
    
    @Autowired
    private DataSourceManager dataSourceManager;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @MockBean
    private ErrorReportingService errorReportingService;
    
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试场景一：运行时异常情况下数据源上下文的清理
     * 验证在Supplier操作抛出异常时，数据源上下文是否被正确清理
     */
    @Test
    public void testRuntimeExceptionInSupplier() {
        // 准备测试数据
        String expectedDataSource = "master";
        String unexpectedDataSource = "slave";
        
        // 设置初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "Initial data source should be null");
        
        try {
            // 执行会抛出异常的操作
            dataSourceManager.withDataSource(expectedDataSource, () -> {
                // 验证数据源设置正确
                assertEquals(expectedDataSource, DataSourceContextHolder.getCurrentLookupKey());
                // 抛出运行时异常
                throw new RuntimeException("Simulated runtime exception");
            });
            fail("Should have thrown RuntimeException");
        } catch (RuntimeException e) {
            // 验证异常被正确传播
            assertEquals("Simulated runtime exception", e.getMessage());
            // 关键验证：异常发生后数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                       "Data source context should be cleared after exception");
        }
        
        // 验证后续操作不受影响
        String result = dataSourceManager.withDataSource(unexpectedDataSource, () -> {
            return unexpectedDataSource;
        });
        assertEquals(unexpectedDataSource, result);
    }
    
    /**
     * 测试场景二：检查型异常情况下数据源上下文的清理
     * 验证在函数式操作抛出检查型异常时，数据源上下文是否被正确清理
     */
    @Test
    public void testCheckedExceptionHandling() {
        // 准备测试数据
        String testDataSource = "slave";
        
        // 设置初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
        
        try {
            // 执行会抛出检查型异常的操作
            dataSourceManager.withDataSource(testDataSource, () -> {
                // 验证数据源设置正确
                assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey());
                // 调用会抛出检查型异常的方法
                throwCheckedException();
                return null; // 这行不会执行
            });
            fail("Should have thrown RuntimeException wrapping the checked exception");
        } catch (RuntimeException e) {
            // 验证异常被正确包装和传播
            assertTrue(e.getCause() instanceof CustomCheckedException);
            assertEquals("Custom checked exception", e.getCause().getMessage());
            // 关键验证：异常发生后数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey());
        }
    }
    
    /**
     * 抛出检查型异常的辅助方法
     */
    private void throwCheckedException() throws CustomCheckedException {
        throw new CustomCheckedException("Custom checked exception");
    }
    
    /**
     * 自定义检查型异常
     */
    private static class CustomCheckedException extends Exception {
        public CustomCheckedException(String message) {
            super(message);
        }
    }
    
    /**
     * 测试场景三：嵌套数据源切换中的异常处理
     * 验证在嵌套数据源切换中内层发生异常时，外层数据源是否被正确恢复
     */
    @Test
    public void testNestedDataSourceWithException() {
        // 准备测试数据
        String outerDataSource = "master";
        String innerDataSource = "slave";
        
        try {
            // 外层数据源切换
            dataSourceManager.withDataSource(outerDataSource, () -> {
                // 验证外层数据源设置正确
                assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey());
                
                try {
                    // 内层数据源切换，这里会抛出异常
                    dataSourceManager.withDataSource(innerDataSource, () -> {
                        // 验证内层数据源设置正确
                        assertEquals(innerDataSource, DataSourceContextHolder.getCurrentLookupKey());
                        // 抛出异常
                        throw new RuntimeException("Inner operation exception");
                    });
                    fail("Inner operation should have thrown exception");
                } catch (RuntimeException e) {
                    // 验证异常被正确传播
                    assertEquals("Inner operation exception", e.getMessage());
                    // 关键验证：内层异常后，外层数据源被正确恢复
                    assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey());
                    
                    // 继续抛出异常
                    throw new RuntimeException("Outer operation exception");
                }
            });
            fail("Outer operation should have thrown exception");
        } catch (RuntimeException e) {
            // 验证外层异常被正确传播
            assertEquals("Outer operation exception", e.getMessage());
            // 关键验证：所有操作完成后，数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey());
        }
    }
    
    /**
     * 测试场景四：Consumer操作中的异常处理
     * 验证在无返回值操作中发生异常时，数据源上下文是否被正确清理
     */
    @Test
    public void testExceptionInConsumer() {
        // 准备测试数据
        String testDataSource = "master";
        final Long userId = 1000L;
        
        // 设置初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
        
        try {
            // 执行会抛出异常的Consumer操作
            dataSourceManager.withDataSource(testDataSource, id -> {
                // 验证数据源设置正确
                assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey());
                // 抛出异常
                throw new RuntimeException("Consumer operation exception");
            }, userId);
            fail("Consumer operation should have thrown exception");
        } catch (RuntimeException e) {
            // 验证异常被正确传播
            assertEquals("Consumer operation exception", e.getMessage());
            // 关键验证：异常发生后数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey());
        }
    }
    
    /**
     * 测试场景五：异常处理和监控集成
     * 验证数据源异常是否被正确报告到监控系统
     */
    @Test
    public void testExceptionReporting() {
        // 准备测试数据
        String testDataSource = "slave";
        String errorMessage = "Monitored data source exception";
        
        try {
            // 执行会抛出异常的操作
            dataSourceManager.withDataSource(testDataSource, () -> {
                // 模拟数据库异常
                when(jdbcTemplate.queryForObject(anyString(), eq(String.class)))
                    .thenThrow(new RuntimeException(errorMessage));
                
                try {
                    // 尝试执行可能失败的操作
                    jdbcTemplate.queryForObject("SELECT name FROM non_existent_table", String.class);
                } catch (RuntimeException e) {
                    // 报告异常
                    errorReportingService.reportError(e, testDataSource);
                    // 重新抛出
                    throw e;
                }
                return null; // 这行不会执行
            });
            fail("Operation should have thrown exception");
        } catch (RuntimeException e) {
            // 验证异常被正确传播
            assertEquals(errorMessage, e.getMessage());
            // 验证异常被正确报告
            verify(errorReportingService).reportError(e, testDataSource);
            // 验证数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey());
        }
    }
    
    /**
     * 测试场景六：DS注解方法中的异常处理
     * 验证在使用@DS注解的方法中发生异常时，数据源上下文是否被正确清理
     */
    @Test
    public void testExceptionInAnnotatedMethod() {
        // 模拟一个使用@DS注解的服务
        TestAnnotatedService service = Mockito.spy(new TestAnnotatedService());
        
        try {
            // 执行会抛出异常的注解方法
            doThrow(new RuntimeException("Annotated method exception"))
                .when(service).annotatedMethod();
            
            service.annotatedMethod();
            fail("Annotated method should have thrown exception");
        } catch (RuntimeException e) {
            // 验证异常被正确传播
            assertEquals("Annotated method exception", e.getMessage());
            // 关键验证：异常发生后数据源上下文被清理
            assertNull(DataSourceContextHolder.getCurrentLookupKey());
        }
    }
    
    /**
     * 测试场景七：极端情况下的资源泄漏防护
     * 验证在各种极端情况下，数据源上下文不会发生泄漏
     */
    @Test
    public void testResourceLeakageProtection() {
        // 模拟大量并发操作中的异常
        final int operationCount = 10;
        
        for (int i = 0; i < operationCount; i++) {
            final int operationId = i;
            
            try {
                // 每个操作使用不同的数据源名称
                String dataSourceName = "ds_" + operationId;
                
                dataSourceManager.withDataSource(dataSourceName, () -> {
                    // 验证数据源设置
                    assertEquals(dataSourceName, DataSourceContextHolder.getCurrentLookupKey());
                    
                    // 模拟部分操作抛出异常
                    if (operationId % 3 == 0) {
                        throw new RuntimeException("Operation " + operationId + " failed");
                    }
                    return "Success " + operationId;
                });
            } catch (RuntimeException e) {
                // 异常处理
                assertTrue(e.getMessage().contains("failed"));
            } finally {
                // 关键验证：无论操作成功还是失败，数据源上下文始终被清理
                assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                          "Data source context leaked after operation " + operationId);
            }
        }
        
        // 最终验证：所有操作完成后，数据源上下文栈为空
        assertTrue(DataSourceContextHolder.isEmpty(), "Data source context stack is not empty");
    }
    
    /**
     * 测试服务接口，用于模拟异常报告
     */
    public interface ErrorReportingService {
        void reportError(Exception e, String dataSource);
    }
    
    /**
     * 测试用注解服务类
     */
    @DS("master")
    public class TestAnnotatedService {
        @DS("slave")
        public void annotatedMethod() {
            // 这个方法的实现由Mockito控制
        }
    }
}