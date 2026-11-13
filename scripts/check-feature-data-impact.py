#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""后端功能变更 → 造数资产 / E2E 用例同步义务（提交前阻断判据）。

判据真源
--------
`scripts/registry/data-assets.json`（`assets` 造数能力清单 + `domains` 功能域同步义务
+ `acks` 显式例外 + `coverage` 派生段）。规范正文见 `scripts/e2e/README.md` §3.4。

为什么需要它
------------
`impact.py` 只管「Controller 端点 → e2e 用例」，DDL / Entity / 迁移 / 枚举变更后，
造数资产（scenarios / fixtures / wordlist / gen.py）很容易不同步：接口还能返回 200，
但演示与联调数据仍是旧结构，AI 下次造数又另写一套。本判据把「同时更新脚本」变成
提交前会红的机器判据，并在 hook 里自动刷新派生清单（`--write-map`）。

三条判据
--------
  C1 静态：登记的造数资产 / 场景 / E2E 用例真实存在，引用有效，acks 有 owner/reason/到期，
     coverage 派生段无漂移。
  C2 不变量：全部企业场景 `invariants.all_ok` 为真且 checks 非空（数据自相矛盾不许入库）。
  C3 同步义务：本次变更加到某域的**数据契约文件**（DDL / Entity / 迁移 / OpenAPI）时，
     同一次提交必须同步该域的造数资产或 E2E 用例；否则须登记带 owner/reason/expires 的 ack。
     纯接口/纯代码重构只输出提醒，不阻断。

退出码：0 合规 / 1 违规 / 2 运行错误（清单损坏、git 不可用 —— 不静默跳过）。
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import subprocess
import sys
from datetime import date
from pathlib import Path
from typing import Dict, List, Optional, Sequence, Tuple

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from lib import data_assets, gate  # noqa: E402

DEFAULT_REGISTRY = "scripts/registry/data-assets.json"
SCHEMA = "bone.feature-data-impact.v1"


class ImpactError(gate.InfraError):
    pass


def _git(root: Path, *args: str) -> List[str]:
    proc = subprocess.run(["git", *args], cwd=str(root), capture_output=True, text=True)
    if proc.returncode != 0:
        raise ImpactError("git %s 失败: %s" % (" ".join(args), proc.stderr.strip()[:300]))
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def changed_files(root: Path, mode: str, rev: Optional[str],
                  changed_file: Optional[Path]) -> List[str]:
    """变更文件路径（相对仓库根）。changed_file 供单测离线注入，避免依赖 git 状态。"""
    if changed_file is not None:
        return [line.strip() for line in changed_file.read_text(encoding="utf-8").splitlines()
                if line.strip()]
    if mode == "staged":
        return _git(root, "diff", "--cached", "--name-only", "--diff-filter=ACDMR")
    if mode == "rev":
        if not rev:
            raise ImpactError("--rev 需要范围参数，如 origin/main...HEAD")
        return _git(root, "diff", "--name-only", rev)
    merged = set(_git(root, "diff", "--name-only"))
    merged.update(_git(root, "diff", "--cached", "--name-only"))
    merged.update(_git(root, "ls-files", "--others", "--exclude-standard"))
    return sorted(merged)


def _matches(paths: Sequence[str], globs: Sequence[str]) -> List[str]:
    return sorted({p for p in paths for g in globs if fnmatch.fnmatch(p, g)})


def _active_ack(data: Dict[str, object], domain: str, today: date) -> bool:
    for ack in data.get("acks", []):  # type: ignore[union-attr]
        if not isinstance(ack, dict) or str(ack.get("domain")) != domain:
            continue
        if not str(ack.get("owner", "")).strip() or not str(ack.get("reason", "")).strip():
            continue
        try:
            if date.fromisoformat(str(ack.get("expires", ""))) >= today:
                return True
        except ValueError:
            continue
    return False


def evaluate(root: Path, data: Dict[str, object], changed: Sequence[str],
             today: Optional[date] = None) -> Tuple[List[dict], List[str]]:
    """返回 (按域的同步义务明细, 违规列表)。C1/C2 由调用方合并进违规列表。"""
    today = today or date.today()
    assets = data["assets"]
    domains = data["domains"]
    details: List[dict] = []
    violations: List[str] = []
    hits = []
    for domain, spec in domains.items():  # type: ignore[union-attr]
        assert isinstance(spec, dict)
        module_hits = _matches(changed, spec.get("module_globs", []))
        if module_hits:
            hits.append((domain, spec, module_hits))
    # 兜底域只在没有具体域命中时生效，否则每次理赔/采购改动都会多一条无意义提醒。
    specific = [h for h in hits if not h[1].get("fallback")]
    if specific:
        hits = specific
    for domain, spec, module_hits in hits:
        contract_hits = _matches(changed, spec.get("data_contract_globs", []))
        sync_paths = sorted(
            [str(assets[i]["path"]) for i in spec.get("assets", [])
             if isinstance(assets.get(i), dict)]  # type: ignore[operator,union-attr]
            + list(spec.get("cases", []))
        )
        synced = _matches(changed, sync_paths)
        required = bool(contract_hits) and bool(sync_paths)
        ack = _active_ack(data, domain, today) if required else False
        missing = required and not synced and not ack
        details.append({
            "domain": domain,
            "desc": spec.get("desc", ""),
            "module_hits": module_hits[:8],
            "data_contract_hits": contract_hits[:8],
            "scenario": spec.get("scenario"),
            "assets_to_sync": sync_paths,
            "synced_in_this_change": synced,
            "sync_required": required,
            "ack": ack,
            "ok": not missing,
        })
        if missing:
            violations.append(
                "C3 域 %s 的数据契约变更未同步造数/用例（%s）。本次须同步下列文件之一：%s；"
                "确属无需同步则在 registry/data-assets.json 的 acks 登记 domain/owner/reason/ISO expires。"
                % (domain, ", ".join(contract_hits[:3]), ", ".join(sync_paths[:6]))
            )
    return details, violations


def build_payload(root: Path, data: Dict[str, object], changed: Sequence[str],
                  mode: str, today: Optional[date] = None) -> Dict[str, object]:
    static_problems = data_assets.verify_static(root, data)
    _, invariant_problems = data_assets.scenario_report(root)
    details, sync_problems = evaluate(root, data, changed, today)
    problems = static_problems + invariant_problems + sync_problems
    return {
        "schema": SCHEMA,
        "mode": mode,
        "changed_files": list(changed),
        "domains": details,
        "static_problems": static_problems,
        "invariant_problems": invariant_problems,
        "sync_problems": sync_problems,
        "problems": problems,
        "ok": not problems,
    }


def _print_text(payload: Dict[str, object]) -> None:
    for d in payload["domains"]:  # type: ignore[union-attr]
        assert isinstance(d, dict)
        tag = "OK" if d["ok"] else "FAIL"
        print("[%s] 域 %s（%s）：变更 %d 个文件，数据契约 %d 个，同步义务 %s" % (
            tag, d["domain"], d["desc"], len(d["module_hits"]),
            len(d["data_contract_hits"]), "是" if d["sync_required"] else "仅提醒"))
        if d["sync_required"]:
            print("     本次已同步: %s" % (", ".join(d["synced_in_this_change"]) or "无"))
            print("     可同步资产: %s" % ", ".join(d["assets_to_sync"]))
    for label, key in (("C1 清单", "static_problems"),
                       ("C2 不变量", "invariant_problems"),
                       ("C3 同步义务", "sync_problems")):
        for problem in payload[key]:  # type: ignore[index]
            print("❌ %s: %s" % (label, problem))
    if payload["ok"]:
        print("✅ 造数资产同步：清单/不变量/变更义务全部合规（%d 个域命中）"
              % len(payload["domains"]))  # type: ignore[arg-type]


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="后端功能变更 → 造数资产/E2E 用例同步判据")
    parser.add_argument("--staged", action="store_true", help="只看 git 暂存区（pre-commit）")
    parser.add_argument("--rev", help="git diff 范围（pre-push，如 origin/main...HEAD）")
    parser.add_argument("--strict", action="store_true", help="有同步义务未履行则退出 1")
    parser.add_argument("--json", action="store_true", help="结构化输出（agent / CI 消费）")
    parser.add_argument("--write-map", action="store_true",
                        help="刷新 coverage 派生段（hook 自动调用；不改动手工登记段）")
    parser.add_argument("--registry", default=None, help="自定义清单路径（测试夹具用）")
    parser.add_argument("--root", default=None, help="仓库根（默认 git rev-parse）")
    parser.add_argument("--changed-file", default=None,
                        help="离线注入变更路径清单（单测用，每行一个相对路径）")
    args = parser.parse_args(argv)

    try:
        root = gate.repo_root(args.root)
        reg_path = Path(args.registry) if args.registry else root / DEFAULT_REGISTRY
        data = data_assets.load(reg_path)
        if args.write_map:
            data_assets.write_coverage(reg_path, data, root)
            data = data_assets.load(reg_path)
        mode = "staged" if args.staged else ("rev" if args.rev else "status")
        changed = changed_files(root, mode, args.rev,
                                Path(args.changed_file) if args.changed_file else None)
        payload = build_payload(root, data, changed, mode)
    except (gate.InfraError, ImpactError, OSError) as exc:
        payload = {"schema": SCHEMA, "ok": False, "error": str(exc), "problems": [str(exc)]}
        print(json.dumps(payload, ensure_ascii=False, indent=2) if args.json
              else "❌ 运行前提不成立: %s" % exc)
        return gate.EXIT_INFRA

    if args.json:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        _print_text(payload)
    if not payload["ok"] and (args.strict or payload.get("static_problems")
                              or payload.get("invariant_problems")):
        return gate.EXIT_VIOLATION
    return gate.EXIT_OK


if __name__ == "__main__":
    raise SystemExit(main())
