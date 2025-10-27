package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.service.CodegenServiceImpl;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import com.bone.tool.codegen.domain.service.DataSourceConfigService;
import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.domain.service.generator.DefaultCodeGenerator;
import com.bone.tool.codegen.domain.service.renderer.VelocityTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试CodeGenerationController的generateCustomCode方法功能 - 基于业界最佳实践
 * 包含多种场景测试：不同模板类型、不同生成场景、边界情况和异常情况
 */
public class GenerateCustomCodeTest {

    // 使用实际的CodegenService实例进行测试
    private CodegenServiceImpl codegenService;
    
    // 模拟所有依赖项
    @Mock
    private CodegenTableRepository codegenTableRepository;
    @Mock
    private CodegenColumnRepository codegenColumnRepository;
    @Mock
    private DefaultCodeGenerator codeGenerator;
    @Mock
    private DatabaseTableService databaseTableService;
    @Mock
    private DataSourceConfigService dataSourceConfigService;
    @Mock
    private VelocityTemplateRenderer templateRenderer;
    @Mock
    private CodegenConverter codegenConverter;

    @BeforeEach
    public void setUp() {
        // 初始化测试环境
        MockitoAnnotations.openMocks(this);
        // 使用正确的构造函数创建CodegenService实例
        codegenService = new CodegenServiceImpl(
            codeGenerator,
            databaseTableService,
            codegenTableRepository,
            templateRenderer
        );
    }
    
    // 辅助方法：验证基础结果
    private void assertBasicResult(GenerateCustomCodeRequest request, byte[] zipBytes, String testCaseName) {
        // 验证返回结果不为null
        assertTrue(zipBytes != null, testCaseName + " - 返回的字节数组不应为null");
        assertTrue(zipBytes.length > 0, testCaseName + " - 生成的ZIP文件大小应为正数，实际大小: " + zipBytes.length + " 字节");
        
        // 输出结果信息以便调试
        System.out.println("\n=== " + testCaseName + " 测试结果 ===");
        System.out.println("- 模板类型: " + request.getModelType());
        System.out.println("- 生成场景: " + request.getScene());
        System.out.println("- 表数量: " + request.getTableNames().size());
        System.out.println("- 生成的ZIP文件大小: " + zipBytes.length + " 字节");
        System.out.println("- 执行状态: 成功");
    }

    /**
     * 基础测试 - 验证正常情况下的代码生成流程
     */
    @Test
    public void testGenerateCustomCode_BasicFlow() {
        // 1. 创建请求参数
        GenerateCustomCodeRequest request = createDefaultRequest();

        // 2. 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);

        // 3. 验证结果
        assertBasicResult(request, zipBytes, "基础流程");
    }

    /**
     * 测试SAAS模板类型的代码生成
     */
    @Test
    public void testGenerateCustomCode_SaasTemplate() {
        // 1. 创建请求参数 - 设置SAAS模板
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setModelType("saas");
        request.setProjectName("saas-demo-project");

        // 2. 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);

        // 3. 验证结果
        assertBasicResult(request, zipBytes, "SAAS模板");
        assertEquals("saas", request.getModelType(), "模板类型应为saas");
    }

    /**
     * 测试DDD模板类型的代码生成
     */
    @Test
    public void testGenerateCustomCode_DddTemplate() {
        // 1. 创建请求参数 - 设置DDD模板
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setModelType("ddd");
        request.setProjectName("ddd-demo-project");
        request.setModuleName("domain");

        // 2. 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);

        // 3. 验证结果
        assertBasicResult(request, zipBytes, "DDD模板");
        assertEquals("ddd", request.getModelType(), "模板类型应为ddd");
    }

    /**
     * 测试批量生成场景
     */
    @Test
    public void testGenerateCustomCode_BatchScene() {
        // 1. 创建请求参数 - 批量场景
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setScene("batch");
        // 添加更多表以模拟批量场景
        request.setTableNames(Arrays.asList("user", "order", "product", "payment", "shipping"));

        // 2. 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);

        // 3. 验证结果
        assertBasicResult(request, zipBytes, "批量生成场景");
        assertEquals("batch", request.getScene(), "生成场景应为batch");
        assertTrue(request.getTableNames().size() > 2, "批量场景应包含多个表");
    }

    /**
     * 测试单表生成场景
     */
    @Test
    public void testGenerateCustomCode_SingleTableScene() {
        // 1. 创建请求参数 - 单表场景
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setScene("single");
        request.setTableNames(Collections.singletonList("user")); // 只有一个表

        // 2. 调用服务层方法生成代码
        byte[] zipBytes = codegenService.generateCustomCode(request);

        // 3. 验证结果
        assertBasicResult(request, zipBytes, "单表生成场景");
        assertEquals("single", request.getScene(), "生成场景应为single");
        assertEquals(1, request.getTableNames().size(), "单表场景应只包含一个表");
    }

    /**
     * 测试边界情况 - 表名列表为空
     */
    @Test
    public void testGenerateCustomCode_WithEmptyTableNames() {
        // 1. 创建请求参数 - 表名列表为空
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setTableNames(Collections.emptyList());

        // 2. 验证异常抛出
        Executable executable = () -> codegenService.generateCustomCode(request);
        Exception exception = assertThrows(Exception.class, executable, "表名列表为空时应抛出异常");
        assertTrue(exception.getMessage().contains("表名列表不能为空"), "异常消息应包含'表名列表不能为空'");
    }

    /**
     * 测试边界情况 - 数据源配置ID为null
     */
    @Test
    public void testGenerateCustomCode_WithNullDataSourceConfigId() {
        // 1. 创建请求参数 - 数据源配置ID为null
        GenerateCustomCodeRequest request = createDefaultRequest();
        request.setDatasourceId(null);

        // 2. 验证异常抛出
        Executable executable = () -> codegenService.generateCustomCode(request);
        Exception exception = assertThrows(Exception.class, executable, "数据源配置ID为null时应抛出异常");
        assertTrue(exception.getMessage().contains("数据源配置ID不能为空"), "异常消息应包含'数据源配置ID不能为空'");
    }
    
    /**
     * 测试新的generateCustomCode重载方法 - 直接写入OutputStream
     */
    @Test
    public void testGenerateCustomCode_WithOutputStream() throws IOException {
        // 1. 创建请求参数和输出流
        GenerateCustomCodeRequest request = createDefaultRequest();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // 2. 调用服务层方法生成代码并写入输出流
        codegenService.generateCustomCode(request, outputStream);

        // 3. 验证结果
        byte[] zipBytes = outputStream.toByteArray();
        assertNotNull(zipBytes, "生成的ZIP字节数组不应为空");
        assertTrue(zipBytes.length > 0, "生成的ZIP字节数组长度应大于0");
        System.out.println("使用OutputStream生成的ZIP大小: " + zipBytes.length + " 字节");
    }
    
    /**
     * 测试generateCustomCode重载方法 - 输出流为null的情况
     */
    @Test
    public void testGenerateCustomCode_WithNullOutputStream() {
        // 1. 创建请求参数
        GenerateCustomCodeRequest request = createDefaultRequest();

        // 2. 验证异常抛出
        Executable executable = () -> codegenService.generateCustomCode(request, null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable, 
                "输出流为null时应抛出IllegalArgumentException");
        assertEquals("输出流不能为空", exception.getMessage(), "异常消息应为'输出流不能为空'");
    }

    /**
     * 辅助方法：创建默认的请求参数
     */
    private GenerateCustomCodeRequest createDefaultRequest() {
        GenerateCustomCodeRequest request = new GenerateCustomCodeRequest();
        request.setDatasourceId(1L);
        request.setTableNames(Arrays.asList("user", "order"));
        request.setProjectName("demo-project");
        request.setModuleName("system");
        request.setBasePackage("com.example");
        request.setModelType("saas"); // 默认使用saas模板
        request.setScene("single"); // 默认单表场景
        request.setAuthor("bone-team");
        return request;
    }


}