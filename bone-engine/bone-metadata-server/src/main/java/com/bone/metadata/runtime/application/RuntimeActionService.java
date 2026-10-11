package com.bone.metadata.runtime.application;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.runtime.domain.ActionParamSpec;
import com.bone.metadata.runtime.domain.RuntimeActionContext;
import com.bone.metadata.runtime.domain.RuntimeActionDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 动作编排（应用服务）：驱动一次状态机动作的完整生命周期。
 *
 * <ol>
 *   <li>解析动作定义（{@code meta_entity_action_def}）；
 *   <li>取当前记录状态，校验 {@code from_status -> to_status} 的合法性（状态机边）；
 *   <li>校验动作必填入参；
 *   <li>资金类动作：校验 Idempotency-Key 并幂等去重（防重放二次记账）；
 *   <li>逃逸动作执行副作用（{@link RuntimeActionDispatcher}）；
 *   <li>由平台统一把 {@code status_field} 流转到 {@code to_status}（调用方无法再绕过状态机直写 status —— 修复 G14）；
 *   <li>落执行日志（幂等 + RC-009 记录级变更审计）；
 *   <li>返回最新记录与副作用结果。
 * </ol>
 */
@Service
@Slf4j
public class RuntimeActionService {

  private final JdbcRuntimeRecordService runtimeRecordService;
  private final RuntimeActionDefRepository actionDefRepository;
  private final RuntimeActionExecutionRepository executionRepository;
  private final RuntimeActionDispatcher dispatcher;
  private final ObjectMapper objectMapper;

  public RuntimeActionService(
      JdbcRuntimeRecordService runtimeRecordService,
      RuntimeActionDefRepository actionDefRepository,
      RuntimeActionExecutionRepository executionRepository,
      RuntimeActionDispatcher dispatcher,
      ObjectMapper objectMapper) {
    this.runtimeRecordService = runtimeRecordService;
    this.actionDefRepository = actionDefRepository;
    this.executionRepository = executionRepository;
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
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

    // 1. 动作定义
    RuntimeActionDef def =
        actionDefRepository
            .findByEntityAndCode(tenantId, entityCode, actionCode)
            .orElseThrow(
                () ->
                    new RuntimeRecordException(
                        MetadataErrorCodes.RUNTIME_ACTION_NOT_FOUND,
                        "未找到动作定义: " + entityCode + "/" + actionCode));

    // 2. 当前记录状态
    Map<String, Object> record = runtimeRecordService.getById(entityCode, tenantId, recordId);
    Object currentStatus = record.get(def.getStatusField());
    String current = (currentStatus == null) ? null : currentStatus.toString();

    // 3. 资金类动作：幂等键必填 + 命中即短路返回。
    //    ⚠ 必须在「状态机校验」之前：已流转到 to_status 的记录重放同一幂等键时，
    //    若先校验 from_status 会撞 409 而无法复用首次结果 —— 违背幂等语义（防重放二次记账）。
    if (def.isRequiresIdempotency()) {
      if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new RuntimeRecordException(
            MetadataErrorCodes.RUNTIME_ACTION_IDEMPOTENCY_REQUIRED,
            "动作「" + actionCode + "」要求携带 Idempotency-Key 请求头（防重放二次打款）");
      }
      Optional<String> prior =
          executionRepository.findSuccessResultByIdempotency(
              tenantId, entityCode, recordId, actionCode, idempotencyKey);
      if (prior.isPresent()) {
        log.info(
            "动作幂等命中，跳过重复执行: {}/{}/{} key={}", entityCode, recordId, actionCode, idempotencyKey);
        Map<String, Object> updated = runtimeRecordService.getById(entityCode, tenantId, recordId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", updated);
        result.put("result", decode(prior.get()));
        result.put("idempotent", true);
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

    // 6. 副作用（逃逸动作）
    Map<String, Object> sideEffect =
        dispatcher.dispatch(
            new RuntimeActionContext(entityCode, tenantId, recordId, def, params, operatorId));

    // 7. 统一状态流转（平台负责，调用方无法绕过状态机）。
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
            null);

    // 8. 执行日志（幂等去重 + 副作用结果留存）
    executionRepository.record(
        tenantId,
        entityCode,
        recordId,
        actionCode,
        idempotencyKey,
        true,
        encode(sideEffect),
        operatorId);

    // 9. 返回最新记录 + 结构化摘要（Runtime §4.4：fromStatus/toStatus 供前端 toast 与审计对齐）
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("record", updated);
    result.put("result", sideEffect);
    result.put("idempotent", false);
    result.put("action", actionCode);
    result.put("fromStatus", current);
    result.put("toStatus", def.getToStatus());
    return result;
  }

  private void validateParams(RuntimeActionDef def, Map<String, Object> params) {
    for (ActionParamSpec spec : def.getParams()) {
      if (spec.isRequired()) {
        Object v = params.get(spec.getName());
        if (v == null || v.toString().isBlank()) {
          throw new RuntimeRecordException(
              MetadataErrorCodes.RUNTIME_ACTION_INVALID_PARAMS,
              "动作参数「" + spec.getLabel() + "（" + spec.getName() + "）」为必填");
        }
      }
    }
  }

  private String encode(Map<String, Object> m) {
    try {
      return objectMapper.writeValueAsString(m);
    } catch (Exception e) {
      return "{}";
    }
  }

  private Map<String, Object> decode(String s) {
    try {
      return objectMapper.readValue(s, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }
}
