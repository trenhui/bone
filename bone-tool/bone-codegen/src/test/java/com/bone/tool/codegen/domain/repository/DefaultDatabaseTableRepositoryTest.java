package com.bone.tool.codegen.domain.repository;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;

import java.sql.*;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class DefaultDatabaseTableRepositoryTest {

    @InjectMocks
    private DefaultDatabaseTableRepository repository;

    @Mock
    private DataSourceConfigRepository dataSourceConfigRepository;

    @Mock
    private Connection mockConnection;

    @Mock
    private DatabaseMetaData mockMetaData;

    @Mock
    private ResultSet mockTablesResultSet;

    @Mock
    private ResultSet mockColumnsResultSet;

    @Mock
    private ResultSet mockPrimaryKeysResultSet;

    private Long mockDataSourceConfigId = 1L;
    private String mockTableName = "test_table";
    private String mockSchema = "public";

    @BeforeEach
    void setUp() throws Exception {
        // 模拟数据源配置
        DataSourceConfig config = new DataSourceConfig();
        config.setId(mockDataSourceConfigId);
        config.setUrl("jdbc:mysql://localhost:3306/test");
        config.setUsername("root");
        config.setPassword("password");
        
        when(dataSourceConfigRepository.findById(mockDataSourceConfigId)).thenReturn(config);
        
        // 注意：由于测试环境限制，这里不再模拟DriverManager的静态方法调用
        // 在实际运行时，测试会使用真实的数据库连接或其他模拟方式
        
        // 模拟连接的基本操作
        when(mockConnection.getSchema()).thenReturn(mockSchema);
        when(mockConnection.getMetaData()).thenReturn(mockMetaData);
    }
    
    // 移除AfterEach方法，因为我们已经移除了静态mock

    @Test
    void testGetConnection_Success() throws Exception {
        // 执行
        Connection connection = repository.getConnection(mockDataSourceConfigId);
        
        // 验证
        assertNotNull(connection);
        verify(dataSourceConfigRepository, times(1)).findById(mockDataSourceConfigId);
        verify(connection, times(1)).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @Test
    void testGetConnection_DataSourceConfigNotFound() {
        // 准备
        when(dataSourceConfigRepository.findById(mockDataSourceConfigId)).thenReturn(null);
        
        // 执行 & 验证
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            repository.getConnection(mockDataSourceConfigId);
        });
        assertTrue(exception.getMessage().contains("数据源配置不存在"));
    }

    @Test
    void testGetDatabaseMetaData_Success() throws Exception {
        // 执行
        DatabaseMetaData metaData = repository.getDatabaseMetaData(mockConnection);
        
        // 验证
        assertNotNull(metaData);
        assertEquals(mockMetaData, metaData);
    }

    @Test
    void testGetTableList_Success() throws Exception {
        // 准备
        when(mockMetaData.getTables(null, mockSchema, null, new String[]{"TABLE"})).thenReturn(mockTablesResultSet);
        when(mockTablesResultSet.next()).thenReturn(true, false);
        when(mockTablesResultSet.getString("TABLE_NAME")).thenReturn(mockTableName);
        when(mockTablesResultSet.getString("REMARKS")).thenReturn("Test Table Comment");
        
        // 模拟获取表字段
        when(mockMetaData.getPrimaryKeys(null, mockSchema, mockTableName)).thenReturn(mockPrimaryKeysResultSet);
        when(mockPrimaryKeysResultSet.next()).thenReturn(false);
        when(mockMetaData.getColumns(null, mockSchema, mockTableName, null)).thenReturn(mockColumnsResultSet);
        when(mockColumnsResultSet.next()).thenReturn(false);
        
        // 执行
        List<TableInfo> tables = repository.getTableList(mockDataSourceConfigId, null);
        
        // 验证
        assertNotNull(tables);
        assertEquals(1, tables.size());
        assertEquals(mockTableName, tables.get(0).getName());
        assertEquals("Test Table Comment", tables.get(0).getComment());
        assertEquals("TestTable", tables.get(0).getEntityName());
    }

    @Test
    void testGetTableInfo_Success() throws Exception {
        // 准备
        when(mockMetaData.getTables(null, mockSchema, mockTableName, new String[]{"TABLE"})).thenReturn(mockTablesResultSet);
        when(mockTablesResultSet.next()).thenReturn(true);
        when(mockTablesResultSet.getString("REMARKS")).thenReturn("Test Table Comment");
        
        // 模拟获取表字段
        when(mockMetaData.getPrimaryKeys(null, mockSchema, mockTableName)).thenReturn(mockPrimaryKeysResultSet);
        when(mockPrimaryKeysResultSet.next()).thenReturn(false);
        when(mockMetaData.getColumns(null, mockSchema, mockTableName, null)).thenReturn(mockColumnsResultSet);
        when(mockColumnsResultSet.next()).thenReturn(false);
        
        // 执行
        TableInfo tableInfo = repository.getTableInfo(mockDataSourceConfigId, mockTableName);
        
        // 验证
        assertNotNull(tableInfo);
        assertEquals(mockTableName, tableInfo.getName());
    }

    @Test
    void testGetTableInfo_TableNotFound() throws Exception {
        // 准备
        when(mockMetaData.getTables(null, mockSchema, mockTableName, new String[]{"TABLE"})).thenReturn(mockTablesResultSet);
        when(mockTablesResultSet.next()).thenReturn(false);
        
        // 执行
        TableInfo tableInfo = repository.getTableInfo(mockDataSourceConfigId, mockTableName);
        
        // 验证
        assertNull(tableInfo);
    }

    @Test
    void testCloseConnection_Success() throws Exception {
        // 执行
        repository.closeConnection(mockConnection);
        
        // 验证
        verify(mockConnection, times(1)).close();
    }

    @Test
    void testCloseConnection_NullConnection() {
        // 执行 - 不应该抛出异常
        repository.closeConnection(null);
        // 无需验证，只要不抛出异常即可
    }

    @Test
    void testConvertToEntityName_Success() throws Exception {
        // 反射调用私有方法
        java.lang.reflect.Method method = DefaultDatabaseTableRepository.class.getDeclaredMethod("convertToEntityName", String.class);
        method.setAccessible(true);
        
        // 测试各种表名转换
        assertEquals("UserInfo", method.invoke(repository, "user_info"));
        assertEquals("User", method.invoke(repository, "user"));
        assertEquals("TUserInfo", method.invoke(repository, "t_user_info"));
        assertNull(method.invoke(repository, null));
    }

    @Test
    void testConvertToJavaType_Success() throws Exception {
        // 反射调用私有方法
        java.lang.reflect.Method method = DefaultDatabaseTableRepository.class.getDeclaredMethod("convertToJavaType", String.class, boolean.class);
        method.setAccessible(true);
        
        // 测试各种数据类型转换
        assertEquals("Long", method.invoke(repository, "BIGINT", true));
        assertEquals("Integer", method.invoke(repository, "INT", false));
        assertEquals("BigDecimal", method.invoke(repository, "DECIMAL", false));
        assertEquals("LocalDateTime", method.invoke(repository, "DATETIME", false));
        assertEquals("Boolean", method.invoke(repository, "BOOLEAN", false));
        assertEquals("String", method.invoke(repository, "VARCHAR", false));
        assertEquals("String", method.invoke(repository, null, false));
    }
}