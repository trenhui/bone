#!/usr/bin/env python3
"""synapClaim 架构逻辑边界机检（只读，不修改任何文件）。

判据一：20 个子域到 7 上下文的归属完整、互斥，且 ``quota`` 只归 Funds（D4=A）。
判据二：Controller 的 ``ApiResponse`` / ``PageResult`` 泛型不得出现 domain **实体/聚合**
        （HC-003）。读模型 Projection、值对象 record 与 application 层输出对象不在判据内。

用法：``python3 scripts/check-synapclaim-architecture.py``；任一判据失败退出码 1。
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MAP_FILE = Path(__file__).resolve().parent / "synapclaim-domain-context-map.json"
APP = (
    REPO
    / "bone-apps"
    / "synap-claim"
    / "synap-claim-app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "bone"
    / "claim"
)
MODEL_DIR = APP / "domain" / "model"
CTRL_DIR = APP / "adapter" / "web" / "controller"
ENTITY_BASE = r"(?:TenantAggregateRoot|TenantAbstractEntity|AbstractEntity|AggregateRoot)"


def check_domain_context() -> list[str]:
    data = json.loads(MAP_FILE.read_text(encoding="utf-8"))
    contexts = data["contexts"]
    errors: list[str] = []
    owner: dict[str, str] = {}
    for ctx, spec in contexts.items():
        for sub in spec["subdomains"]:
            if sub in owner:
                errors.append(f"子域重复归属: {sub} 同时归 {owner[sub]} 与 {ctx}")
            owner[sub] = ctx

    if not MODEL_DIR.is_dir():
        return [f"领域模型目录不存在: {MODEL_DIR}"]
    actual = sorted(p.name for p in MODEL_DIR.iterdir() if p.is_dir())
    mapped = sorted(owner)
    if actual != mapped:
        errors.append(f"子域清单与磁盘不一致: 磁盘={actual} 映射={mapped}")
    if owner.get("quota") != "Funds":
        errors.append(f"quota 归属错误: 期望 Funds，实际 {owner.get('quota')}")
    funds = contexts.get("Funds", {})
    for sub in ("settlement", "reconciliation", "quota"):
        if sub not in funds.get("subdomains", []):
            errors.append(f"Funds 缺少子域 {sub}")
    return errors


def domain_entity_types() -> set[str]:
    """扫描 domain/model 下的实体/聚合类名；Projection、值对象、枚举不算。"""
    entities: set[str] = set()
    for path in MODEL_DIR.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if not re.search(rf"extends\s+{ENTITY_BASE}\b|@Table\s*\(", text):
            continue
        match = re.search(r"\b(?:class|record)\s+(\w+)", text)
        if match:
            entities.add(match.group(1))
    return entities


def check_response_contract() -> list[str]:
    if not CTRL_DIR.is_dir():
        return [f"Controller 目录不存在: {CTRL_DIR}"]
    entities = domain_entity_types()
    errors: list[str] = []
    for path in sorted(CTRL_DIR.glob("*Controller.java")):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"(?:ApiResponse|PageResult)\s*<([^;]*?)>\s+\w+\s*\(", text, re.S
        ):
            generic = match.group(1)
            for entity in entities:
                if re.search(rf"\b{re.escape(entity)}\b", generic):
                    errors.append(f"{path.name}: 响应信封直出领域实体 {entity}")
    return errors


def main() -> int:
    map_errors = check_domain_context()
    resp_errors = check_response_contract()
    print(f"[domain-context] 子域归属 + quota 归 Funds: {'PASS' if not map_errors else 'FAIL'}")
    for e in map_errors:
        print(f"  - {e}")
    print(f"[response-contract] Controller 无领域实体直出: {'PASS' if not resp_errors else 'FAIL'}")
    for e in resp_errors:
        print(f"  - {e}")
    all_errors = map_errors + resp_errors
    if all_errors:
        print(f"FAILED: {len(all_errors)} 项违规")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
