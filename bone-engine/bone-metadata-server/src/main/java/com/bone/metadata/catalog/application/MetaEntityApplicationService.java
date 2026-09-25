package com.bone.metadata.catalog.application;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCommand;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import com.bone.metadata.runtime.RuntimeEntityCacheEvictor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 元数据建模（实体 / 字段 / 物理结构）应用层统一门面（ADR-0028 Application Service First）。
 *
 * <p>适配器（controller）只依赖本类；读侧经 domain.repository 的读模型方法（ADR-0030 合并写侧与领域读模型）， 不另建
 * QueryPort。实体与字段同属「实体建模」限界上下文，故收敛于同一门面；关系建模见 {@link MetaRelationApplicationService}。
 */
@Service
@RequiredArgsConstructor
public class MetaEntityApplicationService {

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;
  private final IamModuleValidator iamModuleValidator;
  private final TenantProvider tenantProvider;
  private final PhysicalStructureGateway physicalStructureGateway;
  private final Optional<RuntimeEntityCacheEvictor> runtimeEntityCacheEvictor;
  private final PlatformTransactionManager transactionManager;

  // ===================== 实体写操作 =====================

  @Transactional
  public Long createEntity(CreateMetaEntityCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    if (cmd.getModuleId() != null) {
      iamModuleValidator.requireExists(cmd.getModuleId());
    }
    assertEntityCodeUnique(tenantId, cmd.getCode());
    assertEntityTableUnique(tenantId, cmd.getTableName());
    int type = cmd.getType() != null ? cmd.getType() : 0;
    int deliveryMode =
        cmd.getDeliveryMode() != null
            ? MetaDeliveryMode.fromCode(cmd.getDeliveryMode()).getCode()
            : MetaDeliveryMode.GENERATIVE.getCode();
    MetaEntity entity =
        MetaEntity.create(
            null,
            tenantId,
            cmd.getName(),
            cmd.getCode(),
            cmd.getDisplayName(),
            cmd.getDescription(),
            cmd.getTableName(),
            type,
            deliveryMode,
            cmd.getIcon(),
            cmd.getModuleId());
    metaEntityRepository.insert(entity);
    return entity.getId();
  }

  @Transactional
  public Integer updateEntity(Long id, UpdateMetaEntityCommand cmd, Integer expectedVersion) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, entity.getVersion());
    entity.update(
        cmd.getName(),
        cmd.getDisplayName(),
        cmd.getDescription(),
        cmd.getTableName(),
        cmd.getSortOrder(),
        cmd.getIcon(),
        cmd.getDeliveryMode());
    metaEntityRepository.update(entity);
    return entity.getVersion();
  }

  @Transactional
  public void deleteEntity(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    // 草稿实体先释放 code / table_name 的唯一键占位，再逻辑删除，
    // 否则「建错了重建」会一直卡在「编码已存在」（唯一键覆盖逻辑删除行）。
    if (entity.releaseUniqueKeysForDelete()) {
      metaEntityRepository.update(entity);
    }
    metaEntityRepository.deleteById(id);
  }

  @Transactional
  public Integer publishEntity(Long id, Integer expectedVersion) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, entity.getVersion());
    entity.publish();
    metaEntityRepository.update(entity);
    runtimeEntityCacheEvictor.ifPresent(
        evictor -> evictor.evict(entity.getCode(), entity.getTenantId()));
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      physicalStructureGateway.align(entity.getTenantId(), entity.getCode());
    }
    return entity.getVersion();
  }

  // ===================== 实体批量写操作（部分成功语义） =====================

  public BatchOperateResult batchDeleteEntities(BatchDeleteMetaEntityCommand cmd) {
    TransactionTemplate txTemplate =
        new TransactionTemplate(
            transactionManager,
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    int success = 0;
    int fail = 0;
    List<String> errors = new ArrayList<>();
    for (Long id : cmd.getIds()) {
      try {
        txTemplate.execute(
            (org.springframework.transaction.TransactionStatus status) -> {
              deleteEntity(id);
              return null;
            });
        success++;
      } catch (Exception e) {
        fail++;
        errors.add("id=" + id + ": " + e.getMessage());
      }
    }
    return new BatchOperateResult(success, fail, errors);
  }

  public BatchOperateResult batchPublishEntities(BatchPublishMetaEntityCommand cmd) {
    TransactionTemplate txTemplate =
        new TransactionTemplate(
            transactionManager,
            new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
    int success = 0;
    int fail = 0;
    List<String> errors = new ArrayList<>();
    for (Long id : cmd.getIds()) {
      try {
        txTemplate.execute(
            (org.springframework.transaction.TransactionStatus status) -> {
              publishEntity(id, null);
              return null;
            });
        success++;
      } catch (Exception e) {
        fail++;
        errors.add("id=" + id + ": " + e.getMessage());
      }
    }
    return new BatchOperateResult(success, fail, errors);
  }

  // ===================== 字段写操作 =====================

  @Transactional
  public Long createField(Long entityId, CreateMetaFieldCommand cmd) {
    if (cmd.getType() == null && cmd.getFieldType() != null) {
      cmd.setType(cmd.getFieldType());
    }
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw BizException.of("所属实体不存在: " + entityId);
    }
    assertFieldCodeUnique(entityId, cmd.getCode());
    MetaField field =
        MetaField.create(
            null,
            tenantProvider.currentTenantId(),
            entityId,
            cmd.getName(),
            cmd.getCode(),
            cmd.getDisplayName(),
            cmd.getType(),
            cmd.getLength(),
            cmd.getRequired(),
            cmd.getUnique(),
            cmd.getDefaultValue(),
            cmd.getComment(),
            cmd.getSortOrder(),
            null);
    metaFieldRepository.insert(field);
    return field.getId();
  }

  @Transactional
  public Integer updateField(Long fieldId, UpdateMetaFieldCommand cmd, Integer expectedVersion) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null) {
      throw BizException.of("字段不存在: " + fieldId);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, field.getVersion());
    field.update(
        cmd.getDisplayName(),
        cmd.getType(),
        cmd.getLength(),
        cmd.getRequired(),
        cmd.getUnique(),
        cmd.getDefaultValue(),
        cmd.getComment(),
        cmd.getSortOrder());
    metaFieldRepository.update(field);
    return field.getVersion();
  }

  @Transactional
  public void deleteField(Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null) {
      throw BizException.of("字段不存在: " + fieldId);
    }
    metaFieldRepository.deleteById(fieldId);
  }

  // ===================== 物理结构治理（破坏性，管理员专属） =====================

  @Transactional
  public PhysicalStructurePlan dropColumn(Long entityId, String fieldCode) {
    MetaEntity entity = requireRuntimeEntity(entityId);
    return physicalStructureGateway.dropColumn(entity.getTenantId(), entity.getCode(), fieldCode);
  }

  @Transactional
  public PhysicalStructurePlan dropDriftedColumns(Long entityId) {
    MetaEntity entity = requireRuntimeEntity(entityId);
    return physicalStructureGateway.dropDriftedColumns(entity.getTenantId(), entity.getCode());
  }

  // ===================== 读操作（ADR-0030：经 domain.repository 读模型） =====================

  @Transactional(readOnly = true)
  public MetaEntityDTO getEntity(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of("实体不存在: " + id);
    }
    return CatalogDtoMapper.toDto(entity);
  }

  @Transactional(readOnly = true)
  public PageResult<MetaEntityDTO> pageEntities(
      String keyword, Integer status, int pageNum, int pageSize) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<MetaEntity> sdkPage =
        metaEntityRepository.pageEntities(tenantId, keyword, status, pageNum, pageSize);
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }

  @Transactional(readOnly = true)
  public MetaFieldDTO getField(Long entityId, Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null || !entityId.equals(field.getEntityId())) {
      throw BizException.of("字段不存在: " + fieldId);
    }
    return CatalogDtoMapper.toDto(field);
  }

  @Transactional(readOnly = true)
  public PageResult<MetaFieldDTO> pageFields(
      Long entityId, String keyword, int pageNum, int pageSize) {
    PageResult<MetaField> sdkPage =
        metaFieldRepository.pageFields(entityId, keyword, pageNum, pageSize);
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }

  // ===================== 内部辅助 =====================

  private void assertEntityCodeUnique(long tenantId, String code) {
    if (code == null || code.isBlank()) {
      return;
    }
    Optional<MetaEntity> existing = metaEntityRepository.findByTenantAndCode(tenantId, code);
    if (existing.isEmpty()) {
      return;
    }
    throw BizException.of(describeOccupied("实体编码", code, existing.get()));
  }

  /**
   * 表名唯一性预检：{@code uk_meta_e_table} 撞键会抛出 DataIntegrityViolationException 并被兜成 500，
   * 用户看到的是「服务器异常」而不是「换个表名」。
   */
  private void assertEntityTableUnique(long tenantId, String tableName) {
    if (tableName == null || tableName.isBlank()) {
      return;
    }
    Optional<MetaEntity> existing =
        metaEntityRepository.findByTenantAndTableName(tenantId, tableName);
    if (existing.isEmpty()) {
      return;
    }
    throw BizException.of(describeOccupied("实体表名", tableName, existing.get()));
  }

  /**
   * 唯一键冲突文案：区分「被在用实体占用」与「被已删除实体占用」。
   *
   * <p>后者只有已发布/归档过的实体才会出现（草稿实体删除时已释放占位），复用其编码会与残留的物理表 / 生成产物冲突，因此明确告知「不可复用」，而不是让用户反复试错。
   */
  private static String describeOccupied(String what, String value, MetaEntity existing) {
    if (Boolean.TRUE.equals(existing.getDeleted())) {
      return what + "已被已删除实体占用: " + value + "（该实体曾发布/归档，编码不可复用以避免与已生成产物冲突，请更换）";
    }
    return what + "已存在: " + value;
  }

  private void assertFieldCodeUnique(long entityId, String code) {
    if (metaFieldRepository.findByEntityAndCode(entityId, code).isPresent()) {
      throw BizException.of("字段编码已存在: " + code);
    }
  }

  private MetaEntity requireRuntimeEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw BizException.of("实体不存在: " + entityId);
    }
    if (!MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      throw BizException.of("仅 RUNTIME 实体支持物理结构维护: " + entity.getCode());
    }
    return entity;
  }
}
