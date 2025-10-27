package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.test.common.BaseDataSourceTest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.util.HashMap;
import java.util.Map;

/**
 * UserService测试类
 * 验证数据源切换功能
 */
public class UserServiceTest extends BaseDataSourceTest {
    
    /**
     * 简化的断言工具类
     */
    private static class SimpleAssertions {
        public static void assertEquals(Object expected, Object actual, String message) {
            boolean isEqual = (expected == null && actual == null) || 
                              (expected != null && expected.equals(actual));
            if (!isEqual) {
                throw new AssertionError(message + " [期望值: " + expected + ", 实际值: " + actual + "]");
            }
            System.out.println("断言通过: " + message);
        }
        
        public static void assertTrue(boolean condition, String message) {
            if (!condition) {
                throw new AssertionError(message);
            }
            System.out.println("断言通过: " + message);
        }
        
        public static void assertFalse(boolean condition, String message) {
            if (condition) {
                throw new AssertionError(message);
            }
            System.out.println("断言通过: " + message);
        }
        
        public static void assertNotNull(Object object, String message) {
            if (object == null) {
                throw new AssertionError(message);
            }
            System.out.println("断言通过: " + message);
        }
        
        public static void assertNull(Object object, String message) {
            if (object != null) {
                throw new AssertionError(message + " [实际值: " + object + "]");
            }
            System.out.println("断言通过: " + message);
        }
    }
    
    /**
     * 模拟数据源类
     */
    private static class MockDataSource implements DataSource {
        private final String name;
        private final Map<Long, String> userData = new HashMap<>();
        private String lastUsedSql = null;
        private Object[] lastUsedParams = null;
        
        public MockDataSource(String name) {
            this.name = name;
            // 初始化一些测试数据
            userData.put(1L, "Test User");
            userData.put(2L, "Admin User");
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            System.out.println("从" + name + "获取连接");
            return new MockConnection(this);
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
        public java.util.logging.Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
            throw new java.sql.SQLFeatureNotSupportedException();
        }
        
        public String getLastUsedSql() {
            return lastUsedSql;
        }
        
        public void setLastUsedSql(String lastUsedSql) {
            this.lastUsedSql = lastUsedSql;
        }
        
        public Object[] getLastUsedParams() {
            return lastUsedParams;
        }
        
        public void setLastUsedParams(Object[] lastUsedParams) {
            this.lastUsedParams = lastUsedParams;
        }
        
        public Map<Long, String> getUserData() {
            return userData;
        }
    }
    
    /**
     * 模拟连接类
     */
    private static class MockConnection implements Connection {
        private final MockDataSource dataSource;
        private boolean closed = false;
        
        @Override
        public java.sql.Array createArrayOf(String typeName, Object[] elements) throws SQLException { return null; }
        
        @Override
        public java.util.Properties getClientInfo() throws SQLException { return new java.util.Properties(); }
        
        @Override
        public String getClientInfo(String name) throws SQLException { return null; }
        
        @Override
        public void setClientInfo(java.util.Properties properties) throws SQLException {}
        
        @Override
        public void setClientInfo(String name, String value) throws SQLException {}
        
        public MockConnection(MockDataSource dataSource) {
            this.dataSource = dataSource;
        }
        
        @Override
        public void close() throws SQLException {
            this.closed = true;
            System.out.println("关闭连接");
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return closed;
        }
        
        @Override
        public PreparedStatement prepareStatement(String sql) throws SQLException {
            dataSource.setLastUsedSql(sql);
            return new MockPreparedStatement(dataSource, sql);
        }
        
        // 其他方法都返回默认值或抛出不支持的异常
        @Override
        public Statement createStatement() throws SQLException { return null; }
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("不支持"); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
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
        
        @Override
        public java.sql.Struct createStruct(String typeName, Object[] attributes) throws SQLException { return null; }
    }
    
    /**
     * 模拟PreparedStatement类
     */
    private static class MockPreparedStatement implements PreparedStatement {
        private final MockDataSource dataSource;
        private final String sql;
        private final Map<Integer, Object> params = new HashMap<>();
        private boolean executed = false;
        private boolean closed = false;
        
        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            return false;
        }
        
        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException { return false; }
        
        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException { return false; }
        
        @Override
        public boolean isClosed() throws SQLException { return closed; }
        
        @Override
        public boolean isPoolable() throws SQLException { return false; }
        
        @Override
        public void setPoolable(boolean poolable) throws SQLException {}
        
        public MockPreparedStatement(MockDataSource dataSource, String sql) {
            this.dataSource = dataSource;
            this.sql = sql;
        }
        
        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {
            params.put(parameterIndex, x);
        }
        
        @Override
        public void setString(int parameterIndex, String x) throws SQLException {
            params.put(parameterIndex, x);
        }
        
        @Override
        public ResultSet executeQuery() throws SQLException {
            executed = true;
            dataSource.setLastUsedParams(params.values().toArray());
            return new MockResultSet(dataSource, params);
        }
        
        @Override
        public int executeUpdate() throws SQLException {
            executed = true;
            dataSource.setLastUsedParams(params.values().toArray());
            
            if (sql.contains("INSERT")) {
                Long id = (Long) params.get(1);
                String name = (String) params.get(2);
                dataSource.getUserData().put(id, name);
                return 1;
            } else if (sql.contains("UPDATE")) {
                String name = (String) params.get(1);
                Long id = (Long) params.get(2);
                if (dataSource.getUserData().containsKey(id)) {
                    dataSource.getUserData().put(id, name);
                    return 1;
                }
                return 0;
            } else if (sql.contains("DELETE")) {
                Long id = (Long) params.get(1);
                if (dataSource.getUserData().remove(id) != null) {
                    return 1;
                }
                return 0;
            }
            return 0;
        }
        
        @Override
        public boolean execute() throws SQLException {
            executed = true;
            return false;
        }
        
        @Override
        public void close() throws SQLException {
            System.out.println("关闭PreparedStatement");
        }
        
        // 其他方法都返回默认值或抛出不支持的异常
        @Override
        public void cancel() throws SQLException {}
        @Override
        public void clearBatch() throws SQLException {}
        @Override
        public void clearParameters() throws SQLException {}
        @Override
        public void closeOnCompletion() throws SQLException {}
        @Override
        public int[] executeBatch() throws SQLException { return new int[0]; }
        @Override
        public ResultSetMetaData getMetaData() throws SQLException { return null; }
        @Override
        public ParameterMetaData getParameterMetaData() throws SQLException { return null; }

        @Override
        public boolean isCloseOnCompletion() throws SQLException { return false; }
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("不支持"); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
        @Override
        public void setArray(int parameterIndex, Array x) throws SQLException {}
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException {}
        @Override
        public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException {}
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException {}
        @Override
        public void setBlob(int parameterIndex, Blob x) throws SQLException {}
        @Override
        public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException {}
        @Override
        public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException {}
        @Override
        public void setBoolean(int parameterIndex, boolean x) throws SQLException {}
        @Override
        public void setByte(int parameterIndex, byte x) throws SQLException {}
        @Override
        public void setBytes(int parameterIndex, byte[] x) throws SQLException {}
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {}
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException {}
        @Override
        public void setClob(int parameterIndex, Clob x) throws SQLException {}
        @Override
        public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException {}
        @Override
        public void setDate(int parameterIndex, Date x) throws SQLException {}
        @Override
        public void setDate(int parameterIndex, Date x, java.util.Calendar cal) throws SQLException {}
        @Override
        public void setDouble(int parameterIndex, double x) throws SQLException {}
        @Override
        public void setFloat(int parameterIndex, float x) throws SQLException {}
        @Override
        public void setInt(int parameterIndex, int x) throws SQLException {}
        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {}
        @Override
        public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException {}
        @Override
        public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException {}
        @Override
        public void setNClob(int parameterIndex, NClob value) throws SQLException {}
        @Override
        public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException {}
        @Override
        public void setNString(int parameterIndex, String value) throws SQLException {}
        @Override
        public void setNull(int parameterIndex, int sqlType) throws SQLException {}
        @Override
        public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {}
        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException {}
        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {}
        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {}
        @Override
        public void setRef(int parameterIndex, Ref x) throws SQLException {}
        @Override
        public void setRowId(int parameterIndex, RowId x) throws SQLException {}
        @Override
        public void setShort(int parameterIndex, short x) throws SQLException {}
        @Override
        public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {}
        @Override
        public void setTime(int parameterIndex, Time x) throws SQLException {}
        @Override
        public void setTime(int parameterIndex, Time x, java.util.Calendar cal) throws SQLException {}
        @Override
        public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {}
        @Override
        public void setTimestamp(int parameterIndex, java.sql.Timestamp x, java.util.Calendar cal) throws SQLException {}
        
        @Override
        public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void setURL(int parameterIndex, java.net.URL x) throws SQLException {}
        @Override
        public void addBatch() throws SQLException {}
        @Override
        public ResultSet executeQuery(String sql) throws SQLException { return null; }
        @Override
        public int executeUpdate(String sql) throws SQLException { return 0; }
        @Override
        public boolean execute(String sql) throws SQLException { return false; }
        @Override
        public ResultSet getResultSet() throws SQLException { return null; }
        @Override
        public int getUpdateCount() throws SQLException { return 0; }
        @Override
        public boolean getMoreResults() throws SQLException { return false; }
        @Override
        public int getFetchDirection() throws SQLException { return 0; }
        @Override
        public void setFetchDirection(int direction) throws SQLException {}
        @Override
        public int getFetchSize() throws SQLException { return 0; }
        @Override
        public void setFetchSize(int rows) throws SQLException {}
        @Override
        public int getMaxFieldSize() throws SQLException { return 0; }
        @Override
        public void setMaxFieldSize(int max) throws SQLException {}
        @Override
        public int getMaxRows() throws SQLException { return 0; }
        @Override
        public void setMaxRows(int max) throws SQLException {}
        @Override
        public boolean getMoreResults(int current) throws SQLException { return false; }
        @Override
        public int getResultSetConcurrency() throws SQLException { return 0; }
        @Override
        public int getResultSetHoldability() throws SQLException { return 0; }
        @Override
        public int getResultSetType() throws SQLException { return 0; }
        @Override
        public int getUpdateCount() throws SQLException { return 0; }
        @Override
        public void setCursorName(String name) throws SQLException {}
        @Override
        public void setEscapeProcessing(boolean enable) throws SQLException {}
        @Override
        public void setQueryTimeout(int seconds) throws SQLException {}
    }
    
    /**
     * 模拟ResultSet类
     */
    private static class MockResultSet implements ResultSet {
        private final MockDataSource dataSource;
        private final Map<Integer, Object> params;
        private boolean nextCalled = false;
        private boolean hasNext = false;
        
        @Override
        public String getCursorName() throws SQLException {
            return null;
        }
        
        @Override
        public void updateRef(String columnLabel, java.sql.Ref x) throws SQLException {}
        
        @Override
        public void updateRef(int columnIndex, java.sql.Ref x) throws SQLException {}
        
        @Override
        public void updateRowId(int columnIndex, java.sql.RowId x) throws SQLException {}
        
        @Override
        public void updateRowId(String columnLabel, java.sql.RowId x) throws SQLException {}
        
        @Override
        public void updateArray(int columnIndex, java.sql.Array x) throws SQLException {}
        
        @Override
        public void updateArray(String columnLabel, java.sql.Array x) throws SQLException {}
        
        public MockResultSet(MockDataSource dataSource, Map<Integer, Object> params) {
            this.dataSource = dataSource;
            this.params = params;
            
            // 检查是否有匹配的数据
            if (params.containsKey(1)) {
                Object param1 = params.get(1);
                if (param1 instanceof Long) {
                    // 根据ID查询用户名
                    hasNext = dataSource.getUserData().containsKey(param1);
                } else if (param1 instanceof String) {
                    // 根据用户名查询ID
                    hasNext = dataSource.getUserData().containsValue(param1);
                }
            }
        }
        
        @Override
        public boolean next() throws SQLException {
            boolean result = !nextCalled && hasNext;
            nextCalled = true;
            return result;
        }
        
        @Override
        public String getString(String columnLabel) throws SQLException {
            if (columnLabel.equalsIgnoreCase("name")) {
                Long id = (Long) params.get(1);
                return dataSource.getUserData().get(id);
            }
            return null;
        }
        
        @Override
        public long getLong(String columnLabel) throws SQLException {
            if (columnLabel.equalsIgnoreCase("id")) {
                String name = (String) params.get(1);
                for (Map.Entry<Long, String> entry : dataSource.getUserData().entrySet()) {
                    if (entry.getValue().equals(name)) {
                        return entry.getKey();
                    }
                }
            }
            return 0;
        }
        
        @Override
        public void close() throws SQLException {
            System.out.println("关闭ResultSet");
        }
        
        // 其他方法都返回默认值或抛出不支持的异常
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("不支持"); }
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
        @Override
        public boolean wasNull() throws SQLException { return false; }
        @Override
        public byte getByte(int columnIndex) throws SQLException { return 0; }
        @Override
        public short getShort(int columnIndex) throws SQLException { return 0; }
        @Override
        public int getInt(int columnIndex) throws SQLException { return 0; }
        @Override
        public long getLong(int columnIndex) throws SQLException { return 0; }
        @Override
        public float getFloat(int columnIndex) throws SQLException { return 0; }
        @Override
        public double getDouble(int columnIndex) throws SQLException { return 0; }
        @Override
        public String getString(int columnIndex) throws SQLException { return null; }
        @Override
        public boolean getBoolean(int columnIndex) throws SQLException { return false; }
        @Override
        public byte[] getBytes(int columnIndex) throws SQLException { return new byte[0]; }
        @Override
        public java.math.BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException { return null; }
        @Override
        public java.math.BigDecimal getBigDecimal(int columnIndex) throws SQLException { return null; }
        @Override
        public java.math.BigDecimal getBigDecimal(String columnLabel) throws SQLException { return null; }
        @Override
        public Date getDate(int columnIndex) throws SQLException { return null; }
        @Override
        public Time getTime(int columnIndex) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(int columnIndex) throws SQLException { return null; }
        @Override
        public java.io.InputStream getAsciiStream(int columnIndex) throws SQLException { return null; }
        @Override
        public java.io.Reader getCharacterStream(int columnIndex) throws SQLException { return null; }
        @Override
        public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException { return null; }
        @Override
        public Object getObject(int columnIndex) throws SQLException { return null; }
        @Override
        public int findColumn(String columnLabel) throws SQLException { return 0; }
        @Override
        public Object getObject(String columnLabel) throws SQLException { return null; }
        @Override
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException { return null; }
        @Override
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException { return null; }
        @Override
        public BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException { return null; }
        @Override
        public BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException { return null; }
        @Override
        public byte getByte(String columnLabel) throws SQLException { return 0; }
        @Override
        public short getShort(String columnLabel) throws SQLException { return 0; }
        @Override
        public int getInt(String columnLabel) throws SQLException { return 0; }
        @Override
        public long getLong(String columnLabel) throws SQLException { return 0; }
        @Override
        public float getFloat(String columnLabel) throws SQLException { return 0; }
        @Override
        public double getDouble(String columnLabel) throws SQLException { return 0; }
        @Override
        public boolean getBoolean(String columnLabel) throws SQLException { return false; }
        @Override
        public byte[] getBytes(String columnLabel) throws SQLException { return new byte[0]; }
        @Override
        public Date getDate(String columnLabel) throws SQLException { return null; }
        @Override
        public Date getDate(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Date getDate(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Time getTime(String columnLabel) throws SQLException { return null; }
        @Override
        public Time getTime(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Time getTime(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(String columnLabel) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public java.io.InputStream getAsciiStream(String columnLabel) throws SQLException { return null; }
        @Override
        public java.io.Reader getCharacterStream(String columnLabel) throws SQLException { return null; }
        @Override
        public java.io.InputStream getBinaryStream(String columnLabel) throws SQLException { return null; }
        public Blob getBlob(int columnIndex) throws SQLException { return null; }
        @Override
        public Blob getBlob(String columnLabel) throws SQLException { return null; }
        public Clob getClob(int columnIndex) throws SQLException { return null; }
        public Clob getClob(String columnLabel) throws SQLException { return null; }
        @Override
        public Array getArray(int columnIndex) throws SQLException { return null; }
        @Override
        public Array getArray(String columnLabel) throws SQLException { return null; }
        @Override
        public Ref getRef(int columnIndex) throws SQLException { return null; }
        public Ref getRef(String columnLabel) throws SQLException { return null; }
        public Struct getStruct(int columnIndex) throws SQLException { return null; }
        @Override
        public Struct getStruct(String columnLabel) throws SQLException { return null; }
        @Override
        public java.net.URL getURL(int columnIndex) throws SQLException { return null; }
        @Override
        public java.net.URL getURL(String columnLabel) throws SQLException { return null; }
        @Override
        public RowId getRowId(int columnIndex) throws SQLException { return null; }
        @Override
        public RowId getRowId(String columnLabel) throws SQLException { return null; }
        @Override
        public NClob getNClob(int columnIndex) throws SQLException { return null; }
        @Override
        public NClob getNClob(String columnLabel) throws SQLException { return null; }
        @Override
        public SQLXML getSQLXML(int columnIndex) throws SQLException { return null; }
        @Override
        public SQLXML getSQLXML(String columnLabel) throws SQLException { return null; }
        @Override
        public boolean isClosed() throws SQLException { return false; }
        @Override
        public void updateNull(int columnIndex) throws SQLException {}
        @Override
        public void updateBoolean(int columnIndex, boolean x) throws SQLException {}
        @Override
        public void updateByte(int columnIndex, byte x) throws SQLException {}
        @Override
        public void updateShort(int columnIndex, short x) throws SQLException {}
        @Override
        public void updateInt(int columnIndex, int x) throws SQLException {}
        @Override
        public void updateLong(int columnIndex, long x) throws SQLException {}
        @Override
        public void updateFloat(int columnIndex, float x) throws SQLException {}
        @Override
        public void updateDouble(int columnIndex, double x) throws SQLException {}
        @Override
        public void updateBigDecimal(int columnIndex, java.math.BigDecimal x) throws SQLException {}
        @Override
        public void updateString(int columnIndex, String x) throws SQLException {}
        @Override
        public void updateBytes(int columnIndex, byte[] x) throws SQLException {}
        @Override
        public void updateDate(int columnIndex, Date x) throws SQLException {}
        @Override
        public void updateTime(int columnIndex, Time x) throws SQLException {}
        @Override
        public void updateTimestamp(int columnIndex, Timestamp x) throws SQLException {}
        @Override
        public void updateAsciiStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void updateBinaryStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void updateCharacterStream(int columnIndex, java.io.Reader x, int length) throws SQLException {}
        @Override
        public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {}
        @Override
        public void updateObject(int columnIndex, Object x) throws SQLException {}
        @Override
        public void updateNull(String columnLabel) throws SQLException {}
        @Override
        public void updateBoolean(String columnLabel, boolean x) throws SQLException {}
        @Override
        public void updateByte(String columnLabel, byte x) throws SQLException {}
        @Override
        public void updateShort(String columnLabel, short x) throws SQLException {}
        @Override
        public void updateInt(String columnLabel, int x) throws SQLException {}
        @Override
        public void updateLong(String columnLabel, long x) throws SQLException {}
        @Override
        public void updateFloat(String columnLabel, float x) throws SQLException {}
        @Override
        public void updateDouble(String columnLabel, double x) throws SQLException {}
        @Override
        public void updateBigDecimal(String columnLabel, java.math.BigDecimal x) throws SQLException {}
        @Override
        public void updateString(String columnLabel, String x) throws SQLException {}
        @Override
        public void updateBytes(String columnLabel, byte[] x) throws SQLException {}
        @Override
        public void updateDate(String columnLabel, Date x) throws SQLException {}
        @Override
        public void updateTime(String columnLabel, Time x) throws SQLException {}
        @Override
        public void updateTimestamp(String columnLabel, Timestamp x) throws SQLException {}
        @Override
        public void updateAsciiStream(String columnLabel, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void updateBinaryStream(String columnLabel, java.io.InputStream x, int length) throws SQLException {}
        @Override
        public void updateCharacterStream(String columnLabel, java.io.Reader x, int length) throws SQLException {}
        @Override
        public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {}
        @Override
        public void updateObject(String columnLabel, Object x) throws SQLException {}
        @Override
        public void insertRow() throws SQLException {}
        @Override
        public void updateRow() throws SQLException {}
        @Override
        public void deleteRow() throws SQLException {}
        @Override
        public void refreshRow() throws SQLException {}
        @Override
        public void cancelRowUpdates() throws SQLException {}
        @Override
        public void moveToInsertRow() throws SQLException {}
        @Override
        public void moveToCurrentRow() throws SQLException {}
        @Override
        public Statement getStatement() throws SQLException { return null; }
        @Override
        public int getRow() throws SQLException { return 0; }
        @Override
        public boolean absolute(int row) throws SQLException { return false; }
        @Override
        public boolean relative(int rows) throws SQLException { return false; }
        @Override
        public boolean previous() throws SQLException { return false; }
        @Override
        public void afterLast() throws SQLException {}
        @Override
        public void beforeFirst() throws SQLException {}
        @Override
        public boolean first() throws SQLException { return false; }
        @Override
        public boolean last() throws SQLException { return false; }
        @Override
        public boolean isAfterLast() throws SQLException { return false; }
        @Override
        public boolean isBeforeFirst() throws SQLException { return false; }
        @Override
        public boolean isFirst() throws SQLException { return false; }
        @Override
        public boolean isLast() throws SQLException { return false; }
        @Override
        public void close() throws SQLException {}
        @Override
        public ResultSetMetaData getMetaData() throws SQLException { return null; }
        @Override
        public Object getObject(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        @Override
        public Object getObject(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Ref getRef(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Ref getRef(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Blob getBlob(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Blob getBlob(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Clob getClob(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Clob getClob(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Array getArray(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Array getArray(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Struct getStruct(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Struct getStruct(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        @Override
        public Date getDate(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Date getDate(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Time getTime(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Time getTime(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        @Override
        public void setFetchDirection(int direction) throws SQLException {}
        @Override
        public int getFetchDirection() throws SQLException { return 0; }
        @Override
        public void setFetchSize(int rows) throws SQLException {}
        @Override
        public int getFetchSize() throws SQLException { return 0; }
        @Override
        public int getType() throws SQLException { return 0; }
        @Override
        public int getConcurrency() throws SQLException { return 0; }
        @Override
        public boolean rowUpdated() throws SQLException { return false; }
        @Override
        public boolean rowInserted() throws SQLException { return false; }
        @Override
        public boolean rowDeleted() throws SQLException { return false; }
        @Override
        public void updateAsciiStream(int columnIndex, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void updateBinaryStream(int columnIndex, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void updateCharacterStream(int columnIndex, java.io.Reader x, long length) throws SQLException {}
        @Override
        public void updateAsciiStream(String columnLabel, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void updateBinaryStream(String columnLabel, java.io.InputStream x, long length) throws SQLException {}
        @Override
        public void updateCharacterStream(String columnLabel, java.io.Reader x, long length) throws SQLException {}
        @Override
        public void updateBlob(int columnIndex, java.io.InputStream inputStream, long length) throws SQLException {}
        @Override
        public void updateBlob(String columnLabel, java.io.InputStream inputStream, long length) throws SQLException {}
        @Override
        public void updateClob(int columnIndex, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void updateClob(String columnLabel, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void updateNCharacterStream(int columnIndex, java.io.Reader x, long length) throws SQLException {}
        @Override
        public void updateNCharacterStream(String columnLabel, java.io.Reader x, long length) throws SQLException {}
        @Override
        public void updateNClob(int columnIndex, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void updateNClob(String columnLabel, java.io.Reader reader, long length) throws SQLException {}
        @Override
        public void updateBlob(int columnIndex, Blob x) throws SQLException {}
        @Override
        public void updateBlob(String columnLabel, Blob x) throws SQLException {}
        @Override
        public void updateClob(int columnIndex, Clob x) throws SQLException {}
        @Override
        public void updateClob(String columnLabel, Clob x) throws SQLException {}
        @Override
        public void updateNClob(int columnIndex, NClob x) throws SQLException {}
        @Override
        public void updateNClob(String columnLabel, NClob x) throws SQLException {}
        @Override
        public void updateSQLXML(int columnIndex, SQLXML xmlObject) throws SQLException {}
        @Override
        public void updateSQLXML(String columnLabel, SQLXML xmlObject) throws SQLException {}
        public void updateObject(int columnIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {}
        public void updateObject(String columnLabel, Object x, int targetSqlType, int scaleOrLength) throws SQLException {}
        @Override
        public void updateAsciiStream(int columnIndex, java.io.InputStream x) throws SQLException {}
        @Override
        public void updateBinaryStream(int columnIndex, java.io.InputStream x) throws SQLException {}
        @Override
        public void updateCharacterStream(int columnIndex, java.io.Reader x) throws SQLException {}
        @Override
        public void updateAsciiStream(String columnLabel, java.io.InputStream x) throws SQLException {}
        @Override
        public void updateBinaryStream(String columnLabel, java.io.InputStream x) throws SQLException {}
        @Override
        public void updateCharacterStream(String columnLabel, java.io.Reader x) throws SQLException {}
        @Override
        public void updateBlob(int columnIndex, java.io.InputStream inputStream) throws SQLException {}
        @Override
        public void updateBlob(String columnLabel, java.io.InputStream inputStream) throws SQLException {}
        @Override
        public void updateClob(int columnIndex, java.io.Reader reader) throws SQLException {}
        @Override
        public void updateClob(String columnLabel, java.io.Reader reader) throws SQLException {}
        @Override
        public void updateNCharacterStream(int columnIndex, java.io.Reader x) throws SQLException {}
        @Override
        public void updateNCharacterStream(String columnLabel, java.io.Reader x) throws SQLException {}
        @Override
        public void updateNClob(int columnIndex, java.io.Reader reader) throws SQLException {}
        @Override
        public void updateNClob(String columnLabel, java.io.Reader reader) throws SQLException {}
        @Override
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException { return null; }
        @Override
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException { return null; }
        @Override
        public SQLWarning getWarnings() throws SQLException { return null; }
        @Override
        public void clearWarnings() throws SQLException {}
        @Override
        public void updateNString(int columnIndex, String value) throws SQLException {}
        @Override
        public void updateNString(String columnLabel, String value) throws SQLException {}
        @Override
        public String getNString(int columnIndex) throws SQLException { return null; }
        @Override
        public String getNString(String columnLabel) throws SQLException { return null; }
        @Override
        public java.io.Reader getNCharacterStream(int columnIndex) throws SQLException { return null; }
        @Override
        public java.io.Reader getNCharacterStream(String columnLabel) throws SQLException { return null; }
        @Override
        public NClob getNClob(int columnIndex) throws SQLException { return null; }
        @Override
        public NClob getNClob(String columnLabel) throws SQLException { return null; }
        public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getBinaryStream(String columnLabel) throws SQLException { return null; }
        public java.io.InputStream getNCharacterStream(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getNCharacterStream(String columnLabel) throws SQLException { return null; }
        public void setTypeMap(java.util.Map<String, Class<?>> map) throws SQLException {}
        public java.util.Map<String, Class<?>> getTypeMap() throws SQLException { return null; }
        public void setCursorName(String name) throws SQLException {}
        public int getHoldability() throws SQLException { return 0; }
        @Override
        public boolean isClosed() throws SQLException { return false; }
    }
    
    private MockDataSource mockDataSource;
    private UserService userService;
    
    /**
     * 测试前准备
     */
    public void setUp() {
        mockDataSource = new MockDataSource("test");
        userService = new UserService(mockDataSource);
    }
    
    /**
     * 测试根据ID获取用户
     */
    public void testGetUserById() {
        setUp();
        
        Long userId = 1L;
        String expectedName = "Test User";
        
        // 执行方法
        String result = userService.getUserById(userId);
        
        // 验证结果
        SimpleAssertions.assertEquals(expectedName, result, "应正确获取用户名称");
        
        // 验证SQL被正确调用
        SimpleAssertions.assertNotNull(mockDataSource.getLastUsedSql(), "SQL语句不应为null");
        SimpleAssertions.assertTrue(mockDataSource.getLastUsedSql().contains("SELECT name FROM user WHERE id = ?"), 
                "SQL语句应包含正确的查询");
        SimpleAssertions.assertNotNull(mockDataSource.getLastUsedParams(), "参数不应为null");
        SimpleAssertions.assertEquals(1, mockDataSource.getLastUsedParams().length, "应有一个参数");
        SimpleAssertions.assertEquals(userId, mockDataSource.getLastUsedParams()[0], "参数值应正确");
    }
    
    /**
     * 测试根据用户名获取用户ID
     */
    public void testGetUserIdByName() {
        setUp();
        
        String username = "Admin User";
        Long expectedId = 2L;
        
        // 执行方法
        Long result = userService.getUserIdByName(username);
        
        // 验证结果
        SimpleAssertions.assertEquals(expectedId, result, "应正确获取用户ID");
    }
    
    /**
     * 测试创建用户
     */
    public void testCreateUser() {
        setUp();
        
        Long userId = 3L;
        String username = "New User";
        
        // 执行方法
        boolean result = userService.createUser(userId, username);
        
        // 验证结果
        SimpleAssertions.assertTrue(result, "用户创建应成功");
        SimpleAssertions.assertEquals(username, mockDataSource.getUserData().get(userId), "用户数据应被正确存储");
    }
    
    /**
     * 测试更新用户
     */
    public void testUpdateUser() {
        setUp();
        
        Long userId = 1L;
        String newUsername = "Updated User";
        
        // 执行方法
        boolean result = userService.updateUser(userId, newUsername);
        
        // 验证结果
        SimpleAssertions.assertTrue(result, "用户更新应成功");
        SimpleAssertions.assertEquals(newUsername, mockDataSource.getUserData().get(userId), "用户数据应被正确更新");
    }
    
    /**
     * 测试删除用户
     */
    public void testDeleteUser() {
        setUp();
        
        Long userId = 1L;
        
        // 执行方法
        boolean result = userService.deleteUser(userId);
        
        // 验证结果
        SimpleAssertions.assertTrue(result, "用户删除应成功");
        SimpleAssertions.assertFalse(mockDataSource.getUserData().containsKey(userId), "用户应被从数据中移除");
    }
    
    /**
     * 测试数据源切换功能
     */
    public void testDataSourceSwitching() {
        setUp();
        
        // 创建多个数据源
        MockDataSource masterDataSource = new MockDataSource("master");
        MockDataSource slaveDataSource = new MockDataSource("slave");
        
        // 在slave数据源中添加特定数据
        slaveDataSource.getUserData().put(999L, "Slave Only User");
        
        // 配置动态数据源
        DynamicDataSource dynamicDataSource = new DynamicDataSource();
        dynamicDataSource.setDefaultTargetDataSource(masterDataSource);
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource);
        targetDataSources.put("slave", slaveDataSource);
        
        dynamicDataSource.setTargetDataSources(targetDataSources);
        
        // 创建使用动态数据源的UserService
        UserService dynamicUserService = new UserService(dynamicDataSource);
        
        try {
            // 测试使用master数据源（默认）
            String masterResult = dynamicUserService.getUserById(1L);
            SimpleAssertions.assertEquals("Test User", masterResult, "默认应使用master数据源");
            
            // 测试切换到slave数据源
            DataSourceContextHolder.setDataSource("slave");
            String slaveResult = dynamicUserService.getUserById(999L);
            SimpleAssertions.assertEquals("Slave Only User", slaveResult, "应成功切换到slave数据源");
            
            // 切换回master数据源
            DataSourceContextHolder.clearDataSource();
            DataSourceContextHolder.setDataSource("master");
            String backToMasterResult = dynamicUserService.getUserById(1L);
            SimpleAssertions.assertEquals("Test User", backToMasterResult, "应成功切换回master数据源");
        } finally {
            // 清理上下文
            DataSourceContextHolder.clearAll();
        }
    }
    
    /**
     * 运行所有测试
     */
    public static void main(String[] args) {
        System.out.println("开始运行UserServiceTest...");
        UserServiceTest test = new UserServiceTest();
        
        try {
            test.testGetUserById();
            test.testGetUserIdByName();
            test.testCreateUser();
            test.testUpdateUser();
            test.testDeleteUser();
            test.testDataSourceSwitching();
            
            System.out.println("所有测试通过！");
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}