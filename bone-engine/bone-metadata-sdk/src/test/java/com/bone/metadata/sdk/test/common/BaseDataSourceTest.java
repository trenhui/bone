package com.bone.metadata.sdk.test.common;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

/**
 * 数据源测试基类
 * <p>提供所有数据源相关测试共用的基础功能和标准化的测试隔离机制</p>
 * <p>确保所有测试在干净、一致的环境中运行，避免测试间相互影响</p>
 */
@TestMethodOrder(OrderAnnotation.class)
public abstract class BaseDataSourceTest {

    /**
     * 测试前置准备
     * <p>确保测试隔离性：清理数据源上下文，为每个测试创建干净的环境</p>
     */
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试环境干净
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试后置清理
     * <p>确保测试隔离性：清理数据源上下文，防止资源泄漏和测试间相互影响</p>
     */
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文，防止资源泄漏
        DataSourceContextHolder.clearAll();
    }
}
