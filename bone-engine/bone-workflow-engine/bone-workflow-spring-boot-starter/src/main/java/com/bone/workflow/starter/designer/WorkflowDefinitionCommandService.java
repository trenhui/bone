package com.bone.workflow.starter.designer;

import com.bone.core.tenant.context.TenantContext;
import com.bone.workflow.domain.WorkflowDefinition;
import com.bone.workflow.domain.WorkflowDefinitionValidator;
import com.bone.workflow.domain.WorkflowDefinitionValidator.Violation;
import com.bone.workflow.ports.spi.WorkflowIdGenerator;
import com.bone.workflow.starter.sdk.WfProcessDefRow;
import com.bone.workflow.starter.sdk.WfProcessDefSdkRepository;
import com.bone.workflow.starter.sdk.WfProcessDraftRow;
import com.bone.workflow.starter.sdk.WfProcessDraftSdkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

/**
 * 流程定义的设计态写路径：草稿保存 / 校验 / 发布 / 上下线 / 版本查询。
 *
 * <p><b>为什么需要它</b>（设计文档 §5）：此前 {@code wf_process_def} 只有读路径，流程定义靠迁移脚本落库——业务方改一个审批人就要
 * 发一次版。设计器的前置不是画布，而是这条写路径。
 *
 * <p><b>三条不可让步的纪律</b>：
 *
 * <ol>
 *   <li><b>草稿可脏、发布必净</b>：草稿允许存在违规（业务人员总有中间态），保存时把违规<b>一并返回</b>作为提醒；发布则 fail-closed，
 *       有违规直接拒绝，绝不「先发上去再改」。
 *   <li><b>JSON 原样存储</b>：{@code definition_json} 一律存调用方提交的原文，<b>不</b>反序列化后重新序列化。否则引擎不读的 {@code ui}
 *       段（画布坐标）会被静默丢弃，表现为每次发布后流程图版式归零。
 *   <li><b>版本号单调占位</b>：新版本号 = 该 key 现有最大版本 + 1，与上下线状态无关（见 {@code
 *       WfProcessDefSdkRepository#findMaxVersionRow} 的注释）。并发发布靠唯一键 {@code uk_wf_def_ver} 兜底而不是靠
 *       {@code SELECT MAX}——两个运营同时点发布会撞号，唯一键把脏数据变成一次明确的失败。
 * </ol>
 *
 * <p><b>版本是否可变</b>：不可变。{@code wf_process_def} 的每一行都是在途实例的审批依据（实例钉启动时的 {@code
 * def_version}），所以「回滚」的正确做法是拿旧版内容<b>发布成新版本</b>，而不是把旧行改回启用——后者会让已发布的 v3 突然又变成 v2 的内容。
 */
public class WorkflowDefinitionCommandService {

  /** 设计态草稿。 */
  public record Draft(
      String defKey, String defName, String definitionJson, int revision, String updatedBy) {}

  /** 已发布版本（不可变快照）。 */
  public record Published(
      String defKey, String defName, int version, String definitionJson, boolean enabled) {}

  /**
   * 保存结果。
   *
   * @param draft 落库后的草稿（含新 revision，前端下次提交必须带上）
   * @param violations 当前草稿的违规清单（<b>提醒性质</b>：草稿允许不合规，但让业务方边改边看到问题）
   */
  public record SaveDraftResult(Draft draft, List<Violation> violations) {}

  /**
   * 校验结果。
   *
   * @param valid 是否可发布
   * @param violations 违规清单（含节点定位，前端据此高亮）
   */
  public record ValidationReport(boolean valid, List<Violation> violations) {
    public ValidationReport {
      violations = violations == null ? List.of() : List.copyOf(violations);
    }
  }

  private final WfProcessDraftSdkRepository drafts;
  private final WfProcessDefSdkRepository definitions;
  private final WorkflowDefinitionValidator validator;
  private final WorkflowIdGenerator idGenerator;
  private final ObjectMapper objectMapper;

  public WorkflowDefinitionCommandService(
      WfProcessDraftSdkRepository drafts,
      WfProcessDefSdkRepository definitions,
      WorkflowDefinitionValidator validator,
      WorkflowIdGenerator idGenerator,
      ObjectMapper objectMapper) {
    this.drafts = Objects.requireNonNull(drafts, "drafts");
    this.definitions = Objects.requireNonNull(definitions, "definitions");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
  }

  // ====================== 校验 ======================

  /** 只校验不落库：给设计器「边改边看」。JSON 本身不可解析时返回单条 {@code WF-021}。 */
  public ValidationReport validate(String definitionJson) {
    try {
      WorkflowDefinition definition = readDefinition(definitionJson);
      List<Violation> violations = new ArrayList<>(validator.validate(definition));
      return new ValidationReport(violations.isEmpty(), violations);
    } catch (WorkflowDefinitionRequestException exception) {
      return new ValidationReport(false, List.of(new Violation("WF-021", exception.getMessage())));
    }
  }

  // ====================== 草稿 ======================

  public Optional<Draft> findDraft(String defKey) {
    requireKey(defKey);
    return drafts
        .findDraftRow(requireTenant(), defKey)
        .map(WorkflowDefinitionCommandService::toDraft);
  }

  /**
   * 保存草稿（新建或覆盖）。
   *
   * @param expectedRevision 前端读到的 revision；<b>覆盖已有草稿时必填</b>（缺失即拒绝，避免「以为在改、其实覆盖了别人」）
   */
  public SaveDraftResult saveDraft(
      String defKey, String definitionJson, Integer expectedRevision, String operatorId) {
    requireKey(defKey);
    requireOperator(operatorId);
    WorkflowDefinition definition = readDefinition(definitionJson);
    if (definition.key() != null && !definition.key().equals(defKey)) {
      throw new WorkflowDefinitionRequestException(
          "定义内的 key（" + definition.key() + "）与请求路径（" + defKey + "）不一致，拒绝保存");
    }
    Long tenantId = requireTenant();
    Instant now = Instant.now();
    Optional<WfProcessDraftRow> existing = drafts.findDraftRow(tenantId, defKey);
    int revision;
    if (existing.isEmpty()) {
      try {
        drafts.insertDraft(
            idGenerator.nextId(),
            tenantId,
            defKey,
            displayName(definition, defKey),
            definitionJson,
            operatorId,
            now);
        revision = 1;
      } catch (DuplicateKeyException race) {
        throw new WorkflowDefinitionConflictException("同一流程定义已被他人创建草稿（并发首存），请刷新后重试", race);
      }
    } else {
      if (expectedRevision == null) {
        throw new WorkflowDefinitionConflictException(
            "覆盖已有草稿必须携带 revision（当前为 " + existing.get().getRevision() + "），否则无法判断是否覆盖了他人修改");
      }
      int updated =
          drafts.updateDraft(
              tenantId,
              defKey,
              displayName(definition, defKey),
              definitionJson,
              expectedRevision,
              operatorId,
              now);
      if (updated == 0) {
        throw new WorkflowDefinitionConflictException(
            "草稿已被他人修改（提交的 revision="
                + expectedRevision
                + "，当前为 "
                + drafts
                    .findDraftRow(tenantId, defKey)
                    .map(WfProcessDraftRow::getRevision)
                    .orElse(-1)
                + "），请刷新后重试");
      }
      revision = expectedRevision + 1;
    }
    List<Violation> violations = validator.validate(definition);
    return new SaveDraftResult(
        new Draft(defKey, displayName(definition, defKey), definitionJson, revision, operatorId),
        violations);
  }

  /** 丢弃草稿（物理删除，见 {@link WfProcessDraftSdkRepository} 的类注释）。 */
  public void discardDraft(String defKey) {
    requireKey(defKey);
    drafts.deleteDraft(requireTenant(), defKey);
  }

  // ====================== 发布 ======================

  /**
   * 发布草稿为不可变版本。
   *
   * @throws WorkflowDefinitionInvalidException 校验不通过（401/422 由宿主映射；违规清单随异常带出）
   * @throws WorkflowDefinitionConflictException revision 过期或版本号并发冲突（409）
   */
  @Transactional
  public Published publish(String defKey, Integer expectedRevision, String operatorId) {
    requireKey(defKey);
    requireOperator(operatorId);
    Long tenantId = requireTenant();
    WfProcessDraftRow draft =
        drafts
            .findDraftRow(tenantId, defKey)
            .orElseThrow(
                () -> new WorkflowDefinitionConflictException("流程定义 " + defKey + " 没有草稿，无法发布"));

    if (expectedRevision != null && expectedRevision != draft.getRevision()) {
      throw new WorkflowDefinitionConflictException(
          "草稿已被他人修改（提交的 revision=" + expectedRevision + "，当前为 " + draft.getRevision() + "），请刷新后重试");
    }

    WorkflowDefinition definition = readDefinition(draft.getDefinitionJson());
    List<Violation> violations = validator.validate(definition);
    if (!violations.isEmpty()) {
      // fail-closed：配错的流程绝不发布。这条纪律的价值不在于「拦下 90%」，而在于那 10%——
      // 一个拼错的算子、一个忘了填的 roleCode，会让单据无人审批地卡住，或者流到错误的审批人手上。
      throw new WorkflowDefinitionInvalidException(defKey, violations);
    }

    int version =
        definitions
            .findMaxVersionRow(defKey, tenantId)
            .map(row -> row.getDefVersion() + 1)
            .orElse(1);
    Instant now = Instant.now();
    try {
      definitions.insertPublished(
          idGenerator.nextId(),
          tenantId,
          defKey,
          draft.getDefName(),
          version,
          draft.getDefinitionJson(),
          operatorId,
          now);
    } catch (DuplicateKeyException race) {
      throw new WorkflowDefinitionConflictException(
          "版本号 " + version + " 并发冲突（他人同时发布了同一版本），请重试", race);
    }
    drafts.deleteDraft(tenantId, defKey);
    return new Published(defKey, draft.getDefName(), version, draft.getDefinitionJson(), true);
  }

  /** 上线 / 下线某版本。不改 status、不删行：在途实例仍钉着这一版。 */
  public Published setEnabled(String defKey, int version, boolean enabled, String operatorId) {
    requireKey(defKey);
    requireOperator(operatorId);
    int updated =
        definitions.updateEnabled(
            requireTenant(), defKey, version, enabled ? 1 : 0, operatorId, Instant.now());
    if (updated == 0) {
      throw new WorkflowDefinitionRequestException(
          "流程定义 " + defKey + " 不存在版本 " + version + "（或已删除）");
    }
    return findVersion(defKey, version)
        .orElseThrow(
            () ->
                new WorkflowDefinitionRequestException(
                    "流程定义 " + defKey + " 版本 " + version + " 读取失败"));
  }

  // ====================== 查询 ======================

  /** 定义总览：每个 key 的当前生效版本。 */
  public List<Published> listLatest() {
    return definitions.listLatestPerKeyRows(requireTenant()).stream()
        .map(WorkflowDefinitionCommandService::toPublished)
        .toList();
  }

  /** 某 key 的全部版本（含已下线）。 */
  public List<Published> listVersions(String defKey) {
    requireKey(defKey);
    return definitions.listVersionRows(defKey, requireTenant()).stream()
        .map(WorkflowDefinitionCommandService::toPublished)
        .toList();
  }

  /** 指定版本（含 {@code ui} 段的原始 JSON，供设计器渲染既有版式）。 */
  public Optional<Published> findVersion(String defKey, int version) {
    requireKey(defKey);
    return definitions.findByVersionRow(defKey, version, requireTenant()).stream()
        .findFirst()
        .map(WorkflowDefinitionCommandService::toPublished);
  }

  // ====================== 内部 ======================

  private WorkflowDefinition readDefinition(String definitionJson) {
    if (definitionJson == null || definitionJson.isBlank()) {
      throw new WorkflowDefinitionRequestException("流程定义 JSON 为空");
    }
    try {
      WorkflowDefinition definition =
          objectMapper.readValue(definitionJson, WorkflowDefinition.class);
      if (definition == null) {
        throw new WorkflowDefinitionRequestException("流程定义 JSON 解析结果为空");
      }
      return definition;
    } catch (WorkflowDefinitionRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new WorkflowDefinitionRequestException(
          "流程定义 JSON 无法解析：" + exception.getMessage(), exception);
    }
  }

  private static String displayName(WorkflowDefinition definition, String defKey) {
    String name = definition.name();
    return name == null || name.isBlank() ? defKey : name;
  }

  private static Draft toDraft(WfProcessDraftRow row) {
    return new Draft(
        row.getDefKey(),
        row.getDefName(),
        row.getDefinitionJson(),
        row.getRevision(),
        row.getUpdatedBy());
  }

  private static Published toPublished(WfProcessDefRow row) {
    // enabled 未被本次查询取出时为 null，按「启用」解释——查询语句不取该列的场景只有单条读取，
    // 而单条读取的语义是「在途实例正在用的版本」，它可以是被下线之后仍在跑的历史版本。
    return new Published(
        row.getDefKey(),
        row.getDefName(),
        row.getDefVersion(),
        row.getDefinitionJson(),
        row.getEnabled() == null || row.getEnabled() == 1);
  }

  private static void requireKey(String defKey) {
    if (defKey == null || defKey.isBlank()) {
      throw new WorkflowDefinitionRequestException("流程定义 key 不能为空");
    }
  }

  private static void requireOperator(String operatorId) {
    if (operatorId == null || operatorId.isBlank()) {
      throw new WorkflowDefinitionRequestException("缺少操作人身份：写路径的审计列必须回答「谁改的」，不接受匿名写入");
    }
  }

  private static Long requireTenant() {
    Long tenantId = TenantContext.getTenantIdAsLong();
    if (tenantId == null) {
      throw new IllegalStateException("流程定义写路径缺少租户上下文（TenantContext）——拒绝在未知租户下读写 wf_* 数据");
    }
    return tenantId;
  }
}
