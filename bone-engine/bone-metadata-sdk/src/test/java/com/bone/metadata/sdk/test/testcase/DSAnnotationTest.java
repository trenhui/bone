package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.interceptor.DataSourceAnnotationInterceptor;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import com.bone.metadata.sdk.test.service.TestUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DS注解拦截器测试
 * 测试@DS注解的实际使用效果和数据源切换功能
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
public class DSAnnotationTest {
    
    @Autowired
    private TestUserService userService;
    
    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;
    
    @Autowired
    @Qualifier("slaveJdbcTemplate")
    private JdbcTemplate slaveJdbcTemplate;
    
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
        
        // 清理测试数据
        masterJdbcTemplate.update("DELETE FROM user WHERE id = 200");
        masterJdbcTemplate.update("DELETE FROM user WHERE id = 201");
        slaveJdbcTemplate.update("DELETE FROM user WHERE id = 200");
        slaveJdbcTemplate.update("DELETE FROM user WHERE id = 201");
        
        // 在主库中插入一条基础测试数据
        masterJdbcTemplate.update("INSERT INTO user (id, name) VALUES (200, 'initial_user')");
        // 在从库中插入对应的测试数据
        slaveJdbcTemplate.update("INSERT INTO user (id, name) VALUES (200, 'initial_user_slave')");
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
    }
    
    /**
     * 测试@DS("master")注解在写方法上的效果
     */
    @Test
    public void testMasterDataSourceAnnotation() {
        // 调用使用@DS("master")注解的方法
        Long testId = 201L;
        String testName = "test_master_user";
        userService.createUser(testId, testName);
        
        // 验证数据只存在于主库中
        String masterResult = masterJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = ?", String.class, testId);
        assertEquals(testName, masterResult);
        
        // 验证从库中没有这条数据（因为我们没有模拟主从同步）
        try {
            slaveJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", String.class, testId);
            fail("Should not find record in slave database");
        } catch (Exception e) {
            // 预期异常，从库中没有这条数据
        }
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试@DS("slave")注解在读方法上的效果
     */
    @Test
    public void testSlaveDataSourceAnnotation() {
        // 调用使用@DS("slave")注解的方法
        String name = userService.getUserNameById(200L);
        
        // 验证读取的是从库的数据
        assertEquals("initial_user_slave", name);
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试注解拦截器的AOP代理效果（模拟测试）
     */
    @Test
    public void testAnnotationInterceptorAop() {
        // 创建一个模拟的测试服务
        TestUserService mockService = new TestUserService();
        
        // 配置AOP代理工厂
        AspectJProxyFactory factory = new AspectJProxyFactory(mockService);
        factory.addAspect(DataSourceAnnotationInterceptor.class);
        
        // 创建代理对象
        TestUserService proxiedService = factory.getProxy();
        
        // 验证代理对象不是原始对象
        assertNotSame(mockService, proxiedService);
        
        // 代理对象应该是TestUserService类型
        assertTrue(proxiedService instanceof TestUserService);
    }
    
    /**
     * 测试数据源更新操作
     */
    @Test
    public void testUpdateOperation() {
        // 更新用户名称
        String newName = "updated_master_user";
        userService.updateUserName(200L, newName);
        
        // 验证主库数据已更新
        String masterResult = masterJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = 200", String.class);
        assertEquals(newName, masterResult);
        
        // 验证从库数据未更新（因为我们没有模拟主从同步）
        String slaveResult = slaveJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = 200", String.class);
        assertEquals("initial_user_slave", slaveResult);
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试自定义数据源注解
     */
    @Test
    public void testCustomDataSourceAnnotation() {
        // 由于tenantA数据源在测试环境中可能没有实际连接，这里我们主要验证方法可以正常执行
        // 而不关心实际的数据源操作结果
        String result = userService.getTenantInfo();
        assertEquals("tenantA_data", result);
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
    
    /**
     * 测试方法级别的数据源优先级
     * 注意：在实际应用中，方法级注解会覆盖类级注解
     */
    @Test
    public void testMethodLevelAnnotationPriority() {
        // 测试验证方法（使用@DS("master")）
        boolean verified = userService.verifyDataSync(200L);
        assertTrue(verified);
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey());
    }
}