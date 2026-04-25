package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.domain.code.GeneratedFile;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.bone.studio.generator.domain.repository.*;
import com.bone.studio.generator.domain.service.FileGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Capability(name = "createCodeGeneration", description = "创建代码生成任务", inputSchema = "{}", outputSchema = "{}")
public class CreateCodeGenerationHandler {

    private final GenerationTaskRepository generationTaskRepository;
    private final DataSourceRepository dataSourceRepository;
    private final GenTableMetadataRepository tableMetadataRepository;
    private final CodeTemplateRepository codeTemplateRepository;
    private final DatabaseMetadataGateway metadataGateway;
    private final List<FileGenerator> fileGenerators;

    @Transactional
    public String handle(CreateCodeGenerationCommand command) {
        // 1. 生成任务ID
        String taskId = UUID.randomUUID().toString();
        
        // 2. 加载数据源
        DataSource dataSource = dataSourceRepository.findById(String.valueOf(command.getDataSourceId()));
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + command.getDataSourceId());
        }
        
        // 3. 加载表元数据
        List<GenTableMetadata> tableMetadatas = new ArrayList<>();
        for (String tableName : command.getTableNames()) {
            // 这里简化实现，实际项目中应该使用 Criteria API 查询
            GenTableMetadata tableMetadata = findTableMetadata(command.getDataSourceId(), tableName);
            if (tableMetadata == null) {
                // 如果表元数据不存在，从数据库同步
                tableMetadata = syncTableMetadata(dataSource, tableName);
            }
            tableMetadatas.add(tableMetadata);
        }
        
        // 4. 加载代码模板
        List<CodeTemplate> templates = new ArrayList<>();
        for (Long templateId : command.getTemplateIds()) {
            CodeTemplate template = codeTemplateRepository.findById(templateId);
            if (template == null) {
                throw new IllegalArgumentException("模板不存在: " + templateId);
            }
            templates.add(template);
        }
        
        // 5. 创建生成任务
        GenerationTask task = GenerationTask.create(
                System.currentTimeMillis(),
                0L, // 租户ID，暂时硬编码
                taskId,
                command.getProjectName(),
                command.getBasePackage(),
                command.getModuleName(),
                command.getDataSourceId(),
                command.getTableNames(),
                command.getTemplateIds(),
                command.getGenConfig()
        );
        
        // 6. 执行代码生成
        task.markProcessing();
        List<GeneratedFile> generatedFiles = new ArrayList<>();
        
        try {
            for (GenTableMetadata table : tableMetadatas) {
                for (CodeTemplate template : templates) {
                    for (FileGenerator generator : fileGenerators) {
                        if (generator.supports(template.getType())) {
                            GeneratedFile file = generator.generate(
                                    table,
                                    template,
                                    command.getBasePackage(),
                                    command.getModuleName()
                            );
                            generatedFiles.add(file);
                            break;
                        }
                    }
                }
            }
            
            // 7. 生成 ZIP 文件（简化实现，实际项目中需要生成真实的 ZIP 文件）
            String zipUrl = "http://localhost:8080/api/generation-tasks/" + taskId + "/download";
            
            // 8. 标记任务完成
            task.markCompleted(generatedFiles, zipUrl);
        } catch (Exception e) {
            // 9. 标记任务失败
            task.markFailed(e.getMessage());
            throw e;
        } finally {
            // 10. 保存任务
            generationTaskRepository.save(task);
        }
        
        return taskId;
    }

    private GenTableMetadata findTableMetadata(Long dataSourceId, String tableName) {
        // 简化实现，实际项目中应该使用 Criteria API 查询
        return null;
    }

    private GenTableMetadata syncTableMetadata(DataSource dataSource, String tableName) {
        GenTableMetadata tableMetadata = GenTableMetadata.create(
                System.currentTimeMillis(),
                0L,
                dataSource.getId(),
                null
        );
        tableMetadataRepository.save(tableMetadata);
        return tableMetadata;
    }
}
