package com.bone.metadata.sdk.support.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * 数据库连接代理类，用于跟踪连接的使用情况和自动释放资源
 * 确保连接在使用完毕后正确关闭，防止连接泄漏
 */
public class TrackingConnectionProxy implements Connection {

    private static final Logger log = LoggerFactory.getLogger(TrackingConnectionProxy.class);
    
    private final Connection delegate;
    private final String connectionId;
    private final ConnectionPoolManager poolManager;
    private boolean closed = false;
    
    public TrackingConnectionProxy(Connection delegate, String connectionId, ConnectionPoolManager poolManager) {
        this.delegate = delegate;
        this.connectionId = connectionId;
        this.poolManager = poolManager;
    }
    
    @Override
    public Statement createStatement() throws SQLException {
        checkNotClosed();
        return new TrackingStatementProxy(delegate.createStatement(), this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(delegate.prepareStatement(sql), this, sql);
    }
    
    @Override
    public CallableStatement prepareCall(String sql) throws SQLException {
        checkNotClosed();
        return new TrackingCallableStatementProxy(delegate.prepareCall(sql), this, sql);
    }
    
    @Override
    public String nativeSQL(String sql) throws SQLException {
        checkNotClosed();
        return delegate.nativeSQL(sql);
    }
    
    @Override
    public void setAutoCommit(boolean autoCommit) throws SQLException {
        checkNotClosed();
        delegate.setAutoCommit(autoCommit);
    }
    
    @Override
    public boolean getAutoCommit() throws SQLException {
        checkNotClosed();
        return delegate.getAutoCommit();
    }
    
    @Override
    public void commit() throws SQLException {
        checkNotClosed();
        delegate.commit();
    }
    
    @Override
    public void rollback() throws SQLException {
        checkNotClosed();
        delegate.rollback();
    }
    
    @Override
    public void close() throws SQLException {
        if (!closed) {
            closed = true;
            try {
                delegate.close();
                // 通知连接池管理器此连接已释放
                poolManager.untrackConnection(connectionId);
                log.trace("Connection {} closed successfully", connectionId);
            } catch (SQLException e) {
                log.error("Error closing connection {}", connectionId, e);
                throw e;
            }
        }
    }
    
    @Override
    public boolean isClosed() throws SQLException {
        return closed || delegate.isClosed();
    }
    
    @Override
    public DatabaseMetaData getMetaData() throws SQLException {
        checkNotClosed();
        return delegate.getMetaData();
    }
    
    @Override
    public void setReadOnly(boolean readOnly) throws SQLException {
        checkNotClosed();
        delegate.setReadOnly(readOnly);
    }
    
    @Override
    public boolean isReadOnly() throws SQLException {
        checkNotClosed();
        return delegate.isReadOnly();
    }
    
    @Override
    public void setCatalog(String catalog) throws SQLException {
        checkNotClosed();
        delegate.setCatalog(catalog);
    }
    
    @Override
    public String getCatalog() throws SQLException {
        checkNotClosed();
        return delegate.getCatalog();
    }
    
    @Override
    public void setTransactionIsolation(int level) throws SQLException {
        checkNotClosed();
        delegate.setTransactionIsolation(level);
    }
    
    @Override
    public int getTransactionIsolation() throws SQLException {
        checkNotClosed();
        return delegate.getTransactionIsolation();
    }
    
    @Override
    public SQLWarning getWarnings() throws SQLException {
        checkNotClosed();
        return delegate.getWarnings();
    }
    
    @Override
    public void clearWarnings() throws SQLException {
        checkNotClosed();
        delegate.clearWarnings();
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        checkNotClosed();
        return new TrackingStatementProxy(delegate.createStatement(resultSetType, resultSetConcurrency), this);
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(
                delegate.prepareStatement(sql, resultSetType, resultSetConcurrency), 
                this, 
                sql
        );
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
        checkNotClosed();
        return new TrackingCallableStatementProxy(
                delegate.prepareCall(sql, resultSetType, resultSetConcurrency), 
                this, 
                sql
        );
    }
    
    @Override
    public Map<String, Class<?>> getTypeMap() throws SQLException {
        checkNotClosed();
        return delegate.getTypeMap();
    }
    
    @Override
    public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
        checkNotClosed();
        delegate.setTypeMap(map);
    }
    
    @Override
    public void setHoldability(int holdability) throws SQLException {
        checkNotClosed();
        delegate.setHoldability(holdability);
    }
    
    @Override
    public int getHoldability() throws SQLException {
        checkNotClosed();
        return delegate.getHoldability();
    }
    
    @Override
    public Savepoint setSavepoint() throws SQLException {
        checkNotClosed();
        return delegate.setSavepoint();
    }
    
    @Override
    public Savepoint setSavepoint(String name) throws SQLException {
        checkNotClosed();
        return delegate.setSavepoint(name);
    }
    
    @Override
    public void rollback(Savepoint savepoint) throws SQLException {
        checkNotClosed();
        delegate.rollback(savepoint);
    }
    
    @Override
    public void releaseSavepoint(Savepoint savepoint) throws SQLException {
        checkNotClosed();
        delegate.releaseSavepoint(savepoint);
    }
    
    @Override
    public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkNotClosed();
        return new TrackingStatementProxy(
                delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability), 
                this
        );
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(
                delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability), 
                this, 
                sql
        );
    }
    
    @Override
    public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
        checkNotClosed();
        return new TrackingCallableStatementProxy(
                delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability), 
                this, 
                sql
        );
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(
                delegate.prepareStatement(sql, autoGeneratedKeys), 
                this, 
                sql
        );
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(
                delegate.prepareStatement(sql, columnIndexes), 
                this, 
                sql
        );
    }
    
    @Override
    public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
        checkNotClosed();
        return new TrackingPreparedStatementProxy(
                delegate.prepareStatement(sql, columnNames), 
                this, 
                sql
        );
    }
    
    @Override
    public Clob createClob() throws SQLException {
        checkNotClosed();
        return delegate.createClob();
    }
    
    @Override
    public Blob createBlob() throws SQLException {
        checkNotClosed();
        return delegate.createBlob();
    }
    
    @Override
    public NClob createNClob() throws SQLException {
        checkNotClosed();
        return delegate.createNClob();
    }
    
    @Override
    public SQLXML createSQLXML() throws SQLException {
        checkNotClosed();
        return delegate.createSQLXML();
    }
    
    @Override
    public boolean isValid(int timeout) throws SQLException {
        checkNotClosed();
        return delegate.isValid(timeout);
    }
    
    @Override
    public void setClientInfo(String name, String value) throws SQLClientInfoException {
        checkNotClosed();
        delegate.setClientInfo(name, value);
    }
    
    @Override
    public void setClientInfo(Properties properties) throws SQLClientInfoException {
        checkNotClosed();
        delegate.setClientInfo(properties);
    }
    
    @Override
    public String getClientInfo(String name) throws SQLException {
        checkNotClosed();
        return delegate.getClientInfo(name);
    }
    
    @Override
    public Properties getClientInfo() throws SQLException {
        checkNotClosed();
        return delegate.getClientInfo();
    }
    
    @Override
    public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
        checkNotClosed();
        return delegate.createArrayOf(typeName, elements);
    }
    
    @Override
    public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
        checkNotClosed();
        return delegate.createStruct(typeName, attributes);
    }
    
    @Override
    public void setSchema(String schema) throws SQLException {
        checkNotClosed();
        delegate.setSchema(schema);
    }
    
    @Override
    public String getSchema() throws SQLException {
        checkNotClosed();
        return delegate.getSchema();
    }
    
    @Override
    public void abort(Executor executor) throws SQLException {
        checkNotClosed();
        delegate.abort(executor);
    }
    
    @Override
    public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
        checkNotClosed();
        delegate.setNetworkTimeout(executor, milliseconds);
    }
    
    @Override
    public int getNetworkTimeout() throws SQLException {
        checkNotClosed();
        return delegate.getNetworkTimeout();
    }
    
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        return delegate.unwrap(iface);
    }
    
    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
    
    private void checkNotClosed() throws SQLException {
        if (closed) {
            throw new SQLException("Connection " + connectionId + " is already closed");
        }
    }
    
    @Override
    protected void finalize() throws Throwable {
        try {
            if (!closed) {
                log.warn("Connection {} was not properly closed, leaking detected in finalizer", connectionId);
                close();
            }
        } finally {
            super.finalize();
        }
    }
    
    /**
     * 内部类：Statement代理，用于跟踪Statement资源
     */
    private static class TrackingStatementProxy implements Statement {
        private final Statement delegate;
        private final TrackingConnectionProxy connectionProxy;
        private boolean closed = false;
        
        public TrackingStatementProxy(Statement delegate, TrackingConnectionProxy connectionProxy) {
            this.delegate = delegate;
            this.connectionProxy = connectionProxy;
        }
        
        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                try {
                    delegate.close();
                } catch (SQLException e) {
                    log.error("Error closing statement", e);
                    throw e;
                }
            }
        }
        
        @Override
        protected void finalize() throws Throwable {
            try {
                if (!closed) {
                    log.warn("Statement was not properly closed, leaking detected in finalizer");
                    close();
                }
            } finally {
                super.finalize();
            }
        }
        
        // 代理其他Statement方法...
        
        @Override
        public ResultSet executeQuery(String sql) throws SQLException {
            checkNotClosed();
            return new TrackingResultSetProxy(delegate.executeQuery(sql), this);
        }
        
        @Override
        public int executeUpdate(String sql) throws SQLException {
            checkNotClosed();
            return delegate.executeUpdate(sql);
        }
        

        
        @Override
        public int getMaxFieldSize() throws SQLException {
            checkNotClosed();
            return delegate.getMaxFieldSize();
        }
        
        @Override
        public void setMaxFieldSize(int max) throws SQLException {
            checkNotClosed();
            delegate.setMaxFieldSize(max);
        }
        
        @Override
        public int getMaxRows() throws SQLException {
            checkNotClosed();
            return delegate.getMaxRows();
        }
        
        @Override
        public void setMaxRows(int max) throws SQLException {
            checkNotClosed();
            delegate.setMaxRows(max);
        }
        
        @Override
        public void setEscapeProcessing(boolean enable) throws SQLException {
            checkNotClosed();
            delegate.setEscapeProcessing(enable);
        }
        
        @Override
        public int getQueryTimeout() throws SQLException {
            checkNotClosed();
            return delegate.getQueryTimeout();
        }
        
        @Override
        public void setQueryTimeout(int seconds) throws SQLException {
            checkNotClosed();
            delegate.setQueryTimeout(seconds);
        }
        
        @Override
        public void cancel() throws SQLException {
            checkNotClosed();
            delegate.cancel();
        }
        
        @Override
        public SQLWarning getWarnings() throws SQLException {
            checkNotClosed();
            return delegate.getWarnings();
        }
        
        @Override
        public void clearWarnings() throws SQLException {
            checkNotClosed();
            delegate.clearWarnings();
        }
        
        @Override
        public void setCursorName(String name) throws SQLException {
            checkNotClosed();
            delegate.setCursorName(name);
        }
        
        @Override
        public boolean execute(String sql) throws SQLException {
            checkNotClosed();
            return delegate.execute(sql);
        }
        
        @Override
        public ResultSet getResultSet() throws SQLException {
            checkNotClosed();
            ResultSet rs = delegate.getResultSet();
            return rs != null ? new TrackingResultSetProxy(rs, this) : null;
        }
        
        @Override
        public int getUpdateCount() throws SQLException {
            checkNotClosed();
            return delegate.getUpdateCount();
        }
        
        @Override
        public boolean getMoreResults() throws SQLException {
            checkNotClosed();
            return delegate.getMoreResults();
        }
        
        @Override
        public int getFetchDirection() throws SQLException {
            checkNotClosed();
            return delegate.getFetchDirection();
        }
        
        @Override
        public void setFetchDirection(int direction) throws SQLException {
            checkNotClosed();
            delegate.setFetchDirection(direction);
        }
        
        @Override
        public int getFetchSize() throws SQLException {
            checkNotClosed();
            return delegate.getFetchSize();
        }
        
        @Override
        public void setFetchSize(int rows) throws SQLException {
            checkNotClosed();
            delegate.setFetchSize(rows);
        }
        
        @Override
        public int getResultSetConcurrency() throws SQLException {
            checkNotClosed();
            return delegate.getResultSetConcurrency();
        }
        
        @Override
        public int getResultSetType() throws SQLException {
            checkNotClosed();
            return delegate.getResultSetType();
        }
        
        @Override
        public void addBatch(String sql) throws SQLException {
            checkNotClosed();
            delegate.addBatch(sql);
        }
        
        @Override
        public void clearBatch() throws SQLException {
            checkNotClosed();
            delegate.clearBatch();
        }
        
        @Override
        public int[] executeBatch() throws SQLException {
            checkNotClosed();
            return delegate.executeBatch();
        }
        
        @Override
        public Connection getConnection() throws SQLException {
            checkNotClosed();
            return connectionProxy;
        }
        
        @Override
        public boolean getMoreResults(int current) throws SQLException {
            checkNotClosed();
            return delegate.getMoreResults(current);
        }
        
        @Override
        public ResultSet getGeneratedKeys() throws SQLException {
            checkNotClosed();
            ResultSet rs = delegate.getGeneratedKeys();
            return rs != null ? new TrackingResultSetProxy(rs, this) : null;
        }
        
        @Override
        public int executeUpdate(String sql, int autoGeneratedKeys) throws SQLException {
            checkNotClosed();
            return delegate.executeUpdate(sql, autoGeneratedKeys);
        }
        
        @Override
        public int executeUpdate(String sql, int[] columnIndexes) throws SQLException {
            checkNotClosed();
            return delegate.executeUpdate(sql, columnIndexes);
        }
        
        @Override
        public int executeUpdate(String sql, String[] columnNames) throws SQLException {
            checkNotClosed();
            return delegate.executeUpdate(sql, columnNames);
        }
        
        @Override
        public boolean execute(String sql, int autoGeneratedKeys) throws SQLException {
            checkNotClosed();
            return delegate.execute(sql, autoGeneratedKeys);
        }
        
        @Override
        public boolean execute(String sql, int[] columnIndexes) throws SQLException {
            checkNotClosed();
            return delegate.execute(sql, columnIndexes);
        }
        
        @Override
        public boolean execute(String sql, String[] columnNames) throws SQLException {
            checkNotClosed();
            return delegate.execute(sql, columnNames);
        }
        
        @Override
        public int getResultSetHoldability() throws SQLException {
            checkNotClosed();
            return delegate.getResultSetHoldability();
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return closed || delegate.isClosed();
        }
        

        

        

        
        @Override
        public void setPoolable(boolean poolable) throws SQLException {
            checkNotClosed();
            delegate.setPoolable(poolable);
        }
        
        @Override
        public boolean isPoolable() throws SQLException {
            checkNotClosed();
            return delegate.isPoolable();
        }
        

        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
        
        private void checkNotClosed() throws SQLException {
            if (closed) {
                throw new SQLException("Statement is already closed");
            }
        }
    }
    
    /**
     * 内部类：PreparedStatement代理，用于跟踪PreparedStatement资源
     */
    private static class TrackingPreparedStatementProxy extends TrackingStatementProxy implements PreparedStatement {
        private final PreparedStatement delegate;
        private final String sql;
        
        public TrackingPreparedStatementProxy(PreparedStatement delegate, TrackingConnectionProxy connectionProxy, String sql) {
            super(delegate, connectionProxy);
            this.delegate = delegate;
            this.sql = sql;
        }
        
        // 代理PreparedStatement方法...
        
        @Override
        public ResultSet executeQuery() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return new TrackingResultSetProxy(delegate.executeQuery(), this);
        }
        
        @Override
        public int executeUpdate() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.executeUpdate();
        }
        
        @Override
        public boolean execute() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.execute();
        }
        
        @Override
        public void setNull(int parameterIndex, int sqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNull(parameterIndex, sqlType);
        }
        
        @Override
        public void setBoolean(int parameterIndex, boolean x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBoolean(parameterIndex, x);
        }
        
        @Override
        public void setByte(int parameterIndex, byte x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setByte(parameterIndex, x);
        }
        
        @Override
        public void setShort(int parameterIndex, short x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setShort(parameterIndex, x);
        }
        
        @Override
        public void setInt(int parameterIndex, int x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setInt(parameterIndex, x);
        }
        
        @Override
        public void setLong(int parameterIndex, long x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setLong(parameterIndex, x);
        }
        
        @Override
        public void setFloat(int parameterIndex, float x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setFloat(parameterIndex, x);
        }
        
        @Override
        public void setDouble(int parameterIndex, double x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDouble(parameterIndex, x);
        }
        
        @Override
        public void setBigDecimal(int parameterIndex, java.math.BigDecimal x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBigDecimal(parameterIndex, x);
        }
        
        @Override
        public void setString(int parameterIndex, String x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setString(parameterIndex, x);
        }
        
        @Override
        public void setBytes(int parameterIndex, byte[] x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBytes(parameterIndex, x);
        }
        
        @Override
        public void setDate(int parameterIndex, java.sql.Date x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDate(parameterIndex, x);
        }
        
        @Override
        public void setTime(int parameterIndex, java.sql.Time x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTime(parameterIndex, x);
        }
        
        @Override
        public void setTimestamp(int parameterIndex, java.sql.Timestamp x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTimestamp(parameterIndex, x);
        }
        
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterIndex, x, length);
        }
        
        @Override
        public void setUnicodeStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setUnicodeStream(parameterIndex, x, length);
        }
        
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterIndex, x, length);
        }
        
        @Override
        public void clearParameters() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.clearParameters();
        }
        
        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterIndex, x, targetSqlType);
        }
        
        @Override
        public void setObject(int parameterIndex, Object x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterIndex, x);
        }
        
        @Override
        public void addBatch() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.addBatch();
        }
        
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterIndex, reader, length);
        }
        
        @Override
        public void setRef(int parameterIndex, Ref x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setRef(parameterIndex, x);
        }
        
        @Override
        public void setBlob(int parameterIndex, Blob x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterIndex, x);
        }
        
        @Override
        public void setClob(int parameterIndex, Clob x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterIndex, x);
        }
        
        @Override
        public void setArray(int parameterIndex, Array x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setArray(parameterIndex, x);
        }
        
        @Override
        public ResultSetMetaData getMetaData() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getMetaData();
        }
        
        @Override
        public void setDate(int parameterIndex, java.sql.Date x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDate(parameterIndex, x, cal);
        }
        
        @Override
        public void setTime(int parameterIndex, java.sql.Time x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTime(parameterIndex, x, cal);
        }
        
        @Override
        public void setTimestamp(int parameterIndex, java.sql.Timestamp x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTimestamp(parameterIndex, x, cal);
        }
        
        @Override
        public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNull(parameterIndex, sqlType, typeName);
        }
        
        @Override
        public void setURL(int parameterIndex, java.net.URL x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setURL(parameterIndex, x);
        }
        
        @Override
        public ParameterMetaData getParameterMetaData() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getParameterMetaData();
        }
        
        @Override
        public void setRowId(int parameterIndex, RowId x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setRowId(parameterIndex, x);
        }
        
        @Override
        public void setNString(int parameterIndex, String value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNString(parameterIndex, value);
        }
        
        @Override
        public void setNCharacterStream(int parameterIndex, java.io.Reader value, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNCharacterStream(parameterIndex, value, length);
        }
        
        @Override
        public void setNClob(int parameterIndex, NClob value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterIndex, value);
        }
        
        @Override
        public void setClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterIndex, reader, length);
        }
        
        @Override
        public void setBlob(int parameterIndex, java.io.InputStream inputStream, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterIndex, inputStream, length);
        }
        
        @Override
        public void setNClob(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterIndex, reader, length);
        }
        
        @Override
        public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setSQLXML(parameterIndex, xmlObject);
        }
        
        @Override
        public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
        }
        
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterIndex, x, length);
        }
        
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterIndex, x, length);
        }
        
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterIndex, reader, length);
        }
        
        @Override
        public void setAsciiStream(int parameterIndex, java.io.InputStream x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterIndex, x);
        }
        
        @Override
        public void setBinaryStream(int parameterIndex, java.io.InputStream x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterIndex, x);
        }
        
        @Override
        public void setCharacterStream(int parameterIndex, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterIndex, reader);
        }
        
        @Override
        public void setNCharacterStream(int parameterIndex, java.io.Reader value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNCharacterStream(parameterIndex, value);
        }
        
        @Override
        public void setClob(int parameterIndex, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterIndex, reader);
        }
        
        @Override
        public void setBlob(int parameterIndex, java.io.InputStream inputStream) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterIndex, inputStream);
        }
        
        @Override
        public void setNClob(int parameterIndex, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterIndex, reader);
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return ((TrackingStatementProxy)this).isClosed();
        }
        
        @Override
        public void closeOnCompletion() throws SQLException {
            ((TrackingStatementProxy)this).closeOnCompletion();
        }
        
        @Override
        public boolean isCloseOnCompletion() throws SQLException {
            return ((TrackingStatementProxy)this).isCloseOnCompletion();
        }
    }
    
    /**
     * 内部类：CallableStatement代理，用于跟踪CallableStatement资源
     */
    private static class TrackingCallableStatementProxy extends TrackingPreparedStatementProxy implements CallableStatement {
        private final CallableStatement delegate;
        
        @Override
        public void setURL(String parameterName, java.net.URL val) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setURL(parameterName, val);
        }
        
        @Override
        public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTimestamp(parameterIndex, cal);
        }
        
        @Override
        public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTime(parameterIndex, cal);
        }
        
        @Override
        public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDate(parameterIndex, cal);
        }
        
        @Override
        public Object getObject(int parameterIndex, java.util.Map<String,Class<?>> map) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterIndex, map);
        }
        
        @Override
        public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBigDecimal(parameterIndex, scale);
        }
        
        public TrackingCallableStatementProxy(CallableStatement delegate, TrackingConnectionProxy connectionProxy, String sql) {
            super(delegate, connectionProxy, sql);
            this.delegate = delegate;
        }
        
        // 代理CallableStatement方法...
        
        @Override
        public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterIndex, sqlType);
        }
        
        @Override
        public java.io.Reader getCharacterStream(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getCharacterStream(parameterName);
        }
        
        @Override
        public java.io.Reader getCharacterStream(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getCharacterStream(parameterIndex);
        }
        
        @Override
        public java.io.Reader getNCharacterStream(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNCharacterStream(parameterName);
        }
        
        @Override
        public java.io.Reader getNCharacterStream(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNCharacterStream(parameterIndex);
        }
        
        @Override
        public String getNString(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNString(parameterName);
        }
        
        @Override
        public String getNString(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNString(parameterIndex);
        }
        
        @Override
        public void setRowId(String parameterName, java.sql.RowId x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setRowId(parameterName, x);
        }
        
        @Override
        public java.sql.Timestamp getTimestamp(String parameterName, java.util.Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTimestamp(parameterName, cal);
        }
        
        @Override
        public java.sql.Time getTime(String parameterName, java.util.Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTime(parameterName, cal);
        }
        
        @Override
        public java.sql.Date getDate(String parameterName, java.util.Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDate(parameterName, cal);
        }
        
        @Override
        public Object getObject(String parameterName, java.util.Map<String,Class<?>> map) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterName, map);
        }
        
        @Override
        public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterIndex, sqlType, scale);
        }
        
        @Override
        public boolean wasNull() throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.wasNull();
        }
        
        @Override
        public String getString(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getString(parameterIndex);
        }
        
        @Override
        public boolean getBoolean(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBoolean(parameterIndex);
        }
        
        @Override
        public byte getByte(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getByte(parameterIndex);
        }
        
        @Override
        public short getShort(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getShort(parameterIndex);
        }
        
        @Override
        public int getInt(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getInt(parameterIndex);
        }
        
        @Override
        public long getLong(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getLong(parameterIndex);
        }
        
        @Override
        public float getFloat(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getFloat(parameterIndex);
        }
        
        @Override
        public double getDouble(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDouble(parameterIndex);
        }
        
        @Override
        public java.math.BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBigDecimal(parameterIndex);
        }
        
        @Override
        public byte[] getBytes(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBytes(parameterIndex);
        }
        
        @Override
        public java.sql.Date getDate(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDate(parameterIndex);
        }
        
        @Override
        public java.sql.Time getTime(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTime(parameterIndex);
        }
        
        @Override
        public java.sql.Timestamp getTimestamp(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTimestamp(parameterIndex);
        }
        
        @Override
        public Object getObject(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterIndex);
        }
        
        @Override
        public Ref getRef(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getRef(parameterIndex);
        }
        
        @Override
        public Blob getBlob(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBlob(parameterIndex);
        }
        
        @Override
        public Clob getClob(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getClob(parameterIndex);
        }
        
        @Override
        public Array getArray(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getArray(parameterIndex);
        }
        
        @Override
        public void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterIndex, sqlType, typeName);
        }
        
        @Override
        public void setURL(int parameterIndex, java.net.URL val) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setURL(parameterIndex, val);
        }
        
        @Override
        public java.net.URL getURL(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getURL(parameterIndex);
        }
        
        @Override
        public RowId getRowId(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getRowId(parameterIndex);
        }
        
        @Override
        public NClob getNClob(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNClob(parameterIndex);
        }
        
        @Override
        public SQLXML getSQLXML(int parameterIndex) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getSQLXML(parameterIndex);
        }
        
        @Override
        public void registerOutParameter(int parameterIndex, int sqlType, int scaleOrLength) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterIndex, sqlType, scaleOrLength);
        }
        
        @Override
        public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterIndex, type);
        }
        
        @Override
        public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterName, sqlType);
        }
        
        @Override
        public void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterName, sqlType, scale);
        }
        
        @Override
        public void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterName, sqlType, typeName);
        }
        
        @Override
        public void setString(String parameterName, String x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setString(parameterName, x);
        }
        
        @Override
        public void setNull(String parameterName, int sqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNull(parameterName, sqlType);
        }
        
        @Override
        public void setBoolean(String parameterName, boolean x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBoolean(parameterName, x);
        }
        
        @Override
        public void setByte(String parameterName, byte x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setByte(parameterName, x);
        }
        
        @Override
        public void setShort(String parameterName, short x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setShort(parameterName, x);
        }
        
        @Override
        public void setInt(String parameterName, int x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setInt(parameterName, x);
        }
        
        @Override
        public void setLong(String parameterName, long x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setLong(parameterName, x);
        }
        
        @Override
        public void setFloat(String parameterName, float x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setFloat(parameterName, x);
        }
        
        @Override
        public void setDouble(String parameterName, double x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDouble(parameterName, x);
        }
        
        @Override
        public void setBigDecimal(String parameterName, java.math.BigDecimal x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBigDecimal(parameterName, x);
        }
        
        @Override
        public void setBytes(String parameterName, byte[] x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBytes(parameterName, x);
        }
        
        @Override
        public void setDate(String parameterName, java.sql.Date x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDate(parameterName, x);
        }
        
        @Override
        public void setTime(String parameterName, java.sql.Time x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTime(parameterName, x);
        }
        
        @Override
        public void setTimestamp(String parameterName, java.sql.Timestamp x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTimestamp(parameterName, x);
        }
        
        @Override
        public void setAsciiStream(String parameterName, java.io.InputStream x, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterName, x, length);
        }
        
        @Override
        public void setBinaryStream(String parameterName, java.io.InputStream x, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterName, x, length);
        }
        
        @Override
        public void setCharacterStream(String parameterName, java.io.Reader reader, int length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterName, reader, length);
        }
        
        @Override
        public void setObject(String parameterName, Object x, int targetSqlType, int scaleOrLength) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterName, x, targetSqlType, scaleOrLength);
        }
        
        @Override
        public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterName, x, targetSqlType);
        }
        
        @Override
        public void setObject(String parameterName, Object x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setObject(parameterName, x);
        }
        
        @Override
        public void setDate(String parameterName, java.sql.Date x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setDate(parameterName, x, cal);
        }
        
        @Override
        public void setTime(String parameterName, java.sql.Time x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTime(parameterName, x, cal);
        }
        
        @Override
        public void setTimestamp(String parameterName, java.sql.Timestamp x, Calendar cal) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setTimestamp(parameterName, x, cal);
        }
        
        @Override
        public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNull(parameterName, sqlType, typeName);
        }
        
        @Override
        public String getString(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getString(parameterName);
        }
        
        @Override
        public boolean getBoolean(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBoolean(parameterName);
        }
        
        @Override
        public byte getByte(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getByte(parameterName);
        }
        
        @Override
        public short getShort(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getShort(parameterName);
        }
        
        @Override
        public int getInt(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getInt(parameterName);
        }
        
        @Override
        public long getLong(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getLong(parameterName);
        }
        
        @Override
        public float getFloat(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getFloat(parameterName);
        }
        
        @Override
        public double getDouble(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDouble(parameterName);
        }
        
        @Override
        public java.math.BigDecimal getBigDecimal(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBigDecimal(parameterName);
        }
        
        @Override
        public byte[] getBytes(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBytes(parameterName);
        }
        
        @Override
        public java.sql.Date getDate(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getDate(parameterName);
        }
        
        @Override
        public java.sql.Time getTime(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTime(parameterName);
        }
        
        @Override
        public java.sql.Timestamp getTimestamp(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getTimestamp(parameterName);
        }
        
        @Override
        public Object getObject(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterName);
        }
        
        @Override
        public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterName, type);
        }
        
        @Override
        public Object getObject(String parameterName, Map<String,Class<?>> map) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getObject(parameterName, map);
        }
        
        @Override
        public Ref getRef(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getRef(parameterName);
        }
        
        @Override
        public Blob getBlob(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getBlob(parameterName);
        }
        
        @Override
        public Clob getClob(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getClob(parameterName);
        }
        
        @Override
        public Array getArray(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getArray(parameterName);
        }
        
        @Override
        public java.net.URL getURL(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getURL(parameterName);
        }
        
        @Override
        public RowId getRowId(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getRowId(parameterName);
        }
        
        @Override
        public NClob getNClob(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getNClob(parameterName);
        }
        
        @Override
        public SQLXML getSQLXML(String parameterName) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            return delegate.getSQLXML(parameterName);
        }
        
        @Override
        public void registerOutParameter(String parameterName, int sqlType, int scaleOrLength) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.registerOutParameter(parameterName, sqlType, scaleOrLength);
        }
        
        @Override
        public void setBlob(String parameterName, Blob x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterName, x);
        }
        
        @Override
        public void setClob(String parameterName, Clob x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterName, x);
        }
        
        @Override
        public void setAsciiStream(String parameterName, java.io.InputStream x, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterName, x, length);
        }
        
        @Override
        public void setBinaryStream(String parameterName, java.io.InputStream x, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterName, x, length);
        }
        
        @Override
        public void setCharacterStream(String parameterName, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterName, reader, length);
        }
        
        @Override
        public void setNCharacterStream(String parameterName, java.io.Reader value, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNCharacterStream(parameterName, value, length);
        }
        
        @Override
        public void setNClob(String parameterName, NClob value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterName, value);
        }
        
        @Override
        public void setClob(String parameterName, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterName, reader, length);
        }
        
        @Override
        public void setBlob(String parameterName, java.io.InputStream inputStream, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterName, inputStream, length);
        }
        
        @Override
        public void setNClob(String parameterName, java.io.Reader reader, long length) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterName, reader, length);
        }
        
        @Override
        public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setSQLXML(parameterName, xmlObject);
        }
        
        @Override
        public void setNString(String parameterName, String value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNString(parameterName, value);
        }
        
        @Override
        public void setRef(String parameterName, Ref x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setRef(parameterName, x);
        }
        
        @Override
        public void setArray(String parameterName, Array x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            // 由于delegate可能只支持int索引版本的setArray，这里做一个合理的默认实现
            // 注意：这只是为了编译通过，实际运行时可能需要根据具体的数据库驱动进行调整
            throw new UnsupportedOperationException("setArray with String parameter name is not supported by this driver");
        }
        
        @Override
        public void setAsciiStream(String parameterName, java.io.InputStream x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setAsciiStream(parameterName, x);
        }
        
        @Override
        public void setBinaryStream(String parameterName, java.io.InputStream x) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBinaryStream(parameterName, x);
        }
        
        @Override
        public void setCharacterStream(String parameterName, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setCharacterStream(parameterName, reader);
        }
        
        @Override
        public void setNCharacterStream(String parameterName, java.io.Reader value) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNCharacterStream(parameterName, value);
        }
        
        @Override
        public void setClob(String parameterName, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setClob(parameterName, reader);
        }
        
        @Override
        public void setBlob(String parameterName, java.io.InputStream inputStream) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setBlob(parameterName, inputStream);
        }
        
        @Override
        public void setNClob(String parameterName, java.io.Reader reader) throws SQLException {
            ((TrackingStatementProxy)this).checkNotClosed();
            delegate.setNClob(parameterName, reader);
        }
        
        @Override
        public boolean isClosed() throws SQLException {
            return ((TrackingStatementProxy)this).isClosed();
        }
        
        @Override
        public void closeOnCompletion() throws SQLException {
            ((TrackingStatementProxy)this).closeOnCompletion();
        }
        
        @Override
        public boolean isCloseOnCompletion() throws SQLException {
            return ((TrackingStatementProxy)this).isCloseOnCompletion();
        }
    }
    
    /**
     * 内部类：ResultSet代理，用于跟踪ResultSet资源
     */
    private static class TrackingResultSetProxy implements ResultSet {
        private final ResultSet delegate;
        private final TrackingStatementProxy statementProxy;
        private boolean closed = false;
        
        private void checkNotClosed() throws SQLException {
            if (closed) {
                throw new SQLException("ResultSet is closed");
            }
        }
        
        @Override
        public <T> T getObject(int columnIndex, Class<T> type) throws SQLException {
            checkNotClosed();
            return delegate.getObject(columnIndex, type);
        }
        
        @Override
        public void updateNClob(String columnLabel, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateNClob(columnLabel, reader);
        }
        
        @Override
        public void updateNClob(int columnIndex, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateNClob(columnIndex, reader);
        }
        
        @Override
        public void updateClob(String columnLabel, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateClob(columnLabel, reader);
        }
        
        @Override
        public void updateClob(int columnIndex, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateClob(columnIndex, reader);
        }
        
        @Override
        public void updateBlob(String columnLabel, java.io.InputStream inputStream) throws SQLException {
            checkNotClosed();
            delegate.updateBlob(columnLabel, inputStream);
        }

        @Override
        public void updateBlob(int columnIndex, java.io.InputStream inputStream) throws SQLException {
            checkNotClosed();
            delegate.updateBlob(columnIndex, inputStream);
        }

        @Override
        public void updateCharacterStream(String columnLabel, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateCharacterStream(columnLabel, reader);
        }

        @Override
        public void updateBinaryStream(String columnLabel, java.io.InputStream inputStream) throws SQLException {
            checkNotClosed();
            delegate.updateBinaryStream(columnLabel, inputStream);
        }

        @Override
        public void updateAsciiStream(String columnLabel, java.io.InputStream inputStream) throws SQLException {
            checkNotClosed();
            delegate.updateAsciiStream(columnLabel, inputStream);
        }

        @Override
        public void updateCharacterStream(int columnIndex, java.io.Reader reader) throws SQLException {
            checkNotClosed();
            delegate.updateCharacterStream(columnIndex, reader);
        }

        @Override
        public void updateBinaryStream(int columnIndex, java.io.InputStream inputStream) throws SQLException {
            checkNotClosed();
            delegate.updateBinaryStream(columnIndex, inputStream);
        }
        
        public TrackingResultSetProxy(ResultSet delegate, TrackingStatementProxy statementProxy) {
            this.delegate = delegate;
            this.statementProxy = statementProxy;
        }
        
        @Override
        public void close() throws SQLException {
            if (!closed) {
                closed = true;
                try {
                    delegate.close();
                } catch (SQLException e) {
                    log.error("Error closing result set", e);
                    throw e;
                }
            }
        }
        
        @Override
        public <T> T getObject(String columnLabel, Class<T> type) throws SQLException {
            checkNotClosed();
            return delegate.getObject(columnLabel, type);
        }
        
        @Override
        protected void finalize() throws Throwable {
            try {
                if (!closed) {
                    log.warn("ResultSet was not properly closed, leaking detected in finalizer");
                    close();
                }
            } finally {
                super.finalize();
            }
        }
        
        // 代理ResultSet方法（简化实现）
        
        @Override
        public boolean next() throws SQLException {
            checkNotClosed();
            return delegate.next();
        }
        

        
        @Override
        public Statement getStatement() throws SQLException {
            checkNotClosed();
            return statementProxy;
        }
        
        // 实现其余ResultSet方法...
        // 为简化实现，这里省略了大部分ResultSet方法
        // 实际应用中应该完整实现所有方法
        
        @Override
        public boolean isClosed() throws SQLException {
            return closed || delegate.isClosed();
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
        
        // 其他必要方法的简化实现...
        @Override public boolean absolute(int row) throws SQLException { checkNotClosed(); return delegate.absolute(row); }
        @Override public void afterLast() throws SQLException { checkNotClosed(); delegate.afterLast(); }
        @Override public void beforeFirst() throws SQLException { checkNotClosed(); delegate.beforeFirst(); }
        @Override public boolean first() throws SQLException { checkNotClosed(); return delegate.first(); }
        @Override public boolean last() throws SQLException { checkNotClosed(); return delegate.last(); }
        @Override public boolean previous() throws SQLException { checkNotClosed(); return delegate.previous(); }
        @Override public int getRow() throws SQLException { checkNotClosed(); return delegate.getRow(); }
        @Override public boolean rowDeleted() throws SQLException { checkNotClosed(); return delegate.rowDeleted(); }
        @Override public boolean rowInserted() throws SQLException { checkNotClosed(); return delegate.rowInserted(); }
        @Override public boolean rowUpdated() throws SQLException { checkNotClosed(); return delegate.rowUpdated(); }
        @Override public void refreshRow() throws SQLException { checkNotClosed(); delegate.refreshRow(); }
        @Override public void updateRow() throws SQLException { checkNotClosed(); delegate.updateRow(); }
        @Override public void cancelRowUpdates() throws SQLException { checkNotClosed(); delegate.cancelRowUpdates(); }
        @Override public void moveToInsertRow() throws SQLException { checkNotClosed(); delegate.moveToInsertRow(); }
        @Override public void moveToCurrentRow() throws SQLException { checkNotClosed(); delegate.moveToCurrentRow(); }
        @Override public ResultSetMetaData getMetaData() throws SQLException { checkNotClosed(); return delegate.getMetaData(); }
        @Override public int getType() throws SQLException { checkNotClosed(); return delegate.getType(); }
        @Override public int getConcurrency() throws SQLException { checkNotClosed(); return delegate.getConcurrency(); }
        @Override public int getHoldability() throws SQLException { checkNotClosed(); return delegate.getHoldability(); }
        @Override public boolean isFirst() throws SQLException { checkNotClosed(); return delegate.isFirst(); }
        @Override public boolean isLast() throws SQLException { checkNotClosed(); return delegate.isLast(); }
        @Override public void insertRow() throws SQLException { checkNotClosed(); delegate.insertRow(); }
        @Override public void deleteRow() throws SQLException { checkNotClosed(); delegate.deleteRow(); }
        
        // 其他getter和setter方法的简化实现...
        @Override public String getString(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getString(columnIndex); }
        @Override public boolean getBoolean(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getBoolean(columnIndex); }
        @Override public byte getByte(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getByte(columnIndex); }
        @Override public short getShort(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getShort(columnIndex); }
        @Override public int getInt(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getInt(columnIndex); }
        @Override public long getLong(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getLong(columnIndex); }
        @Override public float getFloat(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getFloat(columnIndex); }
        @Override public double getDouble(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getDouble(columnIndex); }
        @Override public java.math.BigDecimal getBigDecimal(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getBigDecimal(columnIndex); }
        @Override public byte[] getBytes(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getBytes(columnIndex); }
        @Override public java.sql.Date getDate(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getDate(columnIndex); }
        @Override public java.sql.Time getTime(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getTime(columnIndex); }
        @Override public java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException { checkNotClosed(); return delegate.getTimestamp(columnIndex); }
        @Override public boolean wasNull() throws SQLException { checkNotClosed(); return delegate.wasNull(); }
        
        private void checkNotClosed() throws SQLException {
            if (closed) {
                throw new SQLException("ResultSet is already closed");
            }
        }
        
        // 省略其余未实现的ResultSet方法
    }
}