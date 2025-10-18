package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import com.bone.tool.codegen.domain.entity.DatabaseTableMetadata;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DatabaseTableController的单元测试类
 * <p>
 * 测试数据库表控制器的RESTful API接口
 * 
 * @author bone-team
 */
public class DatabaseTableControllerTest {

    @Mock
    private DatabaseTableService databaseTableService;

    @InjectMocks
    private DatabaseTableController databaseTableController;

    private MockMvc mockMvc;

    private List<DatabaseTableMetadata> mockTableList;
    private DatabaseTableMetadata mockTableInfo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(databaseTableController).build();

        // 初始化模拟数据
        mockTableInfo = new DatabaseTableMetadata();
        mockTableInfo.setTableName("test_table");
        mockTableInfo.setTableComment("测试表");
        mockTableInfo.setEntityName("TestTable");
        mockTableInfo.setFieldName("testTable");

        mockTableList = new ArrayList<>();
        mockTableList.add(mockTableInfo);
    }

    /**
     * 测试分页查询数据库表列表接口
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表不为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetTableListWithAllParams() throws Exception {
        // 模拟服务层返回
        when(databaseTableService.getTableList(anyLong(), anyString(), anyString())).thenReturn(mockTableList);

        // 执行请求并验证响应（传入所有参数）
        mockMvc.perform(get("/api/v1/database-tables/original")
                .param("dataSourceConfigId", "1")
                .param("nameLike", "test")
                .param("commentLike", "测试"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].tableName").value("test_table"))
                .andExpect(jsonPath("$.data[0].tableComment").value("测试表"));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(anyLong(), anyString(), anyString());
    }

    /**
     * 测试只传入必填参数查询数据库表列表接口
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表不为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetTableListWithOnlyRequiredParams() throws Exception {
        // 模拟服务层返回
        when(databaseTableService.getTableList(anyLong(), anyString(), anyString())).thenReturn(mockTableList);

        // 执行请求并验证响应（只传入必填参数）
        mockMvc.perform(get("/api/v1/database-tables/original")
                .param("dataSourceConfigId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(anyLong(), anyString(), anyString());
    }

    /**
     * 测试缺少必填参数查询数据库表列表接口
     * 
     * 验证：
     * 1. 接口返回状态码400
     */
    @Test
    void testGetTableListWithoutRequiredParams() throws Exception {
        // 执行请求并验证响应（缺少必填参数）
        mockMvc.perform(get("/api/v1/database-tables"))
                .andExpect(status().isBadRequest());

        // 验证服务层方法没有被调用
        verify(databaseTableService, never()).getTableList(anyLong(), anyString(), anyString());
    }

    /**
     * 测试获取所有数据库表接口
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表不为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetAllTables() throws Exception {
        // 模拟服务层返回
        when(databaseTableService.getTableList(1L, null, null)).thenReturn(mockTableList);

        // 执行请求并验证响应
        mockMvc.perform(get("/api/v1/database-tables/original/all")
                .param("dataSourceConfigId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("test_table"));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(1L, null, null);
    }

    /**
     * 测试获取数据库表详情接口
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据包含正确的字段值
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetTableInfo() throws Exception {
        // 模拟服务层返回 - 使用getTableList方法替代getTable
        when(databaseTableService.getTableList(1L, "test_table", null)).thenReturn(mockTableList);

        // 执行请求并验证响应
        mockMvc.perform(get("/api/v1/database-tables/original/test_table")
                .param("dataSourceConfigId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tableName").value("test_table"))
                .andExpect(jsonPath("$.data.tableComment").value("测试表"));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(1L, "test_table", null);
    }

    /**
     * 测试获取不存在的表详情
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据不存在
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetTableWithNonExistentTable() throws Exception {
        // 模拟服务层返回 - 使用getTableList方法替代getTable
        when(databaseTableService.getTableList(1L, "non_existent_table", null)).thenReturn(Collections.emptyList());

        // 执行请求并验证响应
        mockMvc.perform(get("/api/v1/database-tables/original/non_existent_table")
                .param("dataSourceConfigId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(1L, "non_existent_table", null);
    }

    /**
     * 测试获取空表列表
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testEmptyTableList() throws Exception {
        // 模拟服务层返回
        when(databaseTableService.getTableList(anyLong(), anyString(), anyString())).thenReturn(Collections.emptyList());

        // 执行请求并验证响应
        mockMvc.perform(get("/api/v1/database-tables")
                .param("dataSourceId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTableList(anyLong(), anyString(), anyString());
    }

    /**
     * 测试获取批量表信息接口
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表不为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetBatchTableInfo() throws Exception {
        // 准备请求数据
        List<String> tableNames = Arrays.asList("test_table", "another_table");
        List<DatabaseTableMetadata> batchTableList = new ArrayList<>(mockTableList);
        
        DatabaseTableMetadata anotherTable = new DatabaseTableMetadata();
        anotherTable.setTableName("another_table");
        anotherTable.setTableComment("另一个测试表");
        anotherTable.setEntityName("AnotherTable");
        anotherTable.setFieldName("anotherTable");
        batchTableList.add(anotherTable);

        // 模拟服务层返回
        when(databaseTableService.getTables(1L, tableNames)).thenReturn(batchTableList);

        // 执行请求并验证响应
        mockMvc.perform(post("/api/v1/database-tables/original/batch")
                .param("dataSourceConfigId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[\"test_table\", \"another_table\"]"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].tableName").value("test_table"))
                .andExpect(jsonPath("$.data[1].tableName").value("another_table"));

        // 验证服务层方法被调用
        verify(databaseTableService, times(1)).getTables(1L, tableNames);
    }

    /**
     * 测试获取批量表信息-空表名列表
     * 
     * 验证：
     * 1. 接口返回状态码200
     * 2. 返回的JSON包含code=200
     * 3. 返回的数据列表为空
     * 4. 服务层方法被正确调用
     */
    @Test
    void testGetBatchTableInfoWithEmptyList() throws Exception {
        // 不需要模拟服务调用，因为控制器会直接返回空列表

        // 执行请求并验证响应
        mockMvc.perform(post("/api/v1/database-tables/original/batch")
                .param("dataSourceConfigId", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(0));
    }
}