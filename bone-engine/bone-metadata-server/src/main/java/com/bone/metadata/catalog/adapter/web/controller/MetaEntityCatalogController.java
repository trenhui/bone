package com.bone.metadata.catalog.adapter.web.controller;

import com.bone.core.model.ApiResponse;
import com.bone.core.model.PageResult;
import com.bone.metadata.catalog.application.MetaEntityApplicationService;
import com.bone.metadata.catalog.application.UiOverlayApplicationService;
import com.bone.metadata.catalog.application.UiSchemaApplicationService;
import com.bone.metadata.catalog.application.command.cmd.BatchDeleteMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.ConfirmUiSchemaMergeCommand;
import com.bone.metadata.catalog.application.command.cmd.CopyMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.CreateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.ImportMetaEntityFromTableCommand;
import com.bone.metadata.catalog.application.command.cmd.UpdateMetaEntityCommand;
import com.bone.metadata.catalog.application.command.cmd.UpsertActionDefCommand;
import com.bone.metadata.catalog.application.query.dto.EntityValidationIssue;
import com.bone.metadata.catalog.application.query.dto.MergePreview;
import com.bone.metadata.catalog.application.query.dto.MetaEntityDTO;
import com.bone.metadata.catalog.application.query.dto.PublishPreviewDTO;
import com.bone.metadata.catalog.application.query.dto.TemplateUpdateCheck;
import com.bone.metadata.catalog.application.query.dto.UiSchemaResp;
import com.bone.metadata.catalog.application.query.qry.MetaEntityPageQuery;
import com.bone.metadata.catalog.application.support.CatalogIdempotencySupport;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.common.CatalogHttpSupport;
import com.bone.metadata.catalog.common.ImportMetaEntityResult;
import com.bone.metadata.catalog.domain.gateway.ActionDefSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 元数据目录：实体建模 API（与扩展字段 /api/v1/metadata/fields:* 分离）。
 *
 * <p>入站边界仅依赖 {@link MetaEntityApplicationService}（ADR-0028）。横切关注点（创建 201+Location、更新 If-Match→412、
 * 发布 Idempotency-Key）保留在控制器层。
 */
@RestController
@RequestMapping("/api/v1/metadata/entities")
@RequiredArgsConstructor
public class MetaEntityCatalogController {

  private final MetaEntityApplicationService metaEntityApplicationService;
  private final UiOverlayApplicationService uiOverlayApplicationService;
  private final UiSchemaApplicationService uiSchemaApplicationService;
  private final CatalogIdempotencySupport catalogIdempotencySupport;
  private final ObjectMapper objectMapper;

  @PostMapping
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Long>> create(@Valid @RequestBody CreateMetaEntityCommand cmd) {
    Long id = metaEntityApplicationService.createEntity(cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + id))
        .body(ApiResponse.success(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> update(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody UpdateMetaEntityCommand cmd) {
    Integer version =
        metaEntityApplicationService.updateEntity(
            id, cmd, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(version))
        .body(ApiResponse.success());
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<PageResult<MetaEntityDTO>> page(MetaEntityPageQuery qry) {
    return ApiResponse.success(
        metaEntityApplicationService.pageEntities(
            qry.getKeyword(),
            qry.getStatus(),
            qry.getModuleId(),
            qry.getType(),
            qry.getDeliveryMode(),
            qry.getPage(),
            qry.getSize()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ResponseEntity<ApiResponse<MetaEntityDTO>> detail(@PathVariable Long id) {
    MetaEntityDTO dto = metaEntityApplicationService.getEntity(id);
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(dto.getVersion()))
        .body(ApiResponse.success(dto));
  }

  @PostMapping("/{id}/publish")
  @PreAuthorize("hasAnyAuthority('metadata:publish', 'metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> publish(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    String path = "/api/v1/metadata/entities/" + id + "/publish";
    String fingerprint = CatalogIdempotencySupport.fingerprint("");
    var apiVoidType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Void.class);
    Optional<ResponseEntity<ApiResponse<Void>>> replay =
        catalogIdempotencySupport.replay(idempotencyKey, "POST", path, fingerprint, apiVoidType);
    if (replay.isPresent()) {
      return replay.get();
    }
    Integer version =
        metaEntityApplicationService.publishEntity(
            id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    ResponseEntity<ApiResponse<Void>> response =
        ResponseEntity.ok()
            .eTag(CatalogHttpSupport.formatEtag(version))
            .body(ApiResponse.success());
    catalogIdempotencySupport.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  /**
   * 归档实体（P0-2）：已发布实体软下线（status→ARCHIVED）。
   *
   * <p>保留物理表与已生成产物，运行时按 status 拒绝该实体的新写入；落库后失效 runtime 缓存并发布归档事件。 反向操作见 {@code
   * /{id}/revert-draft}（回到草稿态重新编辑）。
   */
  @PostMapping("/{id}/archive")
  @PreAuthorize("hasAnyAuthority('metadata:publish', 'metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> archive(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
    String path = "/api/v1/metadata/entities/" + id + "/archive";
    String fingerprint = CatalogIdempotencySupport.fingerprint("");
    var apiVoidType =
        objectMapper.getTypeFactory().constructParametricType(ApiResponse.class, Void.class);
    Optional<ResponseEntity<ApiResponse<Void>>> replay =
        catalogIdempotencySupport.replay(idempotencyKey, "POST", path, fingerprint, apiVoidType);
    if (replay.isPresent()) {
      return replay.get();
    }
    Integer version =
        metaEntityApplicationService.archiveEntity(
            id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    ResponseEntity<ApiResponse<Void>> response =
        ResponseEntity.ok()
            .eTag(CatalogHttpSupport.formatEtag(version))
            .body(ApiResponse.success());
    catalogIdempotencySupport.rememberApiResponse(
        idempotencyKey, "POST", path, fingerprint, response);
    return response;
  }

  /**
   * 回退草稿（P1.5）：已发布 / 归档实体回到 DRAFT，解锁结构性编辑。
   *
   * <p>已发布实体的 {@code update()} 会拒绝（「请先归档或回退草稿」），故必须先经本端点回到草稿态。 回退不删物理表，下次发布会重新对齐。
   */
  @PostMapping("/{id}/revert-draft")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Void>> revertDraft(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
    Integer version =
        metaEntityApplicationService.revertEntityToDraft(
            id, CatalogHttpSupport.parseIfMatchVersion(ifMatch).orElse(null));
    return ResponseEntity.ok()
        .eTag(CatalogHttpSupport.formatEtag(version))
        .body(ApiResponse.success());
  }

  // ===================== 状态机动作定义（meta_entity_action_def）=====================

  /**
   * 列出实体动作定义（状态机建模面板数据源）。
   *
   * <p>与 {@code GET /runtime/entities/{code}/actions} 的分工：那是<b>运行时</b>视图（经 {@code canInvoke}
   * 裁剪出当前用户可触发的动作， 供前端画动作条）；本端点是<b>建模</b>视图，返回全部定义含权限码命名空间，供建模者核对状态机。
   */
  @GetMapping("/{id}/action-defs")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<List<ActionDefSpec>> listActionDefs(@PathVariable Long id) {
    return ApiResponse.success(metaEntityApplicationService.listActionDefs(id));
  }

  /**
   * 登记/更新单条动作定义（P0-3）。
   *
   * <p>无 {@code If-Match}：动作定义行不参与实体版本号（{@code meta_entity.version} 只覆盖实体自身结构），逐条以 {@code (tenant,
   * entityCode, actionCode)} 为并发粒度，UPSERT 幂等。
   */
  @PostMapping("/{id}/action-defs")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<ActionDefSpec>> upsertActionDef(
      @PathVariable Long id, @Valid @RequestBody UpsertActionDefCommand cmd) {
    metaEntityApplicationService.upsertActionDef(id, cmd);
    return ResponseEntity.ok(ApiResponse.success(toSpec(cmd)));
  }

  /** 软删单条动作定义；不存在（含已软删）返回 404 {@code META_ACTION_DEF_NOT_FOUND}。 */
  @DeleteMapping("/{id}/action-defs/{actionCode}")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> deleteActionDef(@PathVariable Long id, @PathVariable String actionCode) {
    metaEntityApplicationService.deleteActionDef(id, actionCode);
    return ApiResponse.success();
  }

  /** 回显写入结果（权限码由服务端推导，见 {@code ActionDefSpec#permissionCodeOf}）。 */
  private static ActionDefSpec toSpec(UpsertActionDefCommand cmd) {
    return new ActionDefSpec(
        cmd.getActionCode(),
        cmd.getLabel(),
        cmd.getFromStatus(),
        cmd.getToStatus(),
        cmd.getStatusField(),
        Boolean.TRUE.equals(cmd.getEscape()),
        cmd.getHandlerBean(),
        cmd.getParamsSchema(),
        Boolean.TRUE.equals(cmd.getRequiresIdempotency()),
        cmd.getSortOrder());
  }

  /** 建模期静态校验（UC-W5）：问题分级列表，ERROR 阻断发布。 */
  @GetMapping("/{id}/validate")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<List<EntityValidationIssue>> validate(@PathVariable Long id) {
    return ApiResponse.success(metaEntityApplicationService.validateEntity(id));
  }

  /** 发布摘要预览（UC-W7 摘要级）：变更清单 + 校验问题 + RUNTIME 物理 inspect 计划（只读不执行）。 */
  @GetMapping("/{id}/publish-preview")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<PublishPreviewDTO> publishPreview(@PathVariable Long id) {
    return ApiResponse.success(metaEntityApplicationService.getPublishPreview(id));
  }

  /**
   * 读当前用户的实体列投影覆盖（0042 · P2-④ 服务端化）。
   *
   * <p>读权限含 {@code metadata:runtime:read}：数据 Tab 的列 hydrate 走此端点，
   * 只持有运行时可读权限的数据录入用户也必须能取回自己的偏好（否则每次打开都退回默认列）。 返回体为 UiOverlay 的 JSON 字符串（version 校验在前端）；从未配置返回
   * {@code data=null}。
   */
  @GetMapping("/{entityCode}/ui-overlay")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read', 'metadata:runtime:read')")
  public ApiResponse<Object> getUiOverlay(@PathVariable String entityCode) {
    // 强转 Object：避免 String 被解析为 success(String message) 重载导致 data 为空
    // （前端 fetchOverlay 读 res.data，见 overlaySync.ts:41）
    return ApiResponse.success((Object) uiOverlayApplicationService.getOverlayJson(entityCode));
  }

  /**
   * 保存当前用户的实体列投影覆盖（UPSERT 幂等）。
   *
   * <p>写权限限建模角色（界面 Tab 的受众）：列结构是建模决策而非数据录入决策。 后续若数据 Tab 开放列设置，再把 {@code metadata:runtime:write}
   * 追加进白名单（加法变更）。
   */
  @PutMapping("/{entityCode}/ui-overlay")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> saveUiOverlay(
      @PathVariable String entityCode,
      @RequestBody com.fasterxml.jackson.databind.JsonNode overlay) {
    uiOverlayApplicationService.saveOverlayJson(
        entityCode, overlay == null ? null : overlay.toString());
    return ApiResponse.success();
  }

  /** 重置为默认投影（软删；localStorage 缓存由前端同步清除）。 */
  @DeleteMapping("/{entityCode}/ui-overlay")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> clearUiOverlay(@PathVariable String entityCode) {
    uiOverlayApplicationService.clearOverlay(entityCode);
    return ApiResponse.success();
  }

  /**
   * 读团队级界面 schema 草稿（迁移 0046）。
   *
   * <p>键走实体主键 {@code {id}}（与 {@code /{id}/validate}、{@code /{id}/publish-preview} 一致； overlay 用
   * {@code {entityCode}} 是个人偏好键历史）。无行 → {@code data=null}，前端回落列投影。
   */
  @GetMapping("/{id}/ui-schema")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<UiSchemaResp> getUiSchema(@PathVariable Long id) {
    return ApiResponse.success(uiSchemaApplicationService.getDraftSchema(id).orElse(null));
  }

  /**
   * 保存团队级界面 schema 草稿（PUT 全量替换幂等）。
   *
   * <p>锁版本走标准头 {@code If-Match: <lockVersion>}（与 runtime {@code invokeAction} 的 If-Match/ETag/412
   * 同语义，技术方案 §3.2），{@code If-Match: *} 表"我不管并发"。 缺头且行已存在 → 412 防误覆盖；锁冲突 → 409；引用校验失败 →
   * 400（UI-REF-xx）。
   */
  @PutMapping("/{id}/ui-schema")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<UiSchemaResp>> saveUiSchema(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody com.fasterxml.jackson.databind.JsonNode body) {
    Long lock = CatalogHttpSupport.parseIfMatchLong(ifMatch).orElse(null);
    UiSchemaResp resp = uiSchemaApplicationService.saveDraftSchema(id, body, lock);
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    String etag = CatalogHttpSupport.formatEtagLong(resp.getLockVersion());
    if (etag != null) {
      builder.eTag(etag);
    }
    return builder.body(ApiResponse.success(resp));
  }

  /** 删草稿行（已发布快照随行走；运行时读以结构为准，无 schema 即回落渲染）。 */
  @DeleteMapping("/{id}/ui-schema")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> deleteUiSchema(@PathVariable Long id) {
    uiSchemaApplicationService.deleteDraftSchema(id);
    return ApiResponse.success();
  }

  /** 界面 schema 引用完整性校验（UI-REF-xx，与实体校验码正交）。 */
  @PostMapping("/{id}/ui-schema/validate")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<List<EntityValidationIssue>> validateUiSchema(@PathVariable Long id) {
    return ApiResponse.success(uiSchemaApplicationService.validateSchema(id));
  }

  /** 模板跟随更新检查（digest 只含计数，不返全量；无指针/资产缺失一律 hasUpdate=false）。 */
  @GetMapping("/{id}/ui-schema/base-update")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<TemplateUpdateCheck> checkUiSchemaBaseUpdate(@PathVariable Long id) {
    return ApiResponse.success(uiSchemaApplicationService.checkBaseUpdate(id));
  }

  /** 模板跟随三路归并预览（服务端算，前端只确认）。 */
  @PostMapping("/{id}/ui-schema/merge-preview")
  @PreAuthorize("hasAnyAuthority('metadata:model:read', 'metadata:read')")
  public ApiResponse<MergePreview> previewUiSchemaMerge(@PathVariable Long id) {
    return ApiResponse.success(uiSchemaApplicationService.previewMerge(id));
  }

  /** 模板跟随合入确认（租户确认后写回草稿；409 = 模板又发版或草稿被并发修改）。 */
  @PostMapping("/{id}/ui-schema/merge-confirm")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<UiSchemaResp>> confirmUiSchemaMerge(
      @PathVariable Long id,
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @RequestBody ConfirmUiSchemaMergeCommand cmd) {
    Long lock = CatalogHttpSupport.parseIfMatchLong(ifMatch).orElse(null);
    UiSchemaResp resp =
        uiSchemaApplicationService.confirmMerge(
            id, cmd.isAcceptConflicts(), cmd.getTemplateVersion(), lock);
    ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
    String etag = CatalogHttpSupport.formatEtagLong(resp.getLockVersion());
    if (etag != null) {
      builder.eTag(etag);
    }
    return builder.body(ApiResponse.success(resp));
  }

  /** 实体复制（UC-W2 流程 B）：复制定义与字段为新草稿实体；目标模块建模角色（G1②）统一校验。 */
  @PostMapping("/{id}/copy")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<Long>> copy(
      @PathVariable Long id, @Valid @RequestBody CopyMetaEntityCommand cmd) {
    Long newId = metaEntityApplicationService.copyEntity(id, cmd);
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + newId))
        .body(ApiResponse.success(newId));
  }

  /**
   * 删除实体（软删定义 + 级联动作定义）。
   *
   * @param dropPhysicalTable true=同时 DROP RUNTIME 物理表（破坏性，须前端二次确认勾选后传 true）； 缺省
   *     false=仅软删，物理表保留（历史数据不丢，可经孤儿表门禁事后治理）。
   */
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<Void> delete(
      @PathVariable Long id, @RequestParam(defaultValue = "false") boolean dropPhysicalTable) {
    metaEntityApplicationService.deleteEntity(id, dropPhysicalTable);
    return ApiResponse.success();
  }

  /**
   * 逆向建模（UC-IMP）：从存量物理表导入为 DRAFT 实体与字段。
   *
   * <p>业界真实场景：存量业务库先纳管、后扩展——没有这一步，「元数据驱动扩展字段」只能作用于平台自建的新表， 存量表仍要人工重录。
   *
   * <p>201 + Location 与 {@code create} 保持一致；{@code dryRun=true} 时 200 且 entityId 为 null（供导入向导预览）。
   */
  @PostMapping("/import-from-table")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ResponseEntity<ApiResponse<ImportMetaEntityResult>> importFromTable(
      @Valid @RequestBody ImportMetaEntityFromTableCommand cmd) {
    ImportMetaEntityResult result = metaEntityApplicationService.importEntityFromTable(cmd);
    if (result.dryRun()) {
      return ResponseEntity.ok(ApiResponse.success(result));
    }
    return ResponseEntity.created(URI.create("/api/v1/metadata/entities/" + result.entityId()))
        .body(ApiResponse.success(result));
  }

  @PostMapping("/batch-publish")
  @PreAuthorize("hasAnyAuthority('metadata:publish', 'metadata:model:write', 'metadata:write')")
  public ApiResponse<BatchOperateResult> batchPublish(
      @Valid @RequestBody BatchPublishMetaEntityCommand cmd) {
    return ApiResponse.success(metaEntityApplicationService.batchPublishEntities(cmd));
  }

  @PostMapping("/batch-delete")
  @PreAuthorize("hasAnyAuthority('metadata:model:write', 'metadata:write')")
  public ApiResponse<BatchOperateResult> batchDelete(
      @Valid @RequestBody BatchDeleteMetaEntityCommand cmd) {
    return ApiResponse.success(metaEntityApplicationService.batchDeleteEntities(cmd));
  }
}
