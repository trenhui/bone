package com.bone.tool.codegen.adapter;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.tool.codegen.adapter.CodegenController;
import com.bone.tool.codegen.application.dto.*;
import com.bone.tool.codegen.domain.service.CodegenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/**
 * CodegenController的单元测试类
 * <p>
 * 测试代码生成控制器的RESTful API接口
 * 
 * @author bone-team
 */
public class CodegenControllerTest {

    @Mock
    private CodegenService codegenService;

    @InjectMocks
    private CodegenController codegenController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private GenerateCustomCodeRequest mockGenerateCustomCodeRequest;
    private CodegenCreateListRequest mockCodegenCreateListRequest;
    private CodegenUpdateRequest mockCodegenUpdateRequest;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(codegenController).build();
        objectMapper = new ObjectMapper();
        initMockData();
    }

    private void initMockData() {
        // 初始化GenerateCustomCodeRequest模拟数据
        mockGenerateCustomCodeRequest = new GenerateCustomCodeRequest();
        mockGenerateCustomCodeRequest.setDataSourceConfigId(1L);
        mockGenerateCustomCodeRequest.setTableNames(Arrays.asList("table1", "table2"));
        mockGenerateCustomCodeRequest.setProjectName("test-project");
        mockGenerateCustomCodeRequest.setModuleName("system");
        mockGenerateCustomCodeRequest.setBasePackage("com.example");
        mockGenerateCustomCodeRequest.setModelType("saas");
        mockGenerateCustomCodeRequest.setScene("single");
        mockGenerateCustomCodeRequest.setAuthor("bone-team");
        
        // 初始化CodegenCreateListRequest模拟数据
        mockCodegenCreateListRequest = new CodegenCreateListRequest();
        mockCodegenCreateListRequest.setDataSourceConfigId(1L);
        mockCodegenCreateListRequest.setTableNames(Arrays.asList("table1", "table2"));
        
        // 初始化CodegenUpdateRequest模拟数据
        mockCodegenUpdateRequest = new CodegenUpdateRequest();
        CodegenTableSaveRequest tableSaveRequest = new CodegenTableSaveRequest();
        tableSaveRequest.setId(1L);
        tableSaveRequest.setTableName("table1");
        tableSaveRequest.setModuleName("system");
        tableSaveRequest.setPackgeName("com.example");
        mockCodegenUpdateRequest.setTable(tableSaveRequest);
        mockCodegenUpdateRequest.setColumns(Collections.emptyList());
    }

    @Test
    public void testGenerateCustomCode_Success() throws Exception {
        // 模拟服务层行为 - 返回字节数组
        byte[] mockZipBytes = "mock zip content".getBytes();
        when(codegenService.generateCustomCode(any(GenerateCustomCodeRequest.class))).thenReturn(mockZipBytes);

        // 执行HTTP请求并验证结果
        mockMvc.perform(post("/api/v1/codegen/generate/custom")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockGenerateCustomCodeRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(content().bytes(mockZipBytes));

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateCustomCode(any(GenerateCustomCodeRequest.class));
    }

    @Test
    public void testGenerateCustomCode_Exception() throws Exception {
        // 模拟服务层抛出异常
        when(codegenService.generateCustomCode(any(GenerateCustomCodeRequest.class)))
                .thenThrow(new RuntimeException("生成代码失败"));

        // 由于Controller直接throws Exception，我们需要在测试中捕获这个异常
        try {
            mockMvc.perform(post("/api/v1/codegen/generate/custom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mockGenerateCustomCodeRequest)));
        } catch (Exception e) {
            // 验证异常是否由我们模拟的RuntimeException引起
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("生成代码失败", e.getCause().getMessage());
        }

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateCustomCode(any(GenerateCustomCodeRequest.class));
    }

    @Test
    public void testGetDatabaseTableList() throws Exception {
        // 模拟数据（Controller中直接返回空列表，无需模拟服务层行为）

        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/db/table/list")
                .param("dataSourceConfigId", "1")
                .param("name", "table")
                .param("comment", "注释"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testGetCodegenTableList() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/table/list")
                .param("dataSourceConfigId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testGetCodegenTablePage() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/table/page")
                .param("pageNo", "1")
                .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    public void testGetCodegenDetail() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/detail")
                .param("tableId", "1024"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    public void testCreateCodegenList() throws Exception {
        // 模拟服务层行为
        List<Long> mockIds = Arrays.asList(1L, 2L);

        // 执行HTTP请求并验证结果
        mockMvc.perform(post("/api/v1/codegen/create-list")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockCodegenCreateListRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    public void testUpdateCodegen() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(put("/api/v1/codegen/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockCodegenUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testSyncCodegenFromDB() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(put("/api/v1/codegen/sync-from-db")
                .param("tableId", "1024"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testDeleteCodegen() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(delete("/api/v1/codegen/delete")
                .param("tableId", "1024"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    public void testDownloadCodegen_SingleTable() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/download")
                .param("tableId", "1024"))
                .andExpect(status().isOk());
    }

    @Test
    public void testDownloadCodegen_MultiTables() throws Exception {
        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/codegen/download2")
                .param("tableId", "1024")
                .param("tableId", "1025"))
                .andExpect(status().isOk());
    }
}