package com.bone.tool.codegen.domain.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bone.core.model.PageResult;
import com.bone.tool.codegen.application.dto.CodegenTablePageRequest;
import com.bone.tool.codegen.application.dto.CodegenTableRequest;
import com.bone.tool.codegen.application.dto.CodegenDetailResponse;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.domain.entity.DataSourceConfig;
import com.bone.tool.codegen.domain.entity.CodegenTable;
import com.bone.tool.codegen.domain.entity.CodegenColumn;
import com.bone.tool.codegen.domain.entity.TableInfo;
import com.bone.tool.codegen.domain.repository.DataSourceConfigRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.DatabaseTableRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Date;

@ExtendWith(MockitoExtension.class)
public class DatabaseTableServiceTest {

    private static final Logger log = LoggerFactory.getLogger(DatabaseTableServiceTest.class);

    @InjectMocks
    private DatabaseTableService databaseTableService;

    @Mock
    private DataSourceConfigRepository dataSourceConfigRepository;

    @Mock
    private CodegenTableRepository codegenTableRepository;

    @Mock
    private CodegenColumnRepository codegenColumnRepository;

    @Mock
    private DatabaseTableRepository databaseTableRepository;

    @Mock
    private CodegenConverter codegenConverter;

    private Long mockDataSourceConfigId = 1L;
    private String mockTableName = "test_table";
    private TableInfo mockTableInfo;
    private CodegenTable mockCodegenTable;
    private List<CodegenColumn> mockColumns;

    @BeforeEach
    void setUp() {
        // 初始化模拟数据
        mockTableInfo = new TableInfo();
        mockTableInfo.setName(mockTableName);
        mockTableInfo.setComment("测试表");
        mockTableInfo.setEntityName("TestTable");
        mockTableInfo.setFieldName("testTable");

        mockColumns = Arrays.asList(
            createMockColumn(1L, "id", "BIGINT", "Long", true, false),
            createMockColumn(2L, "name", "VARCHAR", "String", false, false),
            createMockColumn(3L, "create_time", "DATETIME", "LocalDateTime", false, false)
        );
        mockTableInfo.setFields(mockColumns);

        mockCodegenTable = new CodegenTable();
        mockCodegenTable.setId(1L);
        mockCodegenTable.setDataSourceConfigId(mockDataSourceConfigId);
        mockCodegenTable.setTableName(mockTableName);
        mockCodegenTable.setTableComment("测试表");
        mockCodegenTable.setModuleName("test-module");
        mockCodegenTable.setPackageName("com.example.test");
        mockCodegenTable.setCreateTime(new Date());
        mockCodegenTable.setUpdateTime(new Date());
    }

    private CodegenColumn createMockColumn(Long id, String columnName, String dataType, String javaType, 
                                         boolean primaryKey, boolean autoIncrement) {
        CodegenColumn column = new CodegenColumn();
        column.setId(id);
        column.setColumnName(columnName);
        column.setDataType(dataType);
        column.setJavaType(javaType);
        column.setColumnComment(columnName + "字段");
        column.setPrimaryKey(primaryKey);
        column.setAutoIncrement(autoIncrement);
        return column;
    }

    @Test
    void testGetTableList_Success() throws Exception {
        // 准备
        List<TableInfo> expectedTables = Arrays.asList(mockTableInfo);
        when(databaseTableRepository.getTableList(mockDataSourceConfigId, null)).thenReturn(expectedTables);

        // 执行
        List<TableInfo> actualTables = databaseTableService.getTableList(mockDataSourceConfigId, null, null);

        // 验证
        assertNotNull(actualTables);
        assertEquals(1, actualTables.size());
        assertEquals(mockTableName, actualTables.get(0).getName());
        verify(databaseTableRepository, times(1)).getTableList(mockDataSourceConfigId, null);
    }

    @Test
    void testGetTableList_WithNameLikeFilter() throws Exception {
        // 准备
        TableInfo table1 = new TableInfo();
        table1.setName("user_info");
        TableInfo table2 = new TableInfo();
        table2.setName("role_info");
        List<TableInfo> allTables = Arrays.asList(table1, table2);
        when(databaseTableRepository.getTableList(mockDataSourceConfigId, null)).thenReturn(allTables);

        // 执行
        List<TableInfo> filteredTables = databaseTableService.getTableList(mockDataSourceConfigId, "user", null);

        // 验证
        assertNotNull(filteredTables);
        assertEquals(1, filteredTables.size());
        assertEquals("user_info", filteredTables.get(0).getName());
    }

    @Test
    void testGetTableList_NullDataSourceConfigId() {
        // 执行 & 验证
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            databaseTableService.getTableList(null, null, null);
        });
        assertTrue(exception.getMessage().contains("数据源配置ID不能为空"));
    }

    @Test
    void testGetTables_Success() throws Exception {
        // 准备
        List<String> tableNames = Arrays.asList(mockTableName, "another_table");
        TableInfo anotherTable = new TableInfo();
        anotherTable.setName("another_table");
        
        when(databaseTableRepository.getTableInfo(mockDataSourceConfigId, mockTableName)).thenReturn(mockTableInfo);
        when(databaseTableRepository.getTableInfo(mockDataSourceConfigId, "another_table")).thenReturn(anotherTable);

        // 执行
        List<TableInfo> tables = databaseTableService.getTables(mockDataSourceConfigId, tableNames);

        // 验证
        assertNotNull(tables);
        assertEquals(2, tables.size());
        verify(databaseTableRepository, times(1)).getTableInfo(mockDataSourceConfigId, mockTableName);
        verify(databaseTableRepository, times(1)).getTableInfo(mockDataSourceConfigId, "another_table");
    }

    @Test
    void testGetTables_EmptyTableNames() {
        // 执行
        List<TableInfo> tables = databaseTableService.getTables(mockDataSourceConfigId, Collections.emptyList());

        // 验证
        assertNotNull(tables);
        assertTrue(tables.isEmpty());
    }

    @Test
    void testGetTables_NullDataSourceConfigId() {
        // 执行 & 验证
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            databaseTableService.getTables(null, Arrays.asList(mockTableName));
        });
        assertTrue(exception.getMessage().contains("数据源配置ID不能为空"));
    }

    @Test
    void testImportTablesFromDatabase_Success() throws Exception {
        // 准备
        List<String> tableNames = Arrays.asList(mockTableName);
        String moduleName = "test-module";
        String packageName = "com.example.test";
        Integer sceneType = 1;
        Integer modelType = 1;
        
        when(databaseTableRepository.getTableInfo(mockDataSourceConfigId, mockTableName)).thenReturn(mockTableInfo);
        when(codegenTableRepository.save(any(CodegenTable.class))).thenReturn(1L);

        // 执行
        List<Long> tableIds = databaseTableService.importTablesFromDatabase(
                mockDataSourceConfigId, tableNames, moduleName, packageName, sceneType, modelType);

        // 验证
        assertNotNull(tableIds);
        assertEquals(1, tableIds.size());
        assertEquals(mockCodegenTable.getId(), tableIds.get(0));
        verify(codegenColumnRepository, times(3)).save(any(CodegenColumn.class));
    }

    @Test
    void testGetColumnsByTableId_Success() {
        // 准备
        Long tableId = 1L;
        when(codegenColumnRepository.findByCriteria(any())).thenReturn(mockColumns);

        // 执行
        List<CodegenColumn> columns = databaseTableService.getColumnsByTableId(tableId);

        // 验证
        assertNotNull(columns);
        assertEquals(3, columns.size());
    }

    @Test
    void testDeleteTable_Success() {
        // 准备
        Long tableId = 1L;
        when(codegenColumnRepository.findByCriteria(any())).thenReturn(mockColumns);

        // 执行
        databaseTableService.deleteTable(tableId);

        // 验证
        verify(codegenColumnRepository, times(3)).deleteById(anyLong());
        verify(codegenTableRepository, times(1)).deleteById(tableId);
    }

    @Test
    void testDeleteTable_NullTableId() {
        // 执行 & 验证
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            databaseTableService.deleteTable(null);
        });
        assertTrue(exception.getMessage().contains("表ID不能为空"));
    }

    @Test
    void testUpdateCodegenTable_Success() {
        // 准备
        CodegenTableRequest request = new CodegenTableRequest();
        request.setId(1L);
        request.setModuleName("updated-module");
        request.setPackageName("com.example.updated");
        request.setClassName("UpdatedClass");
        
        when(codegenTableRepository.findById(1L)).thenReturn(mockCodegenTable);

        // 执行
        databaseTableService.updateCodegenTable(request);

        // 验证
        verify(codegenTableRepository, times(1)).update(mockCodegenTable);
        assertEquals("updated-module", mockCodegenTable.getModuleName());
    }

    @Test
    void testUpdateCodegenTable_NullId() {
        // 准备
        CodegenTableRequest request = new CodegenTableRequest();

        // 执行 & 验证
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            databaseTableService.updateCodegenTable(request);
        });
        assertTrue(exception.getMessage().contains("表ID不能为空"));
    }

    @Test
    void testUpdateCodegenTable_TableNotFound() {
        // 准备
        CodegenTableRequest request = new CodegenTableRequest();
        request.setId(999L);
        
        when(codegenTableRepository.findById(999L)).thenReturn(null);

        // 执行 & 验证
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            databaseTableService.updateCodegenTable(request);
        });
        assertTrue(exception.getMessage().contains("表配置不存在"));
    }
}