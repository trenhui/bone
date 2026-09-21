package com.bone.masterdata.application;

import com.bone.core.capability.Capability;
import com.bone.core.exception.NotFoundException;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.DisableMasterDataEntityCommand;
import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCommand;
import com.bone.masterdata.application.query.dto.MasterDataEntityDTO;
import com.bone.masterdata.application.query.qry.MasterDataEntityByIdQuery;
import com.bone.masterdata.application.query.qry.MasterDataEntityPageQuery;
import com.bone.masterdata.common.MasterDataErrorCodes;
import com.bone.masterdata.common.MasterDataErrors;
import com.bone.masterdata.domain.entity.MasterDataEntity;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort;
import com.bone.masterdata.domain.gateway.MetaEntityCatalogPort.MetaEntityRow;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityName;
import com.bone.masterdata.domain.model.entity.vo.MasterDataEntityStatus;
import com.bone.masterdata.domain.repository.MasterDataEntityRepository;
import com.bone.masterdata.domain.repository.MasterDataFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 主数据实体应用服务（ADR-0028 Application Service First）。
 *
 * <p>原 {@code application/command/handler/*} 与 {@code application/query/handler/*} 的全部用例已内联合并到本服务，
 * 适配器只依赖本服务。读侧领域模型经由 {@link MasterDataEntityRepository} 的 default 方法承载（ADR-0030）， 应用层不直接依赖持久化 DSL。
 */
@Service
@RequiredArgsConstructor
public class EntityApplicationService {

  private final MasterDataEntityRepository entityRepository;
  private final MasterDataFieldRepository fieldRepository;
  private final MetaEntityCatalogPort metaEntityCatalogPort;

  @Capability(
      name = "CreateMasterDataEntity",
      description = "创建主数据实体定义",
      inputSchema = "{\"name\": \"string\", \"description\": \"string\", \"category\": \"string\"}",
      outputSchema = "{\"entityId\": \"long\"}",
      idempotent = false,
      cost = 2,
      retryable = true,
      timeout = 15)
  @Transactional
  public Long create(CreateMasterDataEntityCommand cmd) {
    MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());
    long existing = entityRepository.countByEntityName(entityName);
    if (existing > 0) {
      throw MasterDataErrors.of(MasterDataErrorCodes.ENTITY_NAME_DUPLICATE, "主数据实体名称已存在");
    }
    Long entityId = DistributedIdGenerator.generateLongId();
    MasterDataEntity entity =
        MasterDataEntity.create(
            entityId, null, entityName, cmd.getDescription(), cmd.getCategory());
    return entityRepository.save(entity);
  }

  @Capability(
      name = "UpdateMasterDataEntity",
      description = "更新主数据实体定义",
      inputSchema =
          "{\"id\": \"long\", \"name\": \"string\", \"description\": \"string\", \"category\": \"string\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void update(UpdateMasterDataEntityCommand cmd) {
    MasterDataEntity entity = entityRepository.findById(cmd.getId());
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    MasterDataEntityName entityName = MasterDataEntityName.of(cmd.getName());
    entity.update(entityName, cmd.getDescription(), cmd.getCategory());
    entityRepository.save(entity);
  }

  @Capability(
      name = "PublishMasterDataEntity",
      description = "发布主数据实体",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public void publish(Long id) {
    MasterDataEntity entity = entityRepository.findById(id);
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    entity.publish();
    entityRepository.update(entity);
  }

  @Capability(
      name = "DisableMasterDataEntity",
      description = "停用/启用主数据实体",
      inputSchema = "{\"id\": \"long\", \"disabled\": \"boolean\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 1,
      retryable = true,
      timeout = 15)
  @Transactional
  public void disable(DisableMasterDataEntityCommand cmd) {
    MasterDataEntity entity = entityRepository.findById(cmd.getId());
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    if (cmd.isDisabled()) {
      entity.disable();
    } else {
      entity.enable();
    }
    entityRepository.update(entity);
  }

  @Capability(
      name = "DeleteMasterDataEntity",
      description = "删除主数据实体",
      inputSchema = "{\"id\": \"long\"}",
      outputSchema = "{\"success\": \"boolean\"}",
      idempotent = true,
      cost = 3,
      retryable = false,
      timeout = 30)
  @Transactional
  public void delete(Long id) {
    MasterDataEntity entity = entityRepository.findById(id);
    if (entity == null) {
      throw NotFoundException.of("主数据实体不存在");
    }
    entityRepository.deleteById(id);
  }

  /** 从已发布 meta_entity 创建主数据实体（MD-04 · ADR-0002）。原 ConvertFromBusinessEntityHandler，无能力元数据。 */
  @Transactional
  public Long convertFromBusinessEntity(Long metaEntityId) {
    Long existingId = entityRepository.findIdByMetaEntityId(metaEntityId);
    if (existingId != null) {
      return existingId;
    }
    MetaEntityRow meta = metaEntityCatalogPort.requirePublished(metaEntityId);
    String displayName =
        meta.displayName() != null && !meta.displayName().isBlank()
            ? meta.displayName()
            : meta.code();
    MasterDataEntity entity =
        MasterDataEntity.create(
            DistributedIdGenerator.generateLongId(),
            metaEntityId,
            MasterDataEntityName.of(displayName),
            "从元数据实体 " + metaEntityId + " 转换",
            "catalog");
    return entityRepository.insert(entity);
  }

  @Transactional(readOnly = true)
  public PageResult<MasterDataEntityDTO> page(MasterDataEntityPageQuery qry) {
    MasterDataEntityStatus status = null;
    if (qry.getStatus() != null && !qry.getStatus().isBlank()) {
      status = MasterDataEntityStatus.valueOf(qry.getStatus());
    }
    int page = Math.max(1, qry.getPageNum());
    int size = Math.max(1, qry.getPageSize());
    PageResult<MasterDataEntity> result =
        entityRepository.pageByCategoryAndStatus(qry.getCategory(), status, page, size);
    return PageResult.of(
        result.getRecords().stream().map(e -> toDto(e, 0)).toList(),
        result.getTotal(),
        result.getPage(),
        result.getSize());
  }

  @Transactional(readOnly = true)
  public MasterDataEntityDTO detail(MasterDataEntityByIdQuery qry) {
    MasterDataEntity entity = entityRepository.findById(qry.getId());
    if (entity == null) {
      throw new NotFoundException("主数据实体不存在");
    }
    int fieldCount = (int) fieldRepository.countByMasterDataEntityId(entity.getId());
    return toDto(entity, fieldCount);
  }

  private MasterDataEntityDTO toDto(MasterDataEntity entity, int fieldCount) {
    return MasterDataEntityDTO.builder()
        .id(entity.getId())
        .name(entity.getName().value())
        .description(entity.getDescription())
        .category(entity.getCategory())
        .status(entity.getStatus().name())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .fieldCount(fieldCount)
        .build();
  }
}
