package com.bone.metadata.catalog.application;

import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.application.query.dto.MergePreview;
import com.bone.metadata.catalog.application.query.dto.TemplateUpdateCheck;
import com.bone.metadata.catalog.application.query.dto.UiSchemaResp;
import com.bone.metadata.catalog.application.query.dto.UiSchemaSummary;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.catalog.common.CatalogErrors;
import com.bone.metadata.catalog.domain.gateway.TemplateReadPort;
import com.bone.metadata.catalog.domain.gateway.TemplateUiSnapshot;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.gateway.UiSchemaRefCheckPort;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.MetaField;
import com.bone.metadata.catalog.domain.model.ui.UiSchemaDef;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.repository.UiSchemaDefRepository;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 业务对象界面 schema 应用服务（迁移 0046 · P0-3）。
 *
 * <p>后端只做<b>存、验、快照</b>：CRUD + 引用完整性校验 + 发布事务内快照 + 模板跟随三步。 linkage/submit 只在前端跑（体验拦截），后端 {@code
 * RuntimeWriteValidator} + Hook + 行策略是安全底线，不后移。
 *
 * <p>并发：PUT 全量替换，锁版本走 HTTP {@code If-Match} 头（与 runtime {@code invokeAction} 的 ETag/412 同语义），{@code
 * If-Match: *} 表"我不管并发"。SDK 原生 {@code @Version} 乐观锁强制写冲突（ADR-0031）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@NoDomainEvent // E-5.4 豁免：界面配置落库无领域事件（落库方法名避开 save*，见门禁 as 语）
public class UiSchemaApplicationService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final int MAX_FIELDS = 500;

  /** 通用 JSON 嵌套深度上限（条件深度另有 UI-REF-05 的 3 层硬限）。 */
  private static final int MAX_JSON_DEPTH = 32;

  /** 0046 缺失降级日志只打一次（列表页每次翻页都会经过，防刷屏；JVM 级标记，执行迁移后重启恢复）。 */
  private static final AtomicBoolean TABLE_MISSING_WARNED = new AtomicBoolean(false);

  private static void warnTableMissing(String op, BadSqlGrammarException ex) {
    if (TABLE_MISSING_WARNED.compareAndSet(false, true)) {
      log.warn("[ui-schema] 迁移 0046 未执行（meta_entity_ui_schema 缺失），{} 已降级: {}", op, ex.getMessage());
    }
  }

  private static final Set<String> DOLLAR_VARS = Set.of("$me", "$myteam", "$all");

  private final UiSchemaDefRepository uiSchemaDefRepository;
  private final MetaEntityRepository metaEntityRepository;
  private final MetaFieldRepository metaFieldRepository;
  private final UiSchemaRefCheckPort uiSchemaRefCheckPort;
  private final TemplateReadPort templateReadPort;
  private final TenantProvider tenantProvider;

  /** 读草稿（无行 → empty，前端回落列投影）。 */
  @Transactional(readOnly = true)
  public Optional<UiSchemaResp> getDraftSchema(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    return findRow(entity).map(this::toResp);
  }

  /**
   * 保存草稿（幂等全量替换）。
   *
   * @param schemaBody 请求体：{@code {"json": <string|object>}} 或裸 BoneUiSchema（二者都接受）
   * @param ifMatch 锁版本（null=缺头；-1={@code *} 跳过检查）
   */
  @Transactional
  public UiSchemaResp saveDraftSchema(Long entityId, JsonNode schemaBody, Long ifMatch) {
    MetaEntity entity = requireEntity(entityId);
    JsonNode root = extractSchemaNode(schemaBody);
    RefContext refs = loadRefs(entity);
    // 草稿保存仅阻断结构性错误（schemaVersion 未知/非法、JSON 不可解析）：
    // 技术方案 §2.4「草稿随时 PUT」、§8.1「渲染增强永不阻断业务」。
    // UI-REF-* 引用问题（含 ERROR）不阻断草稿保存，由 /validate（validateSchema）暴露、
    // 发布闸门（publishEntity，§228）拦截——草稿是工作态，允许"引用尚未建好的字段"。
    List<EntityValidationIssue> structural =
        validateNodes(root, refs).stream()
            .filter(i -> CatalogErrorCodes.UI_SCHEMA_INVALID.equals(i.getCode()))
            .toList();
    if (!structural.isEmpty()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_INVALID,
          structural.get(0).getCode() + ": " + structural.get(0).getMessage());
    }
    int minor = root.path("minor").asInt(UiSchemaDef.SCHEMA_MINOR);
    String json = root.toString();

    Optional<UiSchemaDef> existing = findRow(entity);
    if (existing.isEmpty()) {
      UiSchemaDef def =
          UiSchemaDef.create(tenantProvider.currentTenantId(), entity.getId(), json, minor);
      try {
        uiSchemaDefRepository.insert(def);
      } catch (BadSqlGrammarException ex) {
        throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_STORE_UNAVAILABLE, "迁移 0046 未执行", ex);
      }
      log.info("[ui-schema] 新建草稿 entity={}", entity.getId());
      return toResp(def);
    }
    if (ifMatch == null) {
      // 缺 If-Match 且行已存在：412 防误覆盖（与技术方案 §3.2 同语义）
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_PRECONDITION_REQUIRED, "entity=" + entity.getId());
    }
    UiSchemaDef def = existing.get();
    if (ifMatch != -1L && !ifMatch.equals(def.getLockVersion())) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT,
          "entity=" + entity.getId() + " 期望锁=" + ifMatch + " 库内锁=" + def.getLockVersion());
    }
    def.replaceDraft(json, minor);
    try {
      uiSchemaDefRepository.update(def);
    } catch (OptimisticLockingFailureException ex) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT, "entity=" + entity.getId() + " 并发写冲突");
    } catch (BadSqlGrammarException ex) {
      throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_STORE_UNAVAILABLE, "迁移 0046 未执行", ex);
    }
    log.info("[ui-schema] 保存草稿 entity={} lock={}", entity.getId(), def.getLockVersion());
    return toResp(def);
  }

  /** 删草稿行（已发布快照保留在行内？不——删行即整行删，快照随行走；运行时读以结构为准）。 */
  @Transactional
  public void deleteDraftSchema(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    findRow(entity).ifPresent(def -> uiSchemaDefRepository.deleteById(def.getId()));
  }

  /**
   * 执行期读协议 schema（供服务端强制 {@code submitRules/linkageRules} 用）。
   *
   * <p><b>优先读发布快照</b>（{@code published_schema_json}）：线上跑的必须是「发布时冻结的那份」，否则建模者改草稿会让线上校验行为在无发布的情况下漂移——
   * 界面（读草稿）与服务端（读草稿）都变，等于绕过发布闸门偷偷改了生产规则。 未发布过（快照为空）则回落草稿：实体尚未发布时不存在「线上」， 用草稿校验反而能提前暴露问题。
   *
   * @param entityId 实体 id
   * @return schema 根节点；未配置 schema 返回 {@link Optional#empty()}（= 无协议规则，不阻断）
   */
  @Transactional(readOnly = true)
  public Optional<JsonNode> schemaForExecution(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    Optional<UiSchemaDef> row = findRow(entity);
    if (row.isEmpty()) {
      return Optional.empty();
    }
    UiSchemaDef def = row.get();
    String json = def.getPublishedSchemaJson();
    if (json == null || json.isBlank()) {
      json = def.getSchemaJson();
    }
    return Optional.ofNullable(parseJson(json));
  }

  /**
   * 实体静态必填映射（{@code meta_field.is_required} → fieldCode）。
   *
   * <p>协议 linkage 的 {@code required} 与字段静态必填取<b>并集</b>（与前端 {@code baseRequiredOf}
   * 同口径）：建模者标了必填的字段，即便 protocol 没写 linkage，也必须非空。
   */
  @Transactional(readOnly = true)
  public Map<String, Boolean> baseRequiredOf(Long entityId) {
    Map<String, Boolean> out = new HashMap<>();
    for (MetaField f : metaFieldRepository.pageFields(entityId, null, 1, MAX_FIELDS).getRecords()) {
      if (f.getCode() != null) {
        out.put(f.getCode(), Boolean.TRUE.equals(f.getRequired()));
      }
    }
    return out;
  }

  /** 引用完整性校验（删被引用字段阻断发布 + 定位到 UiSchemaTab 的原子项）。 */
  @Transactional(readOnly = true)
  public List<EntityValidationIssue> validateSchema(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    Optional<UiSchemaDef> row = findRow(entity);
    if (row.isEmpty()) {
      return List.of();
    }
    JsonNode root = parseJson(row.get().getSchemaJson());
    if (root == null || !root.isObject()) {
      return List.of(
          EntityValidationIssue.error(CatalogErrorCodes.UI_SCHEMA_INVALID, "草稿 schema 非法 JSON"));
    }
    return validateNodes(root, loadRefs(entity));
  }

  /**
   * 发布快照（挂载 {@code publishEntity} 同事务：draft→published 快照 + published_version=entity.version）。
   *
   * <p>快照失败则发布整体回滚，不出现"结构已发布、界面停在旧版"的半吊子。无 schema 行即 no-op。
   */
  @Transactional
  public void snapshotOnPublish(Long entityId, int entityVersion) {
    MetaEntity entity = requireEntity(entityId);
    Optional<UiSchemaDef> row = findRow(entity);
    if (row.isEmpty()) {
      return;
    }
    UiSchemaDef def = row.get();
    def.snapshotOnPublish(entityVersion);
    try {
      uiSchemaDefRepository.update(def);
    } catch (BadSqlGrammarException ex) {
      // 0046 未执行：发布不为此回滚（运行时读以结构为准，schema 只是渲染增强）
      warnTableMissing("发布快照跳过", ex);
      return;
    }
    log.info("[ui-schema] 发布快照 entity={} publishedVersion={}", entity.getId(), entityVersion);
  }

  /** 删实体联动（应用层同步删 schema 行，非 FK 级联）。 */
  @Transactional
  public void deleteByEntity(Long entityId) {
    long tenantId = tenantProvider.currentTenantId();
    try {
      uiSchemaDefRepository
          .findByTenantAndEntity(tenantId, entityId)
          .ifPresent(def -> uiSchemaDefRepository.deleteById(def.getId()));
    } catch (BadSqlGrammarException ex) {
      warnTableMissing("删实体联动跳过", ex);
    }
  }

  /** 列表页 hasUiSchema 批量填充（一次 IN 查询，禁 N+1）。 */
  @Transactional(readOnly = true)
  public Set<Long> schemaEntityIds(List<Long> entityIds) {
    if (entityIds == null || entityIds.isEmpty()) {
      return Set.of();
    }
    long tenantId = tenantProvider.currentTenantId();
    Set<Long> out = new HashSet<>();
    try {
      for (UiSchemaDef def : uiSchemaDefRepository.findByTenantAndEntities(tenantId, entityIds)) {
        out.add(def.getEntityId());
      }
    } catch (BadSqlGrammarException ex) {
      warnTableMissing("hasUiSchema 批量查询", ex);
    }
    return out;
  }

  /** 发布预览 ui 摘要区块（只读计数 + 阻断问题）。 */
  @Transactional(readOnly = true)
  public UiSchemaSummary summarizeForPreview(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    Optional<UiSchemaDef> row = findRow(entity);
    if (row.isEmpty()) {
      return UiSchemaSummary.builder().hasSchema(false).blockingIssues(List.of()).build();
    }
    JsonNode root = parseJson(row.get().getSchemaJson());
    List<EntityValidationIssue> issues =
        root != null && root.isObject()
            ? validateNodes(root, loadRefs(entity))
            : List.of(
                EntityValidationIssue.error(
                    CatalogErrorCodes.UI_SCHEMA_INVALID, "草稿 schema 非法 JSON"));
    List<EntityValidationIssue> blocking =
        issues.stream()
            .filter(i -> EntityValidationIssue.LEVEL_ERROR.equals(i.getLevel()))
            .toList();
    return UiSchemaSummary.builder()
        .hasSchema(true)
        .sections(root != null ? countSections(root) : 0)
        .linkageRules(root != null ? root.path("linkageRules").size() : 0)
        .submitRules(root != null ? root.path("submitRules").size() : 0)
        .actions(root != null ? root.path("actionBar").size() : 0)
        .blockingIssues(blocking)
        .build();
  }

  /**
   * 模板实例化拷贝（挂载 {@code instantiate} 动作展开之后）。
   *
   * <p>模板 {@code ui_schema_json} 非空 → 建 UiSchemaDef 行 + 写 base 指针；namespace 服务端重写为 {@code
   * bone.ui.<entityCode>}（前端不可信，且只做字符串替换不解析语义）。模板资产缺失即跳过（不阻断实例化）。
   */
  @Transactional
  public void copyFromTemplate(
      Long entityId,
      String entityCode,
      String templateCode,
      String templateVersion,
      String templateSchemaJson) {
    if (templateSchemaJson == null || templateSchemaJson.isBlank()) {
      log.info("[ui-schema] 模板无界面资产，跳过拷贝 template={}", templateCode);
      return;
    }
    JsonNode root = parseJson(templateSchemaJson);
    if (root == null || !root.isObject()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_INVALID, "模板界面资产非法 template=" + templateCode);
    }
    String rewritten = rewriteNamespace(root, entityCode);
    int minor = root.path("minor").asInt(UiSchemaDef.SCHEMA_MINOR);
    UiSchemaDef def =
        UiSchemaDef.create(tenantProvider.currentTenantId(), entityId, rewritten, minor);
    def.markFromTemplate(templateCode, templateVersion);
    try {
      uiSchemaDefRepository.insert(def);
    } catch (BadSqlGrammarException ex) {
      throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_STORE_UNAVAILABLE, "迁移 0046 未执行", ex);
    }
    log.info("[ui-schema] 实例化拷贝 entity={} base={}:{}", entityId, templateCode, templateVersion);
  }

  /** 跟随检查（digest 只含计数，不返全量）。 */
  @Transactional(readOnly = true)
  public TemplateUpdateCheck checkBaseUpdate(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    Optional<UiSchemaDef> row = findRow(entity);
    if (row.isEmpty()
        || row.get().getBaseTemplateCode() == null
        || row.get().getBaseTemplateCode().isBlank()) {
      return TemplateUpdateCheck.builder().hasUpdate(false).reason("NO_BASE_POINTER").build();
    }
    UiSchemaDef def = row.get();
    Optional<TemplateUiSnapshot> snapshot =
        templateReadPort.findUiSnapshotByCode(def.getBaseTemplateCode());
    if (snapshot.isEmpty() || !snapshot.get().hasSchema()) {
      // 前置诚实门：资产未补齐即 disabled，不静默开放（技术方案 §5.2）
      return TemplateUpdateCheck.builder()
          .hasUpdate(false)
          .reason("TEMPLATE_ASSET_INCOMPLETE")
          .build();
    }
    TemplateUiSnapshot current = snapshot.get();
    boolean hasUpdate = !current.version().equals(def.getBaseVersion());
    TemplateUpdateCheck.TemplateUpdateCheckBuilder builder =
        TemplateUpdateCheck.builder()
            .hasUpdate(hasUpdate)
            .baseVersion(def.getBaseVersion())
            .currentVersion(current.version());
    if (hasUpdate) {
      fillDigest(builder, def.getSchemaJson(), current.schemaJson());
    }
    return builder.build();
  }

  /** 三路归并预览（服务端算，前端只确认；无历史快照降级为两路）。 */
  @Transactional(readOnly = true)
  public MergePreview previewMerge(Long entityId) {
    MetaEntity entity = requireEntity(entityId);
    UiSchemaDef def =
        findRow(entity)
            .orElseThrow(
                () -> CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_INVALID, "实体无界面 schema"));
    if (def.getBaseTemplateCode() == null || def.getBaseTemplateCode().isBlank()) {
      throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_INVALID, "非模板实例，无需归并");
    }
    TemplateUiSnapshot current =
        templateReadPort
            .findUiSnapshotByCode(def.getBaseTemplateCode())
            .filter(TemplateUiSnapshot::hasSchema)
            .orElseThrow(
                () ->
                    CatalogErrors.of(
                        CatalogErrorCodes.UI_SCHEMA_TEMPLATE_ASSET_INCOMPLETE,
                        "template=" + def.getBaseTemplateCode()));
    Optional<String> baseOld =
        templateReadPort.findUiHistorySnapshot(def.getBaseTemplateCode(), def.getBaseVersion());
    Map<String, JsonNode> baseNew = indexSegments(parseRequired(current.schemaJson()));
    Map<String, JsonNode> instance = indexSegments(parseRequired(def.getSchemaJson()));
    List<String> autoMerged = new ArrayList<>();
    List<MergePreview.MergeConflict> conflicts = new ArrayList<>();
    if (baseOld.isPresent()) {
      Map<String, JsonNode> old = indexSegments(parseRequired(baseOld.get()));
      for (String path : unionKeys(old, baseNew, instance)) {
        JsonNode o = old.get(path);
        JsonNode n = baseNew.get(path);
        JsonNode ins = instance.get(path);
        if (equalsJson(ins, o) && !equalsJson(n, o)) {
          autoMerged.add(path); // 租户未改 → 取 base.new
        } else if (!equalsJson(ins, o) && !equalsJson(n, o) && !equalsJson(ins, n)) {
          conflicts.add(
              MergePreview.MergeConflict.builder()
                  .path(path)
                  .baseNew(n == null ? null : n.toString())
                  .instance(ins == null ? null : ins.toString())
                  .build());
        }
        // 其余（base.new 未动 / 三方一致 / 实例已与新版一致）→ 无需动作
      }
      return MergePreview.builder()
          .autoMerged(autoMerged)
          .conflicts(conflicts)
          .degradedTwoWay(false)
          .build();
    }
    // 两路降级：base.new vs instance 逐段比对，差异全量标冲突
    for (String path : unionKeys(baseNew, instance)) {
      JsonNode n = baseNew.get(path);
      JsonNode ins = instance.get(path);
      if (!equalsJson(n, ins)) {
        conflicts.add(
            MergePreview.MergeConflict.builder()
                .path(path)
                .baseNew(n == null ? null : n.toString())
                .instance(ins == null ? null : ins.toString())
                .build());
      }
    }
    return MergePreview.builder()
        .autoMerged(List.of())
        .conflicts(conflicts)
        .degradedTwoWay(true)
        .build();
  }

  /**
   * 跟随合入确认（租户确认后写回草稿）。
   *
   * @param templateVersion 预览时的模板版本（合入期间模板又发版 → 409，旧 preview 作废重跑）
   * @param ifMatch 草稿锁版本（@Version 再断言，防合入期间有人编辑）
   */
  @Transactional
  public UiSchemaResp confirmMerge(
      Long entityId, boolean acceptConflicts, String templateVersion, Long ifMatch) {
    MetaEntity entity = requireEntity(entityId);
    UiSchemaDef def =
        findRow(entity)
            .orElseThrow(
                () -> CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_INVALID, "实体无界面 schema"));
    TemplateUiSnapshot current =
        templateReadPort
            .findUiSnapshotByCode(def.getBaseTemplateCode())
            .filter(TemplateUiSnapshot::hasSchema)
            .orElseThrow(
                () ->
                    CatalogErrors.of(
                        CatalogErrorCodes.UI_SCHEMA_TEMPLATE_ASSET_INCOMPLETE,
                        "template=" + def.getBaseTemplateCode()));
    if (templateVersion == null || !templateVersion.equals(current.version())) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_MERGE_CONFLICT,
          "模板在预览后又发版（preview=" + templateVersion + " current=" + current.version() + "），请重跑归并预览");
    }
    if (ifMatch != null && ifMatch != -1L && !ifMatch.equals(def.getLockVersion())) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT,
          "entity=" + entity.getId() + " 合入期间草稿被并发修改");
    }
    MergePreview preview = previewMerge(entityId);
    JsonNode merged =
        applyMerge(
            parseRequired(def.getSchemaJson()),
            parseRequired(current.schemaJson()),
            preview,
            acceptConflicts);
    def.replaceDraft(merged.toString(), merged.path("minor").asInt(UiSchemaDef.SCHEMA_MINOR));
    def.markFromTemplate(def.getBaseTemplateCode(), current.version());
    try {
      uiSchemaDefRepository.update(def);
    } catch (OptimisticLockingFailureException ex) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_VERSION_CONFLICT, "entity=" + entity.getId() + " 并发写冲突");
    } catch (BadSqlGrammarException ex) {
      throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_STORE_UNAVAILABLE, "迁移 0046 未执行", ex);
    }
    log.info(
        "[ui-schema] 跟随合入 entity={} base={} acceptConflicts={}",
        entity.getId(),
        current.version(),
        acceptConflicts);
    return toResp(def);
  }

  // ============================ 内部：引用上下文 ============================

  private MetaEntity requireEntity(Long entityId) {
    MetaEntity entity = metaEntityRepository.findById(entityId);
    if (entity == null) {
      throw CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, entityId);
    }
    return entity;
  }

  private Optional<UiSchemaDef> findRow(MetaEntity entity) {
    long tenantId = tenantProvider.currentTenantId();
    try {
      return uiSchemaDefRepository.findByTenantAndEntity(tenantId, entity.getId());
    } catch (BadSqlGrammarException ex) {
      // 0046 未执行：读路径一律视作"从未配置"，前端回落列投影（技术方案 §5.2 前置诚实门）
      warnTableMissing("读草稿", ex);
      return Optional.empty();
    }
  }

  private UiSchemaResp toResp(UiSchemaDef def) {
    UiSchemaResp resp = new UiSchemaResp();
    resp.setJson(def.getSchemaJson());
    resp.setMinor(def.getSchemaMinor());
    resp.setLockVersion(def.getLockVersion());
    resp.setUpdatedAt(def.getUpdatedAt() == null ? null : def.getUpdatedAt().toString());
    return resp;
  }

  /** 引用上下文：字段码、动作码、行策略码（一次查齐，校验内复用）。 */
  private RefContext loadRefs(MetaEntity entity) {
    Set<String> fieldCodes = new HashSet<>();
    for (MetaField f :
        metaFieldRepository.pageFields(entity.getId(), null, 1, MAX_FIELDS).getRecords()) {
      if (f.getCode() != null) {
        fieldCodes.add(f.getCode());
      }
    }
    Set<String> actionCodes =
        uiSchemaRefCheckPort.actionCodesOf(tenantProvider.currentTenantId(), entity.getCode());
    return new RefContext(fieldCodes, actionCodes, parsePolicyCodes(entity.getRowPolicies()));
  }

  private record RefContext(
      Set<String> fieldCodes, Set<String> actionCodes, Set<String> policyCodes) {}

  /**
   * 行策略码提取（两种声明形态：① 数组：字符串或 {code|policyCode|policy_code} 对象；② 对象： {@code {owner_field,
   * rules:[{name|code|policyCode}]}}——引擎 0038 真实形态；非法即空集）。
   */
  private Set<String> parsePolicyCodes(String rowPolicies) {
    Set<String> out = new HashSet<>();
    if (rowPolicies == null || rowPolicies.isBlank()) {
      return out;
    }
    try {
      JsonNode node = MAPPER.readTree(rowPolicies);
      if (node.isArray()) {
        collectPolicyCodes(node, out);
      } else if (node.isObject()) {
        // 引擎形态：rules 数组每条的 name 即策略码
        collectPolicyCodes(node.path("rules"), out);
      }
    } catch (Exception ex) {
      log.warn("[ui-schema] rowPolicies 解析失败，policyCode 引用检查降级为空集: {}", ex.getMessage());
    }
    return out;
  }

  private void collectPolicyCodes(JsonNode items, Set<String> out) {
    if (items == null || !items.isArray()) {
      return;
    }
    for (JsonNode item : items) {
      if (item.isTextual() && !item.asText().isBlank()) {
        out.add(item.asText());
      } else if (item.isObject()) {
        String code = firstText(item, "policyCode", "policy_code", "code", "name");
        if (code != null) {
          out.add(code);
        }
      }
    }
  }

  // ============================ 内部：校验 ============================

  private JsonNode extractSchemaNode(JsonNode body) {
    JsonNode root = body != null && body.has("json") ? body.get("json") : body;
    if (root != null && root.isTextual()) {
      JsonNode parsed = parseJson(root.asText());
      if (parsed == null) {
        throw CatalogErrors.of(CatalogErrorCodes.UI_SCHEMA_INVALID, "json 非法 JSON 字符串");
      }
      return parsed;
    }
    if (root == null || !root.isObject()) {
      throw CatalogErrors.of(
          CatalogErrorCodes.UI_SCHEMA_INVALID, "请求体须为 BoneUiSchema 对象或 {json} 包裹");
    }
    return root;
  }

  private JsonNode parseJson(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(json);
    } catch (Exception ex) {
      return null;
    }
  }

  private JsonNode parseRequired(String json) {
    JsonNode node = parseJson(json);
    if (node == null || !node.isObject()) {
      throw new DomainException("界面 schema 非法 JSON");
    }
    return node;
  }

  /**
   * 引用完整性校验（UI-REF-01~09：code 即前后端对账码）。
   *
   * <p>ERROR 阻断保存/发布；WARNING 只提示（规则缺 code：schema 允许缺省，但缺了日志对不上）。
   */
  List<EntityValidationIssue> validateNodes(JsonNode root, RefContext refs) {
    List<EntityValidationIssue> issues = new ArrayList<>();
    if (root.path("schemaVersion").asInt(-1) != UiSchemaDef.SCHEMA_VERSION) {
      issues.add(
          EntityValidationIssue.error(
              CatalogErrorCodes.UI_SCHEMA_INVALID, "schemaVersion 须为 1（未知大版本显式拒绝，不猜渲染）"));
      return issues;
    }
    if (root.path("minor").asInt(UiSchemaDef.SCHEMA_MINOR) < 1) {
      issues.add(
          EntityValidationIssue.error(CatalogErrorCodes.UI_SCHEMA_INVALID, "minor 非法（须 ≥1）"));
    }

    Set<String> controlCodes = new HashSet<>();
    Set<String> virtualCodes = new HashSet<>();
    // form + detail 分区控件
    for (String view : List.of("form", "detail")) {
      JsonNode sections = root.path("views").path(view).path("sections");
      if (sections.isArray()) {
        for (JsonNode section : sections) {
          JsonNode controls = section.path("controls");
          if (!controls.isArray()) {
            continue;
          }
          for (JsonNode control : controls) {
            String fieldCode = textOf(control, "fieldCode");
            if (fieldCode == null) {
              issues.add(EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_01, "控件缺 fieldCode"));
              continue;
            }
            controlCodes.add(fieldCode);
            if (control.path("virtual").asBoolean(false)) {
              virtualCodes.add(fieldCode);
            }
            String kind = textOf(control, "controlKind");
            if (kind != null && !UiSchemaDef.CONTROL_KINDS.contains(kind)) {
              issues.add(
                  EntityValidationIssue.error(
                      UiSchemaRefCodes.UI_REF_06,
                      "未知 controlKind=" + kind + "（控件 " + fieldCode + "）"));
            }
            if (!control.path("virtual").asBoolean(false)
                && !refs.fieldCodes().contains(fieldCode)) {
              issues.add(
                  EntityValidationIssue.error(
                      UiSchemaRefCodes.UI_REF_01, "控件引用的字段不存在：" + fieldCode));
            }
            if (control.path("virtual").asBoolean(false) && refs.fieldCodes().contains(fieldCode)) {
              issues.add(
                  EntityValidationIssue.error(
                      UiSchemaRefCodes.UI_REF_04, "virtual 控件与真实字段码碰撞：" + fieldCode));
            }
          }
        }
      }
    }

    // linkage / submit（含变体块复用同一套 walker）
    checkRules(root.path("linkageRules"), refs, controlCodes, issues, "");
    checkSubmits(root.path("submitRules"), refs, issues, "");
    checkActionBar(root.path("actionBar"), refs, issues, "");

    // tabs $ 变量（UI-REF-07）与 dataScope.policyCode（UI-REF-03）
    JsonNode list = root.path("views").path("list");
    JsonNode tabs = list.path("tabs");
    if (tabs.isArray()) {
      for (JsonNode tab : tabs) {
        String value = textOf(tab, "value");
        if (value != null && value.startsWith("$") && !DOLLAR_VARS.contains(value)) {
          issues.add(
              EntityValidationIssue.error(
                  UiSchemaRefCodes.UI_REF_07, "tabs.value 未知上下文变量：" + value));
        }
      }
    }
    String policyCode = textOf(list.path("dataScope"), "policyCode");
    if (policyCode != null && !refs.policyCodes().contains(policyCode)) {
      issues.add(
          EntityValidationIssue.error(
              UiSchemaRefCodes.UI_REF_03, "dataScope.policyCode 未在 row_policies 声明：" + policyCode));
    }

    // 变体：key 必填唯一（UI-REF-09 ERROR）；变体规则块复用 walker
    JsonNode variants = root.path("variants");
    if (variants.isArray()) {
      Set<String> keys = new HashSet<>();
      for (JsonNode variant : variants) {
        String key = textOf(variant, "key");
        if (key == null) {
          issues.add(EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_09, "变体缺 key"));
          continue;
        }
        if (!keys.add(key)) {
          issues.add(EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_09, "变体 key 重复：" + key));
        }
        JsonNode views = variant.path("views");
        for (String view : List.of("form", "detail")) {
          JsonNode sections = views.path(view).path("sections");
          if (sections.isArray()) {
            for (JsonNode section : sections) {
              JsonNode controls = section.path("controls");
              if (controls.isArray()) {
                for (JsonNode control : controls) {
                  String fieldCode = textOf(control, "fieldCode");
                  if (fieldCode != null
                      && !control.path("virtual").asBoolean(false)
                      && !refs.fieldCodes().contains(fieldCode)
                      && !controlCodes.contains(fieldCode)) {
                    issues.add(
                        EntityValidationIssue.error(
                            UiSchemaRefCodes.UI_REF_01, "变体 " + key + " 控件引用的字段不存在：" + fieldCode));
                  }
                }
              }
            }
          }
        }
        String prefix = "变体 " + key + " ";
        checkRules(variant.path("linkageRules"), refs, controlCodes, issues, prefix);
        checkSubmits(variant.path("submitRules"), refs, issues, prefix);
        checkActionBar(variant.path("actionBar"), refs, issues, prefix);
      }
    }
    return issues;
  }

  private void checkRules(
      JsonNode rules,
      RefContext refs,
      Set<String> controlCodes,
      List<EntityValidationIssue> issues,
      String prefix) {
    if (rules == null || !rules.isArray()) {
      return;
    }
    for (JsonNode rule : rules) {
      String name = firstText(rule, "code", "name");
      String label = prefix + "linkage[" + (name == null ? "?" : name) + "] ";
      if (textOf(rule, "code") == null) {
        issues.add(
            EntityValidationIssue.warning(
                UiSchemaRefCodes.UI_REF_09, label + "缺 code（前后端日志对账用，建议补 LINK-* 编码）"));
      }
      checkConditionDepth(rule.path("when"), issues, label);
      checkDollarVars(rule.path("when"), issues, label);
      JsonNode thens = rule.path("then");
      if (thens.isArray()) {
        for (JsonNode then : thens) {
          String target = textOf(then, "fieldCode");
          if (target != null
              && !controlCodes.contains(target)
              && !refs.fieldCodes().contains(target)) {
            issues.add(
                EntityValidationIssue.error(
                    UiSchemaRefCodes.UI_REF_01, label + "then 目标未知：" + target));
          }
          // seven 动作外键直接拒绝（schema additionalProperties=false 同口径，防拼写漂移）
          Iterator<String> names = then.fieldNames();
          while (names.hasNext()) {
            String key = names.next();
            if (!Set.of(
                    "fieldCode",
                    "visible",
                    "required",
                    "readonly",
                    "disabled",
                    "notice",
                    "noticeKey",
                    "tier",
                    "value")
                .contains(key)) {
              issues.add(
                  EntityValidationIssue.error(
                      UiSchemaRefCodes.UI_REF_06, label + "then 未知动作：" + key));
            }
          }
        }
      }
    }
  }

  private void checkSubmits(
      JsonNode rules, RefContext refs, List<EntityValidationIssue> issues, String prefix) {
    if (rules == null || !rules.isArray()) {
      return;
    }
    for (JsonNode rule : rules) {
      String name = firstText(rule, "code", "name");
      String label = prefix + "submit[" + (name == null ? "?" : name) + "] ";
      if (textOf(rule, "code") == null) {
        issues.add(
            EntityValidationIssue.warning(
                UiSchemaRefCodes.UI_REF_09, label + "缺 code（前后端日志对账用，建议补 SUB-* 编码）"));
      }
      String function = textOf(rule, "function");
      if (function == null || !UiSchemaDef.SUBMIT_FUNCTIONS.contains(function)) {
        issues.add(
            EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_06, label + "未知函数：" + function));
        continue;
      }
      JsonNode fields = rule.path("fields");
      if (fields.isArray()) {
        for (JsonNode f : fields) {
          if (f.isTextual()) {
            checkFieldPath(f.asText(), refs, issues, label);
          }
        }
      }
      JsonNode compare = rule.path("compare");
      if (compare.isObject()
          && "field".equals(textOf(compare, "valueType"))
          && textOf(compare, "fieldCode") != null) {
        checkFieldPath(compare.path("fieldCode").asText(), refs, issues, label);
      }
      checkConditionDepth(rule.path("when"), issues, label);
      checkDollarVars(rule.path("when"), issues, label);
    }
  }

  private void checkActionBar(
      JsonNode actions, RefContext refs, List<EntityValidationIssue> issues, String prefix) {
    if (actions == null || !actions.isArray()) {
      return;
    }
    for (JsonNode action : actions) {
      String code = textOf(action, "actionCode");
      if (code == null) {
        issues.add(
            EntityValidationIssue.error(
                UiSchemaRefCodes.UI_REF_02, prefix + "actionBar 缺 actionCode"));
        continue;
      }
      if (!refs.actionCodes().contains(code)) {
        issues.add(
            EntityValidationIssue.error(
                UiSchemaRefCodes.UI_REF_02, prefix + "actionCode 未在动作定义中：" + code));
      }
      String label = prefix + "action[" + code + "] ";
      checkConditionDepth(action.path("visibleWhen"), issues, label);
      checkDollarVars(action.path("visibleWhen"), issues, label);
      checkConditionDepth(action.path("requireDualApproveWhen"), issues, label);
      checkDollarVars(action.path("requireDualApproveWhen"), issues, label);
    }
  }

  /** 字段路径根段检查（items.amount→items；纯标量直接比对；virtual 控件声明亦可）。 */
  private void checkFieldPath(
      String path, RefContext refs, List<EntityValidationIssue> issues, String label) {
    if (path == null || path.isBlank()) {
      return;
    }
    String rootSegment = path.split("\\.", 2)[0];
    // 控件声明的 virtual 码不进 meta_field，此处放行（UI-REF-04 已在控件侧断言碰撞）
    if (refs.fieldCodes().contains(rootSegment)) {
      return;
    }
    issues.add(EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_01, label + "引用未知字段：" + path));
  }

  /** 条件深度检查（all/any/not 嵌套 ≤3，UI-REF-05；超限整条 fail-closed 不命中，前端引擎同值）。 */
  private void checkConditionDepth(
      JsonNode cond, List<EntityValidationIssue> issues, String label) {
    int depth = condDepth(cond, 0);
    if (depth > UiSchemaDef.MAX_COND_DEPTH) {
      issues.add(
          EntityValidationIssue.error(
              UiSchemaRefCodes.UI_REF_05, label + "条件嵌套超 3 层（当前 " + depth + "）"));
    }
    if (depth > MAX_JSON_DEPTH) {
      issues.add(
          EntityValidationIssue.error(
              UiSchemaRefCodes.UI_REF_05, label + "条件嵌套异常（>" + MAX_JSON_DEPTH + "），疑似恶意 JSON"));
    }
  }

  private int condDepth(JsonNode cond, int depth) {
    if (cond == null || !cond.isObject() || depth > MAX_JSON_DEPTH) {
      return depth;
    }
    int max = depth;
    for (String key : List.of("all", "any")) {
      JsonNode group = cond.path(key);
      if (group.isArray()) {
        for (JsonNode child : group) {
          max = Math.max(max, condDepth(child, depth + 1));
        }
      }
    }
    if (cond.has("not")) {
      max = Math.max(max, condDepth(cond.get("not"), depth + 1));
    }
    return max;
  }

  /** 条件 $ 变量命名空间检查（UI-REF-08：$me/$myteam/$all 或 $ns:... 且 ns ∈ 白名单）。 */
  private void checkDollarVars(JsonNode cond, List<EntityValidationIssue> issues, String label) {
    if (cond == null || !cond.isObject()) {
      return;
    }
    for (String key : List.of("all", "any")) {
      JsonNode group = cond.path(key);
      if (group.isArray()) {
        for (JsonNode child : group) {
          checkDollarVars(child, issues, label);
        }
      }
    }
    if (cond.has("not")) {
      checkDollarVars(cond.get("not"), issues, label);
    }
    JsonNode value = cond.get("value");
    if (value != null && value.isTextual() && value.asText().startsWith("$")) {
      String var = value.asText();
      if (DOLLAR_VARS.contains(var)) {
        return;
      }
      if (var.startsWith("$") && var.contains(":")) {
        String ns = var.substring(1, var.indexOf(':'));
        if (UiSchemaDef.TIER_NAMESPACES.contains(ns)) {
          return;
        }
      }
      issues.add(EntityValidationIssue.error(UiSchemaRefCodes.UI_REF_08, label + "未知上下文变量：" + var));
    }
  }

  // ============================ 内部：归并 ============================

  private void fillDigest(
      TemplateUpdateCheck.TemplateUpdateCheckBuilder builder,
      String instanceJson,
      String baseNewJson) {
    Map<String, JsonNode> instance = indexSegments(parseRequired(instanceJson));
    Map<String, JsonNode> baseNew = indexSegments(parseRequired(baseNewJson));
    int sectionAdded = 0;
    int sectionRemoved = 0;
    int ruleAdded = 0;
    int ruleRemoved = 0;
    int actionAdded = 0;
    int actionRemoved = 0;
    for (String key : unionKeys(instance, baseNew)) {
      boolean inInstance = instance.containsKey(key);
      boolean inNew = baseNew.containsKey(key);
      if (inNew && !inInstance) {
        if (key.startsWith("section:")) {
          sectionAdded++;
        } else if (key.startsWith("rule:")) {
          ruleAdded++;
        } else if (key.startsWith("action:")) {
          actionAdded++;
        }
      } else if (inInstance && !inNew) {
        if (key.startsWith("section:")) {
          sectionRemoved++;
        } else if (key.startsWith("rule:")) {
          ruleRemoved++;
        } else if (key.startsWith("action:")) {
          actionRemoved++;
        }
      }
    }
    builder
        .sectionAdded(sectionAdded)
        .sectionRemoved(sectionRemoved)
        .ruleAdded(ruleAdded)
        .ruleRemoved(ruleRemoved)
        .actionAdded(actionAdded)
        .actionRemoved(actionRemoved);
  }

  /**
   * schema 分段索引（归并粒度：分区按 id、规则按 code??name、动作按 actionCode、list/i18n 整块）。
   *
   * <p>code 缺失的规则按 name 索引（name 亦缺失按下标，实例与模板下标错位即判冲突——保守但诚实）。
   */
  Map<String, JsonNode> indexSegments(JsonNode root) {
    Map<String, JsonNode> out = new HashMap<>();
    for (String view : List.of("form", "detail")) {
      JsonNode sections = root.path("views").path(view).path("sections");
      if (sections.isArray()) {
        int index = 0;
        for (JsonNode section : sections) {
          String id = textOf(section, "id");
          out.put("section:" + view + ":" + (id == null ? "#" + index : id), section);
          index++;
        }
      }
    }
    indexRules(out, "linkage", root.path("linkageRules"));
    indexRules(out, "submit", root.path("submitRules"));
    JsonNode actions = root.path("actionBar");
    if (actions.isArray()) {
      int index = 0;
      for (JsonNode action : actions) {
        String code = textOf(action, "actionCode");
        out.put("action:" + (code == null ? "#" + index : code), action);
        index++;
      }
    }
    if (root.has("views") && root.get("views").has("list")) {
      out.put("block:views.list", root.get("views").get("list"));
    }
    if (root.has("i18n")) {
      out.put("block:i18n", root.get("i18n"));
    }
    return out;
  }

  private void indexRules(Map<String, JsonNode> out, String kind, JsonNode rules) {
    if (rules == null || !rules.isArray()) {
      return;
    }
    int index = 0;
    for (JsonNode rule : rules) {
      String key = firstText(rule, "code", "name");
      out.put("rule:" + kind + ":" + (key == null ? "#" + index : key), rule);
      index++;
    }
  }

  private Set<String> unionKeys(
      Map<String, JsonNode> first, Map<String, JsonNode> second, Map<String, JsonNode> third) {
    Set<String> out = new HashSet<>();
    if (first != null) {
      out.addAll(first.keySet());
    }
    if (second != null) {
      out.addAll(second.keySet());
    }
    if (third != null) {
      out.addAll(third.keySet());
    }
    return out;
  }

  private Set<String> unionKeys(Map<String, JsonNode> first, Map<String, JsonNode> second) {
    return unionKeys(first, second, null);
  }

  private boolean equalsJson(JsonNode a, JsonNode b) {
    if (a == null || a.isNull()) {
      return b == null || b.isNull();
    }
    return a.equals(b);
  }

  /**
   * 合入写回：autoMerged 取 base.new；冲突按 acceptConflicts 取 base.new（true）或保留实例（false）。
   *
   * <p>实现按段替换：先深拷贝 instance，再把选定的 base.new 段按"同 kind+同 key"写回对应数组（缺数组即建）。
   */
  JsonNode applyMerge(
      JsonNode instance, JsonNode baseNew, MergePreview preview, boolean acceptConflicts) {
    Map<String, JsonNode> baseIndex = indexSegments(baseNew);
    ObjectNode merged = instance.deepCopy();
    List<String> applyPaths = new ArrayList<>(preview.getAutoMerged());
    if (acceptConflicts && preview.getConflicts() != null) {
      for (MergePreview.MergeConflict conflict : preview.getConflicts()) {
        applyPaths.add(conflict.getPath());
      }
    }
    for (String path : applyPaths) {
      JsonNode replacement = baseIndex.get(path);
      if (replacement != null) {
        applySegment(merged, path, replacement);
      }
    }
    return merged;
  }

  private void applySegment(ObjectNode merged, String path, JsonNode replacement) {
    String[] parts = path.split(":", 3);
    if (parts.length < 2) {
      return;
    }
    String kind = parts[0];
    if ("block".equals(kind) && parts.length == 3) {
      // block:views.list / block:i18n
      String[] sub = parts[2].split("\\.", 2);
      if (sub.length == 2 && "views".equals(parts[1])) {
        ObjectNode views =
            merged.has("views") ? (ObjectNode) merged.get("views") : merged.putObject("views");
        views.set(sub[1], replacement);
      } else if ("i18n".equals(parts[1])) {
        merged.set("i18n", replacement);
      }
      return;
    }
    if (parts.length < 3) {
      return;
    }
    String collection;
    String matchField;
    String matchValue = parts[2];
    switch (kind) {
      case "section" -> {
        // section:<view>:<id>：定位 views.<view>.sections[] 内 id 匹配项
        String view = parts[1];
        JsonNode views = merged.path("views").path(view).path("sections");
        if (views.isArray()) {
          for (int i = 0; i < views.size(); i++) {
            JsonNode section = views.get(i);
            String id = textOf(section, "id");
            if ((id == null && matchValue.startsWith("#"))
                || (id != null && id.equals(matchValue))) {
              ((ArrayNode) views).set(i, replacement);
              return;
            }
          }
          ((ArrayNode) views).add(replacement);
        }
        return;
      }
      case "rule" -> {
        // rule:<linkage|submit>:<key>
        String ruleKind = parts[1];
        collection = "linkage".equals(ruleKind) ? "linkageRules" : "submitRules";
        matchField = null; // code 或 name 任一命中
        replaceInArray(merged, collection, matchField, matchValue, replacement);
        return;
      }
      case "action" -> {
        collection = "actionBar";
        matchField = "actionCode";
        replaceInArray(merged, collection, matchField, matchValue, replacement);
        return;
      }
      default -> {
        return;
      }
    }
  }

  private void replaceInArray(
      ObjectNode merged,
      String collection,
      String matchField,
      String matchValue,
      JsonNode replacement) {
    JsonNode array = merged.path(collection);
    if (!array.isArray()) {
      ArrayNode created = merged.putArray(collection);
      created.add(replacement);
      return;
    }
    ArrayNode arr = (ArrayNode) array;
    for (int i = 0; i < arr.size(); i++) {
      JsonNode item = arr.get(i);
      boolean hit;
      if (matchField == null) {
        String code = textOf(item, "code");
        String name = textOf(item, "name");
        hit = matchValue.equals(code) || matchValue.equals(name);
      } else {
        hit = matchValue.equals(textOf(item, matchField));
      }
      if (hit) {
        arr.set(i, replacement);
        return;
      }
    }
    arr.add(replacement);
  }

  private int countSections(JsonNode root) {
    int count = 0;
    for (String view : List.of("form", "detail")) {
      JsonNode sections = root.path("views").path(view).path("sections");
      if (sections.isArray()) {
        count += sections.size();
      }
    }
    return count;
  }

  private String rewriteNamespace(JsonNode root, String entityCode) {
    if (!(root instanceof ObjectNode objectRoot)) {
      return root.toString();
    }
    JsonNode i18n = objectRoot.get("i18n");
    if (i18n instanceof ObjectNode objectI18n) {
      JsonNode namespace = objectI18n.get("namespace");
      if (namespace != null && namespace.isTextual() && namespace.asText().startsWith("bone.ui.")) {
        // 只做 i18n.namespace 字符串前缀替换，不解析语义（技术方案 §5.1）
        objectI18n.put("namespace", "bone.ui." + entityCode);
      }
    }
    return objectRoot.toString();
  }

  private static String textOf(JsonNode node, String field) {
    if (node == null || !node.isObject()) {
      return null;
    }
    JsonNode value = node.get(field);
    if (value == null || !value.isTextual() || value.asText().isBlank()) {
      return null;
    }
    return value.asText();
  }

  private static String firstText(JsonNode node, String... fields) {
    for (String field : fields) {
      String value = textOf(node, field);
      if (value != null) {
        return value;
      }
    }
    return null;
  }
}
