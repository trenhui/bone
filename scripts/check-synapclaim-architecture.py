#!/usr/bin/env python3
"""synapClaim 架构逻辑边界机检（只读，不修改任何文件）。

判据一：20 个子域到 7 上下文的归属完整、互斥，且 ``quota`` 只归 Funds（D4=A）。
判据二：Controller 的 ``ApiResponse`` / ``PageResult`` 泛型不得出现 domain **实体/聚合**
        （HC-003）。读模型 Projection、值对象 record 与 application 层输出对象不在判据内。

用法：
    python3 scripts/check-synapclaim-architecture.py                # 主判据
    python3 scripts/check-synapclaim-architecture.py --self-test    # 负向探针自证

--self-test 在临时目录重建最小模块树并逐个注入违规，断言每个负向探针都判红；
判据恒绿（把违规当通过）是这类门禁最常见的失效形态，故必须与正向同批自证，
并由活入口每次提交强制运行（口径同 tools/check_all.py 的双行登记）。
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
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


def check_domain_context(
    map_file: Path = MAP_FILE, model_dir: Path = MODEL_DIR
) -> list[str]:
    data = json.loads(map_file.read_text(encoding="utf-8"))
    contexts = data["contexts"]
    errors: list[str] = []
    owner: dict[str, str] = {}
    for ctx, spec in contexts.items():
        for sub in spec["subdomains"]:
            if sub in owner:
                errors.append(f"子域重复归属: {sub} 同时归 {owner[sub]} 与 {ctx}")
            owner[sub] = ctx

    if not model_dir.is_dir():
        return [f"领域模型目录不存在: {model_dir}"]
    actual = sorted(p.name for p in model_dir.iterdir() if p.is_dir())
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


def domain_entity_types(model_dir: Path = MODEL_DIR) -> set[str]:
    """扫描 domain/model 下的实体/聚合类名；Projection、值对象、枚举不算。"""
    entities: set[str] = set()
    for path in model_dir.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if not re.search(rf"extends\s+{ENTITY_BASE}\b|@Table\s*\(", text):
            continue
        match = re.search(r"\b(?:class|record)\s+(\w+)", text)
        if match:
            entities.add(match.group(1))
    return entities


def check_response_contract(
    ctrl_dir: Path = CTRL_DIR, model_dir: Path = MODEL_DIR
) -> list[str]:
    if not ctrl_dir.is_dir():
        return [f"Controller 目录不存在: {ctrl_dir}"]
    entities = domain_entity_types(model_dir)
    errors: list[str] = []
    for path in sorted(ctrl_dir.glob("*Controller.java")):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"(?:ApiResponse|PageResult)\s*<([^;]*?)>\s+\w+\s*\(", text, re.S
        ):
            generic = match.group(1)
            for entity in entities:
                if re.search(rf"\b{re.escape(entity)}\b", generic):
                    errors.append(f"{path.name}: 响应信封直出领域实体 {entity}")
    return errors


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


def self_test() -> int:
    """正例基线 + 4 个负向探针：证明判据在跑，不是恒绿。"""
    failures: list[str] = []
    with tempfile.TemporaryDirectory(prefix="synapclaim-arch-selftest-") as tmp:
        root = Path(tmp)
        map_file = root / "synapclaim-domain-context-map.json"
        model = root / "domain" / "model"
        ctrl = root / "adapter" / "web" / "controller"

        def write_baseline() -> None:
            doc = {
                "contexts": {
                    "Funds": {"subdomains": ["settlement", "reconciliation", "quota"]},
                    "Claim": {"subdomains": ["claim"]},
                    "Config": {"subdomains": ["config"]},
                }
            }
            _write(map_file, json.dumps(doc, ensure_ascii=False))
            for sub in ("settlement", "reconciliation", "quota", "claim", "config"):
                (model / sub).mkdir(parents=True, exist_ok=True)
            ctrl.mkdir(parents=True, exist_ok=True)
            _write(
                ctrl / "FooController.java",
                "public class FooController {\n"
                "  public ApiResponse<FooResp> list() { return null; }\n"
                "}\n",
            )

        def expect_red(label: str, errors: list[str]) -> None:
            if errors:
                print(f"  PASS 负向探针 {label}: {errors[0]}")
            else:
                failures.append(f"{label} 未判红（判据可能恒绿）")

        # go 塞：合规基线必须判绿，否则「什么都拦」与「什么都不拦」同样不可用
        write_baseline()
        baseline = check_domain_context(map_file, model) + check_response_contract(
            ctrl, model
        )
        if baseline:
            failures.append(f"正向基线意外判红: {baseline}")
        else:
            print("  PASS 正向基线：子域归属 + HC-003 均合规")

        # 探针 1：D4 核心裁决——quota 错挂 Config
        doc = json.loads(map_file.read_text(encoding="utf-8"))
        doc["contexts"]["Funds"]["subdomains"].remove("quota")
        doc["contexts"]["Config"]["subdomains"].append("quota")
        _write(map_file, json.dumps(doc, ensure_ascii=False))
        expect_red("quota 错挂 Config", check_domain_context(map_file, model))
        write_baseline()

        # 探针 2：同一子域重复归属（映射互斥性）
        doc = json.loads(map_file.read_text(encoding="utf-8"))
        doc["contexts"]["Claim"]["subdomains"].append("quota")
        _write(map_file, json.dumps(doc, ensure_ascii=False))
        expect_red("子域重复归属", check_domain_context(map_file, model))
        write_baseline()

        # 探针 3：映射声明与磁盘子域不一致（防止只校验 JSON 自洽）
        shutil.rmtree(model / "claim")
        expect_red("映射与磁盘不一致", check_domain_context(map_file, model))
        write_baseline()

        # 探针 4：HC-003 Controller 响应信封直出领域实体
        _write(
            model / "claim" / "ClaimCase.java",
            "public class ClaimCase extends TenantAbstractEntity {}\n",
        )
        _write(
            ctrl / "FooController.java",
            "public class FooController {\n"
            "  public ApiResponse<ClaimCase> get() { return null; }\n"
            "}\n",
        )
        expect_red("响应信封直出领域实体", check_response_contract(ctrl, model))

    if failures:
        for f in failures:
            print(f"  FAIL {f}", file=sys.stderr)
        return 1
    print("  self-test: 4/4 负向探针判红，基线全绿")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="synapClaim 架构逻辑边界机检")
    parser.add_argument("--self-test", action="store_true", help="负向探针自证")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

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
