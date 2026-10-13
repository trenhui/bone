#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""P0 修复精修 v3：短锚点断言替换。任何锚点未命中/不唯一立即中止。"""
import sys, io

SVC = "bone-engine/bone-metadata-server/src/main/java/com/bone/metadata/runtime/application/RuntimeActionService.java"
EC = "bone-engine/bone-metadata-engine/bone-metadata-engine-runtime/src/main/java/com/bone/metadata/engine/runtime/MetadataErrorCodes.java"

def rd(p):
    with io.open(p, encoding="utf-8") as f:
        return f.read()

def wr(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)

def rep(s, old, new, label, count=1):
    n = s.count(old)
    assert n == count, "%s: expect %d, got %d" % (label, count, n)
    return s.replace(old, new)

# ── RuntimeActionService.java ──────────────────────────────
s = rd(SVC)

# 1) 移除未消费的中间态变量
s = rep(s,
    "boolean fromIdempotentReplay = false;\n    Map<String, Object> replayedResult = null;\n",
    "", "drop-unused-decls")
s = rep(s, "        fromIdempotentReplay = true;\n", "", "drop-replay-assign")

# 2) 日志占位符补全（孤儿 + 成功两处）
s = rep(s, "{}/{}/ key={}", "{}/{}/{}/ key={}", "log-placeholders", 2)

# 3) FQN 简化（类已 import RuntimeRecordException）
s = rep(s, "cur instanceof com.bone.metadata.engine.runtime.RuntimeRecordException\n"
           "              && MetadataErrorCodes.PRECONDITION_FAILED.equals(\n"
           "                  ((com.bone.metadata.engine.runtime.RuntimeRecordException) cur).getCode())",
        "cur instanceof RuntimeRecordException\n"
           "              && MetadataErrorCodes.PRECONDITION_FAILED\n"
           "                  .equals(((RuntimeRecordException) cur).getCode())",
        "fqn-simplify")

# 4) 移除已无引用的 Optional import
s = rep(s, "import java.util.Optional;\n", "", "drop-optional-import")

# 5) class javadoc <ol> 清单重写（旧 9 条混有重叠描述）
start = s.index(u" * <ol>")
end = s.index(u" * </ol>") + len(u" * </ol>")
new_ol = (u" * <ol>\n"
    u" *   <li>P0-1：解析动作定义后<strong>立即动作授权</strong>（{@link RuntimeAccessGuard#requireAction}："
    u"逃逸动作必须命中动作级细码，实体级写不覆盖）；\n"
    u" *   <li>取当前记录状态；\n"
    u" *   <li>资金类动作：校验 Idempotency-Key 并幂等去重——成功复用首次结果；孤儿态拒绝自动重放（对真实网关重放 = 可能二次出金）；\n"
    u" *   <li>校验动作必填入参；\n"
    u" *   <li>校验 {@code from_status -> to_status} 状态机边；\n"
    u" *   <li>逃逸动作先落 PENDING 执行日志（begin），再执行副作用（{@link RuntimeActionDispatcher}）；\n"
    u" *   <li>平台统一把 {@code status_field} 流转到 {@code to_status}（调用方无法绕过状态机直写 status，修复 G14）；\n"
    u" *   <li>关闭执行生命周期：成功 markSucceeded；If-Match 冲突视副作用已触达按孤儿语义收口；其余失败 markFailed；\n"
    u" *   <li>返回最新记录与副作用结果。\n"
    u" * </ol>")
s = s[:start] + new_ol + s[end:]

wr(SVC, s)
print("service-polished")

# ── MetadataErrorCodes.java：登记 ORPHANED ─────────────────
e = rd(EC)
anchor = '  /** 资金类动作缺少 Idempotency-Key（防重放二次记账）。 */\n' \
         '  public static final String RUNTIME_ACTION_IDEMPOTENCY_REQUIRED =\n' \
         '      "META_RUNTIME_ACTION_IDEMPOTENCY_REQUIRED";\n'
assert e.count(anchor) == 1, "EC anchor missing"
addition = anchor + '\n' \
    '  /**\n' \
    '   * 动作执行孤儿态（副作用已发出、状态流转未完成；幂等重放被拒绝）→ 409。\n' \
    '   *\n' \
    '   * <p>P0-2 半事务防护：对真实支付网关自动重放可能造成二次出金，孤儿态只允许\n' \
    '   * 对账作业或人工确认后续转（Runtime 数据面设计 §4.8.1）。\n' \
    '   */\n' \
    '  public static final String RUNTIME_ACTION_ORPHANED =\n' \
    '      "META_RUNTIME_ACTION_ORPHANED";\n'
e = e.replace(anchor, addition)
wr(EC, e)
print("error-codes-registered")
