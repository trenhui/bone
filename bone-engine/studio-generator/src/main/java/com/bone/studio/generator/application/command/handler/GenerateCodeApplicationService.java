package com.bone.studio.generator.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.studio.generator.application.command.cmd.GenerateCodeCommand;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.catalog.MetadataSourceType;
import com.bone.studio.generator.domain.code.CodeGenerationRequest;
import com.bone.studio.generator.domain.code.CodeGenerationResponse;
import com.bone.studio.generator.domain.history.CodeGenerationHistory;
import com.bone.studio.generator.domain.repository.CodeGenerationHistoryRepository;
import com.bone.studio.generator.domain.repository.CodeTemplateRepository;
import com.bone.studio.generator.domain.repository.DataSourceRepository;
import com.bone.studio.generator.domain.service.CodeGeneratorService;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Capability(name = "generateCode", description = "生成代码", inputSchema = "{}", outputSchema = "{}")
public class GenerateCodeApplicationService {

  private final CodeTemplateRepository codeTemplateRepository;
  private final DataSourceRepository dataSourceRepository;
  private final CodeGenerationHistoryRepository historyRepository;
  private final CodeGeneratorService codeGeneratorService;

  public GenerateCodeApplicationService(
      CodeTemplateRepository codeTemplateRepository,
      DataSourceRepository dataSourceRepository,
      CodeGenerationHistoryRepository historyRepository,
      CodeGeneratorService codeGeneratorService) {
    this.codeTemplateRepository = codeTemplateRepository;
    this.dataSourceRepository = dataSourceRepository;
    this.historyRepository = historyRepository;
    this.codeGeneratorService = codeGeneratorService;
  }

  @Transactional
  public CodeGenerationResponse handle(GenerateCodeCommand command) {
    // 1. 验证模板是否存在
    if (command.getTemplateId() == null || command.getTemplateId().isBlank()) {
      throw new IllegalArgumentException("templateId 不能为空");
    }
    Long templateIdLong;
    try {
      templateIdLong = Long.parseLong(command.getTemplateId());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("templateId 格式无效: " + command.getTemplateId());
    }
    var template = codeTemplateRepository.findById(templateIdLong);
    if (template == null) {
      throw new IllegalArgumentException("模板不存在: " + command.getTemplateId());
    }

    MetadataSourceType sourceType = resolveMetadataSource(command.getMetadataSource());

    // 2. 物理库模式：验证数据源
    if (sourceType == MetadataSourceType.PHYSICAL_DB
        && command.getDataSourceId() != null
        && !command.getDataSourceId().isEmpty()) {
      var dataSource =
          dataSourceRepository.findById(StudioIds.parseRequired(command.getDataSourceId()));
      if (dataSource == null) {
        throw new IllegalArgumentException("数据源不存在: " + command.getDataSourceId());
      }
    }

    // 3. 创建生成历史记录
    String generationId = UUID.randomUUID().toString();
    var history =
        CodeGenerationHistory.create(
            generationId,
            command.getTemplateId(),
            template.getName(),
            command.getName(),
            command.getDataSourceId(),
            command.getTableNames(),
            command.getBasePackage(),
            command.getModuleName());
    historyRepository.save(history);

    try {
      // 4. 构建代码生成请求
      var codeGenRequest =
          CodeGenerationRequest.builder()
              .templateId(command.getTemplateId())
              .name(command.getName())
              .description(command.getDescription())
              .language(command.getLanguage())
              .framework(command.getFramework())
              .parameters(command.getParameters())
              .tags(command.getTags())
              .outputFormat(command.getOutputFormat())
              .outputPath(command.getOutputPath())
              .includeTests(command.isIncludeTests())
              .includeDocumentation(command.isIncludeDocumentation())
              .dataSourceId(command.getDataSourceId())
              .tableNames(command.getTableNames())
              .basePackage(command.getBasePackage())
              .moduleName(command.getModuleName())
              .metadataSource(sourceType)
              .tenantId(command.getTenantId())
              .entityCodes(command.getEntityCodes())
              .build();

      // 5. 执行代码生成
      long startTime = System.currentTimeMillis();
      var response = codeGeneratorService.generateCode(codeGenRequest);
      long executionTime = System.currentTimeMillis() - startTime;

      // 6. 更新历史记录
      history.complete(
          0, // 简化实现，不统计文件数量
          executionTime,
          "" // 简化实现，不记录输出路径
          );
      historyRepository.save(history);

      return response;
    } catch (Exception e) {
      // 7. 处理异常
      history.fail(e.getMessage());
      historyRepository.save(history);
      throw new RuntimeException("代码生成失败: " + e.getMessage(), e);
    }
  }

  private static MetadataSourceType resolveMetadataSource(String raw) {
    if (raw == null || raw.isBlank()) {
      return MetadataSourceType.PHYSICAL_DB;
    }
    return MetadataSourceType.valueOf(raw.trim().toUpperCase());
  }
}
