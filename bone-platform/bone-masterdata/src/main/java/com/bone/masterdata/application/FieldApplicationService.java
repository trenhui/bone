package com.bone.masterdata.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.CreateMasterDataFieldCommand;
import com.bone.masterdata.application.command.UpdateMasterDataFieldCommand;
import com.bone.masterdata.application.event.MasterdataDomainEventPublisher;
import com.bone.masterdata.application.query.dto.MasterDataFieldDTO;
import com.bone.masterdata.application.query.qry.MasterDataFieldDetailQuery;
import com.bone.masterdata.application.query.qry.MasterDataFieldListQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.common.MasterDataProperties;
import com.bone.masterdata.domain.entity.MasterDataField;
import com.bone.masterdata.domain.model.field.event.MasterDataFieldAddedEvent;
import com.bone.masterdata.domain.model.field.vo.FieldCode;
import com.bone.masterdata.domain.model.field.vo.FieldName;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主数据字段应用服务（ADR-0028 Application Service First）。
 *
 * <p>读侧领域模型由 {@link MasterDataFieldRepository} 的 default 方法承载（ADR-0030），应用层不直接依赖持久化 DSL。
 *
 * <p><b>不发 DomainEvent 的豁免理由（E-5.4）</b>：{@link MasterDataField} 是 {@code MasterDataEntity} 聚合内的子实体、
 * 非聚合根，无法承载 {@code publishFrom(AggregateRoot)}；字段新增事件由本类在 save 后显式 {@code
 * domainEventPublisher.publish(...)}，语义等价。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class FieldApplicationService {

  private final MasterDataFieldRepository fieldRepository;
  private final MasterDataEntityRepository entityRepository;
  private final MasterdataDomainEventPublisher domainEventPublisher;
  private final MasterDataProperties properties;

  @Capability(
      name = "CreateMasterDataField",
      description = "创建主数据字段定义",
      inputSchema =
          "{\"masterDataEntityId\": \"long\", \"name\": \"string\", \"type\": \"string\", \"length\": \"int\", \"required\": \"boolean\"}",
      outputSchema = "{\"fieldId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long create(CreateMasterDataFieldCommand cmd) {
    if (entityRepository.findById(cmd.getMasterDataEntityId()) == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    long existing =
        fieldRepository.countByEntityIdAndName(
            cmd.getMasterDataEntityId(), FieldName.of(cmd.getName()));
    if (existing > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.FIELD_NAME_DUPLICATE, "字段名称已存在");
    }
    if (fieldRepository.countByMasterDataEntityId(cmd.getMasterDataEntityId())
        >= properties.getEntityMaxFields()) {
      throw MasterDataErrors.of(
          MasterDataErrorCodes.ENTITY_FIELD_LIMIT_EXCEEDED,
          "字段数已达上限 " + properties.getEntityMaxFields());
    }
    Long fieldId = DistributedIdGenerator.generateLongId();
    MasterDataField field =
        MasterDataField.create(
            fieldId,
            cmd.getMasterDataEntityId(),
            FieldName.of(cmd.getName()),
            FieldCode.of(cmd.getCode()),
            cmd.getType(),
            cmd.getLength(),
            cmd.getRequired(),
            cmd.getDefaultValue(),
            cmd.getDescription(),
            0);
    Long savedId = fieldRepository.save(field);
    domainEventPublisher.publish(new MasterDataFieldAddedEvent(field));
    return savedId;
  }

  @Capability(
      name = "UpdateMasterDataField",
      description = "更新主数据字段定义",
      inputSchema =
          "{\"id\": \"long\", \"name\": \"string\", \"type\": \"string\", \"length\": \"int\", \"required\": \"boolean\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void update(UpdateMasterDataFieldCommand cmd) {
    MasterDataField field = fieldRepository.findById(cmd.getId());
    if (field == null) {
      throw NotFoundException.of("主数据字段不存在");
    }
    field.update(
        FieldName.of(cmd.getName()),
        cmd.getType(),
        cmd.getLength(),
        cmd.getRequired(),
        cmd.getDefaultValue(),
        cmd.getDescription(),
        cmd.getSortOrder());
    fieldRepository.update(field);
  }

  @Capability(
      name = "DeleteMasterDataField",
      description = "删除主数据字段定义",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void delete(Long id) {
    MasterDataField field = fieldRepository.findById(id);
    if (field == null) {
      throw NotFoundException.of("主数据字段不存在");
    }
    fieldRepository.deleteById(id);
  }

  @Transactional(readOnly = true)
  public List<MasterDataFieldDTO> list(MasterDataFieldListQuery qry) {
    List<MasterDataField> fields =
        qry.getMasterDataEntityId() != null
            ? fieldRepository.findByMasterDataEntityId(qry.getMasterDataEntityId())
            : fieldRepository.findAllFields();
    return fields.stream().map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public MasterDataFieldDTO detail(MasterDataFieldDetailQuery qry) {
    MasterDataField field = fieldRepository.findById(qry.id());
    if (field == null) {
      throw NotFoundException.of("主数据字段不存在");
    }
    return toDto(field);
  }

  private MasterDataFieldDTO toDto(MasterDataField field) {
    return MasterDataFieldDTO.builder()
        .id(field.getId())
        .masterDataEntityId(field.getMasterDataEntityId())
        .name(field.getName().value())
        .code(field.getCode().value())
        .type(field.getType())
        .length(field.getLength())
        .required(field.getRequired())
        .defaultValue(field.getDefaultValue())
        .description(field.getDescription())
        .sortOrder(field.getSortOrder())
        .build();
  }
}
