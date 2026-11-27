#!/usr/bin/env python3
"""ContractReviewed 事件闭环门禁（E-CHAIN）。

守护三件事：
  1. bone-init.sql 的 Outbox / Inbox / DLQ 三表 DDL（审计八列 + (tenant_id, review_id) 唯一键 + init-only）；
  2. 三类负向探针在测试源码中可达（重复投递只消费一次 / 跨租户拒绝留痕 / 失败入 DLQ 可重放）；
  3. ContractReviewedEventStore 有非内存持久化实现，且 Adapter 内确实调用条件插入 / DLQ / 重放。

--self-test 用注入式负向反证证明不是假绿，并配当前态正例对照。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DDL = ROOT / "bone-init.sql"
PROC = ROOT / "bone-apps" / "bone-procurement"
PREFIX = "E-CHAIN"

TABLES = ("t_proc_contract_outbox", "t_proc_contract_inbox", "t_proc_contract_dlq")
COLUMNS = ("id", "tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version")
# (源码文件片段, 探针方法名, 关键断言标记)
PROBES = (
    ("ContractReviewedEventChainTest.java", "duplicateDeliveryConsumedOnce", "ConsumeResult.DUPLICATE"),
    ("ContractReviewedEventChainTest.java", "crossTenantRejectedWithTrace", "CROSS_TENANT_REJECTED"),
    ("ContractReviewedEventChainTest.java", "failedConsumptionGoesToDlq", "findDlq"),
    ("ContractReviewedEventChainTest.java", "replaySucceedsAndIsIdempotent", "resolved"),
)
ADAPTER_SYMBOLS = ("claimInbox", "appendDlq", "resolveDlq", "markOutboxDelivered")
MODULES = (
    "bone-procurement-contract",
    "bone-procurement-ai-governance",
    "bone-procurement-sourcing",
    "bone-procurement-qualification",
    "bone-procurement-risk",
)


def read_texts() -> dict[str, str]:
    texts: dict[str, str] = {"ddl": DDL.read_text(encoding="utf-8") if DDL.exists() else ""}
    for module in MODULES:
        base = PROC / module / "src"
        if not base.exists():
            continue
        for path in base.rglob("*.java"):
            texts[str(path.relative_to(ROOT))] = path.read_text(encoding="utf-8")
    return texts


def table_block(ddl: str, table: str) -> str:
    m = re.search(
        r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?" + re.escape(table) + r"`?\s*\((.*?)\)\s*ENGINE=",
        ddl,
        re.S | re.I,
    )
    return m.group(1) if m else ""


def check(texts: dict[str, str]) -> tuple[bool, list[str]]:
    msgs: list[str] = []
    ddl = texts.get("ddl", "")
    for table in TABLES:
        block = table_block(ddl, table)
        if not block:
            msgs.append(f"{PREFIX} 缺少 init-only 表 {table}")
            continue
        for col in COLUMNS:
            if not re.search(r"`" + re.escape(col) + r"`", block):
                msgs.append(f"{PREFIX} {table} 缺少必备列 {col}")
        if not re.search(
            r"UNIQUE\s+KEY\s+`?uk_[^`\s]*`?\s*\([^)]*tenant_id[^)]*review_id", block, re.I
        ):
            msgs.append(f"{PREFIX} {table} 缺少 (tenant_id, review_id) 唯一键")
        pos = ddl.find(table)
        if pos < 0 or re.search(r"DROP\s+TABLE\s+IF\s+EXISTS", ddl[max(0, pos - 160):pos], re.I) is None:
            msgs.append(f"{PREFIX} {table} 未遵循 init-only DROP + CREATE 约定")

    for fragment, method, marker in PROBES:
        hits = [
            (rel, text)
            for rel, text in texts.items()
            if fragment in rel and method in text and marker in text
        ]
        if not hits:
            msgs.append(f"{PREFIX} 负向探针不可达：{method}（需含 {marker}）")

    impls = [
        text
        for rel, text in texts.items()
        if "InMemory" not in rel and "implements ContractReviewedEventStore" in text
    ]
    if not impls:
        msgs.append(f"{PREFIX} 端口 ContractReviewedEventStore 无持久化实现")
    elif not all(symbol in impls[0] for symbol in ADAPTER_SYMBOLS):
        missing = [s for s in ADAPTER_SYMBOLS if s not in impls[0]]
        msgs.append(f"{PREFIX} 持久化 Adapter 缺少事件闭环操作：{missing}")
    return (not msgs), msgs


def self_test(texts: dict[str, str]) -> int:
    cases: list[tuple[str, object, str]] = [
        ("删除 Outbox 表", lambda t: {**t, "ddl": re.sub(
            r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?t_proc_contract_outbox`?",
            "CREATE TABLE `t_proc_contract_removed`", t["ddl"], count=1, flags=re.I)}, "缺少 init-only 表"),
        ("抹掉审计列", lambda t: {**t, "ddl": t["ddl"].replace("`created_by`", "`removed_by`")}, "缺少必备列"),
        ("抹掉事件闭环唯一键", lambda t: {**t, "ddl": t["ddl"].replace(
            "UNIQUE KEY `uk_proc_contract_inbox` (`tenant_id`,`review_id`)",
            "KEY `idx_removed_inbox` (`review_id`)", 1)}, "唯一键"),
        ("倒回非 init-only 建表", lambda t: {**t, "ddl": re.sub(
            r"DROP\s+TABLE\s+IF\s+EXISTS\s+`?t_proc_contract_dlq`?;\s*", "", t["ddl"], count=1, flags=re.I)},
         "init-only"),
        ("删除重复投递负向探针", lambda t: {k: v.replace(
            "duplicateDeliveryConsumedOnce", "removedDuplicateProbe") for k, v in t.items()}, "负向探针不可达"),
        ("删除跨租户负向探针", lambda t: {k: v.replace(
            "crossTenantRejectedWithTrace", "removedCrossTenantProbe") for k, v in t.items()}, "负向探针不可达"),
        ("删除 DLQ 负向探针", lambda t: {k: v.replace(
            "failedConsumptionGoesToDlq", "removedDlqProbe") for k, v in t.items()}, "负向探针不可达"),
        ("删除重放负向探针", lambda t: {k: v.replace(
            "replaySucceedsAndIsIdempotent", "removedReplayProbe") for k, v in t.items()}, "负向探针不可达"),
        ("抹掉事件存储端口持久化实现", lambda t: {k: (v.replace(
            "implements ContractReviewedEventStore", "implements RemovedStore")
            if "ContractReviewedEventStore" in v else v) for k, v in t.items()}, "无持久化实现"),
        ("抹掉 DLQ 写入操作", lambda t: {k: (v.replace("appendDlq", "removedAppendDlq")
            if "implements ContractReviewedEventStore" in v else v) for k, v in t.items()}, "事件闭环操作"),
    ]
    ok = True
    for desc, mutate, expect in cases:
        passed, errs = check(mutate(texts))
        hit = not passed and any(expect in e for e in errs)
        print(f"[{'PASS' if hit else 'FAIL'}] 负向：{desc}")
        if not hit:
            print("       - 结果：" + str(errs[:3]))
        ok = ok and hit
    pos, errs = check(texts)
    print(f"[{'PASS' if pos else 'FAIL'}] 正例：当前实现满足事件闭环全部判据")
    if not pos:
        print("       - " + str(errs[:5]))
    print(f"[SELF-TEST] {'ALL PASS' if ok and pos else 'FAILED'}")
    return 0 if ok and pos else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()
    texts = read_texts()
    if args.self_test:
        return self_test(texts)
    passed, msgs = check(texts)
    for m in msgs:
        print(m)
    print(f"[{PREFIX}] {'PASS' if passed else 'FAIL'} — 3 表 / 必备列 / 唯一键 / 4 负向探针 / 端口持久化实现")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
