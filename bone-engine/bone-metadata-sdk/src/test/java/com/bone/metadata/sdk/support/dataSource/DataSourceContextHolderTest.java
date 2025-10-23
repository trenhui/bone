package com.bone.metadata.sdk.support.dataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataSourceContextHolder单元测试
 */
public class DataSourceContextHolderTest {
    
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    @Test
    public void testSetAndGetDataSource() {
        // 测试设置和获取数据源
        String dataSource = "master";
        DataSourceContextHolder.setDataSource(dataSource);
        
        assertEquals(dataSource, DataSourceContextHolder.getCurrentLookupKey(), 
                "Current datasource should match the one that was set");
    }
    
    @Test
    public void testClearDataSource() {
        // 测试清理数据源
        DataSourceContextHolder.setDataSource("master");
        String cleared = DataSourceContextHolder.clearDataSource();
        
        assertEquals("master", cleared, "Cleared datasource should be returned");
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Current datasource should be null after clearing");
    }
    
    @Test
    public void testClearAll() {
        // 测试清理所有数据源上下文
        DataSourceContextHolder.setDataSource("master");
        DataSourceContextHolder.setDataSource("slave");
        
        DataSourceContextHolder.clearAll();
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "All datasource contexts should be cleared");
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "Should not have any datasource contexts after clearAll");
    }
    
    @Test
    public void testHasDataSource() {
        // 测试是否有数据源设置
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "Should not have datasource initially");
        
        DataSourceContextHolder.setDataSource("master");
        assertTrue(DataSourceContextHolder.hasDataSource(), 
                "Should have datasource after setting");
        
        DataSourceContextHolder.clearDataSource();
        assertFalse(DataSourceContextHolder.hasDataSource(), 
                "Should not have datasource after clearing");
    }
    
    @Test
    public void testContextStackDepth() {
        // 测试上下文栈深度
        assertEquals(0, DataSourceContextHolder.getContextStackDepth(), 
                "Initial context stack depth should be 0");
        
        DataSourceContextHolder.setDataSource("master");
        assertEquals(1, DataSourceContextHolder.getContextStackDepth(), 
                "Context stack depth should be 1 after setting one datasource");
        
        DataSourceContextHolder.setDataSource("slave");
        assertEquals(2, DataSourceContextHolder.getContextStackDepth(), 
                "Context stack depth should be 2 after nested setting");
        
        DataSourceContextHolder.clearDataSource();
        assertEquals(1, DataSourceContextHolder.getContextStackDepth(), 
                "Context stack depth should be 1 after one clear");
    }
    
    @Test
    public void testNestedDataSourceSwitching() {
        // 测试嵌套数据源切换
        DataSourceContextHolder.setDataSource("master");
        DataSourceContextHolder.setDataSource("slave");
        DataSourceContextHolder.setDataSource("tenant_a");
        
        // 验证栈顶是最后设置的数据源
        assertEquals("tenant_a", DataSourceContextHolder.getCurrentLookupKey());
        
        // 清理一层，应该返回上一个数据源
        DataSourceContextHolder.clearDataSource();
        assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey());
        
        // 再清理一层
        DataSourceContextHolder.clearDataSource();
        assertEquals("master", DataSourceContextHolder.getCurrentLookupKey());
        
        // 最后清理一层
        DataSourceContextHolder.clearDataSource();
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    @Test
    public void testExecuteInDataSource() {
        // 测试安全执行方法（无返回值）
        final List<String> executedDatasource = new ArrayList<>();
        
        DataSourceContextHolder.executeInDataSource("test_ds", () -> {
            executedDatasource.add(DataSourceContextHolder.getCurrentLookupKey());
        });
        
        assertEquals("test_ds", executedDatasource.get(0), 
                "Should execute in the specified datasource");
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Context should be cleared after execution");
    }
    
    @Test
    public void testExecuteInDataSourceWithResult() {
        // 测试安全执行方法（有返回值）
        String result = DataSourceContextHolder.executeInDataSourceWithResult("test_ds", () -> {
            return DataSourceContextHolder.getCurrentLookupKey();
        });
        
        assertEquals("test_ds", result, 
                "Should return value from the executed code");
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Context should be cleared after execution");
    }
    
    @Test
    public void testExecuteInDataSourceWithException() {
        // 测试执行异常时的上下文清理
        assertThrows(RuntimeException.class, () -> {
            DataSourceContextHolder.executeInDataSource("test_ds", () -> {
                throw new RuntimeException("Test exception");
            });
        });
        
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Context should be cleared even when exception occurs");
    }
    
    @Test
    public void testThreadSafety() throws InterruptedException {
        // 测试线程安全性
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executorService.submit(() -> {
                try {
                    String threadDs = "thread_ds_" + threadIndex;
                    DataSourceContextHolder.setDataSource(threadDs);
                    
                    // 验证当前线程只能看到自己设置的数据源
                    assertEquals(threadDs, DataSourceContextHolder.getCurrentLookupKey(), 
                            "Thread should only see its own datasource context");
                    
                    // 模拟工作
                    Thread.sleep(10);
                    
                    // 再次验证，确保没有被其他线程修改
                    assertEquals(threadDs, DataSourceContextHolder.getCurrentLookupKey());
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    DataSourceContextHolder.clearAll();
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executorService.shutdown();
        
        // 主线程的上下文应该不受影响
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "Main thread context should not be affected");
    }
}