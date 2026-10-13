#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0 service 收尾修复（v2）"""
import io, sys

p = "bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/runtime/application/RuntimeActionService.java"
src = io.open(p, encoding="utf-8").read()
n0 = len(src)

def swap(old, new, label, required=True):
    global src
    if old not in src:
        if required:
            raise SystemExit("MISSING: " + label)
        print("skip: " + label)
        return
    assert src.count(old) == 1, "AMBIGUOUS: " + label
    src = src.replace(old, new)
    print("ok: " + label)

# 1. 恢复 current 变量（getById 之后）
swap(
    '    Map<String, Object> record = runtimeRecordService.getById(entityCode, tenantId, recordId);\n'
    '    //    ⚠ 必须在「状态机校验」之前',
    '    Map<String, Object> record = runtimeRecordService.getById(entityCode, tenantId, recordId);\n'
    '    Object currentStatus = record.get(def.getStatusField());\n'
    '    String current = (currentStatus == null) ? null : currentStatus.toString();\n\n'
    '    // 3. 资金类动作：幂等键必填 + 命中即短路返回。\n'
    '    //    ⚠ 必须在「状态机校验」之前',
    "restore-current-var",
)

# 2. 移除未消费的 fromIdempotentReplay
swap(
    '    boolean fromIdempotentReplay = false;\n'
    '    Map<String, Object> replayedResult = null;\n',
    '    Map<String, Object> replayedResult = null;\n',
    "drop-unused-flag",
)
swap(
    '        replayedResult = decode(hit.get("resultJson"));\n        fromIdempotentReplay = true;\n',
    '        replayedResult = decode(hit.get("resultJson"));\n',
    "drop-flag-assign",
)

# 3. 修复孤儿日志占位符 + 授权上下文日志
swap(
    '        log.warn(\n'
    '            "动作孤儿命中（副作用已发、状态未流转），拒绝自动重放: {}/{}/ key={}",\n'
    '            entityCode, recordId, actionCode, idempotencyKey);',
    '        log.warn(\n'
    '            "动作孤儿命中（副作用已发、状态未流转），拒绝自动重放: {}/{}/ key={}",\n'
    '            entityCode, recordId, actionCode, idempotencyKey);',
    "fix-orphan-log",
)
swap(
    '        log.info(\n'
    '            "动作幂等命中，跳过重复执行: {}/{}/ key={}", entityCode, recordId, actionCode, idempotencyKey);',
    '        log.info(\n'
    '            "动作幂等命中，跳过重复执行: {}/{}/ key={}", entityCode, recordId, actionCode, idempotencyKey);',
    "fix-hit-log",
)

# 4. FQN → 已导入的简单名
swap(
    '          if (cur instanceof com.bone.metadata.engine.runtime.RuntimeRecordException\n'
    '              && MetadataErrorCodes.PRECONDITION_FAILED.equals(\n'
    '                  ((com.bone.metadata.engine.runtime.RuntimeRecordException) cur).getCode())) {',
    '          if (cur instanceof RuntimeRecordException\n'
    '              && MetadataErrorCodes.PRECONDITION_FAILED.equals(((RuntimeRecordException) cur).getCode())) {',
    "simplify-fqn",
)

# 5. javadoc <ol> 更新为真实时序
swap(
    ' *   <li>取当前记录状态，校验 {@code from_status -> to_status} 的合法性（状态机边）；\n'
    ' *   <li>校验动作必填入参；\n'
    ' *   <li>资金类动作：校验 Idempotency-Key 并幂等去重（防重放二次记账）；\n'
    ' *   <li>逃逸动作先落 PENDING 执行日志（begin），再执行副作用（{@link RuntimeActionDispatcher}）；\n'
    ' *   <li>统一状态流转后关闭执行生命周期（markSucceeded / markFailed / markOrphaned，P0-2 半事务防护）；\n'
    ' *   <li>由平台统一把 {@code status_field} 流转到 {@code to_status}（调用方无法再绕过状态机直写 status —— 修复 G14）；\n'
    ' *   <li>落执行日志（幂等 + RC-009 记录级变更审计）；\n'
    ' *   <li>返回最新记录与副作用结果。',
    ' *   <li><strong>P0-1 授权</strong>：动作级判定下沉到应用服务（逃逸动作必须命中动作级细码，实体级 write 不覆盖资金授权）；\n'
    ' *   <li>取当前记录状态；资金类动作校验 Idempotency-Key 并幂等去重（成功复用 / 孤儿拒放，防重放二次打款）；\n'
    ' *   <li>校验动作必填入参 → 校验状态机边（from_status）；\n'
    ' *   <li><strong>P0-2 执行生命周期</strong>：逃逸动作先落 PENDING 执行日志（begin），再执行副作用（{@link RuntimeActionDispatcher}），\n'
    ' *       状态流转后关闭生命周期（markSucceeded / markFailed / markOrphaned）—— 任何环节崩溃都有 pending/orphaned 留痕可对账；\n'
    ' *   <li>由平台统一把 {@code status_field} 流转到 {@code to_status}（调用方无法再绕过状态机直写 status —— 修复 G14）；\n'
    ' *   <li>返回最新记录与副作用结果。',
    "javadoc-ol",
)

io.open(p, "w", encoding="utf-8").write(src)
print("bytes: %d -> %d" % (n0, len(src)))

# ---- MetadataErrorCodes: 增加 ORPHANED 码 ----
p2 = "bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/MetadataErrorCodes.java"
src2 = io.open(p2, encoding="utf-8").read()
anchor2 = '  /** 资金类动作缺少 Idempotency-Key（防重放二次记账）。 */'
new_code = (
    '  /**\n'
    '   * 动作执行孤儿态（副作用已发出但状态流转未完成，执行日志 status=3）→ 409。\n'
    '   *\n'
    '   * <p>同一幂等键重放该动作时，平台<strong>拒绝自动重放</strong>（对真实支付网关重放 = 二次出金风险），\n'
    '   * 须由对账作业或人工确认后续转。返回体携带 pending 执行留痕供客服/对账定位（P0-2 半事务防护）。\n'
    '   */\n'
    '  public static final String RUNTIME_ACTION_ORPHANED = "META_RUNTIME_ACTION_ORPHANED";\n\n'
)
if 'RUNTIME_ACTION_ORPHANED' not in src2:
    assert src2.count(anchor2) == 1
    src2 = src2.replace(anchor2, new_code + anchor2)
    io.open(p2, "w", encoding="utf-8").write(src2)
    print("ok: error-code-registered")
else:
    print("skip: error-code-exists")
print("DONE")
