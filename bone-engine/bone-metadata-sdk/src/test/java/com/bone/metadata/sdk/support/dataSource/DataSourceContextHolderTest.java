package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.test.common.BaseDataSourceTest;
import com.bone.metadata.sdk.test.common.SimpleAssertions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DataSourceContextHolder测试类
 * <p>全面验证数据源上下文管理的核心功能：</p>
 * <ul>
 *   <li>基本的设置、获取、清理功能</li>
 *   <li>上下文栈的嵌套管理</li>
 *   <li>安全执行方法（异常处理和资源清理）</li>
 *   <li>线程安全性</li>
 * </ul>
 */
public class DataSourceContextHolderTest extends BaseDataSourceTest {
    
    /**
     * 测试设置和获取数据源
     * <p>验证数据源标识能被正确设置和读取</p>
     */
    public void testSetAndGetDataSource() {
        // 准备测试数据
        final String expectedDataSource = "master";
        
        // 执行操作
        DataSourceContextHolder.setDataSource(expectedDataSource);
        
        // 验证结果
        SimpleAssertions.assertEquals(expectedDataSource, DataSourceContextHolder.getCurrentLookupKey(), 
                "获取的数据源应该与设置的数据源一致");
        
        // 清理
        DataSourceContextHolder.clearDataSource();
    }
    
    /**
     * 测试清理单个数据源
     */
    public void testClearDataSource() {
        // 设置数据源
        final String dataSource = "master";
        DataSourceContextHolder.setDataSource(dataSource);
        
        // 清理数据源
        String clearedDataSource = DataSourceContextHolder.clearDataSource();
        
        // 验证
        SimpleAssertions.assertEquals(dataSource, clearedDataSource, "清理操作应返回被清理的数据源");
        SimpleAssertions.assertNull(DataSourceContextHolder.getCurrentLookupKey(), "清理后当前数据源应为null");
    }
    
    /**
     * 测试嵌套上下文栈
     */
    public void testNestedContextStack() {
        // 测试多层嵌套
        DataSourceContextHolder.setDataSource("master");
        SimpleAssertions.assertEquals("master", DataSourceContextHolder.getCurrentLookupKey(), "第一层应该是master");
        SimpleAssertions.assertEquals(1, DataSourceContextHolder.getContextStackDepth(), "栈深度应为1");
        
        DataSourceContextHolder.setDataSource("slave1");
        SimpleAssertions.assertEquals("slave1", DataSourceContextHolder.getCurrentLookupKey(), "第二层应该是slave1");
        SimpleAssertions.assertEquals(2, DataSourceContextHolder.getContextStackDepth(), "栈深度应为2");
        
        DataSourceContextHolder.setDataSource("slave2");
        SimpleAssertions.assertEquals("slave2", DataSourceContextHolder.getCurrentLookupKey(), "第三层应该是slave2");
        SimpleAssertions.assertEquals(3, DataSourceContextHolder.getContextStackDepth(), "栈深度应为3");
        
        // 测试清理顺序
        String removed1 = DataSourceContextHolder.clearDataSource();
        SimpleAssertions.assertEquals("slave2", removed1, "第一次清理应返回slave2");
        SimpleAssertions.assertEquals("slave1", DataSourceContextHolder.getCurrentLookupKey(), "清理一层后应为slave1");
        SimpleAssertions.assertEquals(2, DataSourceContextHolder.getContextStackDepth(), "栈深度应为2");
        
        String removed2 = DataSourceContextHolder.clearDataSource();
        SimpleAssertions.assertEquals("slave1", removed2, "第二次清理应返回slave1");
        SimpleAssertions.assertEquals("master", DataSourceContextHolder.getCurrentLookupKey(), "清理两层后应为master");
        SimpleAssertions.assertEquals(1, DataSourceContextHolder.getContextStackDepth(), "栈深度应为1");
        
        // 清理最后一层
        String removed3 = DataSourceContextHolder.clearDataSource();
        SimpleAssertions.assertEquals("master", removed3, "第三次清理应返回master");
        SimpleAssertions.assertNull(DataSourceContextHolder.getCurrentLookupKey(), "所有层清理后应为null");
        SimpleAssertions.assertEquals(0, DataSourceContextHolder.getContextStackDepth(), "栈深度应为0");
    }
    
    /**
     * 测试安全执行方法
     */
    public void testExecuteInDataSource() {
        // 测试无返回值的执行方法
        DataSourceContextHolder.executeInDataSource("master", () -> {
            SimpleAssertions.assertEquals("master", DataSourceContextHolder.getCurrentLookupKey(), 
                    "executeInDataSource中应设置正确的数据源");
            
            // 测试嵌套执行
            DataSourceContextHolder.executeInDataSource("slave", () -> {
                SimpleAssertions.assertEquals("slave", DataSourceContextHolder.getCurrentLookupKey(), 
                        "嵌套executeInDataSource中应设置正确的数据源");
            });
            
            // 验证嵌套执行后恢复到原始数据源
            SimpleAssertions.assertEquals("master", DataSourceContextHolder.getCurrentLookupKey(), 
                    "嵌套执行后应恢复到原始数据源");
        });
        
        // 验证执行后清除了数据源
        SimpleAssertions.assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "执行方法后应清除数据源上下文");
        
        // 测试有返回值的执行方法
        String result = DataSourceContextHolder.executeInDataSourceWithResult("master", () -> {
            SimpleAssertions.assertEquals("master", DataSourceContextHolder.getCurrentLookupKey(), 
                    "executeInDataSourceWithResult中应设置正确的数据源");
            return "success:" + DataSourceContextHolder.getCurrentLookupKey();
        });
        
        SimpleAssertions.assertEquals("success:master", result, "应返回正确的执行结果");
        SimpleAssertions.assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                "执行方法后应清除数据源上下文");
        
        // 测试异常处理
        try {
            DataSourceContextHolder.executeInDataSource("master", () -> {
                throw new RuntimeException("测试异常处理");
            });
            SimpleAssertions.fail("应该抛出RuntimeException");
        } catch (RuntimeException e) {
            SimpleAssertions.assertEquals("测试异常处理", e.getMessage(), "应正确传播异常信息");
            // 验证异常后清除了数据源
            SimpleAssertions.assertNull(DataSourceContextHolder.getCurrentLookupKey(), 
                    "异常后应清除数据源上下文");
        }
    }
    
    /**
     * 测试线程安全性
     */
    public void testThreadSafety() throws InterruptedException {
        final int threadCount = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        final java.util.List<String> results = java.util.Collections.synchronizedList(new ArrayList<>());
        
        try {
            // 创建多个线程并发操作数据源上下文
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executorService.submit(() -> {
                    try {
                        String dataSource = "ds_" + threadId;
                        DataSourceContextHolder.setDataSource(dataSource);
                        
                        // 模拟业务操作
                        Thread.sleep(10);
                        
                        // 验证每个线程看到的是自己的数据源
                        String currentDataSource = DataSourceContextHolder.getCurrentLookupKey();
                        results.add(currentDataSource);
                        SimpleAssertions.assertEquals(dataSource, currentDataSource, 
                                "线程" + threadId + "应该看到自己设置的数据源");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        DataSourceContextHolder.clearDataSource();
                        latch.countDown();
                    }
                });
            }
            
            latch.await();
            // 等待所有线程完成
            Thread.sleep(50); // 给一些额外时间确保所有结果都已添加
            SimpleAssertions.assertEquals(threadCount, results.size(), "应收集到所有线程的结果");
            
        } finally {
            executorService.shutdown();
        }
    }
    
    /**
     * 运行所有测试
     */
    public static void main(String[] args) {
        System.out.println("开始运行DataSourceContextHolderTest...");
        DataSourceContextHolderTest test = new DataSourceContextHolderTest();
        
        try {
            // 运行单个测试
            System.out.println("\n===== 测试基本设置和获取 =====");
            test.runTest(test::testSetAndGetDataSource);
            
            System.out.println("\n===== 测试清理功能 =====");
            test.runTest(test::testClearDataSource);
            
            System.out.println("\n===== 测试嵌套上下文栈 =====");
            test.runTest(test::testNestedContextStack);
            
            System.out.println("\n===== 测试安全执行方法 =====");
            test.runTest(test::testExecuteInDataSource);
            
            System.out.println("\n===== 测试线程安全性 =====");
            test.runTest(() -> {
                try {
                    test.testThreadSafety();
                } catch (InterruptedException e) {
                    throw new RuntimeException("线程中断", e);
                }
            });
            
            System.out.println("\n所有测试通过！");
        } catch (Exception e) {
            System.err.println("测试执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}