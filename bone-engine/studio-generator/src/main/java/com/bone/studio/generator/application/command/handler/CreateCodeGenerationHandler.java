package com.bone.studio.generator.application.command.handler;

import com.bone.core.usecase.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.exception.MultipleResultsException;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.code.GeneratedFile;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenTableMetadataRepository;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
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
    private final List<FileGenerator> fileGenerators;

    @Transactional
    public String handle(CreateCodeGenerationCommand command) {
        String taskId = UUID.randomUUID().toString();

        DataSource dataSource = dataSourceRepository.findById(command.getDataSourceId());
        if (dataSource == null) {
            throw new IllegalArgumentException("数据源不存在: " + command.getDataSourceId());
        }

        List<GenTableMetadata> tableMetadatas = new ArrayList<>();
        for (String tableName : command.getTableNames()) {
            GenTableMetadata tableMetadata = findTableMetadata(command.getDataSourceId(), tableName);
            if (tableMetadata == null) {
                throw new IllegalArgumentException("表元数据不存在，请先同步: " + tableName);
            }
            tableMetadatas.add(tableMetadata);
        }

        List<CodeTemplate> templates = new ArrayList<>();
        for (Long templateId : command.getTemplateIds()) {
            CodeTemplate template = codeTemplateRepository.findById(templateId);
            if (template == null) {
                throw new IllegalArgumentException("模板不存在: " + templateId);
            }
            templates.add(template);
        }

        GenerationTask task = GenerationTask.create(
                DistributedIdGenerator.generateLongId(),
                0L,
                taskId,
                command.getProjectName(),
                command.getBasePackage(),
                command.getModuleName(),
                command.getDataSourceId(),
                command.getTableNames(),
                command.getTemplateIds(),
                command.getGenConfig());

        task.markProcessing();
        List<GeneratedFile> generatedFiles = new ArrayList<>();

        try {
            for (GenTableMetadata table : tableMetadatas) {
                for (CodeTemplate template : templates) {
                    for (FileGenerator generator : fileGenerators) {
                        if (generator.supports(template.getType())) {
                            generatedFiles.add(generator.generate(
                                    table, template, command.getBasePackage(), command.getModuleName()));
                            break;
                        }
                    }
                }
            }
            String zipUrl = "http://localhost:8080/api/generation-tasks/" + taskId + "/download";
            task.markCompleted(generatedFiles, zipUrl);
        } catch (Exception e) {
            task.markFailed(e.getMessage());
            throw e;
        } finally {
            generationTaskRepository.save(task);
        }

        return taskId;
    }

    private GenTableMetadata findTableMetadata(Long dataSourceId, String tableName) {
        try {
            return tableMetadataRepository.findOneByCriteria(Criteria.<GenTableMetadata>create()
                    .eq("dataSourceId", StudioIds.dataSourceKey(dataSourceId))
                    .eq("originalTableName", tableName));
        } catch (MultipleResultsException e) {
            throw new IllegalStateException("duplicate table metadata", e);
        }
    }
}
