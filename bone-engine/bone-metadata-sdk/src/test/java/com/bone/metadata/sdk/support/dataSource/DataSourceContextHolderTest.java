package com.bone.metadata.sdk.support.dataSource;

import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSourceContextHolder单元测试类
 * <p>全面验证数据源上下文管理的核心功能：</p>
 * <ul>
 *   <li>基本的设置、获取、清理功能</li>
 *   <li>上下文栈的嵌套管理</li>
 *   <li>安全执行方法（异常处理和资源清理）</li>
 *   <li>线程安全性</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataSourceContextHolderTest {
    
    /**
     * 测试前置准备
     * <p>确保测试隔离性：清理数据源上下文</p>
     */
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试后置清理
     * <p>确保测试隔离性：清理数据源上下文</p>
     */
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文，防止资源泄漏
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试设置和获取数据源
     * <p>验证数据源标识能被正确设置和读取</p>
     */
    @Test
    @Order(1)
    public void shouldReturnCorrectDataSource_whenDataSourceIsSet() {
        // 准备测试数据
        final String expectedDataSource = "master";
        
        // 执行操作
        DataSourceContextHolder.setDataSource(expectedDataSource);
        
        // 验证结果
        assertEquals(expectedDataSource, DataSourceContextHolder.getCurrentLookupKey(), 
                "获取的数据源应该与设置的数据源一致");
    }
    
    /**
     * 测试清理单个数据源
     * <p>验证：</p>
     * <ul>
     *   <li>清理操作返回被清理的数据源标识</li>
     *   <li>清理后当前数据源为null</li>
     * </ul>
     */
    @Test
    @Order(2)
    public void shouldReturnClearedDataSource_whenDataSourceIsCleared() {
        // 准备测试数据
        final String testDataSource = "master";
        
        // 设置数据源
        DataSourceContextHolder.setDataSource(testDataSource);
        
        // 执行清理
        String clearedDataSource = DataSourceContextHolder.clearDataSource();
        
        // 验证返回值
        assertEquals(testDataSource, clearedDataSource, 
                "清理操作应该返回被清理的数据源标识");
        
        // 验证当前数据源已被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "清理后的数据源上下文应该为null");
    }
    
    /**
     * 测试清理所有数据源上下文
     * <p>验证清理所有操作后上下文栈被完全清空</p>
     */
    @Test
    @Order(3)
    public void shouldClearAllContexts_whenClearAllIsCalled() {
        // 准备测试数据
        final String firstDataSource = "master";
        final String secondDataSource = "slave";
        
        // 设置多个数据源，模拟嵌套场景
        DataSourceContextHolder.setDataSource(firstDataSource);
        DataSourceContextHolder.setDataSource(secondDataSource);
        
        // 执行清理所有操作
        DataSourceContextHolder.clearAll();
        
        // 验证结果
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "清理所有操作后数据源上下文应为null");
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "清理所有操作后不应存在任何数据源上下文");
    }
    
    /**
     * 测试数据源存在性检查
     * <p>验证hasDataSource方法在不同状态下的行为</p>
     */
    @Test
    @Order(4)
    public void shouldCorrectlyCheckDataSourceExistence() {
        // 初始状态应该没有数据源
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "初始状态下不应存在数据源");
        
        // 设置数据源后应该返回true
        DataSourceContextHolder.setDataSource("master");
        assertTrue(DataSourceContextHolder.hasDataSource(), 
                "设置数据源后应检测到数据源存在");
        
        // 清理数据源后应该返回false
        DataSourceContextHolder.clearDataSource();
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "清理数据源后不应检测到数据源存在");
    }
    
    /**
     * 测试上下文栈深度
     * <p>验证上下文栈的深度正确反映了嵌套层级</p>
     */
    @Test
    @Order(5)
    public void shouldReturnCorrectStackDepth() {
        // 初始状态栈深度为0
        assertEquals(0, DataSourceContextHolder.getContextStackDepth(), 
                "初始状态上下文栈深度应为0");
        
        // 设置一个数据源后栈深度为1
        DataSourceContextHolder.setDataSource("master");
        assertEquals(1, DataSourceContextHolder.getContextStackDepth(), 
                "设置一个数据源后上下文栈深度应为1");
        
        // 嵌套设置数据源后栈深度增加
        DataSourceContextHolder.setDataSource("slave");
        assertEquals(2, DataSourceContextHolder.getContextStackDepth(), 
                "嵌套设置数据源后上下文栈深度应为2");
        
        // 清理一层后栈深度减少
        DataSourceContextHolder.clearDataSource();
        assertEquals(1, DataSourceContextHolder.getContextStackDepth(), 
                "清理一层后上下文栈深度应为1");
    }
    
    /**
     * 测试嵌套数据源切换
     * <p>验证嵌套设置和清理时数据源上下文的正确恢复</p>
     */
    @Test
    @Order(6)
    public void shouldRestorePreviousDataSource_whenNestedContextIsCleared() {
        // 准备测试数据
        final String firstDataSource = "master";
        final String secondDataSource = "slave";
        final String thirdDataSource = "tenant_a";
        
        // 嵌套设置多个数据源
        DataSourceContextHolder.setDataSource(firstDataSource);
        DataSourceContextHolder.setDataSource(secondDataSource);
        DataSourceContextHolder.setDataSource(thirdDataSource);
        
        // 验证栈顶是最后设置的数据源
        assertEquals(thirdDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                "当前数据源应该是最后设置的数据源");
        
        // 清理一层，应该返回上一个数据源
        DataSourceContextHolder.clearDataSource();
        assertEquals(secondDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                "清理一层后应恢复到前一个数据源");
        
        // 再清理一层
        DataSourceContextHolder.clearDataSource();
        assertEquals(firstDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                "再清理一层后应恢复到初始数据源");
        
        // 最后清理一层
        DataSourceContextHolder.clearDataSource();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(),
                "完全清理后数据源上下文应为null");
    }
    
    /**
     * 测试安全执行方法（无返回值）
     * <p>验证在指定数据源上下文中执行操作，并在完成后自动清理</p>
     */
    @Test
    @Order(7)
    public void shouldExecuteInSpecifiedDataSource_andCleanup_whenNoReturnValue() {
        // 准备测试数据
        final String testDataSource = "test_ds";
        final List<String> executedDatasource = new ArrayList<>();
        
        // 执行安全操作
        DataSourceContextHolder.executeInDataSource(testDataSource, () -> {
            // 记录执行上下文的数据源
            executedDatasource.add(DataSourceContextHolder.getCurrentLookupKey());
        });
        
        // 验证操作在正确的数据源上下文中执行
        assertEquals(testDataSource, executedDatasource.get(0), 
                "操作应在指定的数据源上下文中执行");
        
        // 验证执行完成后上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "执行完成后数据源上下文应被自动清理");
    }
    
    /**
     * 测试安全执行方法（有返回值）
     * <p>验证在指定数据源上下文中执行操作，返回结果，并在完成后自动清理</p>
     */
    @Test
    @Order(8)
    public void shouldReturnCorrectResult_andCleanup_whenExecuteWithResult() {
        // 准备测试数据
        final String testDataSource = "test_ds";
        
        // 执行安全操作并获取结果
        String result = DataSourceContextHolder.executeInDataSourceWithResult(testDataSource, () -> {
            return DataSourceContextHolder.getCurrentLookupKey();
        });
        
        // 验证返回结果正确
        assertEquals(testDataSource, result, 
                "应返回操作中产生的正确结果");
        
        // 验证执行完成后上下文被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "执行完成后数据源上下文应被自动清理");
    }
    
    /**
     * 测试执行异常时的上下文清理
     * <p>验证即使执行过程中发生异常，数据源上下文也会被正确清理</p>
     */
    @Test
    @Order(9)
    public void shouldCleanupContext_whenExceptionOccursDuringExecution() {
        // 准备测试数据
        final String testDataSource = "test_ds";
        
        // 执行会抛出异常的操作
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            DataSourceContextHolder.executeInDataSource(testDataSource, () -> {
                // 验证数据源设置正确
                assertEquals(testDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                        "异常抛出前数据源应正确设置");
                // 模拟业务异常
                throw new RuntimeException("Test exception");
            });
        }, "应正确抛出RuntimeException");
        
        // 验证异常信息
        assertEquals("Test exception", exception.getMessage(), 
                "异常信息不匹配");
        
        // 关键验证：即使发生异常，上下文也被清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "异常发生时数据源上下文必须被清理，避免资源泄漏");
    }
    
    /**
     * 测试线程安全性
     * <p>验证在多线程环境下，各线程的数据源上下文互不影响</p>
     */
    @Test
    @Order(10)
    public void shouldMaintainIsolation_whenMultipleThreadsAccessContext() throws InterruptedException {
        // 准备测试数据
        final int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 启动多个线程同时操作数据源上下文
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    String threadSpecificDataSource = "thread_ds_" + threadIndex;
                    // 设置当前线程的数据源
                    DataSourceContextHolder.setDataSource(threadSpecificDataSource);
                    
                    // 验证当前线程只能看到自己设置的数据源
                    assertEquals(threadSpecificDataSource, DataSourceContextHolder.getCurrentLookupKey(), 
                            "线程只能看到自己的数据源上下文，线程索引: " + threadIndex);
                    
                    // 模拟业务操作延迟
                    Thread.sleep(10);
                    
                    // 再次验证，确保没有被其他线程修改
                    assertEquals(threadSpecificDataSource, DataSourceContextHolder.getCurrentLookupKey(),
                            "线程数据源上下文不应被其他线程修改，线程索引: " + threadIndex);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 清理当前线程的数据源上下文
                    DataSourceContextHolder.clearAll();
                    latch.countDown();
                }
            });
        }
        
        // 等待所有线程执行完成
        latch.await();
        executorService.shutdown();
        
        // 验证主线程的上下文不受影响
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "主线程数据源上下文不应受其他线程影响");
    }
}