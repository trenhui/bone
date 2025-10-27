package com.bone.metadata.sdk.test.common;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;

/**
 * 数据源测试基类
 * 提供数据源上下文管理和测试执行框架
 */
public abstract class BaseDataSourceTest {
    
    /**
     * 测试前置准备
     * 确保测试隔离性：清理数据源上下文，为每个测试创建干净的环境
     */
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
        System.out.println("测试环境准备完成");
    }
    
    /**
     * 测试后置清理
     * 确保测试隔离性：清理数据源上下文，防止资源泄漏和测试间相互影响
     */
    public void tearDown() {
        // 清理数据源上下文，防止资源泄漏
        DataSourceContextHolder.clearAll();
        System.out.println("测试环境清理完成");
    }
    
    /**
     * 执行测试方法
     * 这是一个简化的测试执行方法，用于在没有JUnit的情况下运行测试
     */
    public void runTest(Runnable testMethod) {
        try {
            // 前置准备
            setUp();
            
            // 执行测试方法
            testMethod.run();
            
            // 后置清理
            tearDown();
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 切换数据源
     * 为测试提供数据源切换功能
     */
    protected void switchDataSource(String dataSourceName) {
        DataSourceContextHolder.setDataSource(dataSourceName);
    }
    
    /**
     * 获取当前数据源
     * 为测试提供数据源信息查询功能
     */
    protected String getCurrentDataSource() {
        String dataSource = DataSourceContextHolder.getCurrentLookupKey();
        return dataSource != null ? dataSource : "default";
    }
    
    /**
     * 获取当前数据源状态信息
     */
    public String getCurrentDataSourceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("当前线程数据源: ").append(DataSourceContextHolder.getCurrentLookupKey()).append("\n");
        info.append("上下文栈深度: ").append(DataSourceContextHolder.getContextStackDepth());
        return info.toString();
    }
    
    /**
     * 在指定数据源上下文中执行操作
     */
    protected void executeInDataSource(String dataSource, Runnable action) {
        DataSourceContextHolder.executeInDataSource(dataSource, action);
    }
    
    /**
     * 在指定数据源上下文中执行操作并返回结果
     */
    protected <T> T executeInDataSourceWithResult(String dataSource, java.util.function.Supplier<T> supplier) {
        return DataSourceContextHolder.executeInDataSourceWithResult(dataSource, supplier);
    }
}