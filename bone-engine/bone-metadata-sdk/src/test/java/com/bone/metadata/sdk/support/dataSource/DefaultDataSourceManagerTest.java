package com.bone.metadata.sdk.support.dataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DefaultDataSourceManager 单元测试类，全面测试数据源管理器的核心功能。
 * 验证数据源的注册、获取、切换、健康检查和指标收集等功能是否正常工作。
 */
class DefaultDataSourceManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(DefaultDataSourceManagerTest.class);

    @Mock
    private DataSource masterDataSource;
    
    @Mock
    private DataSource slaveDataSource;
    
    @Mock
    private Connection masterConnection;
    
    @Mock
    private Connection slaveConnection;

    @InjectMocks
    private DefaultDataSourceManager dataSourceManager;

    private AutoCloseable mockitoCloseable;
    private ScheduledExecutorService originalScheduler;

    @BeforeEach
    void setUp() throws SQLException {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        
        // 模拟数据源连接行为
        when(masterDataSource.getConnection()).thenReturn(masterConnection);
        when(slaveDataSource.getConnection()).thenReturn(slaveConnection);
        when(masterConnection.isClosed()).thenReturn(false);
        when(slaveConnection.isClosed()).thenReturn(false);
        when(masterConnection.isValid(anyInt())).thenReturn(true);
        when(slaveConnection.isValid(anyInt())).thenReturn(true);
        
        // 保存原始的健康检查调度器，避免测试之间的影响
        // 设置严格模式为false，便于测试
        dataSourceManager.setStrictMode(false);
        
        // 注册测试数据源
        dataSourceManager.registerDataSource("master", masterDataSource);
        dataSourceManager.registerDataSource("slave", slaveDataSource);
        
        logger.info("测试环境准备完成");
    }

    @AfterEach
    void tearDown() throws Exception {
        mockitoCloseable.close();
        
        // 清理资源，确保没有线程泄漏
        logger.info("测试环境清理完成");
    }

    /**
     * 测试数据源注册功能
     */
    @Test
    void testRegisterDataSource() {
        logger.info("开始测试: 数据源注册功能");
        
        // 创建一个新的模拟数据源
        DataSource testDataSource = mock(DataSource.class);
        
        // 测试正常注册
        dataSourceManager.registerDataSource("test", testDataSource);
        assertNotNull(dataSourceManager.getDataSource("test"));
        
        // 测试重复注册（应该抛出异常）
        assertThrows(IllegalStateException.class, () -> {
            dataSourceManager.registerDataSource("test", testDataSource);
        });
        
        // 测试空名称注册（应该抛出异常）
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.registerDataSource("", testDataSource);
        });
        
        // 测试null数据源注册（应该抛出异常）
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.registerDataSource("test2", null);
        });
        
        logger.info("数据源注册功能测试通过");
    }

    /**
     * 测试数据源移除功能
     */
    @Test
    void testUnregisterDataSource() {
        logger.info("开始测试: 数据源移除功能");
        
        // 测试移除存在的数据源
        boolean removed = dataSourceManager.unregisterDataSource("slave");
        assertTrue(removed);
        assertNull(dataSourceManager.getDataSource("slave"));
        
        // 测试移除不存在的数据源
        boolean notRemoved = dataSourceManager.unregisterDataSource("nonexistent");
        assertFalse(notRemoved);
        
        // 测试移除当前活动的数据源
        dataSourceManager.switchDataSource("master"); // 确保master是当前活动数据源
        assertTrue(dataSourceManager.unregisterDataSource("master"));
        // 移除当前活动数据源后应该切换到默认数据源
        assertNull(dataSourceManager.getCurrentDataSourceName());
        
        logger.info("数据源移除功能测试通过");
    }

    /**
     * 测试数据源获取功能
     */
    @Test
    void testGetDataSource() {
        logger.info("开始测试: 数据源获取功能");
        
        // 测试获取存在的数据源
        assertEquals(masterDataSource, dataSourceManager.getDataSource("master"));
        assertEquals(slaveDataSource, dataSourceManager.getDataSource("slave"));
        
        // 测试获取不存在的数据源（非严格模式下返回null）
        dataSourceManager.setStrictMode(false);
        assertNull(dataSourceManager.getDataSource("nonexistent"));
        
        // 测试获取不存在的数据源（严格模式下抛出异常）
        dataSourceManager.setStrictMode(true);
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.getDataSource("nonexistent");
        });
        
        // 测试获取空名称数据源
        dataSourceManager.setStrictMode(true);
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.getDataSource("");
        });
        
        logger.info("数据源获取功能测试通过");
    }

    /**
     * 测试当前数据源获取功能
     */
    @Test
    void testGetCurrentDataSource() {
        logger.info("开始测试: 当前数据源获取功能");
        
        // 默认应该是master数据源
        assertEquals(masterDataSource, dataSourceManager.getCurrentDataSource());
        
        // 切换到slave后应该获取slave
        dataSourceManager.switchDataSource("slave");
        assertEquals(slaveDataSource, dataSourceManager.getCurrentDataSource());
        
        // 移除当前数据源后应该尝试使用默认数据源
        dataSourceManager.unregisterDataSource("slave");
        assertEquals(masterDataSource, dataSourceManager.getCurrentDataSource());
        
        // 移除所有数据源后（非严格模式）
        dataSourceManager.unregisterDataSource("master");
        dataSourceManager.setStrictMode(false);
        assertNull(dataSourceManager.getCurrentDataSource());
        
        // 移除所有数据源后（严格模式）
        dataSourceManager.setStrictMode(true);
        assertThrows(IllegalStateException.class, () -> {
            dataSourceManager.getCurrentDataSource();
        });
        
        logger.info("当前数据源获取功能测试通过");
    }

    /**
     * 测试数据源切换功能
     */
    @Test
    void testSwitchDataSource() {
        logger.info("开始测试: 数据源切换功能");
        
        // 测试切换到存在的数据源
        assertTrue(dataSourceManager.switchDataSource("slave"));
        assertEquals("slave", dataSourceManager.getCurrentDataSourceName());
        
        // 测试切换回master
        assertTrue(dataSourceManager.switchDataSource("master"));
        assertEquals("master", dataSourceManager.getCurrentDataSourceName());
        
        // 测试切换到不存在的数据源（非严格模式）
        dataSourceManager.setStrictMode(false);
        assertFalse(dataSourceManager.switchDataSource("nonexistent"));
        
        // 测试切换到不存在的数据源（严格模式）
        dataSourceManager.setStrictMode(true);
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.switchDataSource("nonexistent");
        });
        
        // 测试切换到空名称
        assertFalse(dataSourceManager.switchDataSource(""));
        
        logger.info("数据源切换功能测试通过");
    }

    /**
     * 测试数据源重置功能
     */
    @Test
    void testResetDataSource() {
        logger.info("开始测试: 数据源重置功能");
        
        // 切换到slave
        dataSourceManager.switchDataSource("slave");
        assertEquals("slave", dataSourceManager.getCurrentDataSourceName());
        
        // 重置后应该回到默认数据源（master）
        dataSourceManager.resetDataSource();
        assertEquals("master", dataSourceManager.getCurrentDataSourceName());
        
        // 修改默认数据源
        dataSourceManager.setDefaultDataSourceName("slave");
        dataSourceManager.resetDataSource();
        assertEquals("slave", dataSourceManager.getCurrentDataSourceName());
        
        logger.info("数据源重置功能测试通过");
    }

    /**
     * 测试获取所有数据源名称功能
     */
    @Test
    void testGetAllDataSourceNames() {
        logger.info("开始测试: 获取所有数据源名称功能");
        
        Set<String> dataSourceNames = dataSourceManager.getAllDataSourceNames();
        assertEquals(2, dataSourceNames.size());
        assertTrue(dataSourceNames.contains("master"));
        assertTrue(dataSourceNames.contains("slave"));
        
        // 移除一个数据源后
        dataSourceManager.unregisterDataSource("slave");
        dataSourceNames = dataSourceManager.getAllDataSourceNames();
        assertEquals(1, dataSourceNames.size());
        assertTrue(dataSourceNames.contains("master"));
        
        logger.info("获取所有数据源名称功能测试通过");
    }

    /**
     * 测试数据源健康检查功能
     */
    @Test
    void testIsDataSourceHealthy() throws SQLException {
        logger.info("开始测试: 数据源健康检查功能");
        
        // 测试健康的数据源
        assertTrue(dataSourceManager.isDataSourceHealthy("master"));
        
        // 测试不健康的数据源
        when(masterConnection.isValid(anyInt())).thenReturn(false);
        assertFalse(dataSourceManager.isDataSourceHealthy("master"));
        
        // 模拟连接异常
        when(masterDataSource.getConnection()).thenThrow(new SQLException("Connection error"));
        assertFalse(dataSourceManager.isDataSourceHealthy("master"));
        
        // 测试不存在的数据源
        assertFalse(dataSourceManager.isDataSourceHealthy("nonexistent"));
        
        logger.info("数据源健康检查功能测试通过");
    }

    /**
     * 测试执行操作方法（有返回值）
     */
    @Test
    void testExecuteWithDataSource() {
        logger.info("开始测试: 执行操作方法（有返回值）");
        
        // 测试在master上执行操作
        String result = dataSourceManager.executeWithDataSource("master", () -> {
            assertEquals("master", dataSourceManager.getCurrentDataSourceName());
            return "success"; // 返回模拟结果
        });
        
        assertEquals("success", result);
        
        // 测试在不存在的数据源上执行（严格模式）
        dataSourceManager.setStrictMode(true);
        assertThrows(IllegalArgumentException.class, () -> {
            dataSourceManager.executeWithDataSource("nonexistent", () -> "should not execute");
        });
        
        // 测试在不存在的数据源上执行（非严格模式）
        dataSourceManager.setStrictMode(false);
        String nonexistentResult = dataSourceManager.executeWithDataSource("nonexistent", () -> "should execute");
        assertEquals("should execute", nonexistentResult);
        
        logger.info("执行操作方法（有返回值）测试通过");
    }

    /**
     * 测试执行操作方法（无返回值）
     */
    @Test
    void testExecuteWithDataSourceRunnable() {
        logger.info("开始测试: 执行操作方法（无返回值）");
        
        // 模拟计数器来验证方法是否执行
        final boolean[] executed = {false};
        
        // 测试在slave上执行操作
        dataSourceManager.executeWithDataSource("slave", () -> {
            assertEquals("slave", dataSourceManager.getCurrentDataSourceName());
            executed[0] = true;
        });
        
        assertTrue(executed[0]);
        
        // 测试执行后是否恢复原数据源
        dataSourceManager.switchDataSource("master");
        
        dataSourceManager.executeWithDataSource("slave", () -> {
            // 这里不做任何操作
        });
        
        assertEquals("master", dataSourceManager.getCurrentDataSourceName());
        
        logger.info("执行操作方法（无返回值）测试通过");
    }

    /**
     * 测试最大数据源数量限制
     */
    @Test
    void testMaxDataSourceCount() {
        logger.info("开始测试: 最大数据源数量限制");
        
        // 设置较小的最大数量限制
        dataSourceManager.setMaxDataSourceCount(3);
        
        // 添加一个额外的数据源，达到限制
        DataSource testDataSource = mock(DataSource.class);
        dataSourceManager.registerDataSource("test1", testDataSource);
        
        // 尝试添加超出限制的数据源
        assertThrows(IllegalStateException.class, () -> {
            dataSourceManager.registerDataSource("test2", mock(DataSource.class));
        });
        
        logger.info("最大数据源数量限制测试通过");
    }

    /**
     * 测试并发安全性
     */
    @Test
    void testConcurrentOperations() throws InterruptedException {
        logger.info("开始测试: 并发安全性");
        
        // 创建多个线程并发切换和使用数据源
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    String dataSourceName = threadId % 2 == 0 ? "master" : "slave";
                    
                    dataSourceManager.executeWithDataSource(dataSourceName, () -> {
                        // 模拟一些操作
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("线程执行被中断", e);
                        }
                        return null;
                    });
                } catch (Exception e) {
                    fail("并发操作失败: " + e.getMessage());
                }
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("线程等待被中断: " + e.getMessage());
            }
        }
        
        // 验证没有数据丢失
        Set<String> dataSourceNames = dataSourceManager.getAllDataSourceNames();
        assertEquals(2, dataSourceNames.size());
        
        logger.info("并发安全性测试通过");
    }
}
