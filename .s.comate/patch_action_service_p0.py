#!/usr/bin/env python3
"""P0-2 生命周期改造：RuntimeActionService 幂等段 + 步骤 6-9 重写。

策略：短锚点（起止各一行），断言唯一命中后区间重写，原子写回。
"""
import re
import sys

PATH = "bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/runtime/application/RuntimeActionService.java"

with open(PATH, encoding="utf-8") as f:
    src = f.read()

def assert_once(s, token, label):
    n = s.count(token)
    assert n == 1, f"{label}: found {n} (expect 1): {token[:60]!r}"

# ── 段 A：幂等段（3. 资金类动作 → 幂等键校验 + 成功命中 + 孤儿命中分支）──
A_START = '    // 3. 资金类动作：幂等键必填 + 命中即短路返回。'
A_END = '    // 4. 必填入参校验'
assert_once(src, A_START, "A-start")
assert_once(src, A_END, "A-end")
a_i = src.index(A_START)
a_j = src.index(A_END)

seg_a_new = '''    // 3. 资金类动作：幂等键必填 + 命中即短路返回。
    //    ⚠ 必须在「状态机校验」之前：已流转到 to_status 的记录重放同一幂等键时，
    //    若先校验 from_status 会撞 409 而无法复用首次结果 —— 违背幂等语义（防重放二次记账）。
    //    P0-2：命中语义从「仅成功」扩展为「成功 OR 孤儿」——
    //    · 成功（status=1）：直接复用首次结果；
    //    · 孤儿（status=3，副作用已发出但状态流转未完成）：不自动重放（对真实支付网关重放=二次出金），
    //      抛 409 + ORPHANED 标记，由对账作业或人工确认后续转；返回体透传 sideEffect 供客服定位。
    boolean fromIdempotentReplay = false;
    Map<String, Object> replayedResult = null;
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
        int hitStatus = (int) hit.get("status");
        replayedResult = decode(hit.get("resultJson"));
        fromIdempotentReplay = true;
        if (hitStatus == 3) {
          log.warn(
              "动作孤儿命中（副作用已发、状态未流转），拒绝自动重放: {}/{}/ key={}",
              entityCode, recordId, actionCode, idempotencyKey);
          throw new RuntimeRecordException(
              MetadataErrorCodes.RUNTIME_ACTION_ORPHANED,
              "该幂等键存在未完成的执行（副作用可能已发出），须由对账流程确认后人工推进:"
                  + " action=" + actionCode + " record=" + recordId);
        }
        log.info(
            "动作幂等命中，跳过重复执行: {}/{}/ key={}", entityCode, recordId, actionCode, idempotencyKey);
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

'''
src = src[:a_i] + seg_a_new + src[a_j:]

# ── 段 B：步骤 6-9（副作用 → 执行生命周期 + 终态 + 返回体）──
B_START = '    // 6. 副作用（逃逸动作）'
B_END = '  private void validateParams('
assert_once(src, B_START, "B-start")
assert_once(src, B_END, "B-end")
b_i = src.index(B_START)
b_j = src.index(B_END)

seg_b_new = '''    // 6. 逃逸动作先记 PENDING（begin），再做副作用 —— 执行留痕前置（P0-2 核心）。
    //    旧实现「副作用→状态流转→成功才写日志」存在半事务窗口：网关已受理、
    //    平台 updateState 失败时零留痕，重试即可能二次出金（Runtime §4.8.1）。
    //    新时序：begin(status=PENDING) → dispatch → markSucceeded/markFailed/markOrphaned，
    //    任何一环崩溃（含 JVM kill、DB 闪断）系统里都有一行 pending/orphaned 记录可对账。
    Long executionId = null;
    Map<String, Object> sideEffect = new LinkedHashMap<>();
    if (def.isEscape()) {
      executionId =
          executionRepository.begin(
              tenantId, entityCode, recordId, actionCode, idempotencyKey, operatorId, params);
    }
    try {
      if (def.isEscape()) {
        sideEffect =
            dispatcher.dispatch(
                new RuntimeActionContext(entityCode, tenantId, recordId, def, params, operatorId));
      }

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

      if (executionId != null) {
        executionRepository.markSucceeded(executionId, encode(sideEffect));
      }

      // 9. 返回最新记录 + 结构化摘要（Runtime §4.4：fromStatus/toStatus 供前端 toast 与审计对齐）
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
          if (cur instanceof com.bone.metadata.engine.runtime.RuntimeRecordException
              && MetadataErrorCodes.PRECONDITION_FAILED.equals(
                  ((com.bone.metadata.engine.runtime.RuntimeRecordException) cur).getCode())) {
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
              entityCode, recordId, sideEffect, e);
        }
      }
      throw e;
    }
  }

'''
src = src[:b_i] + seg_b_new + src[b_j:]

# ── JavaDoc：步骤描述同步 ──
OLD_DOC = ''' *   <li>逃逸动作执行副作用（{@link RuntimeActionDispatcher}）；'''
NEW_DOC = ''' *   <li>逃逸动作先落 PENDING 执行日志（begin），再执行副作用（{@link RuntimeActionDispatcher}）；
 *   <li>统一状态流转后关闭执行生命周期（markSucceeded / markFailed / markOrphaned，P0-2 半事务防护）；'''
assert_once(src, OLD_DOC, "javadoc")
src = src.replace(OLD_DOC, NEW_DOC)

# ── 原子写回 ──
tmp = PATH + ".tmp"
with open(tmp, "w", encoding="utf-8") as f:
    f.write(src)
import os
os.replace(tmp, PATH)
print("runtime-action-service-rewritten")
