package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.GenTableMetadataReadPort;
import com.bone.studio.generator.domain.gateway.GenerationTaskReadPort;
import com.bone.studio.generator.domain.model.catalog.MetadataSourceType;
import com.bone.studio.generator.domain.model.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.DataSource;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import com.bone.studio.generator.domain.model.data.GenerationTask;
import com.bone.studio.generator.domain.model.history.CodeGenerationHistory;
import com.bone.studio.generator.domain.repository.CodeGenerationHistoryRepository;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.repository.GenerationTaskRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import com.bone.studio.generator.domain.service.FileGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 创建代码生成任务。
 *
 * <p><b>不发 DomainEvent 豁免（E-5.4）</b>：本服务写入聚合，当前不发布领域事件； 若将来接入事件发布，须改为调用 publishFrom 并移除本豁免。
 */
@Component
@RequiredArgsConstructor
@NoDomainEvent
@Capability(
    name = "createCodeGeneration",
    description = "创建代码生成任务",
    inputSchema = "{}",
    outputSchema = "{}")
public class CreateCodeGenerationApplicationService {

  private final GenerationTaskRepository generationTaskRepository;
  private final GenerationTaskReadPort generationTaskReadPort;
  private final DataSourceRepository dataSourceRepository;
  private final GenTableMetadataReadPort genTableMetadataReadPort;
  private final CodeTemplateRepository codeTemplateRepository;
  private final CodeGenerationHistoryRepository historyRepository;
  private final List<FileGenerator> fileGenerators;
  private final CodeGeneratorService codeGeneratorService;

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
    MetadataSourceType source = resolveSource(command.getMetadataSource());
    GenerationTask task = loadOrCreateTask(taskId, command);
    task.markProcessing();
    generationTaskRepository.save(task);

    // 模板驱动路径此前不落 gen_code_generation_history，历史页在这条主链路上永远是空的
    CodeGenerationHistory history =
        CodeGenerationHistory.create(
            taskId,
            templateIdsText(command),
            templateNamesText(command),
            command.getProjectName(),
            String.valueOf(command.getDataSourceId()),
            command.getTableNames(),
            command.getBasePackage(),
            command.getModuleName());
    historyRepository.save(history);

    long startedAt = System.currentTimeMillis();
    List<GeneratedFile> generatedFiles = new ArrayList<>();
    try {
      if (source == MetadataSourceType.CATALOG_SNAPSHOT) {
        // catalog 模式：复用已验证的 CodeGeneratorService 引擎（按 entityCodes 读取 meta_* 快照）
        generatedFiles.addAll(runCatalogGeneration(command));
      } else {
        ResolvedInputs inputs = validateAndResolve(command);
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
      }
      String zipUrl = "/api/v1/generator/code-generation/tasks/" + taskId + "/download";
      task.markCompleted(generatedFiles, zipUrl);
      history.complete(generatedFiles.size(), System.currentTimeMillis() - startedAt, zipUrl);
    } catch (Exception e) {
      task.markFailed(e.getMessage());
      history.fail(e.getMessage());
      if (propagateErrors) {
        throw e;
      }
    } finally {
      generationTaskRepository.save(task);
      try {
        historyRepository.save(history);
      } catch (RuntimeException ignored) {
        // 历史是旁路记录，写不进去不应影响生成结果
      }
    }
  }

  private GenerationTask loadOrCreateTask(String taskId, CreateCodeGenerationCommand command) {
    GenerationTask existing = generationTaskReadPort.findByTaskId(taskId).orElse(null);
    if (existing != null) {
      existing.markProcessing();
      return existing;
    }
    return buildPendingTask(taskId, command);
  }

  /** catalog 模式逐模板调用 CodeGeneratorService（其原生支持 CATALOG_SNAPSHOT）。 */
  private List<GeneratedFile> runCatalogGeneration(CreateCodeGenerationCommand command) {
    List<Long> templateIds = command.getTemplateIds();
    if (templateIds == null || templateIds.isEmpty()) {
      throw new IllegalArgumentException("templateIds 不能为空");
    }
    List<GeneratedFile> files = new ArrayList<>();
    for (Long templateId : templateIds) {
      CodeGenerationRequest request =
          CodeGenerationRequest.builder()
              .templateId(String.valueOf(templateId))
              .name(command.getProjectName())
              .basePackage(command.getBasePackage())
              .moduleName(command.getModuleName())
              .dataSourceId(
                  command.getDataSourceId() == null
                      ? null
                      : String.valueOf(command.getDataSourceId()))
              .tableNames(command.getTableNames())
              .metadataSource(MetadataSourceType.CATALOG_SNAPSHOT)
              .tenantId(command.getTenantId())
              .entityCodes(command.getEntityCodes())
              .includeTests(true)
              .includeDocumentation(true)
              .build();
      CodeGenerationResponse response = codeGeneratorService.generateCode(request);
      if (response.getGeneratedFiles() != null) {
        files.addAll(response.getGeneratedFiles());
      }
    }
    return files;
  }

  private static MetadataSourceType resolveSource(String raw) {
    if (raw == null || raw.isBlank()) {
      return MetadataSourceType.PHYSICAL_DB;
    }
    return MetadataSourceType.valueOf(raw.trim().toUpperCase());
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
    return new ResolvedInputs(tableMetadatas, ensureResponseTemplate(templates));
  }

  /**
   * 控制器模板依赖响应对象（HC-003：Controller 不裸返领域对象），用户只选 controller 时自动补上内置 response 模板，否则生成的控制器会引用一个不存在的类。
   */
  private List<CodeTemplate> ensureResponseTemplate(List<CodeTemplate> templates) {
    boolean needsResponse =
        templates.stream().anyMatch(t -> "controller".equals(t.getType()))
            && templates.stream().noneMatch(t -> "response".equals(t.getType()));
    if (!needsResponse) {
      return templates;
    }
    List<CodeTemplate> withResponse = new ArrayList<>(templates);
    withResponse.add(
        CodeTemplate.builder().code("response").name("response").type("response").build());
    return withResponse;
  }

  private String templateIdsText(CreateCodeGenerationCommand command) {
    List<Long> ids = command.getTemplateIds();
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(ids.get(i));
    }
    return sb.toString();
  }

  private String templateNamesText(CreateCodeGenerationCommand command) {
    List<Long> ids = command.getTemplateIds();
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < ids.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      CodeTemplate template = codeTemplateRepository.findById(ids.get(i));
      sb.append(
          template == null
              ? String.valueOf(ids.get(i))
              : (template.getName() == null ? template.getCode() : template.getName()));
    }
    return sb.toString();
  }

  private GenTableMetadata findTableMetadata(Long dataSourceId, String tableName) {
    return genTableMetadataReadPort
        .findByDataSourceKeyAndTableName(StudioIds.dataSourceKey(dataSourceId), tableName)
        .orElse(null);
  }

  private record ResolvedInputs(
      List<GenTableMetadata> tableMetadatas, List<CodeTemplate> templates) {

    String templateIdsText() {
      StringBuilder sb = new StringBuilder();
      for (CodeTemplate template : templates) {
        if (sb.length() > 0) {
          sb.append(',');
        }
        sb.append(template.getId());
      }
      return sb.toString();
    }

    String templateNamesText() {
      StringBuilder sb = new StringBuilder();
      for (CodeTemplate template : templates) {
        if (sb.length() > 0) {
          sb.append(',');
        }
        sb.append(template.getName() == null ? template.getCode() : template.getName());
      }
      return sb.toString();
    }
  }
}
