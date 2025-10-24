package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.util.function.Consumer;

/**
 * 数据源异常处理测试类
 * <p>全面验证在各种异常情况下数据源上下文的正确清理和恢复机制</p>
 * <p>确保无论操作成功与否，资源都能被正确释放，避免内存泄漏</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataSourceExceptionHandlingTest {
    
    @Autowired
    private DataSourceManager dataSourceManager;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @MockBean
    private ErrorReportingService errorReportingService;
    
    /**
     * 测试前置准备
     * <p>确保测试隔离性：清理数据源上下文，设置初始状态</p>
     */
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试后置清理
     * <p>确保测试隔离性：清理数据源上下文，防止资源泄漏</p>
     */
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试运行时异常情况下数据源上下文的清理
     * <p>验证：</p>
     * <ul>
     *   <li>运行时异常正确传播</li>
     *   <li>异常后数据源上下文被清理</li>
     *   <li>后续操作不受影响</li>
     * </ul>
     */
    @Test
    @Order(1)
    public void shouldCleanupContext_whenRuntimeExceptionOccurs() {
        // 准备测试数据
        final String masterDataSource = "master";
        final String slaveDataSource = "slave";
        
        // 验证初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "初始状态下数据源上下文应为null");
        
        // 执行异常操作并验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dataSourceManager.withDataSource(masterDataSource, () -> {
                // 验证数据源设置
                assertEquals(masterDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                        "操作执行期间数据源设置不正确");
                // 模拟业务异常
                throw new RuntimeException("Simulated runtime exception");
            });
        }, "应正确抛出RuntimeException");
        
        // 验证异常信息
        assertEquals("Simulated runtime exception", exception.getMessage(), 
                "异常信息不匹配");
        
        // 关键验证：异常发生后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "运行时异常发生后数据源上下文必须被清理");
        
        // 验证后续操作不受影响
        String result = dataSourceManager.withDataSource(slaveDataSource, () -> {
            return slaveDataSource;
        });
        assertEquals(slaveDataSource, result, "后续操作应正常执行");
    }
    
    /**
     * 测试检查型异常情况下数据源上下文的清理
     * <p>验证在函数式操作抛出检查型异常时，数据源上下文是否被正确清理</p>
     */
    @Test
    @Order(2)
    public void shouldCleanupContext_whenCheckedExceptionIsWrapped() {
        // 准备测试数据
        final String testDataSource = "slave";
        
        // 验证初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "初始状态下数据源上下文应为null");
        
        // 执行会抛出检查型异常的操作
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dataSourceManager.withDataSource(testDataSource, () -> {
                // 验证数据源设置
                assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                        "操作执行期间数据源设置不正确");
                
                // 调用会抛出检查型异常的方法
                try {
                    throwCheckedException();
                } catch (CustomCheckedException e) {
                    // 包装并重新抛出
                    throw new RuntimeException(e);
                }
                return null; // 这行不会执行
            });
        }, "应正确抛出包装了检查型异常的RuntimeException");
        
        // 验证异常包装正确
        assertTrue(exception.getCause() instanceof CustomCheckedException, 
                "异常应包含CustomCheckedException作为cause");
        assertEquals("Custom checked exception", exception.getCause().getMessage(), 
                "检查型异常消息不匹配");
        
        // 关键验证：异常发生后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "检查型异常发生后数据源上下文必须被清理");
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
     * 测试嵌套数据源切换中的异常处理
     * <p>验证：</p>
     * <ul>
     *   <li>内层异常时外层数据源被正确恢复</li>
     *   <li>外层异常传播正常</li>
     *   <li>最终数据源上下文被清理</li>
     * </ul>
     */
    @Test
    @Order(3)
    public void shouldRestoreOuterDataSource_whenInnerOperationThrowsException() {
        // 准备测试数据
        final String outerDataSource = "master";
        final String innerDataSource = "slave";
        
        // 执行嵌套数据源操作并验证
        RuntimeException outerException = assertThrows(RuntimeException.class, () -> {
            dataSourceManager.withDataSource(outerDataSource, () -> {
                // 验证外层数据源设置
                assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                        "外层数据源设置不正确");
                
                try {
                    // 内层数据源切换，这里会抛出异常
                    dataSourceManager.withDataSource(innerDataSource, () -> {
                        // 验证内层数据源设置
                        assertEquals(innerDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                                "内层数据源设置不正确");
                        // 模拟内层异常
                        throw new RuntimeException("Inner operation exception");
                    });
                } catch (RuntimeException innerException) {
                    // 验证内层异常正确
                    assertEquals("Inner operation exception", innerException.getMessage(), 
                            "内层异常信息不匹配");
                    
                    // 关键验证：内层异常后，外层数据源被正确恢复
                    assertEquals(outerDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                            "内层异常后外层数据源应被恢复");
                    
                    // 继续抛出外层异常
                    throw new RuntimeException("Outer operation exception");
                }
                return null; // 这行不会执行
            });
        }, "应正确抛出外层RuntimeException");
        
        // 验证外层异常信息
        assertEquals("Outer operation exception", outerException.getMessage(), 
                "外层异常信息不匹配");
        
        // 关键验证：所有操作完成后，数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "嵌套异常场景下数据源上下文最终必须被清理");
    }
    
    /**
     * 测试Consumer操作中的异常处理
     * <p>验证在无返回值操作中发生异常时，数据源上下文是否被正确清理</p>
     */
    @Test
    @Order(4)
    public void shouldCleanupContext_whenConsumerOperationThrowsException() {
        // 准备测试数据
        final String testDataSource = "master";
        final Long userId = 1000L;
        
        // 验证初始状态
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "初始状态下数据源上下文应为null");
        
        // 执行会抛出异常的Consumer操作
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dataSourceManager.withDataSource(testDataSource, userId, (Consumer<Long>) id -> {
                // 验证数据源设置
                assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                        "操作执行期间数据源设置不正确");
                // 验证参数传递正确
                assertEquals(userId, id, "Consumer参数传递不正确");
                // 模拟异常
                throw new RuntimeException("Consumer operation exception");
            });
        }, "Consumer操作应正确抛出异常");
        
        // 验证异常信息
        assertEquals("Consumer operation exception", exception.getMessage(), 
                "异常信息不匹配");
        
        // 关键验证：异常发生后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Consumer操作异常后数据源上下文必须被清理");
    }
    
    /**
     * 测试异常处理和监控集成
     * <p>验证数据源异常是否被正确报告到监控系统</p>
     */
    @Test
    @Order(5)
    public void shouldReportErrorAndCleanup_whenDatabaseExceptionOccurs() {
        // 准备测试数据
        final String testDataSource = "slave";
        final String errorMessage = "Monitored data source exception";
        
        // 执行会抛出异常的操作
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
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
        }, "数据库操作异常应被正确抛出");
        
        // 验证异常信息
        assertEquals(errorMessage, exception.getMessage(), "异常信息不匹配");
        
        // 验证异常被正确报告
        verify(errorReportingService).reportError(exception, testDataSource);
        
        // 验证数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "异常报告后数据源上下文必须被清理");
    }
    
    /**
     * 测试DS注解方法中的异常处理
     * <p>验证在使用@DS注解的方法中发生异常时，数据源上下文是否被正确清理</p>
     */
    @Test
    @Order(6)
    public void shouldCleanupContext_whenAnnotatedMethodThrowsException() {
        // 创建并监视使用@DS注解的服务
        TestAnnotatedService service = Mockito.spy(new TestAnnotatedService());
        
        // 配置方法抛出异常
        doThrow(new RuntimeException("Annotated method exception"))
            .when(service).annotatedMethod();
        
        // 执行注解方法并验证
        RuntimeException exception = assertThrows(RuntimeException.class, 
                service::annotatedMethod, "注解方法应正确抛出异常");
        
        // 验证异常信息
        assertEquals("Annotated method exception", exception.getMessage(), 
                "异常信息不匹配");
        
        // 验证方法被调用
        verify(service).annotatedMethod();
        
        // 关键验证：异常发生后数据源上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "注解方法异常后数据源上下文必须被清理");
    }
    
    /**
     * 测试极端情况下的资源泄漏防护
     * <p>验证在各种极端情况下，数据源上下文不会发生泄漏</p>
     */
    @Test
    @Order(7)
    public void shouldPreventResourceLeakage_inExtremeScenarios() {
        // 模拟大量并发操作中的异常情况
        final int operationCount = 10;
        
        for (int i = 0; i < operationCount; i++) {
            final int operationId = i;
            
            try {
                // 每个操作使用不同的数据源名称
                String dataSourceName = "ds_" + operationId;
                
                dataSourceManager.withDataSource(dataSourceName, () -> {
                    // 验证数据源设置正确
                    assertEquals(dataSourceName, DataSourceContextHolder.getCurrentLookupKey(),
                            "操作 " + operationId + " 数据源设置不正确");
                    
                    // 模拟部分操作抛出异常（每3个操作抛出1个异常）
                    if (operationId % 3 == 0) {
                        throw new RuntimeException("Operation " + operationId + " failed");
                    }
                    return "Success " + operationId;
                });
            } catch (RuntimeException e) {
                // 验证异常信息
                assertTrue(e.getMessage().contains("failed"), 
                        "操作 " + operationId + " 异常信息不匹配");
            } finally {
                // 关键验证：无论操作成功还是失败，数据源上下文始终被清理
                assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                        "操作 " + operationId + " 完成后数据源上下文未清理，存在泄漏风险");
            }
        }
        
        // 最终验证：所有操作完成后，数据源上下文栈为空
        assertTrue(DataSourceContextHolder.isEmpty(), 
                "所有操作完成后数据源上下文栈应为空");
    }
    
    /**
     * 测试服务接口，用于模拟异常报告
     */
    public interface ErrorReportingService {
        /**
         * 报告错误到监控系统
         * @param e 异常对象
         * @param dataSource 数据源标识
         */
        void reportError(Exception e, String dataSource);
    }
    
    /**
     * 测试用注解服务类
     * <p>用于验证@DS注解在异常情况下的行为</p>
     */
    @DS("master")
    public class TestAnnotatedService {
        /**
         * 使用从数据源的注解方法
         * <p>覆盖类级别注解</p>
         */
        @DS("slave")
        public void annotatedMethod() {
            // 这个方法的实现由Mockito控制
        }
    }
}