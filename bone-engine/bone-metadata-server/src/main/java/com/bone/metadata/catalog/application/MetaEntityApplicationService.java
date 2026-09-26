package com.bone.metadata.catalog.application;

import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CopyMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCommand;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.dto.PublishPreviewDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityRelation;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import com.bone.metadata.runtime.RuntimeEntityCacheEvictor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
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

  /** 标识符白名单（2a UC-MT2）：模式 B 建表/路由依赖合法标识符，编码与表名同规。 */
  static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

  /** 单次校验/复制读取的字段上限（实体字段数远小于此；防御性分页上限而非业务约束）。 */
  private static final int MAX_FIELDS = 500;

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;
  private final MetaEntityRelationRepository metaEntityRelationRepository;
  private final IamModuleValidator iamModuleValidator;
  private final TenantProvider tenantProvider;
  private final PhysicalStructureGateway physicalStructureGateway;
  private final CurrentUserProvider currentUserProvider;
  private final Optional<RuntimeEntityCacheEvictor> runtimeEntityCacheEvictor;
  private final PlatformTransactionManager transactionManager;

  // ===================== 实体写操作 =====================

  @Transactional
  public Long createEntity(CreateMetaEntityCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    if (cmd.getModuleId() != null) {
      // G1②：建模准入 = 模块存在 + 租户一致 + 应用角色（无主体场景在校验器内豁免并记 WARN）
      iamModuleValidator.requireModelingAllowed(
          cmd.getModuleId(), currentUserProvider.currentUserIdOrNull());
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
      // 发布前先校验物理表类型漂移（非破坏性 align 无法修正），把运行期 SQL 错误前移为发布期拒绝
      physicalStructureGateway.validateForPublish(entity.getTenantId(), entity.getCode());
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

  // ===================== 建模期校验 / 发布预览 / 实体复制（建模工作台闭环） =====================

  /**
   * 静态校验（UC-W5）：命名规范、必填、字段编码重复、关系引用完整性、RUNTIME 物理漂移。
   *
   * <p>只读不落库、不执行 DDL；物理漂移复用 {@link PhysicalStructureGateway#inspect}（纯只读 diff）。 结果分级 ERROR（阻断发布）/
   * WARNING / INFO，供前端发布按钮禁用与问题面板展示；发布期兜底校验不受影响。
   */
  @Transactional(readOnly = true)
  public List<EntityValidationIssue> validateEntity(Long id) {
    return collectIssues(requireEntity(id));
  }

  /**
   * 发布摘要预览（UC-W7 摘要级）：实体 + 字段清单 + 关系数 + 校验问题 + RUNTIME 物理 inspect 计划。
   *
   * <p>摘要级先行于 ADR-0039 R1 的完整发布包：此处不产生审批/快照语义，仅回答「发布将发生什么」。
   */
  @Transactional(readOnly = true)
  public PublishPreviewDTO getPublishPreview(Long id) {
    MetaEntity entity = requireEntity(id);
    List<EntityValidationIssue> issues = collectIssues(entity);
    boolean hasBlocking =
        issues.stream().anyMatch(i -> EntityValidationIssue.LEVEL_ERROR.equals(i.getLevel()));
    List<MetaFieldDTO> fields =
        CatalogPageMapper.toApiPage(
                metaFieldRepository.pageFields(id, null, 1, MAX_FIELDS), CatalogDtoMapper::toDto)
            .getRecords();
    long tenantId = tenantProvider.currentTenantId();
    long relationCount =
        metaEntityRelationRepository.pageRelations(tenantId, id, null, null, 1, 1).getTotal()
            + metaEntityRelationRepository.pageRelations(tenantId, null, id, null, 1, 1).getTotal();
    return PublishPreviewDTO.builder()
        .entityId(entity.getId())
        .entityCode(entity.getCode())
        .status(entity.getStatus())
        .deliveryMode(entity.getDeliveryMode())
        .fields(fields)
        .relationCount(relationCount)
        .validationIssues(issues)
        .hasBlockingErrors(hasBlocking)
        .runnable(!hasBlocking)
        .physical(runtimePlan(entity))
        .build();
  }

  /**
   * 实体复制（UC-W2 流程 B）：复制实体定义 + 全部字段为新草稿实体（关系不复制——跨实体引用需人工重定向）。
   *
   * <p>复用 {@link #createEntity}：目标模块唯一性校验与 G1② 应用建模角色校验统一在其中强制。
   */
  @Transactional
  public Long copyEntity(Long sourceId, CopyMetaEntityCommand cmd) {
    if (cmd.getCode() == null || cmd.getCode().isBlank()) {
      throw BizException.of(400, "复制实体必须提供新编码");
    }
    if (cmd.getTableName() == null || cmd.getTableName().isBlank()) {
      throw BizException.of(400, "复制实体必须提供新表名");
    }
    MetaEntity source = requireEntity(sourceId);
    long tenantId = tenantProvider.currentTenantId();
    Long targetModuleId =
        cmd.getTargetModuleId() != null ? cmd.getTargetModuleId() : source.getModuleId();
    CreateMetaEntityCommand createCmd = new CreateMetaEntityCommand();
    createCmd.setName(orDefault(cmd.getName(), source.getName() + "_copy"));
    createCmd.setCode(cmd.getCode().trim());
    createCmd.setDisplayName(orDefault(cmd.getDisplayName(), source.getDisplayName() + " 副本"));
    createCmd.setDescription(
        orDefault(cmd.getDescription(), "复制自实体 " + source.getCode() + "（#" + source.getId() + "）"));
    createCmd.setTableName(cmd.getTableName().trim());
    createCmd.setType(source.getType());
    createCmd.setDeliveryMode(source.getDeliveryMode());
    createCmd.setIcon(source.getIcon());
    createCmd.setModuleId(targetModuleId);
    Long newId = createEntity(createCmd);
    List<MetaField> fields = loadAllFields(sourceId);
    for (MetaField f : fields) {
      metaFieldRepository.insert(
          MetaField.create(
              null,
              tenantId,
              newId,
              f.getName(),
              f.getCode(),
              f.getDisplayName(),
              f.getType(),
              f.getLength(),
              f.getRequired(),
              f.getUnique(),
              f.getDefaultValue(),
              f.getComment(),
              f.getSortOrder(),
              targetModuleId));
    }
    return newId;
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
      String keyword, Integer status, Long moduleId, int pageNum, int pageSize) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<MetaEntity> sdkPage =
        metaEntityRepository.pageEntities(tenantId, keyword, status, moduleId, pageNum, pageSize);
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

  // ===================== 校验/预览/复制 内部辅助 =====================

  private MetaEntity requireEntity(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw BizException.of(404, "实体不存在: " + id);
    }
    return entity;
  }

  private List<MetaField> loadAllFields(Long entityId) {
    return metaFieldRepository.pageFields(entityId, null, 1, MAX_FIELDS).getRecords();
  }

  /** 汇总静态校验问题：基础规范 → 字段 → 关系 → RUNTIME 物理漂移 → 已发布说明。 */
  private List<EntityValidationIssue> collectIssues(MetaEntity entity) {
    List<EntityValidationIssue> issues = new ArrayList<>();
    validateBasics(entity, issues);
    List<MetaField> fields = loadAllFields(entity.getId());
    validateFields(fields, issues);
    validateRelations(entity, issues);
    // 物理漂移阻断判定与发布期同源：复用 validateForPublish（缺表/缺列放行——由 align 补齐；
    // 仅「模型类型 vs 物理列类型不兼容」才拒绝，与 publish 的 409 行为一致，避免预览过拦）。
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      try {
        physicalStructureGateway.validateForPublish(entity.getTenantId(), entity.getCode());
      } catch (BizException e) {
        issues.add(
            EntityValidationIssue.error(
                "PHYSICAL_DRIFT", "物理结构与模型存在类型漂移，发布将被拒绝：" + e.getMessage()));
      }
    }
    if (MetaEntityStatus.PUBLISHED == entity.statusEnum()) {
      issues.add(
          EntityValidationIssue.info("PUBLISHED_READONLY", "已发布实体仅允许加字段或修改非破坏属性；结构性变更须先归档或回退草稿"));
    }
    return issues;
  }

  private void validateBasics(MetaEntity entity, List<EntityValidationIssue> issues) {
    if (isBlankOrInvalidIdentifier(entity.getCode())) {
      issues.add(
          EntityValidationIssue.error(
              "ENTITY_CODE_INVALID", "实体编码不符合规范 ^[a-zA-Z][a-zA-Z0-9_]*$: " + entity.getCode()));
    }
    if (isBlankOrInvalidIdentifier(entity.getTableName())) {
      issues.add(
          EntityValidationIssue.error(
              "ENTITY_TABLE_INVALID", "表名不符合规范 ^[a-zA-Z][a-zA-Z0-9_]*$: " + entity.getTableName()));
    }
    if (entity.getName() == null
        || entity.getName().isBlank()
        || entity.getDisplayName() == null
        || entity.getDisplayName().isBlank()) {
      issues.add(EntityValidationIssue.error("ENTITY_NAME_REQUIRED", "实体名称与显示名均不能为空"));
    }
    if (entity.getModuleId() == null) {
      issues.add(
          EntityValidationIssue.warning("MODULE_UNASSIGNED", "实体未归属任何模块，建议归属以收敛权限与查询（应用是建模权限边界）"));
    }
  }

  private void validateFields(List<MetaField> fields, List<EntityValidationIssue> issues) {
    Set<String> seen = new HashSet<>();
    for (MetaField f : fields) {
      if (isBlankOrInvalidIdentifier(f.getCode())) {
        issues.add(
            EntityValidationIssue.fieldError(
                "FIELD_CODE_INVALID", "字段编码不符合规范: " + f.getCode(), f.getId(), f.getDisplayName()));
      } else if (!seen.add(f.getCode())) {
        issues.add(
            EntityValidationIssue.fieldError(
                "FIELD_CODE_DUPLICATE",
                "字段编码在实体内重复: " + f.getCode(),
                f.getId(),
                f.getDisplayName()));
      }
      if (Boolean.TRUE.equals(f.getRequired()) && Boolean.TRUE.equals(f.getUnique())) {
        issues.add(
            EntityValidationIssue.fieldWarning(
                "FIELD_REQUIRED_UNIQUE",
                "字段「" + f.getDisplayName() + "」同时必填且唯一，可能阻断存量数据导入与运行时写入",
                f.getId(),
                f.getDisplayName()));
      }
      if (("STRING".equalsIgnoreCase(f.getType()) || "TEXT".equalsIgnoreCase(f.getType()))
          && (f.getLength() == null || f.getLength() <= 0)) {
        issues.add(
            EntityValidationIssue.fieldWarning(
                "FIELD_LENGTH_MISSING",
                "字符串字段「" + f.getDisplayName() + "」未设置长度，建列将使用实现默认长度，请确认符合预期",
                f.getId(),
                f.getDisplayName()));
      }
    }
  }

  /** 关系引用完整性：两端实体必须真实存在（软删后不再命中 findById）。 */
  private void validateRelations(MetaEntity entity, List<EntityValidationIssue> issues) {
    long tenantId = tenantProvider.currentTenantId();
    List<MetaEntityRelation> related = new ArrayList<>();
    related.addAll(
        metaEntityRelationRepository
            .pageRelations(tenantId, entity.getId(), null, null, 1, MAX_FIELDS)
            .getRecords());
    related.addAll(
        metaEntityRelationRepository
            .pageRelations(tenantId, null, entity.getId(), null, 1, MAX_FIELDS)
            .getRecords());
    for (var rel : related) {
      Long otherId =
          entity.getId().equals(rel.getSourceEntityId())
              ? rel.getTargetEntityId()
              : rel.getSourceEntityId();
      if (otherId == null || metaEntityRepository.findById(otherId) == null) {
        issues.add(
            EntityValidationIssue.error(
                "RELATION_BROKEN", "关系「" + rel.getName() + "」引用的实体不存在（可能已删除）: id=" + otherId));
      }
    }
  }

  /** RUNTIME 实体的物理结构 inspect（纯只读）；GENERATIVE 返回 null（无物理对齐动作）。 */
  private PhysicalStructurePlan runtimePlan(MetaEntity entity) {
    if (!MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      return null;
    }
    return physicalStructureGateway.inspect(entity.getTenantId(), entity.getCode());
  }

  private boolean isBlankOrInvalidIdentifier(String value) {
    return value == null || value.isBlank() || !IDENTIFIER_PATTERN.matcher(value).matches();
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
