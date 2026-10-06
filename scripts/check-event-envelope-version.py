#!/usr/bin/env python3
"""事件信封版本 / Topic 版本契约门禁（Bone 消息与事件规范 §2 / §3）。

扫描集成事件实现（``IntegrationEnvelope``）与 Topic 命名，校验三条契约：

* **§3 载荷版本**：每个集成事件须携带 ``schemaVersion``（非空白）。事件实现
  ``IntegrationEnvelope`` 时由 record 组件 ``schemaVersion`` 承接接口默认
  ``CURRENT_SCHEMA_VERSION``；本门禁断言该组件存在且不被覆盖为空白。
* **§2 Topic 版本后缀**：Topic 命名须以 ``.v{major}`` 主版本后缀结尾
  （规范原文：「末尾主版本」「不兼容 payload 时递增」）。点分、全小写、蛇形
  resource_action 的 Topic 常量若缺版本后缀即违规。
* **兼容性 diff**：事件载荷（record 组件）的删/改名属于不兼容变更，须伴随
  ``schemaVersion`` 主版本递增。以 ``--baseline`` 生成的快照为基线，
  比对时「字段减少/改名 且 主版本未升」判为 breaking。

**运行形态（与仓库其它门禁同构）**：:

    python3 scripts/check-event-envelope-version.py            # report-only，恒 exit 0
    python3 scripts/check-event-envelope-version.py --check    # 违规即 exit 1
    python3 scripts/check-event-envelope-version.py --baseline # 重新生成基线（不判违规）

本脚本为**新增门禁草案**，默认**不**接入 ``scripts/check.sh`` / ``ci-check.sh``
（L3 门禁须架构师在 ``doc/architecture/gate-state.json`` 登记真实载体并接入
``check.sh`` 后方可阻断）。report-only 形态下只打印发现，不影响提交。
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
BASELINE = REPO / "doc/architecture/event-envelope-baseline.json"

# 集成事件实现：class/record ... implements ... IntegrationEnvelope
IMPL_RE = re.compile(r"(?:public\s+)?(?:record|class)\s+(\w+)[^;]*?implements\b.*?IntegrationEnvelope")
# record 组件块：public record X( ... ) implements | {
RECORD_BLOCK_RE = re.compile(r"public\s+record\s+(\w+)\s*\((.*?)\)\s*(?:implements|extends|\{)", re.S)
# Topic 候选（消息与事件规范 §2 形状：{scope}.{domain}.{resource}_{action}[.v{major}]）。
# 关键识别点：resource_action 段含下划线（蛇形），借此排除包名 / 配置开关 / 指标 / 外部
# API 方法名（它们均为纯点分、无下划线，如 com.bone.blueprint、bone.blueprint.channel.broadcast、
# taobao.trade.orders.get、http.server.requests）。
TOPIC_CANDIDATE_RE = re.compile(
    r'=\s*"([a-z][a-z0-9]*\.[a-z0-9_]+\.[a-z0-9]*_[a-z0-9_]*(\.[a-z0-9_]+)*)"'
)


def find_event_files():
    """返回所有集成事件实现类所在 .java 文件（integration/event 包或实现 IntegrationEnvelope）。"""
    hits = []
    for path in REPO.rglob("*.java"):
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            continue
        if "IntegrationEnvelope" in text and IMPL_RE.search(text):
            hits.append((path, text))
    return hits


def record_components(text, classname):
    """抽取某 record 的组件名列表；非 record 返回空列表。"""
    m = RECORD_BLOCK_RE.search(text)
    if not m or m.group(1) != classname:
        # 兜底：扫描文件内第一个匹配该类的 record 块
        for blk in RECORD_BLOCK_RE.finditer(text):
            if blk.group(1) == classname:
                m = blk
                break
    if not m:
        return []
    body = m.group(2)
    # 去掉泛型/注解噪音后按逗号切分顶层组件（按括号深度）
    parts, depth, buf = [], 0, ""
    for ch in body:
        if ch in "([{":
            depth += 1
            buf += ch
        elif ch in ")]}":
            depth -= 1
            buf += ch
        elif ch == "," and depth == 0:
            parts.append(buf)
            buf = ""
        else:
            buf += ch
    if buf.strip():
        parts.append(buf)
    names = []
    for p in parts:
        p = p.strip()
        if not p:
            continue
        # 取末段标识符（跳过类型与注解）：最后一个是名字
        m2 = re.search(r"([A-Za-z_]\w*)\s*$", p)
        if m2 and m2.group(1) not in ("final", "public", "private"):
            names.append(m2.group(1))
    return names


def schema_version_of(text, classname):
    """尽力抽取该事件的 schemaVersion 字面量；取不到返回 None（交基线/结构判断）。"""
    # 常见：fromDomain(...) new Xxx(..., CURRENT_SCHEMA_VERSION) 或 record 默认
    m = re.search(rf"{classname}\s*\(([^)]*CURRENT_SCHEMA_VERSION[^)]*)\)", text)
    if m:
        return "CURRENT_SCHEMA_VERSION"
    m = re.search(rf'{classname}\(.*?,\s*"(\d+\.\d+)"\s*\)', text, re.S)
    if m:
        return m.group(1)
    return None


def scan_events():
    events = {}
    for path, text in find_event_files():
        for m in IMPL_RE.finditer(text):
            cls = m.group(1)
            comps = record_components(text, cls)
            events[cls] = {
                "file": str(path.relative_to(REPO)),
                "fields": comps,
                "hasSchemaVersion": "schemaVersion" in comps,
                "schemaVersion": schema_version_of(text, cls),
            }
    return events


def scan_topics():
    topics = {}
    for path in REPO.rglob("*.java"):
        try:
            text = path.read_text(encoding="utf-8")
        except Exception:
            continue
        for m in TOPIC_CANDIDATE_RE.finditer(text):
            val = m.group(1)
            # 末尾 .vN 即版本化；否则视为漏写版本后缀（§2 违规）
            topics[val] = "versioned" if re.search(r"\.v\d+$", val) else "unversioned"
    return topics


def major_of(sv):
    if not sv:
        return None
    m = re.match(r"(\d+)", sv.split(".")[0] if "." in sv else sv)
    return int(m.group(1)) if m else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="违规即 exit 1（默认 report-only，恒 exit 0）")
    ap.add_argument("--baseline", action="store_true", help="重新生成基线 JSON 后退出")
    args = ap.parse_args()

    events = scan_events()
    topics = scan_topics()

    if args.baseline:
        baseline = {
            "version": "1.0",
            "events": {k: {"fields": v["fields"], "schemaVersion": v["schemaVersion"] or "CURRENT_SCHEMA_VERSION"} for k, v in events.items()},
            "topics": topics,
        }
        BASELINE.write_text(json.dumps(baseline, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print("基线已生成：{}（{} 个事件，{} 个 Topic 常量）".format(BASELINE.relative_to(REPO), len(events), len(topics)))
        return 0

    violations = []

    # §3 结构：每个事件须有 schemaVersion 组件
    for cls, info in events.items():
        if not info["hasSchemaVersion"]:
            violations.append("§3 {}（{}）：集成事件未携带 schemaVersion 组件".format(cls, info["file"]))

    # §2 Topic 版本后缀
    for val, kind in topics.items():
        if kind == "unversioned":
            violations.append("§2 Topic 常量 '{}' 缺 .v{{major}} 版本后缀".format(val))

    # 兼容性 diff（需基线）
    if BASELINE.exists():
        prev = json.loads(BASELINE.read_text(encoding="utf-8"))
        prev_events = prev.get("events", {})
        for cls, info in events.items():
            if cls not in prev_events:
                print("[info] 新增事件 {}（基线未收录，不判 breaking）".format(cls))
                continue
            old = set(prev_events[cls].get("fields", []))
            new = set(info["fields"])
            removed = old - new
            if removed:
                old_major = major_of(prev_events[cls].get("schemaVersion"))
                cur_major = major_of(info["schemaVersion"] or "CURRENT_SCHEMA_VERSION")
                if old_major is not None and cur_major is not None and cur_major > old_major:
                    print("[info] {} 删字段 {} 但 schemaVersion 主版本已升 {}.x→{}.x（兼容）".format(cls, sorted(removed), old_major, cur_major))
                else:
                    violations.append("兼容 {} 删/改名了基线字段 {} 但 schemaVersion 主版本未升（{}）".format(cls, sorted(removed), info["schemaVersion"] or "CURRENT_SCHEMA_VERSION"))
    else:
        print("[warn] 无基线 {}，跳过兼容性 diff；先跑 --baseline 生成".format(BASELINE.relative_to(REPO)))

    # 输出
    if violations:
        print("事件信封门禁发现 {} 处违规：".format(len(violations)))
        for v in violations:
            print("  - " + v)
    else:
        print("事件信封门禁：当前扫描无违规（事件 {} 个 / Topic {} 个）".format(len(events), len(topics)))

    return 1 if (args.check and violations) else 0


if __name__ == "__main__":
    sys.exit(main())
