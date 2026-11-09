#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""从 LLD 总纲附录 B「事件契约注册表」+ 各卷 §7 字段速查生成 asyncapi.yaml。

单一真源 = 总纲附录 B（事件规范 §4「先登记注册表 → 再写 Producer」）。
禁止手改 asyncapi.yaml；改动流程：改注册表 → 重跑本脚本 → 门禁 A8 校验零漂移。
版本 v0.3（2026-11-09）：附录 B 为唯一真源、本工件为派生物；INBOUND 事件的
唯一 payload 定义见 D3-v2 §2（ContractReviewed）+ §2.1（其余核心/INBOUND 字段级 schema，
续诊 O1 闭合 payload 空壳），消费方投影行（以「见 」开头）不参与字段速查。
零依赖（纯 stdlib），可直接接 CI。

用法：
  python3 generate_asyncapi.py            # 生成 / 覆盖 asyncapi.yaml
  python3 generate_asyncapi.py --check    # 不写文件，仅比对；不一致返回 1
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHARTER = HERE.parent / "00-总纲与统一约定.md"
D3V2 = HERE.parent.parent / "采购AI-接口契约与数据字典-v2.md"
OUT = HERE / "asyncapi.yaml"
VOLUMES = sorted(HERE.parent.glob("0[1-6]-*.md"))

# 续诊 O1：核心 / INBOUND 事件字段级 schema 真源在 D3-v2 §2（ContractReviewed）+ §2.1。
# 这些事件的 asyncapi payload.properties 由本集生成；其余事件维持 additionalProperties:true + x-fields。
CORE_EVENTS = {
    "ContractReviewed", "GoodsReceived", "InvoiceSubmitted", "InvoiceMatched",
    "PaymentRecorded", "PurchaseOrderCreated", "VendorActivated",
}

# 附录 B 表格行：| `EventType` | `topic` | producer | consumers | partition_key | 备注 |
REGISTRY_ROW = re.compile(r"^\|\s+`(?P<event>[A-Z][A-Za-z0-9]*)`\s+\|\s+`(?P<topic>domain\.procurement\.[a-z0-9_.]+)`\s+\|")
EVENT_TOKEN = re.compile(r"`([A-Z][A-Za-z0-9]*)`")


def yaml_str(value: str) -> str:
    """把任意文档字段串安全放进 YAML 双引号标量。"""
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("\n", " ") + '"'


def parse_registry(text: str) -> list[dict[str, str]]:
    if "**B.1 已冻结" not in text or "**B.2 LLD 级新增" not in text:
        raise SystemExit("总纲附录 B 结构异常：缺 B.1 / B.2 分节")
    b1 = text.split("**B.1 已冻结", 1)[1].split("**B.2", 1)[0]
    b2 = text.split("**B.2 LLD 级新增", 1)[1].split("**B.3", 1)[0]
    rows: list[dict[str, str]] = []
    seen: set[str] = set()
    for section, status in ((b1, "Frozen"), (b2, "Proposal")):
        for line in section.splitlines():
            m = REGISTRY_ROW.match(line)
            if not m:
                continue
            cols = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cols) < 6:
                raise SystemExit(f"注册表行列数异常：{line}")
            event, topic = m.group("event"), m.group("topic")
            if event in seen:
                raise SystemExit(f"注册表 eventType 重复：{event}")
            if topic in {r["topic"] for r in rows}:
                raise SystemExit(f"注册表 topic 重复：{topic}")
            seen.add(event)
            producer, consumers, pkey, note = cols[2], cols[3], cols[4], cols[5]
            rows.append({
                "event": event, "topic": topic, "producer": producer,
                "consumers": consumers, "pkey": pkey, "note": note,
                "status": status,
                "inbound": "INBOUND" in producer or "INBOUND" in note,
                "deviation": "L31" in note or "domain 段偏差" in note,
            })
    if len(rows) < 20:
        raise SystemExit(f"注册表条目过少（{len(rows)}），疑似解析失败或注册表被删")
    return rows


def parse_fields_from_texts(rows: list[dict[str, str]],
                            volume_texts: dict[str, str]) -> dict[str, str]:
    """纯函数版：从已读入的各卷文本抽取字段速查串，供门禁 A8 注入式反证复用。

    `volume_texts` 的键仅用于稳定排序（生产方优先以表内「生产」行覆盖）。
    """
    fields: dict[str, str] = {}
    for name in sorted(volume_texts):
        text = volume_texts[name]
        if "## 7. 事件契约" not in text:
            continue
        section = text.split("## 7. 事件契约", 1)[1].split("\n## ", 1)[0]
        for line in section.splitlines():
            if not re.match(r"^\| (生产|消费|中继) \|", line):
                continue
            cols = [c.strip() for c in line.strip().strip("|").split("|")]
            if len(cols) < 3:
                continue
            events = EVENT_TOKEN.findall(cols[1])
            payload = cols[2]
            # 消费方投影声明不是 payload 定义（R3：避免按卷序取简版）
            if payload.startswith("见 ") or "唯一 payload 定义" in payload:
                continue
            for ev in events:
                if ev not in fields or cols[0] == "生产":
                    fields[ev] = payload
    missing = [r["event"] for r in rows if r["event"] not in fields]
    if missing:
        raise SystemExit(f"注册表事件在各卷 §7 无字段速查（禁止无 payload 契约）：{missing}")
    return fields


def parse_fields(rows: list[dict[str, str]]) -> dict[str, str]:
    """从各卷 §7 表格抽取 eventType → payload 字段速查串（CLI 入口，读盘）。"""
    return parse_fields_from_texts(
        rows, {p.name: p.read_text(encoding="utf-8") for p in VOLUMES}
    )


FIELD_ROW = re.compile(
    r"^\|\s+`?([A-Za-z0-9_]+)`?\s+\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*(.*?)\s*\|$"
)


def _field_tuple(m: re.Match[str]) -> tuple[str, str, bool, str]:
    field, typ, req_raw, desc = (m.group(i).strip() for i in range(1, 5))
    required = ("✅" in req_raw) or ("必填" in req_raw)
    return (field, typ, required, desc)


def parse_payload_schemas(text: str) -> dict[str, list[tuple[str, str, bool, str]]]:
    """从 D3-v2 §2（ContractReviewed）+ §2.1（其余核心/INBOUND）抽取字段级 schema。

    返回的键集合 = 本工件应渲染 properties 的核心事件集合（CORE_EVENTS 子集）。
    """
    schemas: dict[str, list[tuple[str, str, bool, str]]] = {}
    # §2 ContractReviewed（R3 唯一真源）
    if "**payload 字段（" in text:
        seg = text.split("**payload 字段（", 1)[1]
        started = False
        for ln in seg.splitlines():
            if re.match(r"^\|\s*字段\s*\|", ln):
                started = True
                continue
            if started:
                if re.match(r"^[\|\s:|-]+$", ln):
                    continue
                if not ln.strip().startswith("|"):
                    break
                m = FIELD_ROW.match(ln)
                if m:
                    schemas.setdefault("ContractReviewed", []).append(_field_tuple(m))
    # §2.1 各 ### Event 子表
    if "## 2.1" in text:
        s21 = text.split("## 2.1", 1)[1].split("\n## ", 1)[0]
        for h in re.finditer(r"^###\s+(\w+)", s21, re.M):
            ev = h.group(1)
            schemas.setdefault(ev, [])  # 标题必登记；无字段行则空壳，供门禁 8.6 捕获
            sub = re.split(r"\n### |\n---|\n## ", s21[h.end():], maxsplit=1)[0]
            started = False
            for ln in sub.splitlines():
                if re.match(r"^\|\s*字段\s*\|", ln):
                    started = True
                    continue
                if started:
                    if re.match(r"^[\|\s:|-]+$", ln):
                        continue
                    if not ln.strip().startswith("|"):
                        break
                    m = FIELD_ROW.match(ln)
                    if m:
                        schemas.setdefault(ev, []).append(_field_tuple(m))
    return schemas


def _render_property(field: str, typ: str, desc: str) -> list[str]:
    out = [f"          {field}:"]
    t = typ.strip()
    if t == "Money":
        out.append("            $ref: '#/components/schemas/Money'")
    elif t.startswith("enum("):
        vals = [v.strip() for v in t[5:-1].split("/") if v.strip()]
        out.append("            type: string")
        out.append("            enum: [" + ", ".join(f"'{v}'" for v in vals) + "]")
    elif t == "boolean":
        out.append("            type: boolean")
    elif t == "integer":
        out.append("            type: integer")
    elif t == "decimal":
        out.append("            type: number")
    elif t == "date-time":
        out.append("            type: string")
        out.append("            format: date-time")
    elif t == "date":
        out.append("            type: string")
        out.append("            format: date")
    else:
        out.append("            type: string")
    if desc:
        out.append(f"            description: {yaml_str(desc)}")
    return out


def render(rows: list[dict[str, str]], fields: dict[str, str],
           schemas: dict[str, list[tuple[str, str, bool, str]]] | None = None) -> str:
    schemas = schemas or {}
    lines: list[str] = [
        "asyncapi: 3.0.0",
        "info:",
        "  title: Procura 采购域集成事件契约（LLD 契约工件）",
        "  version: 0.3.0",
        "  description: |",
        "    Proposal 设计态（未冻结）。本文件由 contracts/generate_asyncapi.py 从总纲附录 B",
        "    事件契约注册表自动生成，禁止手改；事件规范 §4「先登记注册表 → 再写 Producer」。",
        "    payload 字段清单来自各卷 §7 速查视图；核心/INBOUND 事件字段级类型由 D3-v2 §2/§2.1 生成；",
        "    其余事件字段级类型在契约冻结时补齐（L35）。",
        "    producer owns schema（总纲 §16.2）；消费侧 tolerant reader（§16.5）+ Inbox 幂等（§6）。",
        "    envelope 为事件规范 §3 的 8 字段；partition_key 不在信封内，落 t_proc_outbox.partition_key。",
        "  x-status: Proposal",
        "  x-frozen: false",
        "  x-generated-from: detailed-design/00-总纲与统一约定.md 附录 B",
        "  x-gate: tools/check_procura_alignment.py A8（注册表 ↔ 工件零漂移）",
        "defaultContentType: application/json",
        "",
        "x-envelope:",
        "  description: 事件规范 §3 的 8 字段信封；eventId 为消费幂等键",
        "  fields: [eventId, eventType, topic, occurredAt, tenantId, traceId, schemaVersion, payload]",
        "  time-format: ISO-8601 UTC",
        "",
        "x-compatibility:",
        "  within-version: 仅加性可选字段 + 默认值",
        "  breaking: 删除/改名/改类型/改单位或含义/枚举扩容/新增必填字段",
        "  breaking-migration: 新 .v2 topic + 双发布 + 消费组指标确认 + 下线 v1（总纲 §16.6）",
        "  consumer: 忽略未知字段；未知枚举隔离告警，不得崩溃或静默压成已知值（§16.5）",
        "  delivery: at-least-once（Outbox 不提供 exactly-once）",
        "  ordering: 仅同一 partition_key 内成立；payload 带 aggregate_version 供乱序丢弃",
        "  retention: 待 L35 冻结；PII 只传 ID 与 hash（§20.7）",
        "  replay: mode=REBUILD（默认，抑制不可逆副作用） | REDELIVER（§16.7）",
        "  producer-guarantee: transactional outbox（业务表 + t_proc_outbox 同本地事务）",
        "  inbox: t_proc_processed_event（consumer_group + event_id 唯一；去重与副作用同事务）",
        "",
        "x-frozen-exceptions:",
        "  description: 总体 §7.1 已冻结 topic 的缩写 resource 段，登记为冻结例外，不得改名（§16.8）",
        "  abbreviations: [po, gr, rfx, asn, ir, capa, rtv, pr, recon]",
        "  verb-less-topics: [domain.procurement.risk_alert.v1]",
        "  verb-less-note: 领域 §6.2 冻结值缺 verb 段；登记为冻结例外，禁止按命名规范改名（§16.8；L37 已关闭）",
        "",
        "channels:",
    ]
    for r in rows:
        t, e = r["topic"], r["event"]
        lines += [
            f"  {t}:",
            f"    address: {t}",
            f"    title: {e}",
            "    messages:",
            f"      {e}:",
            f"        $ref: '#/components/messages/{e}'",
            f"    x-owner: {yaml_str(r['producer'])}",
            f"    x-consumers: {yaml_str(r['consumers'])}",
            f"    x-partition-key: {yaml_str(r['pkey'])}",
            f"    x-status: {r['status']}",
            f"    x-inbound: {'true' if r['inbound'] else 'false'}",
            f"    x-domain-segment-deviation: {'true' if r['deviation'] else 'false'}",
            f"    x-note: {yaml_str(r['note'])}",
            "",
        ]
    lines += ["operations:"]
    for r in rows:
        e = r["event"]
        lines += [
            f"  {'send' if not r['inbound'] else 'receive'}{e}:",
            f"    action: {'send' if not r['inbound'] else 'receive'}",
            f"    title: {e} ({r['topic']})",
            f"    channel:",
            f"      $ref: '#/channels/{r['topic']}'",
            "    messages:",
            f"      - $ref: '#/channels/{r['topic']}/messages/{e}'",
            "",
        ]
    lines += ["components:", "  messages:"]
    for r in rows:
        e = r["event"]
        lines += [
            f"    {e}:",
            f"      name: {e}",
            f"      title: {e}（topic 见总纲附录 B；status={r['status']}）",
            "      contentType: application/json",
            f"      x-schema-version: '1.0'",
            f"      x-owner: {yaml_str(r['producer'])}",
            "      x-delivery: at-least-once",
            f"      x-partition-key: {yaml_str(r['pkey'])}",
            "      x-producer-guarantee: transactional outbox (t_proc_outbox)",
            "      x-replay: REBUILD | REDELIVER（默认 REBUILD）",
            "      x-pii: ids and hashes only",
        ]
        payload_lines = [
            "      payload:",
            "        type: object",
            "        additionalProperties: true   # tolerant reader：生产方加性变更不得压垮消费者",
            f"        x-fields: {yaml_str(fields[e])}",
            "        properties:",
            "          aggregate_version:",
            "            type: integer",
            "            description: 聚合版本戳，供消费侧丢弃乱序或旧版本事件（总纲 §6 双轨）",
        ]
        required = ["aggregate_version"]
        for (field, typ, is_req, desc) in schemas.get(e, []):
            payload_lines += _render_property(field, typ, desc)
            if is_req:
                required.append(field)
        if len(required) > 1:
            payload_lines.append("        required: [" + ", ".join(required) + "]")
        payload_lines.append("")
        lines += payload_lines
    lines += [
        "  schemas:",
        "    Envelope:",
        "      type: object",
        "      required: [eventId, eventType, topic, occurredAt, tenantId, traceId, schemaVersion, payload]",
        "      properties:",
        "        eventId: { type: string, description: 消费幂等键 }",
        "        eventType: { type: string }",
        "        topic: { type: string }",
        "        occurredAt: { type: string, format: date-time }",
        "        tenantId: { type: string }",
        "        traceId: { type: string }",
        "        schemaVersion: { type: string, example: '1.0' }",
        "        payload: { type: object }",
        "    Money:",
        "      type: object",
        "      description: 金额与币种同行；amount 为字符串；ISO 4217 minor unit 决定小数位（总纲 §14）",
        "      required: [amount, currency]",
        "      properties:",
        "        amount: { type: string, pattern: '^-?[0-9]+(\\.[0-9]+)?$' }",
        "        currency: { type: string, pattern: '^[A-Z]{3}$' }",
        "        fx_rate: { type: string, nullable: true }",
        "        fx_as_of: { type: string, format: date-time, nullable: true }",
        "        fx_source: { type: string, nullable: true }",
        "",
        "x-platform-channels:",
        "  description: 平台 / 基础设施事件，不属采购域注册表（附录 B.3），仅列引用",
        "  topics: [platform.dead_letter.v1]",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="由总纲附录 B 生成 AsyncAPI 契约工件")
    ap.add_argument("--check", action="store_true", help="仅比对，不写文件；不一致返回 1")
    args = ap.parse_args()
    rows = parse_registry(CHARTER.read_text(encoding="utf-8"))
    fields = parse_fields(rows)
    schemas = parse_payload_schemas(D3V2.read_text(encoding="utf-8"))
    content = render(rows, fields, schemas)
    if args.check:
        current = OUT.read_text(encoding="utf-8") if OUT.is_file() else ""
        if current != content:
            print(f"[FAIL] asyncapi.yaml 与注册表不一致（注册表 {len(rows)} 条），请重跑 generate_asyncapi.py")
            return 1
        print(f"[PASS] asyncapi.yaml 与附录 B 注册表零漂移（{len(rows)} 条事件）")
        return 0
    OUT.write_text(content, encoding="utf-8")
    print(f"[OK] 已生成 {OUT.name}：{len(rows)} 条事件 / {len(content.splitlines())} 行")
    return 0


if __name__ == "__main__":
    sys.exit(main())
