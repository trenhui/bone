package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.code.GeneratedFile;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.GenTableMetadata;
import com.bone.studio.generator.domain.data.GenerationTask;
import com.bone.studio.generator.domain.gateway.GenTableMetadataReadPort;
import com.bone.studio.generator.domain.gateway.GenerationTaskReadPort;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Capability(
    name = "createCodeGeneration",
    description = "创建代码生成任务",
    inputSchema = "{}",
    outputSchema = "{}")
public class CreateCodeGenerationHandler {

  private final GenerationTaskRepository generationTaskRepository;
  private final GenerationTaskReadPort generationTaskReadPort;
  private final DataSourceRepository dataSourceRepository;
  private final GenTableMetadataReadPort genTableMetadataReadPort;
  private final CodeTemplateRepository codeTemplateRepository;
  private final List<FileGenerator> fileGenerators;

  /** 同步执行（原行为）。 */
  @Transactional
  public String handle(CreateCodeGenerationCommand command) {
    String taskId = UUID.randomUUID().toString();
    runGenerationWork(taskId, command, true);
    return taskId;
  }

  /** LRO：仅创建 PENDING 任务并持久化。 */
  @Transactional
  public String startPending(CreateCodeGenerationCommand command) {
    String taskId = UUID.randomUUID().toString();
    validateAndResolve(command);
    GenerationTask task = buildPendingTask(taskId, command);
    generationTaskRepository.save(task);
    return taskId;
  }

  /** LRO：后台执行生成（不向外抛业务异常）。 */
  public void executeByTaskId(String taskId, CreateCodeGenerationCommand command) {
    try {
      runGenerationWork(taskId, command, false);
    } catch (Exception ex) {
      // runGenerationWork 已标记 FAILED；异步路径仅记录
    }
  }

  private void runGenerationWork(
      String taskId, CreateCodeGenerationCommand command, boolean propagateErrors) {
    ResolvedInputs inputs = validateAndResolve(command);
    GenerationTask task = loadOrCreateTask(taskId, command, inputs);
    task.markProcessing();
    generationTaskRepository.save(task);

    List<GeneratedFile> generatedFiles = new ArrayList<>();
    try {
      for (GenTableMetadata table : inputs.tableMetadatas()) {
        for (CodeTemplate template : inputs.templates()) {
          for (FileGenerator generator : fileGenerators) {
            if (generator.supports(template.getType())) {
              generatedFiles.add(
                  generator.generate(
                      table, template, command.getBasePackage(), command.getModuleName()));
              break;
            }
          }
        }
      }
      String zipUrl = "/api/v1/generator/code-generation/tasks/" + taskId + "/download";
      task.markCompleted(generatedFiles, zipUrl);
    } catch (Exception e) {
      task.markFailed(e.getMessage());
      if (propagateErrors) {
        throw e;
      }
    } finally {
      generationTaskRepository.save(task);
    }
  }

  private GenerationTask loadOrCreateTask(
      String taskId, CreateCodeGenerationCommand command, ResolvedInputs inputs) {
    GenerationTask existing = generationTaskReadPort.findByTaskId(taskId).orElse(null);
    if (existing != null) {
      existing.markProcessing();
      return existing;
    }
    return buildPendingTask(taskId, command);
  }

  private GenerationTask buildPendingTask(String taskId, CreateCodeGenerationCommand command) {
    return GenerationTask.create(
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
  }

  private ResolvedInputs validateAndResolve(CreateCodeGenerationCommand command) {
    DataSource dataSource = dataSourceRepository.findById(command.getDataSourceId());
    if (dataSource == null) {
      throw new IllegalArgumentException("数据源不存在: " + command.getDataSourceId());
    }

    List<String> tableNames = command.getTableNames();
    if (tableNames == null || tableNames.isEmpty()) {
      throw new IllegalArgumentException("tableNames 不能为空");
    }

    List<GenTableMetadata> tableMetadatas = new ArrayList<>();
    for (String tableName : tableNames) {
      GenTableMetadata tableMetadata = findTableMetadata(command.getDataSourceId(), tableName);
      if (tableMetadata == null) {
        throw new IllegalArgumentException("表元数据不存在，请先同步: " + tableName);
      }
      tableMetadatas.add(tableMetadata);
    }

    List<Long> templateIds = command.getTemplateIds();
    if (templateIds == null || templateIds.isEmpty()) {
      throw new IllegalArgumentException("templateIds 不能为空");
    }

    List<CodeTemplate> templates = new ArrayList<>();
    for (Long templateId : templateIds) {
      CodeTemplate template = codeTemplateRepository.findById(templateId);
      if (template == null) {
        throw new IllegalArgumentException("模板不存在: " + templateId);
      }
      templates.add(template);
    }
    return new ResolvedInputs(tableMetadatas, templates);
  }

  private GenTableMetadata findTableMetadata(Long dataSourceId, String tableName) {
    return genTableMetadataReadPort
        .findByDataSourceKeyAndTableName(StudioIds.dataSourceKey(dataSourceId), tableName)
        .orElse(null);
  }

  private record ResolvedInputs(
      List<GenTableMetadata> tableMetadatas, List<CodeTemplate> templates) {}
}
