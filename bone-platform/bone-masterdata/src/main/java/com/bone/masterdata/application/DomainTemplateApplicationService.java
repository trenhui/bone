package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateDomainTemplateCommand;
import com.bone.masterdata.application.command.InstantiateFromTemplateCommand;
import com.bone.masterdata.application.command.PublishDomainTemplateVersionCommand;
import com.bone.masterdata.application.command.UpdateDomainTemplateCommand;
import com.bone.masterdata.application.query.dto.DomainTemplateDTO;
import com.bone.masterdata.application.query.dto.TemplateVersionDTO;
import com.bone.masterdata.application.query.qry.DomainTemplatePageQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.model.entity.MasterDataEntity;
import com.bone.masterdata.domain.model.entity.MasterDataField;
import com.bone.masterdata.domain.model.entity.valueobject.MasterDataEntityName;
import com.bone.masterdata.domain.model.field.valueobject.FieldCode;
import com.bone.masterdata.domain.model.field.valueobject.FieldName;
import com.bone.masterdata.domain.model.template.DomainTemplate;
import com.bone.masterdata.domain.model.template.TemplateVersion;
import com.bone.masterdata.domain.model.template.valueobject.DomainTemplateStatus;
import com.bone.masterdata.domain.repository.DomainTemplateRepository;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import com.bone.masterdata.domain.repository.TemplateVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 域模板应用服务（G9 / UC-P1、UC-P2、UC-T1 主流程 A）：平台侧建模板、发版本；租户只读实例化。
 *
 * <p>实例化 = 按 {@code field_schema} 生成 {@link MasterDataEntity} + {@link MasterDataField}， 并携带
 * domainCode / templateId / templateVersion / governanceTier 三层归属与治理配置（§3.2、§4.3）。
 */
@Service
@RequiredArgsConstructor
public class DomainTemplateApplicationService {

  private static final int MAX_PAGE_SIZE = 500;

  private final DomainTemplateRepository templateRepository;
  private final TemplateVersionRepository versionRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;
  private final ObjectMapper objectMapper;

  @Capability(
      name = "CreateDomainTemplate",
      description = "创建主数据域模板（平台侧）",
      inputSchema =
          "{\"domainCode\": \"string\", \"domainName\": \"string\", \"fieldSchema\": \"string\"}",
      outputSchema = "{\"templateId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long create(CreateDomainTemplateCommand cmd) {
    if (templateRepository.countByDomainCode(cmd.getDomainCode()) > 0) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_DOMAIN_DUPLICATE, "域编码已存在: " + cmd.getDomainCode());
    }
    validateFieldSchema(cmd.getFieldSchema());
    DomainTemplate template =
        DomainTemplate.create(
            DistributedIdGenerator.generateLongId(),
            cmd.getDomainCode(),
            cmd.getDomainName(),
            cmd.getDescription(),
            cmd.getDefaultGovernanceTier(),
            cmd.getFieldSchema(),
            cmd.getRuleSchema(),
            cmd.getCategorySchema());
    return templateRepository.insert(template);
  }

  @Transactional
  public void update(UpdateDomainTemplateCommand cmd) {
    DomainTemplate template = requireTemplate(cmd.getId());
    validateFieldSchema(cmd.getFieldSchema());
    template.update(
        cmd.getDomainName(),
        cmd.getDescription(),
        cmd.getFieldSchema(),
        cmd.getRuleSchema(),
        cmd.getCategorySchema(),
        cmd.getDefaultGovernanceTier());
    templateRepository.update(template);
  }

  /** 发布版本（UC-P2）：快照 schema 冻结到版本表，推进 current_version 并置 PUBLISHED。 */
  @Capability(
      name = "PublishDomainTemplateVersion",
      description = "发布域模板新版本（快照 schema）",
      inputSchema = "{\"id\": \"long\", \"versionNumber\": \"string\"}",
      outputSchema = "{\"versionId\": \"long\"}",
      idempotent = false,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public Long publishVersion(PublishDomainTemplateVersionCommand cmd) {
    DomainTemplate template = requireTemplate(cmd.getId());
    if (versionRepository.countByTemplateIdAndVersion(cmd.getId(), cmd.getVersionNumber()) > 0) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_VERSION_DUPLICATE, "版本已存在: " + cmd.getVersionNumber());
    }
    TemplateVersion snapshot =
        TemplateVersion.snapshot(
            DistributedIdGenerator.generateLongId(),
            template.getId(),
            cmd.getVersionNumber(),
            cmd.getChangeLog(),
            template.getFieldSchema(),
            template.getRuleSchema(),
            template.getCategorySchema());
    Long versionId = versionRepository.insert(snapshot);
    template.publishVersion(cmd.getVersionNumber());
    templateRepository.update(template);
    return versionId;
  }

  @Transactional
  public void archive(Long id) {
    DomainTemplate template = requireTemplate(id);
    template.archive();
    templateRepository.update(template);
  }

  /**
   * 从模板实例化主数据实体（UC-T1 主流程 A）。模板必须处于 PUBLISHED；entityCode 租户内唯一； withFields（默认 true）时按
   * field_schema（JSON 数组）逐条创建字段。
   */
  @Capability(
      name = "InstantiateFromTemplate",
      description = "租户从域模板实例化主数据实体",
      inputSchema = "{\"templateId\": \"long\", \"name\": \"string\", \"entityCode\": \"string\"}",
      outputSchema = "{\"entityId\": \"long\"}",
      idempotent = false,
      cost = 5,
      retryable = false,
      timeout = 30)
  @Transactional
  public Long instantiate(InstantiateFromTemplateCommand cmd) {
    DomainTemplate template = requireTemplate(cmd.getTemplateId());
    if (!template.getStatus().isInstantiable()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_NOT_PUBLISHED, "模板未发布，不可实例化: " + template.getDomainCode());
    }
    String entityCode =
        cmd.getEntityCode() == null || cmd.getEntityCode().isBlank()
            ? template.getDomainCode() + "_" + cmd.getTemplateId()
            : cmd.getEntityCode();
    if (entityRepository.countByEntityCode(entityCode) > 0) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.ENTITY_CODE_DUPLICATE, "实体编码已存在: " + entityCode);
    }
    String tier =
        cmd.getGovernanceTier() == null || cmd.getGovernanceTier().isBlank()
            ? template.getDefaultGovernanceTier()
            : cmd.getGovernanceTier();
    MasterDataEntity entity =
        MasterDataEntity.createFromTemplate(
            DistributedIdGenerator.generateLongId(),
            entityCode,
            MasterDataEntityName.of(cmd.getName()),
            cmd.getDescription(),
            template.getDomainCode(),
            template.getId(),
            template.getCurrentVersion(),
            tier);
    if (cmd.getOwningAppId() != null) {
      entity.bindOwningApp(cmd.getOwningAppId());
    }
    Long entityId = entityRepository.insert(entity);
    if (cmd.getWithFields() == null || cmd.getWithFields()) {
      createFieldsFromSchema(entityId, template.getFieldSchema());
    }
    return entityId;
  }

  @Transactional(readOnly = true)
  public PageResult<DomainTemplateDTO> page(DomainTemplatePageQuery qry) {
    DomainTemplateStatus status = null;
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      status = DomainTemplateStatus.valueOf(qry.getStatus());
    }
    int page = Math.max(1, qry.getPageNum());
    int size = Math.min(Math.max(1, qry.getPageSize()), MAX_PAGE_SIZE);
    PageResult<DomainTemplate> result = templateRepository.pageByStatus(status, page, size);
    return PageResult.of(
        result.getRecords().stream().map(this::toDto).toList(),
        result.getTotal(),
        result.getPage(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public DomainTemplateDTO detail(Long id) {
    return toDto(requireTemplate(id));
  }

  @Transactional(readOnly = true)
  public List<TemplateVersionDTO> versions(Long templateId) {
    requireTemplate(templateId);
    return versionRepository.findByTemplateId(templateId).stream()
        .map(
            v ->
                TemplateVersionDTO.builder()
                    .id(v.getId())
                    .templateId(v.getTemplateId())
                    .versionNumber(v.getVersionNumber())
                    .changeLog(v.getChangeLog())
                    .fieldSchema(v.getFieldSchema())
                    .ruleSchema(v.getRuleSchema())
                    .categorySchema(v.getCategorySchema())
                    .createdAt(v.getCreatedAt())
                    .build())
        .toList();
  }

  private void createFieldsFromSchema(Long entityId, String fieldSchema) {
    if (fieldSchema == null || fieldSchema.isBlank()) {
      return;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(fieldSchema);
    } catch (JsonProcessingException e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_FIELD_SCHEMA_INVALID, "field_schema 不是合法 JSON", e);
    }
    if (!root.isArray()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_FIELD_SCHEMA_INVALID, "field_schema 必须是 JSON 数组");
    }
    int sortOrder = 0;
    for (JsonNode node : root) {
      String code = node.path("code").asText(null);
      String name = node.path("name").asText(null);
      if (code == null || name == null) {
        throw MasterDataErrors.of(
            MasterDataErrorCodes.TEMPLATE_FIELD_SCHEMA_INVALID,
            "field_schema 元素必须包含 code 与 name: " + node);
      }
      MasterDataField field =
          MasterDataField.create(
              DistributedIdGenerator.generateLongId(),
              entityId,
              FieldName.of(name),
              FieldCode.of(code),
              node.path("type").asText("STRING"),
              node.has("length") ? node.get("length").asInt() : null,
              node.path("required").asBoolean(false),
              node.path("defaultValue").asText(null),
              node.path("description").asText(null),
              sortOrder++);
      fieldRepository.insert(field);
    }
  }

  private void validateFieldSchema(String fieldSchema) {
    if (fieldSchema == null || fieldSchema.isBlank()) {
      return;
    }
    try {
      JsonNode root = objectMapper.readTree(fieldSchema);
      if (!root.isArray()) {
        throw MasterDataErrors.of(
            MasterDataErrorCodes.TEMPLATE_FIELD_SCHEMA_INVALID, "field_schema 必须是 JSON 数组");
      }
    } catch (JsonProcessingException e) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.TEMPLATE_FIELD_SCHEMA_INVALID, "field_schema 不是合法 JSON", e);
    }
  }

  private DomainTemplate requireTemplate(Long id) {
    DomainTemplate template = templateRepository.findById(id);
    if (template == null) {
      throw NotFoundException.of("域模板不存在");
    }
    return template;
  }

  private DomainTemplateDTO toDto(DomainTemplate t) {
    return DomainTemplateDTO.builder()
        .id(t.getId())
        .domainCode(t.getDomainCode())
        .domainName(t.getDomainName())
        .description(t.getDescription())
        .currentVersion(t.getCurrentVersion())
        .defaultGovernanceTier(t.getDefaultGovernanceTier())
        .fieldSchema(t.getFieldSchema())
        .ruleSchema(t.getRuleSchema())
        .categorySchema(t.getCategorySchema())
        .status(t.getStatus().name())
        .createdAt(t.getCreatedAt())
        .updatedAt(t.getUpdatedAt())
        .build();
  }
}
