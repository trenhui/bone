package com.bone.metadata.catalog.application;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.core.exception.DomainException;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CopyMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaFieldCommand;
import com.bone.metadata.catalog.application.command.cmd.ImportMetaEntityFromTableCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaFieldCommand;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.MetaFieldDTO;
import com.bone.metadata.catalog.application.query.dto.PendingAuthorizationDTO;
import com.bone.metadata.catalog.application.query.dto.PublishPreviewDTO;
import com.bone.metadata.catalog.application.query.mapper.CatalogDtoMapper;
import com.bone.metadata.catalog.application.support.PhysicalTypeMapper;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.common.CatalogErrors;
import com.bone.metadata.catalog.common.CatalogPageMapper;
import com.bone.metadata.catalog.common.CatalogVersionSupport;
import com.bone.metadata.catalog.common.ImportMetaEntityResult;
import com.bone.metadata.catalog.domain.gateway.ActionDefMaterializePort;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaDeliveryMode;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityRelation;
import com.bone.metadata.catalog.domain.model.meta.MetaEntityStatus;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.meta.event.MetaEntityPublishedEvent;
import com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan;
import com.bone.metadata.catalog.domain.model.physical.PhysicalTableColumn;
import com.bone.metadata.catalog.domain.model.physical.PhysicalTableSnapshot;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import com.bone.metadata.runtime.RuntimeEntityCacheEvictor;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.bone.metadata.sdk.support.config.MetadataSdkContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class MetaEntityApplicationService {

  /** 标识符白名单（2a UC-MT2）：模式 B 建表/路由依赖合法标识符，编码与表名同规。 */
  static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]*$");

  /** 单次校验/复制读取的字段上限（实体字段数远小于此；防御性分页上限而非业务约束）。 */
  private static final int MAX_FIELDS = 500;

  /** 数据分级合法取值（业界通用：公开/内部/秘密/机密/绝密）。 */
  private static final Set<String> ALLOWED_DATA_CLASSIFICATIONS =
      Set.of("PUBLIC", "INTERNAL", "CONFIDENTIAL", "SECRET", "TOP_SECRET");

  /** 敏感级别合法取值（业界通用：L1 一般/L2 较敏感/L3 敏感/L4 极敏感）。 */
  private static final Set<String> ALLOWED_SENSITIVITY_LEVELS = Set.of("L1", "L2", "L3", "L4");

  /** 自动编号配置 JSON 解析器（校验写入前配置合法性，避免脏数据静默失效）。 */
  private static final ObjectMapper AUTO_NUMBER_MAPPER = new ObjectMapper();

  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;
  private final MetaEntityRelationRepository metaEntityRelationRepository;
  private final IamModuleValidator iamModuleValidator;
  private final TenantProvider tenantProvider;
  private final PhysicalStructureGateway physicalStructureGateway;
  private final MetadataService metadataService;
  private final CurrentUserProvider currentUserProvider;
  private final Optional<RuntimeEntityCacheEvictor> runtimeEntityCacheEvictor;
  private final ActionDefMaterializePort actionDefMaterializePort;
  private final DomainEventPublisher domainEventPublisher;
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
    assertAutoNumberConfigValid(cmd.getAutoNumber());
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
            cmd.getModuleId(),
            cmd.getAutoNumber());
    metaEntityRepository.insert(entity);
    return entity.getId();
  }

  @Transactional
  public Integer updateEntity(Long id, UpdateMetaEntityCommand cmd, Integer expectedVersion) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, entity.getVersion());
    assertAutoNumberConfigValid(cmd.getAutoNumber());
    entity.update(
        cmd.getName(),
        cmd.getDisplayName(),
        cmd.getDescription(),
        cmd.getTableName(),
        cmd.getSortOrder(),
        cmd.getIcon(),
        cmd.getDeliveryMode(),
        cmd.getAutoNumber());
    metaEntityRepository.update(entity);
    return entity.getVersion();
  }

  @Transactional
  public void deleteEntity(Long id) {
    deleteEntity(id, false);
  }

  /**
   * 删除实体（可选联动 DROP 物理表）。
   *
   * @param dropPhysicalTable true=同时删除 RUNTIME 实体的物理表（破坏性，用户显式勾选时才为 true）； GENERATIVE 实体无物理表，该旗标对它是
   *     no-op。false=仅软删定义，物理表保留（默认）。
   */
  @Transactional
  public void deleteEntity(Long id, boolean dropPhysicalTable) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, id);
    }
    // 先取原始 code / tenantId / tableName：草稿删除会追加 _del_<id> 后缀释放唯一键，级联必须按原始 code 定位动作定义。
    long tenantId = entity.getTenantId();
    String entityCode = entity.getCode();
    String tableName = entity.getTableName();
    // 草稿实体先释放 code / table_name 的唯一键占位，再逻辑删除，
    // 否则「建错了重建」会一直卡在「编码已存在」（唯一键覆盖逻辑删除行）。
    if (entity.releaseUniqueKeysForDelete()) {
      metaEntityRepository.update(entity);
    }
    metaEntityRepository.deleteById(id);
    // 级联软删运行时动作定义：否则实体已删而定义仍活跃，运行时会继续暴露一个不存在实体的动作入口，
    // 且重建同名实体时 UPSERT 会把旧定义复活（用户看到「没配过却有动作」）。
    int cascaded = actionDefMaterializePort.deleteByEntity(tenantId, entityCode);
    if (cascaded > 0) {
      log.info("[MetaEntity] 删除实体 {} 时级联软删 {} 条运行时动作定义", entityCode, cascaded);
    }
    if (dropPhysicalTable) {
      // 用户显式勾选「同时删除物理表」⇒ 补上删除闭环（此前只软删定义，库里积压 36 张无主表）。
      // GENERATIVE 实体从未建表（align 仅对 RUNTIME 生效）⇒ 不调网关，避免无意义 DDL。
      // 注意：MySQL DDL 隐式提交，DROP 不受本事务回滚保护——放在流程末端且仅在显式意图下执行。
      if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum()) && tableName != null) {
        PhysicalStructurePlan dropPlan = physicalStructureGateway.dropTable(tableName);
        if (dropPlan.executed() > 0) {
          log.info("[MetaEntity] 删除实体 {} 时联动 DROP 物理表 {}", entityCode, dropPlan.tableName());
        } else {
          log.info("[MetaEntity] 删除实体 {} 联动 DROP 跳过：{}", entityCode, dropPlan.message());
        }
      }
    }
    // RC-002 对称：删除（含联动 DROP 物理表）后失效 runtime 缓存，使运行时的已发布实体快照随之失效，
    // 否则缓存 TTL 内仍命中旧快照并对已 DROP 的物理表发起查询 → 500（应为 404 实体不存在）。
    runtimeEntityCacheEvictor.ifPresent(evictor -> evictor.evict(entityCode, tenantId));
  }

  /**
   * 发布实体（doc2a §337 主流程步骤④⑤）：状态落库 → 失效 runtime 缓存 → RUNTIME 物理结构对齐 → 发布领域事件。
   *
   * <p>事件 {@link MetaEntityPublishedEvent} 在全部发布动作成功后发出（align 失败即发布失败、无事件）； 单条与批量发布共用本方法，下游经
   * {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅。
   */
  @Transactional
  public Integer publishEntity(Long id, Integer expectedVersion) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, id);
    }
    CatalogVersionSupport.assertExpected(expectedVersion, entity.getVersion());
    entity.publish();
    metaEntityRepository.update(entity);
    if (runtimeEntityCacheEvictor.isEmpty()) {
      // 失效器缺失 ⇒ 发布后运行时仍读旧快照（最长 TTL 60s），新字段写入会被**静默丢弃**。
      // 这是数据正确性缺陷而非性能问题，必须显式报警而不是静默降级。
      log.error(
          "[RC-002] 运行时缓存失效器未装配：发布后最长 {} 内运行时仍用旧快照，新字段写入将被丢弃。"
              + "请检查 CatalogInfrastructureConfiguration 的 RuntimeEntityCacheEvictor 注册条件。",
          "TTL");
    }
    runtimeEntityCacheEvictor.ifPresent(
        evictor -> evictor.evict(entity.getCode(), entity.getTenantId()));
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      // 机制 B：对尚无物理列的新字段，经 SDK 预留列池动态分配 ext_*（不 ALTER 真实列），写回物理列名
      allocateReservedColumns(entity);
      // 发布前先校验物理表类型漂移（非破坏性 align 无法修正），把运行期 SQL 错误前移为发布期拒绝
      validatePhysicalStructureForPublish(entity);
      physicalStructureGateway.align(entity.getTenantId(), entity.getCode());
    }
    // RC-002 热生效：缓存在**全部发布期变更完成后**再失效一次。
    // 前置失效不够 —— allocateReservedColumns / align 之间若发生任何读取，都会把「尚未带上新字段/新物理列」
    // 的快照回填进 Caffeine（TTL 60s），表现为「发布成功但新字段写入被静默丢弃」的数据丢失，
    // 只有等 TTL 过期才自愈。RUNTIME 模式必须二次失效。
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      runtimeEntityCacheEvictor.ifPresent(
          evictor -> evictor.evict(entity.getCode(), entity.getTenantId()));
    }
    domainEventPublisher.publish(new MetaEntityPublishedEvent(entity));
    return entity.getVersion();
  }

  /**
   * 发布期物理结构校验（doc2a §328）：网关抛出的 {@link DomainException} 在应用层翻译为 {@code 409 + META_DOMAIN_ERROR} 的
   * {@link BizException}——handler 的 DomainException 兜底分支是 400 通用码，直接漏过去会把「漂移拒绝」降级成参数错误，且预览侧 {@code
   * catch (BizException)} 也接不住。
   */
  private void validatePhysicalStructureForPublish(MetaEntity entity) {
    try {
      physicalStructureGateway.validateForPublish(entity.getTenantId(), entity.getCode());
    } catch (DomainException e) {
      throw new BizException(409, e.getMessage(), CatalogErrorCodes.META_DOMAIN_ERROR, e);
    }
  }

  // ===================== 逆向建模（UC-IMP：存量物理表 → 目录实体） =====================

  /**
   * 从存量物理表导入建模（业界元数据平台的 schema crawl / 逆向采集）。
   *
   * <p>真实场景价值：企业存量业务表（如本仓库 bone-blueprint 的 {@code t_order}）要先被元数据平台看见， 才能在其上做「元数据驱动的字段扩展」——
   * 否则存量表只能靠人工逐字段重录，且极易与物理表漂移。
   *
   * <p>安全与一致性口径：
   *
   * <ul>
   *   <li>表不存在直接拒绝（不建与物理库脱节的空模型）；
   *   <li>平台保留列（id / tenant_id / version / deleted / 审计列）默认跳过，由平台托管，建模它们会让运行期读写冲突；
   *   <li>字段类型与物理列同大类（经 {@link PhysicalTypeMapper}），保证导入后可被发布校验接受（零漂移）；
   *   <li>产物为 DRAFT 实体——导入不等于发布，人工复核后再发布，避免误纳管。
   * </ul>
   *
   * @return 导入结果（dryRun 时 entityId 为 null，仅回采集统计）
   */
  @Transactional
  public ImportMetaEntityResult importEntityFromTable(ImportMetaEntityFromTableCommand cmd) {
    long tenantId = tenantProvider.currentTenantId();
    PhysicalTableSnapshot snapshot = physicalStructureGateway.readTableSnapshot(cmd.getTableName());
    if (!snapshot.exists()) {
      throw CatalogErrors.of(CatalogErrorCodes.PHYSICAL_TABLE_NOT_FOUND, cmd.getTableName());
    }
    List<PhysicalTableColumn> columns = selectColumns(snapshot, cmd.getIncludeReserved());
    List<String> skipped =
        snapshot.columns().stream()
            .filter(c -> !columns.contains(c))
            .map(PhysicalTableColumn::columnName)
            .toList();
    String code = defaultIfBlank(cmd.getCode(), snapshot.tableName());
    boolean dryRun = Boolean.TRUE.equals(cmd.getDryRun());
    if (dryRun) {
      return new ImportMetaEntityResult(
          null, code, snapshot.tableName(), columns.size(), skipped, true);
    }
    assertEntityCodeUnique(tenantId, code);
    assertEntityTableUnique(tenantId, snapshot.tableName());
    // 存量表已物理存在且需被运行时数据面读写 → 默认 RUNTIME（模式 B）
    int deliveryMode =
        cmd.getDeliveryMode() != null
            ? MetaDeliveryMode.fromCode(cmd.getDeliveryMode()).getCode()
            : MetaDeliveryMode.RUNTIME.getCode();
    MetaEntity entity =
        MetaEntity.create(
            null,
            tenantId,
            defaultIfBlank(cmd.getName(), code),
            code,
            defaultIfBlank(cmd.getDisplayName(), snapshot.tableName()),
            cmd.getDescription(),
            snapshot.tableName(),
            0,
            deliveryMode,
            null,
            null);
    metaEntityRepository.insert(entity);
    int sortOrder = 0;
    for (PhysicalTableColumn column : columns) {
      MetaField field =
          MetaField.createFromPhysical(
              null,
              tenantId,
              entity.getId(),
              column.columnName(),
              displayNameOf(column),
              PhysicalTypeMapper.toMetaType(column),
              PhysicalTypeMapper.toLength(column),
              column.isDecimal() ? column.numericPrecision() : null,
              !column.nullable(),
              column.comment(),
              sortOrder);
      metaFieldRepository.insert(field);
      sortOrder += 10;
    }
    return new ImportMetaEntityResult(
        entity.getId(), code, snapshot.tableName(), columns.size(), skipped, false);
  }

  private static List<PhysicalTableColumn> selectColumns(
      PhysicalTableSnapshot snapshot, Boolean includeReserved) {
    return Boolean.TRUE.equals(includeReserved) ? snapshot.columns() : snapshot.modelableColumns();
  }

  /** 显示名：优先物理列注释（存量库注释即业务语义），无注释时回落列名。 */
  private static String displayNameOf(PhysicalTableColumn column) {
    String comment = column.comment();
    return comment == null || comment.isBlank() ? column.columnName() : comment;
  }

  private static String defaultIfBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
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
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, entityId);
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
            null,
            cmd.getDataClassification(),
            cmd.getPii(),
            cmd.getSensitivityLevel(),
            cmd.getDataSteward(),
            cmd.getBusinessTerm(),
            cmd.getSourceSystem(),
            cmd.getEnumValues(),
            cmd.getValidationRules());
    metaFieldRepository.insert(field);
    return field.getId();
  }

  @Transactional
  public Integer updateField(Long fieldId, UpdateMetaFieldCommand cmd, Integer expectedVersion) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null) {
      throw CatalogErrors.of(CatalogErrorCodes.FIELD_NOT_FOUND, fieldId);
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
        cmd.getSortOrder(),
        cmd.getDataClassification(),
        cmd.getPii(),
        cmd.getSensitivityLevel(),
        cmd.getDataSteward(),
        cmd.getBusinessTerm(),
        cmd.getSourceSystem(),
        cmd.getEnumValues(),
        cmd.getValidationRules());
    metaFieldRepository.update(field);
    return field.getVersion();
  }

  @Transactional
  public void deleteField(Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null) {
      throw CatalogErrors.of(CatalogErrorCodes.FIELD_NOT_FOUND, fieldId);
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
   *
   * <p>P1 onboarding 两项附加回答（2026-10 诊断落地）：
   *
   * <ul>
   *   <li>{@code pendingAuthorizations}：逃逸动作的动作级细码应付清单——回答「发布后要授哪些码」， 把「按钮置灰却不知道找谁授」的断链前移为发布时可见的清单；
   *   <li>{@code deliveryModeNotice}：交付模式后果明示——回答「发布成功为什么没有表/没有数据入口」， 把运行期惊讶前移为创建期决策。
   * </ul>
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
        .pendingAuthorizations(pendingAuthorizationsOf(tenantId, entity))
        .deliveryModeNotice(deliveryModeNoticeOf(entity))
        .build();
  }

  /** 逃逸动作细码应付清单（P1 onboarding）。非逃逸动作由实体级/通配 write 覆盖，无逐动作补权必要，故只收 escape。 */
  private List<PendingAuthorizationDTO> pendingAuthorizationsOf(long tenantId, MetaEntity entity) {
    return actionDefMaterializePort.listEscapeActions(tenantId, entity.getCode()).stream()
        .map(
            a ->
                PendingAuthorizationDTO.builder()
                    .code(a.permissionCode())
                    .actionCode(a.actionCode())
                    .actionLabel(a.label())
                    .build())
        .toList();
  }

  /** 交付模式后果明示（P1）：与 MetaDeliveryMode 语义同源，文案面向建模决策者。 */
  private static String deliveryModeNoticeOf(MetaEntity entity) {
    if (MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      return "运行时交付（RUNTIME）：发布将创建/对齐物理表 "
          + entity.getTableName()
          + "，之后即可在「数据」页录入业务记录；其中的逃逸动作（资金/外部副作用）须动作级授权后方可执行。";
    }
    return "生成式交付（GENERATIVE）：发布不创建运行时物理表，记录不落 runtime 数据面；" + "如需录入业务数据并驱动状态机，请在建模时选择「运行时交付」。";
  }

  /**
   * 实体复制（UC-W2 流程 B）：复制实体定义 + 全部字段为新草稿实体（关系不复制——跨实体引用需人工重定向）。
   *
   * <p>复用 {@link #createEntity}：目标模块唯一性校验与 G1② 应用建模角色校验统一在其中强制。
   */
  @Transactional
  public Long copyEntity(Long sourceId, CopyMetaEntityCommand cmd) {
    if (cmd.getCode() == null || cmd.getCode().isBlank()) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_COPY_CODE_REQUIRED, "复制实体必须提供新编码");
    }
    if (cmd.getTableName() == null || cmd.getTableName().isBlank()) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_COPY_TABLE_NAME_REQUIRED, "复制实体必须提供新表名");
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
    // 复制定义时一并复制自动编号配置（auto_number 属实体定义，避免副本丢失单据号能力）
    createCmd.setAutoNumber(source.getAutoNumber());
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
              targetModuleId,
              f.getDataClassification(),
              f.getPii(),
              f.getSensitivityLevel(),
              f.getDataSteward(),
              f.getBusinessTerm(),
              f.getSourceSystem(),
              f.getEnumValues(),
              f.getValidationRules()));
    }
    return newId;
  }

  // ===================== 读操作（ADR-0030：经 domain.repository 读模型） =====================

  @Transactional(readOnly = true)
  public MetaEntityDTO getEntity(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, id);
    }
    return CatalogDtoMapper.toDto(entity);
  }

  @Transactional(readOnly = true)
  public PageResult<MetaEntityDTO> pageEntities(
      String keyword, Integer status, Long moduleId, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<MetaEntity> sdkPage =
        metaEntityRepository.pageEntities(tenantId, keyword, status, moduleId, page, size);
    return CatalogPageMapper.toApiPage(sdkPage, CatalogDtoMapper::toDto);
  }

  @Transactional(readOnly = true)
  public MetaFieldDTO getField(Long entityId, Long fieldId) {
    MetaField field = metaFieldRepository.findById(fieldId);
    if (field == null || !entityId.equals(field.getEntityId())) {
      throw CatalogErrors.of(CatalogErrorCodes.FIELD_NOT_FOUND, fieldId);
    }
    return CatalogDtoMapper.toDto(field);
  }

  @Transactional(readOnly = true)
  public PageResult<MetaFieldDTO> pageFields(Long entityId, String keyword, int page, int size) {
    PageResult<MetaField> sdkPage = metaFieldRepository.pageFields(entityId, keyword, page, size);
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
    throw CatalogErrors.of(
        CatalogErrorCodes.ENTITY_CODE_CONFLICT, describeOccupied("实体编码", code, existing.get()));
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
    throw CatalogErrors.of(
        CatalogErrorCodes.ENTITY_TABLE_NAME_CONFLICT,
        describeOccupied("实体表名", tableName, existing.get()));
  }

  /**
   * 自动编号配置合法性预检（0043）：空 = 不启用，直接放行；非空必须能解析为 JSON 且含非空 {@code field} / {@code prefix}，否则在写入前给出明确
   * 400，而非写脏数据后运行时静默不注入。
   *
   * <p>与运行时 {@code JdbcRuntimeRecordService#parseAutoNumber} 同源校验口径，但此处是「写入口守卫」——
   * 把错误挡在用户操作那一刻，而不是等首次录入业务数据时才发现单据号没生成。
   */
  private void assertAutoNumberConfigValid(String json) {
    if (json == null || json.isBlank()) {
      return;
    }
    // 仅把 JSON 解析失败包成配置非法；字段校验直接用 CatalogErrors.of()（运行时异常）透传，带规范错误码。
    JsonNode n;
    try {
      n = AUTO_NUMBER_MAPPER.readTree(json);
    } catch (Exception e) {
      throw CatalogErrors.of(
          CatalogErrorCodes.AUTO_NUMBER_CONFIG_INVALID, "自动编号配置不是合法 JSON: " + e.getMessage());
    }
    String field = n.path("field").asText(null);
    String prefix = n.path("prefix").asText(null);
    String reset = n.path("reset").asText("none");
    if (field == null || prefix == null || field.isBlank() || prefix.isBlank()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.AUTO_NUMBER_CONFIG_INVALID, "自动编号配置缺少必填项 field / prefix");
    }
    if (!reset.equals("none") && !reset.equals("daily")) {
      throw CatalogErrors.of(
          CatalogErrorCodes.AUTO_NUMBER_CONFIG_INVALID,
          "自动编号重置粒度 reset 仅支持 none（全局递增）/ daily（每日重置）");
    }
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
      throw CatalogErrors.of(CatalogErrorCodes.FIELD_CODE_CONFLICT, code);
    }
  }

  private MetaEntity requireRuntimeEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, entityId);
    }
    if (!MetaDeliveryMode.RUNTIME.equals(entity.deliveryModeEnum())) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_RUNTIME, entity.getCode());
    }
    return entity;
  }

  // ===================== 校验/预览/复制 内部辅助 =====================

  private MetaEntity requireEntity(Long id) {
    MetaEntity entity = metaEntityRepository.findById(id);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, id);
    }
    return entity;
  }

  private List<MetaField> loadAllFields(Long entityId) {
    return metaFieldRepository.pageFields(entityId, null, 1, MAX_FIELDS).getRecords();
  }

  /**
   * 机制 B 预留列分配（方案 A）：RUNTIME 实体发布前，对「模型字段在物理表无对应列」的字段， 经 {@code
   * MetadataService.allocateAndPersistFields} 复用 SDK ColumnAllocator 从 {@code column_allocation}
   * 池动态分配一个 {@code ext_*} 预留列，并把物理列名写回 {@code meta_field.physical_column}。
   *
   * <p>已分配过（physicalColumn 非空）的字段跳过——重复发布不会重复分配，SDK 依据 column_allocation 池状态推进。 真实物理列（机制 A /
   * 逆向建模列，code 已存在于物理表）同样跳过。
   */
  private void allocateReservedColumns(MetaEntity entity) {
    PhysicalTableSnapshot snapshot =
        physicalStructureGateway.readTableSnapshot(entity.getTableName());
    if (!snapshot.exists()) {
      return; // 缺表由 align 创建（GENERATIVE 语义，无 B 字段分配需求）
    }
    Set<String> existing =
        snapshot.columns().stream()
            .map(PhysicalTableColumn::columnName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    List<MetaField> fields = loadAllFields(entity.getId());
    Map<String, MetaField> bFields = new LinkedHashMap<>();
    for (MetaField f : fields) {
      if (f.getPhysicalColumn() != null) {
        continue; // 已分配预留列（机制 B 已完成）
      }
      if (existing.contains(f.getCode())) {
        continue; // 已是真实物理列（机制 A / 逆向建模列）
      }
      bFields.put(f.getCode(), f);
    }
    if (bFields.isEmpty()) {
      return;
    }
    String appCode = MetadataSdkContext.getAppCode();
    List<FieldMetadata> toAllocate = new ArrayList<>();
    for (MetaField f : bFields.values()) {
      toAllocate.add(
          FieldMetadata.builder()
              .tenantId(entity.getTenantId())
              .appCode(appCode)
              .bizIdentityCode(entity.getCode())
              .entityType(entity.getCode())
              .name(f.getCode())
              .dataType(toSdkDataType(f.getType()))
              .build());
    }
    List<FieldMetadata> allocated = metadataService.allocateAndPersistFields(toAllocate);
    Map<String, String> columnByName =
        allocated.stream()
            .filter(fm -> fm.getColumnName() != null)
            .collect(
                Collectors.toMap(
                    FieldMetadata::getName, FieldMetadata::getColumnName, (a, b) -> a));
    for (MetaField f : bFields.values()) {
      String extCol = columnByName.get(f.getCode());
      if (extCol != null) {
        f.assignPhysicalColumn(extCol);
        metaFieldRepository.update(f);
      }
    }
  }

  /** 元数据模型类型 → SDK DataType 枚举名（决定分配哪个 ext_* 池，必须对齐 column_allocation.data_type）。 */
  private static String toSdkDataType(String metaType) {
    if (metaType == null) {
      return "STRING";
    }
    return switch (metaType.toUpperCase()) {
      case "TEXT" -> "TEXT";
      case "JSON" -> "JSON";
      case "NUMBER", "DECIMAL", "DOUBLE", "FLOAT" -> "NUMBER";
      case "INT", "INTEGER" -> "INTEGER";
      case "DATE", "DATETIME", "TIMESTAMP", "TIME" -> "DATE";
      case "BOOLEAN", "BOOL" -> "BOOLEAN";
      default -> "STRING"; // STRING / VARCHAR / EMAIL / URL / LONG / BIGINT 等统一进 str 池
    };
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
        validatePhysicalStructureForPublish(entity);
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
      // ===== 业界元数据 / 数据治理校验 =====
      validateFieldGovernance(f, issues);
    }
  }

  /**
   * 数据治理校验：PII 或高敏感分级字段必须有数据管家；分级取值须合法。
   *
   * <p>设计取舍：治理属性不阻断建模（warn 而非 error），避免与 core 列校验耦合； 仅在「PII 或 机密/绝密」这种强合规场景下要求责任人，否则列为「建议补全」提示。
   */
  private void validateFieldGovernance(MetaField f, List<EntityValidationIssue> issues) {
    String dc = f.getDataClassification();
    if (dc != null && !ALLOWED_DATA_CLASSIFICATIONS.contains(dc)) {
      issues.add(
          EntityValidationIssue.fieldError(
              "FIELD_DC_INVALID",
              "字段「"
                  + f.getDisplayName()
                  + "」数据分级非法(应为 "
                  + String.join("/", ALLOWED_DATA_CLASSIFICATIONS)
                  + "): "
                  + dc,
              f.getId(),
              f.getDisplayName()));
      return;
    }
    if (f.getSensitivityLevel() != null
        && !ALLOWED_SENSITIVITY_LEVELS.contains(f.getSensitivityLevel())) {
      issues.add(
          EntityValidationIssue.fieldError(
              "FIELD_SENS_INVALID",
              "字段「"
                  + f.getDisplayName()
                  + "」敏感级别非法(应为 "
                  + String.join("/", ALLOWED_SENSITIVITY_LEVELS)
                  + "): "
                  + f.getSensitivityLevel(),
              f.getId(),
              f.getDisplayName()));
      return;
    }
    boolean highRisk =
        Boolean.TRUE.equals(f.getPii())
            || "CONFIDENTIAL".equals(dc)
            || "SECRET".equals(dc)
            || "TOP_SECRET".equals(dc);
    if (highRisk && isBlank(f.getDataSteward())) {
      issues.add(
          EntityValidationIssue.fieldWarning(
              "FIELD_STEWARD_MISSING",
              "字段「" + f.getDisplayName() + "」为个人敏感或高密级数据，但未指定数据管家/责任人，发布前建议补全",
              f.getId(),
              f.getDisplayName()));
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

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String orDefault(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }
}
