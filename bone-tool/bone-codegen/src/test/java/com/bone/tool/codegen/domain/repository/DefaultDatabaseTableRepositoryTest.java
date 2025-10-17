package com.bone.tool.codegen.domain.repository;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import java.io.InputStream;
import java.io.IOException;
import java.sql.DriverManager;

import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.Datasource;

import java.sql.*;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class DefaultDatabaseTableRepositoryTest {
    
    private Long mockDataSourceConfigId = 1L;
    private String mockDataSourceUrl = "jdbc:mysql://localhost:3306/test";
    private String mockDataSourceUsername = "root";
    private String mockDataSourcePassword = "password";

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

    private String mockTableName = "test_table";
    private String mockSchema = "public";

    @BeforeEach
    void setUp() throws Exception {
        // 尝试从配置文件读取数据库配置
        try {
            readConfigFromFile();
        } catch (Exception e) {
            // 如果读取失败，使用默认值
            System.out.println("无法从配置文件读取数据库配置，使用默认值: " + e.getMessage());
        }
        
        // 模拟数据源配置 - 使用lenient避免不必要的模拟警告
        Datasource config = new Datasource();
        config.setId(mockDataSourceConfigId);
        config.setUrl(mockDataSourceUrl);
        config.setUsername(mockDataSourceUsername);
        config.setPassword(mockDataSourcePassword);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        Mockito.lenient().when(dataSourceConfigRepository.findById(mockDataSourceConfigId)).thenReturn(config);
        
        // 模拟连接的基本操作 - 使用lenient避免不必要的模拟警告
        Mockito.lenient().when(mockConnection.getSchema()).thenReturn(mockSchema);
        Mockito.lenient().when(mockConnection.getMetaData()).thenReturn(mockMetaData);
    }
    
    // 移除AfterEach方法，因为我们已经移除了静态mock

    @Test
    void testGetConnection_Success() throws Exception {
        // 模拟DriverManager的静态方法调用
        try (MockedStatic<DriverManager> mockedDriverManager = mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() -> DriverManager.getConnection(
                    mockDataSourceUrl, 
                    mockDataSourceUsername, 
                    mockDataSourcePassword))
                    .thenReturn(mockConnection);
            
            // 执行
            Connection connection = repository.getConnection(mockDataSourceConfigId);
            
            // 验证
            assertNotNull(connection);
            verify(dataSourceConfigRepository, times(1)).findById(mockDataSourceConfigId);
            verify(connection, times(1)).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        }
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
        // 模拟DriverManager的静态方法调用
        try (MockedStatic<DriverManager> mockedDriverManager = mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() -> DriverManager.getConnection(
                    mockDataSourceUrl, 
                    mockDataSourceUsername, 
                    mockDataSourcePassword))
                    .thenReturn(mockConnection);
            
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
    }

    @Test
    void testGetTableInfo_Success() throws Exception {
        // 模拟DriverManager的静态方法调用
        try (MockedStatic<DriverManager> mockedDriverManager = mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() -> DriverManager.getConnection(
                    mockDataSourceUrl, 
                    mockDataSourceUsername, 
                    mockDataSourcePassword))
                    .thenReturn(mockConnection);
            
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
    }

    @Test
    void testGetTableInfo_TableNotFound() throws Exception {
        // 模拟DriverManager的静态方法调用
        try (MockedStatic<DriverManager> mockedDriverManager = mockStatic(DriverManager.class)) {
            mockedDriverManager.when(() -> DriverManager.getConnection(
                    mockDataSourceUrl, 
                    mockDataSourceUsername, 
                    mockDataSourcePassword))
                    .thenReturn(mockConnection);
            
            // 准备
            when(mockMetaData.getTables(null, mockSchema, mockTableName, new String[]{"TABLE"})).thenReturn(mockTablesResultSet);
            when(mockTablesResultSet.next()).thenReturn(false);
            
            // 执行
            TableInfo tableInfo = repository.getTableInfo(mockDataSourceConfigId, mockTableName);
            
            // 验证
            assertNull(tableInfo);
        }
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
        assertEquals("TestTable", method.invoke(repository, "test_table"));
        assertEquals("User", method.invoke(repository, "user"));
    }

    @Test
    void testConvertToJavaType_Success() throws Exception {
        // 反射调用私有方法
        java.lang.reflect.Method method = DefaultDatabaseTableRepository.class.getDeclaredMethod("convertToJavaType", String.class, boolean.class);
        method.setAccessible(true);
        
        // 测试各种数据类型转换
        assertEquals("String", method.invoke(repository, "VARCHAR", false));
        assertEquals("Integer", method.invoke(repository, "INT", false));
        assertEquals("Integer", method.invoke(repository, "BIGINT", false));
        assertEquals("Boolean", method.invoke(repository, "BOOLEAN", false));
        assertEquals("BigDecimal", method.invoke(repository, "DECIMAL", false));
        assertEquals("LocalDate", method.invoke(repository, "DATE", false));
        assertEquals("String", method.invoke(repository, "UNKNOWN_TYPE", false));
        assertEquals("String", method.invoke(repository, "VARCHAR(100)", false));
        assertEquals("String", method.invoke(repository, null, false));
    }
    
    /**
     * 从配置文件读取数据库配置
     */
    private void readConfigFromFile() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application-test.yml")) {
            if (input != null) {
                // 简单解析YAML文件的关键部分
                // 这里使用简单的文本解析，实际项目中可以使用专门的YAML解析库
                byte[] buffer = new byte[input.available()];
                input.read(buffer);
                String content = new String(buffer);
                
                // 提取配置值
                if (content.contains("bone.codegen.test.datasource.id")) {
                    mockDataSourceConfigId = Long.valueOf(extractValue(content, "bone.codegen.test.datasource.id"));
                }
                if (content.contains("bone.codegen.test.datasource.url")) {
                    mockDataSourceUrl = extractValue(content, "bone.codegen.test.datasource.url");
                }
                if (content.contains("bone.codegen.test.datasource.username")) {
                    mockDataSourceUsername = extractValue(content, "bone.codegen.test.datasource.username");
                }
                if (content.contains("bone.codegen.test.datasource.password")) {
                    mockDataSourcePassword = extractValue(content, "bone.codegen.test.datasource.password");
                }
            }
        }
    }
    
    /**
     * 从YAML文本中提取值
     */
    private String extractValue(String yamlContent, String key) {
        String[] lines = yamlContent.split("\\n");
        for (String line : lines) {
            if (line.trim().startsWith(key + ":")) {
                return line.split(":", 2)[1].trim();
            }
        }
        return null;
    }
}