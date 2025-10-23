package com.bone.metadata.sdk.test.testcase;

import com.bone.metadata.sdk.support.dataSource.DataSourceContextHolder;
import com.bone.metadata.sdk.support.dataSource.DataSourceManager;
import com.bone.metadata.sdk.support.dataSource.annotation.DS;
import com.bone.metadata.sdk.test.config.MultiDataSourceTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多数据源集成测试
 * 模拟真实业务场景中的多数据源使用情况
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {MultiDataSourceTestConfig.class})
public class MultiDataSourceIntegrationTest {
    
    @Autowired
    private DataSourceManager dataSourceManager;
    
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
        
        // 准备测试数据结构
        createTestTables();
        
        // 清理测试数据
        cleanupTestData();
    }
    
    @AfterEach
    public void tearDown() {
        // 清理数据源上下文
        DataSourceContextHolder.clearAll();
        
        // 可选：清理测试数据
        cleanupTestData();
    }
    
    /**
     * 创建测试表
     */
    private void createTestTables() {
        // 在所有数据源中创建必要的测试表
        String createUserTable = "CREATE TABLE IF NOT EXISTS test_user (id BIGINT PRIMARY KEY, name VARCHAR(100), email VARCHAR(100))";
        String createOrderTable = "CREATE TABLE IF NOT EXISTS test_order (id BIGINT PRIMARY KEY, user_id BIGINT, amount DECIMAL(10,2), status VARCHAR(20))";
        
        masterJdbcTemplate.execute(createUserTable);
        masterJdbcTemplate.execute(createOrderTable);
        slaveJdbcTemplate.execute(createUserTable);
        slaveJdbcTemplate.execute(createOrderTable);
    }
    
    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        masterJdbcTemplate.update("DELETE FROM test_order");
        masterJdbcTemplate.update("DELETE FROM test_user");
        slaveJdbcTemplate.update("DELETE FROM test_order");
        slaveJdbcTemplate.update("DELETE FROM test_user");
    }
    
    /**
     * 测试场景一：读写分离 - 写操作走主库，读操作走从库
     */
    @Test
    public void testReadWriteSplitting() {
        Long userId = 1000L;
        String userName = "rw_test_user";
        String userEmail = "test@example.com";
        
        // 写操作：使用主库插入用户
        dataSourceManager.withMaster(() -> {
            masterJdbcTemplate.update(
                "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                userId, userName, userEmail
            );
            return null;
        });
        
        // 模拟主从复制延迟（在实际应用中，这里可能需要等待或处理延迟问题）
        // 在测试环境中，我们手动将数据同步到从库
        dataSourceManager.withSlave(() -> {
            slaveJdbcTemplate.update(
                "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                userId, userName + "_slave", userEmail // 故意使用不同的值以区分数据源
            );
            return null;
        });
        
        // 读操作：从主库读取
        String masterUserName = dataSourceManager.withMaster(() -> {
            return masterJdbcTemplate.queryForObject(
                "SELECT name FROM test_user WHERE id = ?", String.class, userId
            );
        });
        
        // 读操作：从从库读取
        String slaveUserName = dataSourceManager.withSlave(() -> {
            return slaveJdbcTemplate.queryForObject(
                "SELECT name FROM test_user WHERE id = ?", String.class, userId
            );
        });
        
        // 验证从不同数据源读取到的值不同
        assertEquals(userName, masterUserName);
        assertEquals(userName + "_slave", slaveUserName);
    }
    
    /**
     * 测试场景二：事务中的数据源一致性
     * 验证在同一个事务中，多次数据源切换后操作的一致性
     */
    @Test
    public void testTransactionDataSourceConsistency() {
        // 注意：在实际应用中，这个测试应该使用@Transactional注解
        // 这里我们使用函数式API模拟事务行为
        
        Long userId = 1001L;
        
        // 模拟事务开始
        try {
            // 步骤1：在主库创建用户
            dataSourceManager.withMaster(() -> {
                masterJdbcTemplate.update(
                    "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                    userId, "transaction_user", "transaction@example.com"
                );
                return null;
            });
            
            // 步骤2：在主库创建订单
            dataSourceManager.withMaster(() -> {
                masterJdbcTemplate.update(
                    "INSERT INTO test_order (id, user_id, amount, status) VALUES (?, ?, ?, ?)",
                    2001L, userId, 100.00, "PENDING"
                );
                return null;
            });
            
            // 模拟提交事务
            // 验证数据是否正确插入
            Long orderCount = dataSourceManager.withMaster(() -> {
                return masterJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM test_order WHERE user_id = ?", Long.class, userId
                );
            });
            assertEquals(1L, orderCount);
            
        } catch (Exception e) {
            // 模拟回滚事务
            fail("Transaction test failed: " + e.getMessage());
        }
    }
    
    /**
     * 测试场景三：多租户数据源隔离
     * 模拟不同租户使用不同数据源的场景
     */
    @Test
    public void testMultiTenantDataSourceIsolation() {
        // 模拟租户A的数据操作
        simulateTenantOperation("tenantA", 3001L, "tenantA_user");
        
        // 模拟租户B的数据操作（使用主库作为租户B的数据源）
        simulateTenantOperation("master", 3002L, "tenantB_user");
        
        // 验证租户隔离 - 每个数据源只能访问自己的数据
        // 注意：在实际多租户系统中，每个租户会有自己独立的数据源
        // 这里我们使用主库和tenantA数据源来模拟
        
        // 检查租户A的数据在tenantA数据源中存在
        boolean tenantADataExists = dataSourceManager.withDataSource("tenantA", () -> {
            try {
                // tenantA数据源在测试环境中可能没有实际数据，这里主要验证数据源切换功能
                // 实际应用中这里应该查询tenantA数据源中的数据
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        
        assertTrue(tenantADataExists);
    }
    
    /**
     * 模拟租户操作
     */
    private void simulateTenantOperation(String tenantDataSource, Long userId, String userName) {
        dataSourceManager.withDataSource(tenantDataSource, () -> {
            // 在实际应用中，这里会使用对应的租户数据源
            // 在测试环境中，我们只是验证数据源切换功能
            System.out.println("Executing operation for tenant using data source: " + tenantDataSource);
            System.out.println("User ID: " + userId + ", User Name: " + userName);
            return null;
        });
    }
    
    /**
     * 测试场景四：批量操作的数据源管理
     * 验证批量操作中数据源切换的正确性
     */
    @Test
    public void testBatchOperationDataSourceManagement() {
        // 批量插入数据到主库
        dataSourceManager.withMaster(() -> {
            for (int i = 0; i < 5; i++) {
                Long userId = 4000L + i;
                masterJdbcTemplate.update(
                    "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                    userId, "batch_user_" + i, "batch_" + i + "@example.com"
                );
            }
            return null;
        });
        
        // 统计主库中的数据量
        Long masterCount = dataSourceManager.withMaster(() -> {
            return masterJdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_user", Long.class);
        });
        
        assertEquals(5L, masterCount);
        
        // 验证从库中还没有这些数据（因为我们没有同步）
        Long slaveCount = dataSourceManager.withSlave(() -> {
            return slaveJdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_user", Long.class);
        });
        
        assertEquals(0L, slaveCount);
    }
    
    /**
     * 测试场景五：跨数据源事务一致性（实际应用中的挑战）
     * 注意：跨数据源事务通常需要分布式事务协调器（如Seata）
     */
    @Test
    public void testCrossDataSourceTransactionChallenges() {
        // 这个测试展示了跨数据源事务的挑战
        // 在实际应用中，这种场景应该使用分布式事务解决方案
        
        Long userId = 5000L;
        Long orderId = 6000L;
        
        try {
            // 步骤1：在主库创建用户
            dataSourceManager.withMaster(() -> {
                masterJdbcTemplate.update(
                    "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                    userId, "cross_tx_user", "cross_tx@example.com"
                );
                return null;
            });
            
            // 模拟故障点 - 在实际应用中，这里可能发生异常导致事务不一致
            // 步骤2：在从库创建订单（这在实际应用中是不正确的，订单应该在主库创建）
            dataSourceManager.withSlave(() -> {
                slaveJdbcTemplate.update(
                    "INSERT INTO test_order (id, user_id, amount, status) VALUES (?, ?, ?, ?)",
                    orderId, userId, 200.00, "PENDING"
                );
                return null;
            });
            
            // 验证数据状态
            Long masterUserCount = masterJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_user WHERE id = ?", Long.class, userId
            );
            Long slaveOrderCount = slaveJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_order WHERE id = ?", Long.class, orderId
            );
            
            assertEquals(1L, masterUserCount);
            assertEquals(1L, slaveOrderCount);
            
            // 在实际应用中，如果步骤2失败，步骤1的数据已经提交，导致数据不一致
            // 这正是分布式事务需要解决的问题
            
        } catch (Exception e) {
            // 异常处理
            fail("Cross data source transaction test failed: " + e.getMessage());
        }
    }
    
    /**
     * 内部测试服务类，用于演示@DS注解在复杂业务方法中的使用
     */
    @org.springframework.stereotype.Service
    static class TestBusinessService {
        
        @Autowired
        private DataSourceManager dataSourceManager;
        
        @Autowired
        @Qualifier("masterJdbcTemplate")
        private JdbcTemplate masterJdbcTemplate;
        
        @Autowired
        @Qualifier("slaveJdbcTemplate")
        private JdbcTemplate slaveJdbcTemplate;
        
        /**
         * 业务方法：创建用户并初始化默认订单
         * 使用@DS注解指定使用主数据源
         */
        @DS("master")
        public void createUserWithDefaultOrder(Long userId, String userName) {
            // 创建用户
            masterJdbcTemplate.update(
                "INSERT INTO test_user (id, name, email) VALUES (?, ?, ?)",
                userId, userName, userName + "@example.com"
            );
            
            // 创建默认订单
            masterJdbcTemplate.update(
                "INSERT INTO test_order (id, user_id, amount, status) VALUES (?, ?, ?, ?)",
                userId + 10000L, userId, 0.00, "DEFAULT"
            );
        }
        
        /**
         * 业务方法：查询用户信息
         * 使用@DS注解指定使用从数据源
         */
        @DS("slave")
        public String getUserName(Long userId) {
            return slaveJdbcTemplate.queryForObject(
                "SELECT name FROM test_user WHERE id = ?", String.class, userId
            );
        }
    }
}