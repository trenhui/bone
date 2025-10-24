package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.interceptor.DataSourceAnnotationInterceptor;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import com.bone.metadata.sdk.test.service.TestUserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.*;
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
 * @DS注解功能测试类
 * <p>验证@DS注解是否能正确路由到指定数据源，测试主从分离、读写分离场景</p>
 * <p>确保数据源上下文在各种情况下正确清理，避免资源泄漏</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DSAnnotationTest {
    
    @Autowired
    private TestUserService userService;
    
    @Autowired
    @Qualifier("masterJdbcTemplate")
    private JdbcTemplate masterJdbcTemplate;
    
    @Autowired
    @Qualifier("slaveJdbcTemplate")
    private JdbcTemplate slaveJdbcTemplate;
    
    /**
     * 测试前置准备
     * <p>确保测试隔离性：清理数据源上下文，清理测试数据，初始化基础测试数据</p>
     */
    @BeforeEach
    public void setUp() {
        // 清理数据源上下文，确保测试隔离
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
     * 测试主数据源写操作 - @DS("master")注解效果
     * <p>验证：</p>
     * <ul>
     *   <li>数据正确写入主数据源</li>
     *   <li>数据不出现在从数据源（模拟主从不复制场景）</li>
     *   <li>操作完成后数据源上下文正确清理</li>
     * </ul>
     */
    @Test
    @Order(1)
    public void shouldWriteDataToMasterDataSource_whenMethodAnnotatedWithMaster() {
        // 调用使用@DS("master")注解的方法
        final Long testId = 201L;
        final String testName = "test_master_user";
        userService.createUser(testId, testName);
        
        // 验证数据只存在于主库中
        String masterResult = masterJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = ?", String.class, testId);
        assertEquals(testName, masterResult, "数据未正确写入主数据源");
        
        // 验证从库中没有这条数据（因为我们没有模拟主从同步）
        assertThrows(Exception.class, () -> {
            slaveJdbcTemplate.queryForObject("SELECT name FROM user WHERE id = ?", String.class, testId);
        }, "从数据源不应包含主库写入的数据（主从不复制场景）");
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "操作完成后数据源上下文未清理");
    }
    
    /**
     * 测试从数据源读操作 - @DS("slave")注解效果
     * <p>验证：</p>
     * <ul>
     *   <li>能够正确从从数据源读取数据</li>
     *   <li>操作完成后数据源上下文正确清理</li>
     * </ul>
     */
    @Test
    @Order(2)
    public void shouldReadDataFromSlaveDataSource_whenMethodAnnotatedWithSlave() {
        // 调用使用@DS("slave")注解的方法
        String name = userService.getUserNameById(200L);
        
        // 验证读取的是从库的数据
        assertEquals("initial_user_slave", name, "未正确从从数据源读取数据");
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "操作完成后数据源上下文未清理");
    }
    
    /**
     * 测试注解拦截器的AOP代理效果（模拟测试）
     * <p>验证：</p>
     * <ul>
     *   <li>代理对象创建正确</li>
     *   <li>代理对象与原始对象不是同一实例</li>
     *   <li>代理对象保持类型一致性</li>
     * </ul>
     */
    @Test
    @Order(3)
    public void shouldCreateValidProxyObject_whenUsingAopInterceptor() {
        // 创建一个模拟的测试服务
        TestUserService mockService = new TestUserService();
        
        // 配置AOP代理工厂
        AspectJProxyFactory factory = new AspectJProxyFactory(mockService);
        factory.addAspect(DataSourceAnnotationInterceptor.class);
        
        // 创建代理对象
        TestUserService proxiedService = factory.getProxy();
        
        // 验证代理对象不是原始对象
        assertNotSame(mockService, proxiedService, "代理对象应该不同于原始对象");
        
        // 代理对象应该是TestUserService类型
        assertTrue(proxiedService instanceof TestUserService, "代理对象应该保持原始类型");
    }
    
    /**
     * 测试数据源更新操作
     * <p>验证：</p>
     * <ul>
     *   <li>主数据源数据正确更新</li>
     *   <li>从数据源数据未更新（模拟主从不复制场景）</li>
     *   <li>操作完成后数据源上下文正确清理</li>
     * </ul>
     */
    @Test
    @Order(4)
    public void shouldUpdateMasterDataOnly_whenUpdatingUserInformation() {
        // 更新用户名称
        final String newName = "updated_master_user";
        userService.updateUserName(200L, newName);
        
        // 验证主库数据已更新
        String masterResult = masterJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = 200", String.class);
        assertEquals(newName, masterResult, "主数据源数据未正确更新");
        
        // 验证从库数据未更新（因为我们没有模拟主从同步）
        String slaveResult = slaveJdbcTemplate.queryForObject(
                "SELECT name FROM user WHERE id = 200", String.class);
        assertEquals("initial_user_slave", slaveResult, "从数据源数据不应被更新（主从不复制场景）");
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "操作完成后数据源上下文未清理");
    }
    
    /**
     * 测试自定义数据源注解
     * <p>验证：</p>
     * <ul>
     *   <li>自定义数据源方法可以正常执行</li>
     *   <li>操作完成后数据源上下文正确清理</li>
     * </ul>
     */
    @Test
    @Order(5)
    public void shouldExecuteCustomDataSourceMethodSuccessfully() {
        // 由于tenantA数据源在测试环境中可能没有实际连接，这里我们主要验证方法可以正常执行
        String result = userService.getTenantInfo();
        assertEquals("tenantA_data", result, "自定义数据源方法执行结果不符合预期");
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "操作完成后数据源上下文未清理");
    }
    
    /**
     * 测试方法级别的数据源优先级
     * <p>验证：</p>
     * <ul>
     *   <li>方法级注解覆盖类级注解</li>
     *   <li>验证操作正确执行</li>
     *   <li>操作完成后数据源上下文正确清理</li>
     * </ul>
     */
    @Test
    @Order(6)
    public void shouldUseMethodLevelAnnotationOverClassLevel() {
        // 测试验证方法（使用@DS("master")）
        boolean verified = userService.verifyDataSync(200L);
        assertTrue(verified, "数据同步验证失败");
        
        // 验证数据源上下文已清理
        assertNull(DataSourceContextHolder.getCurrentLookupKey(), "操作完成后数据源上下文未清理");
    }
}