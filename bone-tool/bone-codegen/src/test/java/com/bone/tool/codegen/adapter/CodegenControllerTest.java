package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.application.service.CodegenService;
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

import java.io.OutputStream;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CodeGenerationController的单元测试类
 * <p>
 * 测试代码生成控制器的RESTful API接口
 * 
 * @author bone-team
 */
public class CodegenControllerTest {

    @Mock
    private CodegenService codegenService;

    @InjectMocks
    private CodeGenerationController codegenController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private GenerateCustomCodeRequest mockGenerateCustomCodeRequest;

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
        mockGenerateCustomCodeRequest.setDatasourceId(1L);
        mockGenerateCustomCodeRequest.setTableNames(Arrays.asList("table1", "table2"));
        mockGenerateCustomCodeRequest.setProjectName("test-project");
        mockGenerateCustomCodeRequest.setModuleName("system");
        mockGenerateCustomCodeRequest.setBasePackage("com.example");
        mockGenerateCustomCodeRequest.setModelType("saas");
        mockGenerateCustomCodeRequest.setScene("single");
        mockGenerateCustomCodeRequest.setAuthor("bone-team");
    }

    @Test
    public void testGenerateCustomCode_Success() throws Exception {
        // 模拟服务层行为 - 写入输出流
        doNothing().when(codegenService).generateCustomCode(any(GenerateCustomCodeRequest.class), any(OutputStream.class));

        // 执行HTTP请求并验证结果
        mockMvc.perform(post("/api/v1/code-generation/generate/custom")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mockGenerateCustomCodeRequest)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateCustomCode(any(GenerateCustomCodeRequest.class), any(OutputStream.class));
    }

    @Test
    public void testGenerateCustomCode_Exception() throws Exception {
        // 模拟服务层抛出异常
        doThrow(new RuntimeException("生成代码失败")).when(codegenService)
                .generateCustomCode(any(GenerateCustomCodeRequest.class), any(OutputStream.class));

        // 由于Controller直接throws Exception，我们需要在测试中捕获这个异常
        try {
            mockMvc.perform(post("/api/v1/code-generation/generate/custom")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mockGenerateCustomCodeRequest)));
        } catch (Exception e) {
            // 验证异常是否由我们模拟的RuntimeException引起
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("生成代码失败", e.getCause().getMessage());
        }

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateCustomCode(any(GenerateCustomCodeRequest.class), any(OutputStream.class));
    }

    @Test
    public void testGenerateAndDownloadCode_Success() throws Exception {
        // 模拟服务层行为
        doNothing().when(codegenService).generateBatchCodes(anyList(), anyString(), anyInt(), any(OutputStream.class));

        // 执行HTTP请求并验证结果
        mockMvc.perform(get("/api/v1/code-generation/generate/batch")
                .param("tableIds", "1,2,3")
                .param("groupId", "default")
                .param("modelType", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"));

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateBatchCodes(anyList(), anyString(), anyInt(), any(OutputStream.class));
    }

    @Test
    public void testGenerateAndDownloadCode_Exception() throws Exception {
        // 模拟服务层抛出异常
        doThrow(new RuntimeException("下载失败")).when(codegenService)
                .generateBatchCodes(anyList(), anyString(), anyInt(), any(OutputStream.class));

        // 由于Controller直接throws Exception，我们需要在测试中捕获这个异常
        try {
            mockMvc.perform(get("/api/v1/code-generation/generate/batch")
                    .param("tableIds", "1,2,3")
                    .param("groupId", "default")
                    .param("modelType", "1"));
        } catch (Exception e) {
            // 验证异常是否由我们模拟的RuntimeException引起
            assertTrue(e.getCause() instanceof RuntimeException);
            assertEquals("下载失败", e.getCause().getMessage());
        }

        // 验证服务层方法是否被调用
        verify(codegenService, times(1)).generateBatchCodes(anyList(), anyString(), anyInt(), any(OutputStream.class));
    }
}