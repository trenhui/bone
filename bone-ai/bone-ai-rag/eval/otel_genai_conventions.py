#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
BONE-AI LLM/RAG 指标 → OpenTelemetry GenAI 语义约定 映射与校验器
=====================================================================

背景
----
`bone-ai-app` 的 `MicrometerLlmMetrics` 已把网关 `LlmMetrics` 发射点落到 Micrometer，
其 Java 注释声明「与 OTel gen_ai.* 映射表版本化 v1 对齐」，但**实际映射在任何地方都未落地**
（诊断 §3.1 标记为「OTel 语义映射（规范仍 Development）为收口动作」）。本工具把这份映射做成
**版本化、可校验、可预览**的单一真源，使平台指标能零代码改动地流入任意 OTel 后端
（Grafana / Jaeger / Datadog OTel Collector）。

为什么不直接在 Java 里改 meter 名
-------------------------------
1. 现有告警与看板直接引用 `bone.ai.gateway.*` 名称，重命名会静默打断既有规则；
2. 库形态纪律要求「零新增必选依赖」——引入 Micrometer→OTel 注册表属于新增依赖，
   须由宿主按 `management.otlp.metrics.export.*` 标准开关启用，而非代码强耦合；
3. 因此**正确做法**是：保留 `bone.ai.gateway.*` 作为内部名，本工具给出权威别名映射，
   接入方在 OTel Collector / Actuator 桥接层做 `bone.ai.gateway.* → gen_ai.*` 重命名即可。

OTel GenAI 语义约定版本
----------------------
采用 OpenTelemetry `gen_ai.*` Semantic Conventions（2025–2026，规格态仍为 *Development*，
但已被 Grafana/DataDog/Jaeger 广泛接纳）。关键点：
- 时长类指标单位必须为 **秒（s）**——BONE 内部以毫秒记录，桥接时必须 ÷1000，否则直方图量纲错误；
- token 用量统一为 `gen_ai.client.token.usage`（counter，`{token}` 单位），以 `gen_ai.token.type`
  = input | output | total 区分；不再用 `gen_ai.usage.input_tokens` 这类旧属性；
- 调用/错误率由 `gen_ai.client.operation.duration` 的**记录数**与 `error.type` 属性派生，
  不再单独发 calls counter；
- 平台治理面指标（`short_circuited` / `cache.hit` / `quota.exceeded`）**故意保留在
  `bone.ai.gateway.*` 命名空间**，不污染标准 `gen_ai.*`，避免与上游未来约定冲突。

用法
----
    # CI 必跑：零依赖自校验（证明映射本身正确，不会被「没产物就不跑」绕过）
    python3 otel_genai_conventions.py --self-test

    # 打印人类可读的映射表
    python3 otel_genai_conventions.py --report

    # 把一个 BONE meter 样本 JSON 转成 OTel 等价 JSON（桥接预览）
    python3 otel_genai_conventions.py --emit-otel bone_meters.sample.json

退出码：自校验通过=0，任一断言失败=1；`--emit-otel` 正常=0。
"""
from __future__ import annotations

import json
import sys

# --------------------------------------------------------------------------- #
# 1. OTel GenAI 语义约定（权威元数据）
# --------------------------------------------------------------------------- #
OTEL_VERSION = "gen_ai_semconv_1.30.0_development"  # 采用态仍为 Development

# OTel gen_ai.* 指标名 + 单位 + 标准属性键
OTEL_GEN_AI = {
    "gen_ai.client.operation.duration": {
        "unit": "s",
        "kind": "histogram",
        "attrs": ["gen_ai.operation.name", "gen_ai.request.model", "gen_ai.system",
                  "gen_ai.response.model", "error.type"],
        "note": "每次 provider 调用记录一条；success=false 时附 error.type。",
    },
    "gen_ai.client.time.to_first.token": {
        "unit": "s",
        "kind": "histogram",
        "attrs": ["gen_ai.operation.name", "gen_ai.request.model", "gen_ai.system"],
        "note": "流式 TTFT；非流式可省略。",
    },
    "gen_ai.client.token.usage": {
        "unit": "{token}",
        "kind": "counter",
        "attrs": ["gen_ai.operation.name", "gen_ai.request.model", "gen_ai.system",
                  "gen_ai.token.type"],
        "note": "gen_ai.token.type ∈ {input, output, total}。",
    },
}

# OTel 标准属性键（与 gen_ai.* 配套的语义键）
OTEL_ATTR = {
    "operation_name": "gen_ai.operation.name",  # 如 "chat"
    "request_model": "gen_ai.request.model",     # BONE 的 model
    "system": "gen_ai.system",                   # provider 族，如 "qwen"
    "response_model": "gen_ai.response.model",
    "token_type": "gen_ai.token.type",           # input | output | total
    "error_type": "error.type",                  # OTel 通用错误属性
}

# 平台治理面指标：故意保留在 bone.ai.gateway.* 命名空间，不进 gen_ai.*
GOVERNANCE_KEPT = {
    "bone.ai.gateway.short_circuited": "短路（fail→确定性/人工）命中，治理面，非 GenAI 标准",
    "bone.ai.gateway.cache.hit": "精确缓存命中，治理面，非 GenAI 标准",
    "bone.ai.gateway.quota.exceeded": "配额超限，治理面，非 GenAI 标准",
}

# --------------------------------------------------------------------------- #
# 2. BONE 实际 meter（取自 MicrometerLlmMetrics，PREFIX=bone.ai.gateway.）
# --------------------------------------------------------------------------- #
PREFIX = "bone.ai.gateway."
BONE_METERS = {
    PREFIX + "tokens": {"unit": "{token}", "type": "counter",
                        "tags": ["tenant", "model", "kind"], "kind_values": ["prompt", "completion", "total"]},
    PREFIX + "provider.latency": {"unit": "ms", "type": "timer",
                                  "tags": ["tenant", "model"]},
    PREFIX + "first_token.latency": {"unit": "ms", "type": "timer",
                                     "tags": ["tenant", "model"]},
    PREFIX + "provider.calls": {"unit": "{call}", "type": "counter",
                                "tags": ["tenant", "model", "success"]},
    PREFIX + "short_circuited": {"unit": "{call}", "type": "counter", "tags": ["tenant", "model"]},
    PREFIX + "cache.hit": {"unit": "{call}", "type": "counter", "tags": ["tenant", "model"]},
    PREFIX + "quota.exceeded": {"unit": "{call}", "type": "counter", "tags": ["tenant", "model"]},
}

# token.kind → OTel gen_ai.token.type
TOKEN_TYPE_MAP = {"prompt": "input", "completion": "output", "total": "total"}

# --------------------------------------------------------------------------- #
# 3. 核心映射函数
# --------------------------------------------------------------------------- #
def map_bone_to_otel(name: str, tags: dict, value, unit: str = None):
    """把一个 BONE meter 样本转成 OTel 等价表示。

    返回 dict：
      - mapped=True 且含 otel_name / otel_attributes / otel_unit / otel_value（时长已转秒）
      - mapped=False 且 kept_as 指向保留的 bone.ai.gateway.* 名（治理面）
      - mapped=None 表示该 BONE meter 未被任何规则覆盖（应报错）
    """
    if name not in BONE_METERS:
        return {"mapped": None, "reason": f"unknown BONE meter: {name}"}

    # 治理面：保留命名空间
    if name in GOVERNANCE_KEPT:
        return {"mapped": False, "kept_as": name, "otel_attributes": _common_attrs(tags),
                "reason": GOVERNANCE_KEPT[name]}

    attrs = _common_attrs(tags)
    # model → gen_ai.request.model
    if tags.get("model"):
        attrs[OTEL_ATTR["request_model"]] = tags["model"]

    if name == PREFIX + "tokens":
        kind = tags.get("kind")
        otel_kind = TOKEN_TYPE_MAP.get(kind)
        if otel_kind is None:
            return {"mapped": None, "reason": f"tokens kind={kind!r} 不在 {list(TOKEN_TYPE_MAP)}"}
        attrs[OTEL_ATTR["token_type"]] = otel_kind
        return {"mapped": True, "otel_name": "gen_ai.client.token.usage",
                "otel_unit": OTEL_GEN_AI["gen_ai.client.token.usage"]["unit"],
                "otel_attributes": attrs, "otel_value": value}

    if name in (PREFIX + "provider.latency", PREFIX + "first_token.latency"):
        # 毫秒 → 秒（OTel 时长单位必须为 s）
        if name == PREFIX + "provider.latency":
            otel_name = "gen_ai.client.operation.duration"
            # success=false → 错误子集
            if tags.get("success") == "false":
                attrs[OTEL_ATTR["error_type"]] = "provider_error"
        else:
            otel_name = "gen_ai.client.time.to_first.token"
        seconds = (value / 1000.0) if isinstance(value, (int, float)) else value
        return {"mapped": True, "otel_name": otel_name,
                "otel_unit": OTEL_GEN_AI[otel_name]["unit"],
                "otel_attributes": attrs, "otel_value": seconds}

    if name == PREFIX + "provider.calls":
        # 不单独发 counter；由 operation.duration 的记录数派生调用量/错误率
        attrs = _common_attrs(tags)
        if tags.get("model"):
            attrs[OTEL_ATTR["request_model"]] = tags["model"]
        if tags.get("success") == "false":
            attrs[OTEL_ATTR["error_type"]] = "provider_error"
        return {"mapped": True, "otel_name": "gen_ai.client.operation.duration",
                "otel_unit": "s", "otel_attributes": attrs,
                "otel_value": None,  # 仅计数语义，无时长
                "note": "calls 由 duration 直方图记录数派生；错误率由 error.type 子集派生"}

    return {"mapped": None, "reason": f"no mapping rule for {name}"}


def _common_attrs(tags: dict) -> dict:
    attrs = {OTEL_ATTR["operation_name"]: "chat"}  # 平台 LLM 操作为 chat 类
    # tenant 保留为平台维度（OTel 允许自定义属性，不进 gen_ai.*）
    if tags.get("tenant"):
        attrs["tenant"] = tags["tenant"]
    return attrs


# --------------------------------------------------------------------------- #
# 4. 校验：BONE 覆盖度 + OTel 量纲正确性
# --------------------------------------------------------------------------- #
def validate_bone_coverage():
    """检查每个 BONE meter 都有映射或明确保留；时长类必须转秒。"""
    problems = []
    for name, spec in BONE_METERS.items():
        r = map_bone_to_otel(name, _sample_tags(name), 1.0)
        if r.get("mapped") is None:
            problems.append(f"未覆盖: {name} -> {r.get('reason')}")
        elif r.get("mapped") is True and "duration" in r.get("otel_name", ""):
            if r.get("otel_unit") != "s":
                problems.append(f"量纲错误: {name} 未转秒 (got {r.get('otel_unit')})")
    return problems


def _sample_tags(name: str) -> dict:
    base = {"tenant": "t1", "model": "qwen3.5-27b"}
    spec = BONE_METERS[name]
    if "kind" in spec["tags"]:
        base["kind"] = "prompt"
    if "success" in spec["tags"]:
        base["success"] = "true"
    return base


# --------------------------------------------------------------------------- #
# 5. CLI
# --------------------------------------------------------------------------- #
def cmd_report():
    print(f"OTel GenAI Semantic Conventions 版本: {OTEL_VERSION}")
    print(f"OTel 指标名 -> 单位 / 类型 / 标准属性")
    for n, m in OTEL_GEN_AI.items():
        print(f"  {n}  [{m['unit']}]  {m['kind']}")
        print(f"      attrs: {', '.join(m['attrs'])}")
    print("\nBONE meter -> OTel 映射:")
    for name in BONE_METERS:
        r = map_bone_to_otel(name, _sample_tags(name), 1234.0)
        if r.get("mapped") is True:
            print(f"  {name}  ->  {r['otel_name']}  {r.get('otel_unit','')}  attrs={r['otel_attributes']}")
        elif r.get("mapped") is False:
            print(f"  {name}  ->  保留 {r['kept_as']}（治理面，不进 gen_ai.*）")
    print("\n治理面保留项:")
    for n, why in GOVERNANCE_KEPT.items():
        print(f"  {n}  ({why})")
    return 0


def cmd_emit(path: str):
    with open(path, "r", encoding="utf-8") as f:
        samples = json.load(f)
    out = []
    for s in samples:
        r = map_bone_to_otel(s["name"], s.get("tags", {}), s.get("value"), s.get("unit"))
        out.append({"bone": s, "otel": r})
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0


def cmd_self_test():
    """零依赖自校验：内嵌合规 + 违规 fixture，CI 必跑。"""
    results = []
    ok = True

    def check(cid, cond, detail):
        nonlocal ok
        passed = bool(cond)
        ok = ok and passed
        results.append({"id": cid, "passed": passed, "detail": detail})

    # NP1: tokens prompt -> input，单位 {token}
    r = map_bone_to_otel(PREFIX + "tokens", {"tenant": "t1", "model": "qwen3.5-27b", "kind": "prompt"}, 100)
    check("NP1_tokens_prompt_to_input",
          r["mapped"] is True and r["otel_name"] == "gen_ai.client.token.usage"
          and r["otel_attributes"]["gen_ai.token.type"] == "input" and r["otel_unit"] == "{token}"
          and r["otel_value"] == 100, str(r))

    # NP2: tokens total -> total
    r = map_bone_to_otel(PREFIX + "tokens", {"tenant": "t1", "model": "m", "kind": "total"}, 300)
    check("NP2_tokens_total", r["otel_attributes"]["gen_ai.token.type"] == "total"
          and r["otel_value"] == 300, str(r))

    # NP3: provider.latency ms -> s（÷1000），且 model 映射为 request.model
    r = map_bone_to_otel(PREFIX + "provider.latency", {"tenant": "t1", "model": "qwen3.5-27b"}, 250)
    check("NP3_latency_ms_to_s", r["mapped"] is True
          and r["otel_name"] == "gen_ai.client.operation.duration" and r["otel_unit"] == "s"
          and abs(r["otel_value"] - 0.25) < 1e-9
          and r["otel_attributes"]["gen_ai.request.model"] == "qwen3.5-27b", str(r))

    # NP4: first_token.latency -> time.to_first.token，且转秒
    r = map_bone_to_otel(PREFIX + "first_token.latency", {"tenant": "t1", "model": "m"}, 80)
    check("NP4_ttft", r["otel_name"] == "gen_ai.client.time.to_first.token"
          and r["otel_unit"] == "s" and abs(r["otel_value"] - 0.08) < 1e-9, str(r))

    # NP5: provider.calls success=false -> duration 带 error.type（错误率派生）
    r = map_bone_to_otel(PREFIX + "provider.calls",
                         {"tenant": "t1", "model": "m", "success": "false"}, 1)
    check("NP5_calls_error_derived", r["mapped"] is True
          and r["otel_name"] == "gen_ai.client.operation.duration"
          and r["otel_attributes"].get("error.type") == "provider_error", str(r))

    # NP6: 治理面保留命名空间，不进 gen_ai.*
    for gov in GOVERNANCE_KEPT:
        r = map_bone_to_otel(gov, {"tenant": "t1", "model": "m"}, 1)
        check(f"NP6_keep_{gov.split('.')[-1]}", r["mapped"] is False
              and r["kept_as"] == gov, str(r))

    # NP7: 未知 BONE meter 必须被标记（不可静默放行）
    r = map_bone_to_otel("bone.ai.gateway.unknown", {}, 1)
    check("NP7_unknown_rejected", r["mapped"] is None, str(r))

    # NP8: 覆盖度 + 量纲全局校验
    probs = validate_bone_coverage()
    check("NP8_coverage_and_units", len(probs) == 0, "; ".join(probs) if probs else "ok")

    print(json.dumps({"passed": ok, "results": results}, ensure_ascii=False, indent=2))
    print("[eval] " + ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


def main(argv):
    if not argv or argv[0] in ("--self-test", "-s"):
        return cmd_self_test()
    if argv[0] in ("--report", "-r"):
        return cmd_report()
    if argv[0] in ("--emit-otel", "-e"):
        if len(argv) < 2:
            print("usage: --emit-otel <bone_meters.json>", file=sys.stderr)
            return 2
        return cmd_emit(argv[1])
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
