package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.test.common.BaseDataSourceTest;
import com.bone.metadata.sdk.test.common.SimpleAssertions;
import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * DynamicDataSource测试类
 */
public class DynamicDataSourceTest extends BaseDataSourceTest {
    

    
    /**
     * 模拟数据源类
     */
    private static class MockDataSource implements DataSource {
        private final String name;
        private final boolean healthy;
        
        public MockDataSource(String name) {
            this(name, true);
        }
        
        public MockDataSource(String name, boolean healthy) {
            this.name = name;
            this.healthy = healthy;
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            if (!healthy) {
                throw new SQLException("数据源" + name + "不健康");
            }
            System.out.println("从" + name + "获取连接");
            return new MockConnection(name);
        }
        
        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("不支持unwrap操作");
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
        
        @Override
        public java.io.PrintWriter getLogWriter() throws SQLException {
            return null;
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) throws SQLException {
            // 不实现
        }
        
        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            // 不实现
        }
        
        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }
    }
    
    /**
     * 模拟连接类
     */
    private static class MockConnection implements Connection {
        private final String dataSourceName;
        private boolean closed = false;
        
        public MockConnection(String dataSourceName) {
            this.dataSourceName = dataSourceName;
        }
        
        public String getDataSourceName() {
            return dataSourceName;
        }
        
        @Override
        public void close() throws SQLException {
            this.closed = true;
            System.out.println("关闭" + dataSourceName + "的连接");
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return closed;
        }
        
        // 其他方法都返回默认值或抛出不支持的异常
        @Override
        public Statement createStatement() throws SQLException { return null; }
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("不支持"); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException { return null; }
        @Override
        public CallableStatement prepareCall(String sql) throws SQLException { return null; }
        @Override
        public String nativeSQL(String sql) throws SQLException { return null; }
        @Override
        public void setAutoCommit(boolean autoCommit) throws SQLException {}
        @Override
        public boolean getAutoCommit() throws SQLException { return true; }
        @Override
        public void commit() throws SQLException {}
        @Override
        public void rollback() throws SQLException {}
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException { return null; }
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return null; }
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException { return null; }
        @Override
        public int getTransactionIsolation() throws SQLException { return 0; }
        @Override
        public void setTransactionIsolation(int level) throws SQLException {}
        @Override
        public SQLWarning getWarnings() throws SQLException { return null; }
        @Override
        public void clearWarnings() throws SQLException {}
        @Override
        public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return null; }
        @Override
        public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return null; }
        @Override
        public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException { return null; }
        @Override
        public DatabaseMetaData getMetaData() throws SQLException { return null; }
        @Override
        public void setReadOnly(boolean readOnly) throws SQLException {}
        @Override
        public boolean isReadOnly() throws SQLException { return false; }
        @Override
        public void setCatalog(String catalog) throws SQLException {}
        @Override
        public String getCatalog() throws SQLException { return null; }
        @Override
        public void setSchema(String schema) throws SQLException {}
        @Override
        public String getSchema() throws SQLException { return null; }
        @Override
        public void abort(java.util.concurrent.Executor executor) throws SQLException {}
        @Override
        public void setNetworkTimeout(java.util.concurrent.Executor executor, int milliseconds) throws SQLException {}
        @Override
        public int getNetworkTimeout() throws SQLException { return 0; }
        @Override
        public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException { return null; }
        @Override
        public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException { return null; }
        @Override
        public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException { return null; }
        
        // 新增方法以满足Connection接口的实现要求
        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException {
            throw new SQLException("不支持createStruct操作");
        }
        
        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException {
            throw new SQLException("不支持createArrayOf操作");
        }
        
        @Override
        public java.sql.NClob createNClob() throws SQLException {
            throw new SQLException("不支持createNClob操作");
        }
        
        @Override
        public java.sql.Blob createBlob() throws SQLException {
            throw new SQLException("不支持createBlob操作");
        }
        
        @Override
        public java.sql.Clob createClob() throws SQLException {
            throw new SQLException("不支持createClob操作");
        }
        
        @Override
        public java.sql.SQLXML createSQLXML() throws SQLException {
            throw new SQLException("不支持createSQLXML操作");
        }
        
        @Override
        public boolean isValid(int timeout) throws SQLException {
            return !closed;
        }
        
        @Override
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException {
            return new java.util.HashMap<>();
        }
        
        @Override
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {}
        
        @Override
        public void setHoldability(int holdability) throws SQLException {}
        
        @Override
        public int getHoldability() throws SQLException {
            return 0;
        }
        
        @Override
        public java.sql.Savepoint setSavepoint() throws SQLException {
            throw new SQLException("不支持事务保存点");
        }
        
        @Override
        public java.sql.Savepoint setSavepoint(String name) throws SQLException {
            throw new SQLException("不支持事务保存点");
        }
        
        @Override
        public void rollback(java.sql.Savepoint savepoint) throws SQLException {
            throw new SQLException("不支持事务保存点");
        }
        
        @Override
        public void releaseSavepoint(java.sql.Savepoint savepoint) throws SQLException {
            throw new SQLException("不支持事务保存点");
        }
        
        // 添加客户端信息相关的Connection接口方法
        @Override
        public java.util.Properties getClientInfo() throws SQLException {
            return new java.util.Properties();
        }
        
        @Override
        public String getClientInfo(String name) throws SQLException {
            return null;
        }
        
        @Override
        public void setClientInfo(java.util.Properties properties) throws java.sql.SQLClientInfoException {}
        
        @Override
        public void setClientInfo(String name, String value) throws java.sql.SQLClientInfoException {}
    }
    
    private DynamicDataSource dynamicDataSource;
    private DataSource masterDataSource;
    private DataSource slaveDataSource;
    private DataSource testDataSource;
    
    /**
     * 测试前准备
     */
    public void setUp() {
        // 创建测试数据源
        masterDataSource = new MockDataSource("master");
        slaveDataSource = new MockDataSource("slave");
        testDataSource = new MockDataSource("test");
        
        // 初始化动态数据源
        dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);
        targetDataSources.put("test", testDataSource);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
    }
    
    /**
     * 测试使用默认数据源
     */
    public void testDefaultDataSource() throws SQLException {
        setUp();
        
        // 清理上下文，确保使用默认数据源
        DataSourceContextHolder.clearAll();
        
        Connection connection = dynamicDataSource.getConnection();
        try {
            SimpleAssertions.assertNotNull(connection, "获取连接不应为null");
            SimpleAssertions.assertTrue(connection instanceof MockConnection, "连接应为MockConnection类型");
            SimpleAssertions.assertEquals("master", ((MockConnection) connection).getDataSourceName(), 
                    "未指定数据源时应使用默认数据源");
        } finally {
            connection.close();
        }
    }
    
    /**
     * 测试切换到master数据源
     */
    public void testSwitchToMasterDataSource() throws SQLException {
        setUp();
        
        try {
            // 设置上下文为master
            DataSourceContextHolder.setDataSource("master");
            
            Connection connection = dynamicDataSource.getConnection();
            try {
                SimpleAssertions.assertEquals("master", ((MockConnection) connection).getDataSourceName(), 
                        "应切换到指定的master数据源");
            } finally {
                connection.close();
            }
        } finally {
            DataSourceContextHolder.clearAll();
        }
    }
    
    /**
     * 测试切换到slave数据源
     */
    public void testSwitchToSlaveDataSource() throws SQLException {
        setUp();
        
        try {
            // 设置上下文为slave
            DataSourceContextHolder.setDataSource("slave");
            
            Connection connection = dynamicDataSource.getConnection();
            try {
                SimpleAssertions.assertEquals("slave", ((MockConnection) connection).getDataSourceName(), 
                        "应切换到指定的slave数据源");
            } finally {
                connection.close();
            }
        } finally {
            DataSourceContextHolder.clearAll();
        }
    }
    
    /**
     * 测试切换到不存在的数据源
     */
    public void testSwitchToNonexistentDataSource() throws SQLException {
        setUp();
        
        try {
            // 设置上下文为不存在的数据源
            DataSourceContextHolder.setDataSource("nonexistent");
            
            Connection connection = dynamicDataSource.getConnection();
            try {
                // 当指定的数据源不存在时，应使用默认数据源
                SimpleAssertions.assertEquals("master", ((MockConnection) connection).getDataSourceName(), 
                        "指定不存在的数据源时应使用默认数据源");
            } finally {
                connection.close();
            }
        } finally {
            DataSourceContextHolder.clearAll();
        }
    }
    
    /**
     * 运行所有测试
     */
    public static void main(String[] args) {
        System.out.println("开始运行DynamicDataSourceTest...");
        DynamicDataSourceTest test = new DynamicDataSourceTest();
        
        try {
            // 使用BaseDataSourceTest的runTest方法运行每个测试
            System.out.println("\n===== 测试默认数据源 =====");
            test.runTest(() -> {
                try {
                    test.testDefaultDataSource();
                } catch (SQLException e) {
                    throw new RuntimeException("SQL异常", e);
                }
            });
            
            System.out.println("\n===== 测试切换到master数据源 =====");
            test.runTest(() -> {
                try {
                    test.testSwitchToMasterDataSource();
                } catch (SQLException e) {
                    throw new RuntimeException("SQL异常", e);
                }
            });
            
            System.out.println("\n===== 测试切换到slave数据源 =====");
            test.runTest(() -> {
                try {
                    test.testSwitchToSlaveDataSource();
                } catch (SQLException e) {
                    throw new RuntimeException("SQL异常", e);
                }
            });
            
            System.out.println("\n===== 测试切换到不存在的数据源 =====");
            test.runTest(() -> {
                try {
                    test.testSwitchToNonexistentDataSource();
                } catch (SQLException e) {
                    throw new RuntimeException("SQL异常", e);
                }
            });
            
            System.out.println("\n所有测试通过！");
        } catch (Exception e) {
            System.err.println("测试执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}