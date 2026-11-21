#!/usr/bin/env python3
"""procuraAI 生产化加固门禁（A10.29）。

守护 init-only DDL 的 6 张新表、必备列/唯一约束、内存实现不再注册为 bean，
以及 9 个端口都有非内存实现。--self-test 用注入式负向反证证明不是假绿。
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DDL = ROOT / "bone-init.sql"
PROC = ROOT / "bone-apps" / "bone-procurement"
PREFIX = "A10.29"

TABLES = (
    "t_proc_contract_review",
    "t_proc_review_trace",
    "t_proc_ai_capability",
    "t_proc_human_decision",
    "t_proc_global_kill_switch",
    "t_proc_governance_trace",
)
COLUMNS = ("id", "tenant_id", "created_by", "updated_by", "created_at", "updated_at", "deleted", "version")
PORTS = (
    "ContractReviewStore",
    "ReviewTracePort",
    "CapabilityRegistry",
    "HumanDecisionLedger",
    "GlobalKillSwitchStore",
    "GovernanceTracePort",
    "SourcingProjectRepository",
    "QualificationRepository",
    "RiskAlertRepository",
)
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
        base = PROC / module / "src" / "main" / "java"
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
        if re.search(r"DROP\s+TABLE\s+IF\s+EXISTS", ddl[ddl.find(table) - 200 : ddl.find(table)], re.I) is None:
            msgs.append(f"{PREFIX} {table} 未遵循 init-only DROP + CREATE 约定")
    review = table_block(ddl, "t_proc_contract_review")
    if review:
        if "`idempotency_key`" not in review:
            msgs.append(f"{PREFIX} 审查表缺少 idempotency_key 列")
        if not (
            re.search(r"UNIQUE\s+KEY\s+`?uk_[^`\s]*`?\s*\([^)]*tenant_id[^)]*review_id", review, re.I)
            and re.search(r"UNIQUE\s+KEY\s+`?uk_[^`\s]*`?\s*\([^)]*tenant_id[^)]*idempotency_key", review, re.I)
        ):
            msgs.append(f"{PREFIX} 审查表缺少 (tenant_id, review_id) 与 (tenant_id, idempotency_key) 双唯一键")
    for rel, text in texts.items():
        if "InMemory" in rel and re.search(r"^\s*@(?:Repository|Component|Service|Bean)\b", text, re.M):
            msgs.append(f"{PREFIX} 内存实现仍注册为 bean：{rel}")
    for port in PORTS:
        impls = [
            rel
            for rel, text in texts.items()
            if "InMemory" not in rel and re.search(
                r"implements\s+[^\n{;]*\b" + re.escape(port) + r"\b", text
            )
        ]
        if not impls:
            msgs.append(f"{PREFIX} 端口 {port} 无持久化实现")
    return (not msgs), msgs


def self_test(texts: dict[str, str]) -> int:
    cases: list[tuple[str, object, str]] = [
        ("删除一张新表", lambda t: {**t, "ddl": re.sub(
            r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?t_proc_global_kill_switch`?",
            "CREATE TABLE `t_proc_removed`", t["ddl"], count=1, flags=re.I)}, "缺少 init-only 表"),
        ("删除 idempotency_key 列", lambda t: {**t, "ddl": t["ddl"].replace("`idempotency_key`", "`removed_key`")}, "缺少 idempotency_key"),
        ("恢复内存实现 bean 注册", lambda t: {**t, next(k for k in t if "InMemory" in k): "@Repository\n" + t[next(k for k in t if "InMemory" in k)]}, "内存实现仍注册为 bean"),
        ("用 @Service 绕过 bean 检测", lambda t: {**t, next(k for k in t if "InMemory" in k): "@Service\n" + t[next(k for k in t if "InMemory" in k)]}, "内存实现仍注册为 bean"),
        ("用 @Bean 绕过 bean 检测", lambda t: {**t, next(k for k in t if "InMemory" in k): "@Bean\n" + t[next(k for k in t if "InMemory" in k)]}, "内存实现仍注册为 bean"),
        ("抹掉审查端口持久化实现", lambda t: {k: (v.replace("implements ContractReviewStore", "implements RemovedStore") if "ContractReviewStore" in v else v) for k, v in t.items()}, "无持久化实现"),
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
    print(f"[{'PASS' if pos else 'FAIL'}] 正例：当前实现满足全部加固判据")
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
    print(f"[{PREFIX}] {'PASS' if passed else 'FAIL'} — 6 表 / 双唯一键 / 9 端口持久化实现")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
