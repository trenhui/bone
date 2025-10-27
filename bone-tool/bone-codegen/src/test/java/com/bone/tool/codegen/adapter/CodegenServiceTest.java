package com.bone.tool.codegen.adapter;

import com.bone.tool.codegen.application.converter.CodegenConverter;
import com.bone.tool.codegen.application.dto.GenerateCustomCodeRequest;
import com.bone.tool.codegen.domain.repository.CodegenTableRepository;
import com.bone.tool.codegen.domain.repository.CodegenColumnRepository;
import com.bone.tool.codegen.domain.service.CodegenService;
import com.bone.tool.codegen.domain.service.CodegenServiceImpl;
import com.bone.tool.codegen.domain.service.DataSourceConfigService;
import com.bone.tool.codegen.domain.service.DatabaseTableService;
import com.bone.tool.codegen.domain.service.generator.DefaultCodeGenerator;
import com.bone.tool.codegen.domain.service.renderer.VelocityTemplateRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 代码生成服务测试类
 * 实际测试代码生成功能并输出到文件系统
 */
@ExtendWith(MockitoExtension.class)
public class CodegenServiceTest {

    @InjectMocks
    private CodegenServiceImpl codegenService;
    
    @Mock
    private CodegenTableRepository codegenTableRepository;
    
    @Mock
    private CodegenColumnRepository codegenColumnRepository;
    
    @Mock
    private CodegenConverter codegenConverter;
    
    @Mock
    private DefaultCodeGenerator defaultCodeGenerator;
    
    @Mock
    private DatabaseTableService databaseTableService;
    
    @Mock
    private DataSourceConfigService dataSourceConfigService;
    
    @Mock
    private VelocityTemplateRenderer velocityTemplateRenderer;
    
    @BeforeEach
    void setUp() {
        // 可以在这里设置mock的行为
    }

    /**
     * 测试生成单个表的代码并保存为ZIP文件
     */
    @Test
    public void testGenerateSingleTableCode() throws Exception {
        // 创建测试输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 创建输出文件路径
        String outputFilePath = outputDir.resolve("user-table-code.zip").toString();
        File outputFile = new File(outputFilePath);
        
        // 准备生成代码请求参数
        GenerateCustomCodeRequest request = new GenerateCustomCodeRequest();
        request.setDatasourceId(1L);
        request.setTableNames(List.of("user"));
        request.setProjectName("test-project");
        request.setModuleName("system");
        request.setBasePackage("com.example");
        request.setModelType("saas");
        request.setScene("single");
        request.setAuthor("test-author");
        
        // 生成代码并写入文件
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            codegenService.generateCustomCode(request, outputStream);
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(outputFilePath)), "代码文件压缩包未生成");
        assertTrue(outputFile.length() > 0, "生成的ZIP文件为空");
        
        System.out.println("代码生成成功！文件路径: " + outputFilePath);
    }

    /**
     * 测试生成多个表的代码并保存为ZIP文件
     */
    @Test
    public void testGenerateMultiTableCode() throws Exception {
        // 创建测试输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 创建输出文件路径
        String outputFilePath = outputDir.resolve("multi-tables-code.zip").toString();
        File outputFile = new File(outputFilePath);
        
        // 准备生成代码请求参数
        GenerateCustomCodeRequest request = new GenerateCustomCodeRequest();
        request.setDatasourceId(1L);
        request.setTableNames(Arrays.asList("user", "role", "permission"));
        request.setProjectName("test-project");
        request.setModuleName("system");
        request.setBasePackage("com.example");
        request.setModelType("saas");
        request.setScene("multi");
        request.setAuthor("test-author");
        
        // 生成代码并写入文件
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            codegenService.generateCustomCode(request, outputStream);
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(outputFilePath)), "多表代码文件压缩包未生成");
        assertTrue(outputFile.length() > 0, "生成的ZIP文件为空");
        
        System.out.println("多表代码生成成功！文件路径: " + outputFilePath);
    }

    /**
     * 测试最小配置生成代码
     */
    @Test
    public void testGenerateMinimalConfigCode() throws Exception {
        // 创建测试输出目录
        Path outputDir = Paths.get("./target/generated-test-code");
        Files.createDirectories(outputDir);
        
        // 创建输出文件路径
        String outputFilePath = outputDir.resolve("minimal-code.zip").toString();
        File outputFile = new File(outputFilePath);
        
        // 准备最小配置的生成代码请求
        GenerateCustomCodeRequest request = new GenerateCustomCodeRequest();
        request.setDatasourceId(1L);
        request.setTableNames(List.of("user"));
        request.setModuleName("system");
        request.setBasePackage("com.example");
        request.setModelType("saas");
        request.setScene("single");
        
        // 生成代码并写入文件
        try (OutputStream outputStream = new FileOutputStream(outputFile)) {
            codegenService.generateCustomCode(request, outputStream);
        }
        
        // 验证文件是否生成成功
        assertTrue(Files.exists(Paths.get(outputFilePath)), "最小配置代码文件压缩包未生成");
        assertTrue(outputFile.length() > 0, "生成的ZIP文件为空");
        
        System.out.println("最小配置代码生成成功！文件路径: " + outputFilePath);
    }
}