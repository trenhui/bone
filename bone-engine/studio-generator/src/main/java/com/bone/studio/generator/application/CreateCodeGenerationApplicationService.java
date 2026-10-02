package com.bone.studio.generator.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.studio.generator.application.command.cmd.CreateCodeGenerationCommand;
import com.bone.studio.generator.common.GeneratorErrorCodes;
import com.bone.studio.generator.common.GeneratorErrors;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.gateway.GenTableMetadataReadPort;
import com.bone.studio.generator.domain.gateway.GenerationTaskReadPort;
import com.bone.studio.generator.domain.gateway.TenantProvider;
import com.bone.studio.generator.domain.model.catalog.MetadataSourceType;
import com.bone.studio.generator.domain.model.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.model.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.code.OptionalArtifactType;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
  private final TenantProvider tenantProvider;
  private final ObjectMapper objectMapper;

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
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(
          GeneratorErrorCodes.TENANT_CONTEXT_MISSING, command.getProjectName());
    }
    CodeGenerationHistory history =
        CodeGenerationHistory.create(
            tenantId,
            taskId,
            templateIdsOrBuiltinText(command),
            templateNamesOrBuiltinText(command),
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
          // 开关产物（单测 / 文档）不在 templateIds 里，按 genConfig 追加
          appendOptionalArtifacts(generatedFiles, table, command);
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
      } catch (Exception ignored) {
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

  /**
   * catalog 模式调用 CodeGeneratorService（其原生支持 CATALOG_SNAPSHOT）。
   *
   * <p>注意：catalog 引擎按 {@code BUILT_IN_TEMPLATE_TYPES} 一次性生成全部内置类型，{@code templateId} 仅作记录用途，
   * 不会限制产出类型。若对每个 templateId 各调用一次，会产出 N 倍重复文件，故这里只调用一次。 模板精细选择（按用户勾选子集生成）属 P1 增强，当前 catalog
   * 链路以"全量内置模板"为准。
   */
  private List<GeneratedFile> runCatalogGeneration(CreateCodeGenerationCommand command) {
    List<Long> templateIds = command.getTemplateIds();
    // catalog 引擎不消费 templateId（见类注释）；为空时按默认语义「全部内建模板」记录。
    String recordTemplateId =
        templateIds == null || templateIds.isEmpty()
            ? "builtin"
            : String.valueOf(templateIds.get(0));
    GenFlags flags = resolveGenFlags(command.getGenConfig());
    CodeGenerationRequest request =
        CodeGenerationRequest.builder()
            .templateId(recordTemplateId)
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
            // 开关来自 genConfig（前端两个 checkbox），不再写死 true
            .includeTests(flags.includeTests())
            .includeDocumentation(flags.includeDocumentation())
            .build();
    CodeGenerationResponse response = codeGeneratorService.generateCode(request);
    return response.getGeneratedFiles() == null ? List.of() : response.getGeneratedFiles();
  }

  /**
   * 解析前端 {@code genConfig}（JSON 字符串）里的生成开关。
   *
   * <p>此前这两个开关一路传到后端却没人读，用户勾了「生成测试 / 生成文档」没有任何产物。
   *
   * <p><b>默认值取 {@code includeTests=true}</b>：目标模块的 TEST-HYGIENE-01 门禁要求每个聚合有纯单测，
   * 未指定时按"要生成"更不容易让新模块一接门禁就红；文档是锦上添花，默认关。
   */
  GenFlags resolveGenFlags(String genConfig) {
    if (genConfig == null || genConfig.isBlank()) {
      return new GenFlags(true, false);
    }
    try {
      JsonNode node = objectMapper.readTree(genConfig);
      return new GenFlags(
          node.path("includeTests").asBoolean(true),
          node.path("includeDocumentation").asBoolean(false));
    } catch (JsonProcessingException e) {
      // 解析失败按默认值继续，但不静默：开关没生效必须有日志可查
      log.warn("[genConfig 解析失败] 按默认开关继续（tests=true, docs=false）: {}", genConfig, e);
      return new GenFlags(true, false);
    }
  }

  /** 追加开关产物：它们不在 {@code templateIds} 里，只能按开关单独产出。 */
  void appendOptionalArtifacts(
      List<GeneratedFile> out, GenTableMetadata table, CreateCodeGenerationCommand command) {
    GenFlags flags = resolveGenFlags(command.getGenConfig());
    for (String type :
        OptionalArtifactType.typesFor(flags.includeTests(), flags.includeDocumentation())) {
      CodeTemplate template = CodeTemplate.builder().code(type).name(type).build();
      for (FileGenerator generator : fileGenerators) {
        if (generator.supports(type)) {
          out.add(
              generator.generate(
                  table, template, command.getBasePackage(), command.getModuleName()));
          break;
        }
      }
    }
  }

  /** 生成开关：是否产出聚合单测 / 接口文档（包内可见，供单测直接校验解析口径）。 */
  record GenFlags(boolean includeTests, boolean includeDocumentation) {}

  private static MetadataSourceType resolveSource(String raw) {
    if (raw == null || raw.isBlank()) {
      return MetadataSourceType.PHYSICAL_DB;
    }
    return MetadataSourceType.valueOf(raw.trim().toUpperCase());
  }

  private GenerationTask buildPendingTask(String taskId, CreateCodeGenerationCommand command) {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    if (tenantId == null) {
      throw GeneratorErrors.of(
          GeneratorErrorCodes.TENANT_CONTEXT_MISSING, command.getProjectName());
    }
    return GenerationTask.create(
        DistributedIdGenerator.generateLongId(),
        tenantId,
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
      // 默认语义（真实场景）：不选模板 = 全部内建（平台已发布）模板一键生成。
      // 注意用 findPlatformPublishedAllTenants 而非种子口径——迁移 0015 收敛的内建模板部分带创建人，
      // 种子口径（created_by IS NULL）会漏掉 create/update 命令、DTO 等一半内建模板，产出残缺代码包。
      List<CodeTemplate> builtIn =
          codeTemplateRepository.findPlatformPublishedAllTenants(1, 500).getRecords();
      if (builtIn.isEmpty()) {
        throw new IllegalArgumentException("templateIds 不能为空，且平台内建模板不可用");
      }
      return new ResolvedInputs(tableMetadatas, ensureResponseTemplate(new ArrayList<>(builtIn)));
    }

    List<CodeTemplate> templates = new ArrayList<>();
    for (Long templateId : templateIds) {
      // 用户可从「对自己可见」的列表里选平台(0)种子模板，故按 id 读取需跨租户（受控、仅命中该 id 行）。
      CodeTemplate template = codeTemplateRepository.findByIdAllTenants(templateId);
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

  /** 历史记录用：模板 ID 文本；未选模板（默认全部内建）时记 {@code builtin}。 */
  private String templateIdsOrBuiltinText(CreateCodeGenerationCommand command) {
    String text = templateIdsText(command);
    return text.isEmpty() ? "builtin" : text;
  }

  /** 历史记录用：模板名文本；未选模板（默认全部内建）时记可读标记。 */
  private String templateNamesOrBuiltinText(CreateCodeGenerationCommand command) {
    String text = templateNamesText(command);
    return text.isEmpty() ? "全部内建模板（默认）" : text;
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
