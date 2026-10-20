package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动作编排（应用服务）：驱动一次状态机动作的完整生命周期。
 *
 * <ol>
 *   <li>P0-1：解析动作定义后<strong>立即动作授权</strong>（{@link
 *       RuntimeAccessGuard#requireAction}：逃逸动作必须命中动作级细码，实体级写不覆盖）；
 *   <li>取当前记录状态；
 *   <li>资金类动作：校验 Idempotency-Key 并幂等去重——成功复用首次结果；孤儿态拒绝自动重放（对真实网关重放 = 可能二次出金）；
 *   <li>校验动作必填入参；
 *   <li>校验 {@code from_status -> to_status} 状态机边；
 *   <li>界面协议 {@code submitRules/linkageRules} 服务端强制（{@link UiSchemaRuleEnforcer}：前端先拦、此处置底， 违规带
 *       rule code 抛 422）；
 *   <li>逃逸动作先落 PENDING 执行日志（begin），再执行副作用（{@link RuntimeActionDispatcher}）；
 *   <li>平台统一把 {@code status_field} 流转到 {@code to_status}（调用方无法绕过状态机直写 status，修复 G14）；
 *   <li>关闭执行生命周期：成功 markSucceeded；If-Match 冲突视副作用已触达按孤儿语义收口；其余失败 markFailed；
 *   <li>返回最新记录与副作用结果。
 * </ol>
 */
@Service
@Slf4j
public class RuntimeActionApplicationService {

  private final JdbcRuntimeRecordService runtimeRecordService;
  private final RuntimeActionDefRepository actionDefRepository;
  private final RuntimeActionExecutionRepository executionRepository;
  private final RuntimeActionDispatcher dispatcher;
  private final ObjectMapper objectMapper;
  private final RuntimeAccessGuard accessGuard;
  private final UiSchemaRuleEnforcer uiSchemaRuleEnforcer;
  private final com.bone.metadata.runtime.domain.CurrentRoleProvider currentRoleProvider;

  public RuntimeActionApplicationService(
      JdbcRuntimeRecordService runtimeRecordService,
      RuntimeActionDefRepository actionDefRepository,
      RuntimeActionExecutionRepository executionRepository,
      RuntimeActionDispatcher dispatcher,
      ObjectMapper objectMapper,
      RuntimeAccessGuard accessGuard,
      UiSchemaRuleEnforcer uiSchemaRuleEnforcer,
      com.bone.metadata.runtime.domain.CurrentRoleProvider currentRoleProvider) {
    this.runtimeRecordService = runtimeRecordService;
    this.actionDefRepository = actionDefRepository;
    this.executionRepository = executionRepository;
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
    this.accessGuard = accessGuard;
    this.uiSchemaRuleEnforcer = uiSchemaRuleEnforcer;
    this.currentRoleProvider = currentRoleProvider;
  }

  /**
   * @param expectedVersion 可选的 {@code If-Match} 版本；资金 / 库存类动作建议携带，冲突 412。 动作与 CRUD
   *     共用同一套乐观并发语义，避免「列表看到 v1，动作却在 v3 上生效」。
   */
  public Map<String, Object> invokeAction(
      String entityCode,
      long tenantId,
      String recordId,
      String actionCode,
      Map<String, Object> params,
      String operatorId,
      String idempotencyKey,
      Integer expectedVersion) {
    params = (params == null) ? Map.of() : params;

    // 1. 动作定义 + 动作级鉴权（P0-1 闭合点）：def 感知的 RuntimeAccessGuard#requireAction
    //    逃逸动作（confirmPayout 等资金/外部副作用）必须命中动作级细码或声明码，
    //    实体级写 / 通配写不覆盖 —— 否则「按动作收回授权」无不动点，§4.6 门禁空转。
    //    该判定依赖 0034 迁移把细码 / 实体码 / 通配码授到角色（L3 审批项，由 DBA/CI 落地）；
    //    dev 口径 security.enabled=false 时匿名主体带 platform.admin，此处 early-return 不影响本地联调。
    RuntimeActionDef def =
        actionDefRepository
            .findByEntityAndCode(tenantId, entityCode, actionCode)
            .orElseThrow(
                () ->
                    new RuntimeRecordException(
                        MetadataErrorCodes.RUNTIME_ACTION_NOT_FOUND,
                        "未找到动作定义: " + entityCode + "/" + actionCode));
    accessGuard.requireAction(def);

    // 2. 当前记录状态
    Map<String, Object> record = runtimeRecordService.getById(entityCode, tenantId, recordId);
    Object currentStatus = record.get(def.getStatusField());
    String current = (currentStatus == null) ? null : currentStatus.toString();

    // 3. 资金类动作：幂等键必填 + 命中即短路返回。
    //    ⚠ 必须在「状态机校验」之前：已流转到 to_status 的记录重放同一幂等键时，
    //    若先校验 from_status 会撞 409 而无法复用首次结果 —— 违背幂等语义（防重放二次记账）。
    //    P0-2：命中语义从「仅成功」扩展为「成功 OR 孤儿」——
    //    · 成功（status=1）：直接复用首次结果；
    //    · 孤儿（status=3，副作用已发出但状态流转未完成）：不自动重放（对真实支付网关重放=二次出金），
    //      抛 409 + ORPHANED 标记，由对账作业或人工确认后续转；返回体透传 sideEffect 供客服定位。
    if (def.isRequiresIdempotency()) {
      if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_ACTION_IDEMPOTENCY_REQUIRED,
            "动作「" + actionCode + "」要求携带 Idempotency-Key 请求头（防重放二次打款）");
      }
      Map<String, Object> hit =
          executionRepository.findByIdempotency(
              tenantId, entityCode, recordId, actionCode, idempotencyKey);
      if (hit != null) {
        int hitStatus = ((Number) hit.get("status")).intValue();
        Object replayedResult = decode(hit.get("result_json"));
        if (hitStatus == 3) {
          log.warn(
              "动作孤儿命中（副作用已发、状态未流转），拒绝自动重放: {}/{}/{}/ key={}",
              entityCode,
              recordId,
              actionCode,
              idempotencyKey);
          throw new RuntimeRecordException(
              MetadataErrorCodes.RUNTIME_ACTION_ORPHANED,
              "该幂等键存在未完成的执行（副作用可能已发出），须由对账流程确认后人工推进:"
                  + " action="
                  + actionCode
                  + " record="
                  + recordId);
        }
        log.info(
            "动作幂等命中，跳过重复执行: {}/{}/{}/ key={}", entityCode, recordId, actionCode, idempotencyKey);
        Map<String, Object> updated = runtimeRecordService.getById(entityCode, tenantId, recordId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", updated);
        result.put("result", replayedResult);
        result.put("idempotent", true);
        result.put("orphaned", false);
        result.put("action", actionCode);
        return result;
      }
    }

    // 4. 必填入参校验（轻校验前置：缺参是客户端错误，与当前状态无关，应先于状态机校验）
    validateParams(def, params);

    // 5. 状态机边校验
    if (def.getFromStatus() != null && !def.getFromStatus().equals(current)) {
      throw new RuntimeRecordException(
          MetadataErrorCodes.RUNTIME_ACTION_ILLEGAL_TRANSITION,
          "动作「" + actionCode + "」要求当前状态为 " + def.getFromStatus() + "，实际为 " + current);
    }

    // 6. 界面协议强制（submitRules / linkageRules 服务端执行期兜底）。
    //    前端 runSubmit 先拦是「体验」，这里是「安全底线」：绕过界面直接调本 API 也必须被同一批规则拦下，
    //    且违规文案带 rule code（协议 §submitRule.code 的前后端对账用途）。
    //    位置在「幂等命中短路」与「副作用」之间 —— 既不能因规则失败而消费幂等键（用户改正数据后重试应能成功），
    //    也必须在逃逸动作 begin 前拦下（否则已打款才发现单据不合规）。
    uiSchemaRuleEnforcer.enforceBeforeAction(entityCode, recordId, record, operatorId);

    // 7. 逃逸动作先记 PENDING（begin），再做副作用 —— 执行留痕前置（P0-2 核心）。
    //    旧实现「副作用→状态流转→成功才写日志」存在半事务窗口：网关已受理、
    //    平台 updateState 失败时零留痕，重试即可能二次出金（Runtime §4.8.1）。
    //    新时序：begin(status=PENDING) → dispatch → markSucceeded/markFailed/markOrphaned，
    //    任何一环崩溃（含 JVM kill、DB 闪断）系统里都有一行 pending/orphaned 记录可对账。
    Long executionId = null;
    Map<String, Object> sideEffect = new LinkedHashMap<>();
    // 留痕对所有动作生效（不再只逃逸动作）：GET /records/{id}/action-executions 的契约是
    // 「这张单据被谁何时提交/审批/打款、每次结果如何」——只记逃逸动作会让提交/审批/驳回在审计时间线上
    // 完全不可见（实测：一条走完 提交→审批→打款 的报销单只有 1 条打款留痕），与端点 javadoc 宣称的
    // 「任何一次执行都留有痕迹（含孤儿）」不符，也不满足等保/SOX 对全量状态流转留痕的要求。
    // 幂等键仅在动作声明 requiresIdempotency 时落库：非幂等动作带同一 key 重试是合法行为
    // （网络超时后重发），落唯一键会把它变成冲突错误。
    executionId =
        executionRepository.begin(
            tenantId,
            entityCode,
            recordId,
            actionCode,
            def.isRequiresIdempotency() ? idempotencyKey : null,
            operatorId,
            params);
    try {
      if (def.isEscape()) {
        sideEffect =
            dispatcher.dispatch(
                new RuntimeActionContext(entityCode, tenantId, recordId, def, params, operatorId));
      }

      // 8. 统一状态流转（平台负责，调用方无法绕过状态机）。
      //    走 updateState 而非 update：后者对 state_field 一律 422；前者是状态机唯一合法推进入口，
      //    内部落记录级审计（RC-009：who 在何时把 status 从 from 改到 to）。
      Map<String, Object> updated =
          runtimeRecordService.updateState(
              entityCode,
              tenantId,
              recordId,
              def.getStatusField(),
              def.getToStatus(),
              expectedVersion,
              operatorId,
              actionCode,
              null,
              // 动作响应同样按角色裁剪：动作端点是用户面路径，与 CRUD 同一套字段级约束
              currentRoleProvider.currentRoleCodesOrNull());

      if (executionId != null) {
        executionRepository.markSucceeded(executionId, encode(sideEffect));
      }

      // 10. 返回最新记录 + 结构化摘要（Runtime §4.4：fromStatus/toStatus 供前端 toast 与审计对齐）
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("record", updated);
      result.put("result", sideEffect);
      result.put("idempotent", false);
      result.put("orphaned", false);
      result.put("action", actionCode);
      result.put("fromStatus", current);
      result.put("toStatus", def.getToStatus());
      return result;
    } catch (RuntimeException e) {
      if (executionId != null) {
        boolean transitionFailed = false;
        Throwable cur = e;
        while (cur != null) {
          if (cur instanceof RuntimeRecordException
              && MetadataErrorCodes.PRECONDITION_FAILED.equals(
                  ((RuntimeRecordException) cur).getErrorCode())) {
            transitionFailed = true;
            break;
          }
          cur = cur.getCause();
        }
        if (transitionFailed) {
          executionRepository.markSucceeded(executionId, encode(sideEffect));
        } else {
          executionRepository.markFailed(executionId, e.getMessage());
        }
        if (sideEffect != null && !sideEffect.isEmpty()) {
          log.error(
              "逃逸动作副作用已执行但整链失败，须对账: entity={} record={} sideEffect={}",
              entityCode,
              recordId,
              sideEffect,
              e);
        }
      }
      throw e;
    }
  }

  /**
   * 动作入参校验（Hasura Actions inputSchema 语义：平台统一类型 + 约束校验，Handler 不再各自校验）。
   *
   * <p>规则：必填缺失 → 422；类型不匹配（string/number/integer/boolean/enum）→ 422； number/integer 的 min/max 越界 →
   * 422；string 的 pattern 不匹配 → 422； enum 取值不在 values 内 → 422。全部收敛到 {@code
   * META_RUNTIME_ACTION_INVALID_PARAMS}。
   */
  private void validateParams(RuntimeActionDef def, Map<String, Object> params) {
    for (ActionParamSpec spec : def.getParams()) {
      String name = spec.getName();
      Object v = params.get(name);
      if (v == null || (v instanceof String s && s.isBlank())) {
        if (spec.isRequired()) {
          throw invalidParam(name, "为必填");
        }
        continue;
      }
      String type = spec.getType();
      if (type != null) {
        switch (type) {
          case "number", "integer" -> {
            double num;
            if (v instanceof Number n) {
              num = n.doubleValue();
            } else if (v instanceof String str) {
              try {
                num = Double.parseDouble(str);
              } catch (NumberFormatException ex) {
                throw invalidParam(name, "必须为数值");
              }
            } else {
              throw invalidParam(name, "必须为数值");
            }
            if ("integer".equals(type) && num != Math.floor(num)) {
              throw invalidParam(name, "必须为整数");
            }
            if (spec.getMin() != null && num < spec.getMin()) {
              throw invalidParam(name, "不能小于 " + trim(spec.getMin()));
            }
            if (spec.getMax() != null && num > spec.getMax()) {
              throw invalidParam(name, "不能大于 " + trim(spec.getMax()));
            }
          }
          case "boolean" -> {
            if (!(v instanceof Boolean)) {
              throw invalidParam(name, "必须为布尔值");
            }
          }
          case "enum" -> {
            List<String> values = spec.getValues();
            if (values != null && !values.isEmpty() && !values.contains(String.valueOf(v))) {
              throw invalidParam(name, "取值必须在 " + values + " 内");
            }
          }
          case "string" -> {
            // 任意可字符串化的值均可；约束由 pattern 承担
          }
          default -> {
            // 未知类型：不校验（兼容历史声明）
          }
        }
      }
      if (spec.getPattern() != null && !String.valueOf(v).matches(spec.getPattern())) {
        throw invalidParam(name, "格式不合法（pattern=" + spec.getPattern() + "）");
      }
    }
  }

  private RuntimeRecordException invalidParam(String name, String detail) {
    return new RuntimeRecordException(
        MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS, "动作参数「" + name + "」" + detail);
  }

  private static String trim(double d) {
    if (d == Math.floor(d) && !Double.isInfinite(d)) {
      return String.valueOf((long) d);
    }
    return String.valueOf(d);
  }

  private String encode(Map<String, Object> m) {
    try {
      return objectMapper.writeValueAsString(m);
    } catch (Exception e) {
      return "{}";
    }
  }

  private Map<String, Object> decode(Object raw) {
    if (!(raw instanceof String s) || s.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(s, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }
}
