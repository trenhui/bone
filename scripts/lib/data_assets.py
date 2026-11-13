#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""造数能力清单的共享实现（`bone.py data` 与功能变更判据共用，不复制逻辑）。

真源只有 `scripts/registry/data-assets.json`：登记造数资产、功能域 → 同步义务、acks。
动态能力（场景名 / fixtures）不写死在清单里，一律问 `e2e_lib/scenarios.py` 与
`datagen.py list --json`，避免「清单说有、代码没有」的第二套漂移。
"""

from __future__ import annotations

import json
import subprocess
import sys
from datetime import date
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from . import gate

DEFAULT_PATH = "scripts/registry/data-assets.json"
SCENARIO_SEED = "closure-check"


class DataAssetsError(gate.InfraError):
    """清单损坏 / 造数库不可用（运行错误，不是业务违规）。"""


def load(path: Path) -> Dict[str, object]:
    if not path.is_file():
        raise DataAssetsError("造数能力清单不存在: %s" % path)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise DataAssetsError("造数能力清单不可解析: %s: %s" % (path, exc)) from exc
    if not isinstance(data, dict) or data.get("schema") != "bone.data-assets.v1":
        raise DataAssetsError("造数能力清单 schema 不是 bone.data-assets.v1: %s" % path)
    for key in ("assets", "domains", "acks", "coverage"):
        if key not in data:
            raise DataAssetsError("造数能力清单缺 %s 段: %s" % (key, path))
    if not isinstance(data["assets"], dict) or not isinstance(data["domains"], dict):
        raise DataAssetsError("assets / domains 必须是对象: %s" % path)
    return data


def _scenarios(root: Path):
    """按需把 e2e 目录挂进 sys.path，导入场景库（不复制任何造数逻辑）。"""
    e2e = root / "scripts" / "e2e"
    if str(e2e) not in sys.path:
        sys.path.insert(0, str(e2e))
    try:
        from e2e_lib import scenarios  # noqa: E402
    except ImportError as exc:
        raise DataAssetsError("e2e_lib/scenarios.py 不可用: %s" % exc) from exc
    return scenarios


def scenario_report(root: Path, seed: str = SCENARIO_SEED) -> Tuple[Dict[str, dict], List[str]]:
    """实跑全部场景的不变量；空 checks 视为「没有契约」，必须判红。"""
    scenarios = _scenarios(root)
    report: Dict[str, dict] = {}
    problems: List[str] = []
    for name in scenarios.names():
        try:
            data = scenarios.build(name, seed)
        except Exception as exc:  # noqa: BLE001 - 造数库异常要如实报出，不能当通过
            problems.append("场景 %s 构建失败: %s" % (name, exc))
            continue
        inv = data.get("invariants")
        if not isinstance(inv, dict) or not isinstance(inv.get("checks"), list):
            problems.append("场景 %s 缺 invariants.checks（无契约的数据不算企业真实场景）" % name)
            continue
        checks = inv["checks"]
        report[name] = {
            "all_ok": bool(inv.get("all_ok")),
            "checks": [str(c.get("name", "")) for c in checks if isinstance(c, dict)],
        }
        if not checks:
            problems.append("场景 %s 的 invariants.checks 为空" % name)
        if not inv.get("all_ok"):
            problems.append(
                "场景 %s 不变量不成立: %s" % (
                    name,
                    ", ".join(str(c.get("name")) for c in checks
                              if isinstance(c, dict) and not c.get("ok")),
                )
            )
    return report, problems


def compute_coverage(root: Path, data: Dict[str, object]) -> Dict[str, object]:
    """派生段：每个域的场景 / 资产 / 用例是否真实在位（供 hook 自动刷新）。"""
    scenarios = _scenarios(root)
    available = set(scenarios.names())
    inv_report, _ = scenario_report(root)
    assets = data["assets"]
    coverage: Dict[str, object] = {}
    for domain, spec in data["domains"].items():  # type: ignore[union-attr]
        assert isinstance(spec, dict)
        scenario = spec.get("scenario")
        pending: List[str] = []
        if scenario and scenario not in available:
            pending.append("scenarios.py 缺场景 %s" % scenario)
        if scenario and not inv_report.get(str(scenario), {}).get("all_ok"):
            pending.append("场景 %s 不变量未全绿" % scenario)
        asset_rows = []
        for asset_id in spec.get("assets", []):
            entry = assets.get(asset_id) if isinstance(assets, dict) else None  # type: ignore[union-attr]
            if not isinstance(entry, dict):
                pending.append("未登记的资产 id %s" % asset_id)
                continue
            exists = (root / str(entry["path"])).is_file()
            if not exists:
                pending.append("造数资产缺失 %s" % entry["path"])
            asset_rows.append({"id": asset_id, "exists": exists})
        case_rows = []
        for case in spec.get("cases", []):
            exists = (root / str(case)).is_file()
            if not exists:
                pending.append("E2E 用例缺失 %s" % case)
            case_rows.append({"path": case, "exists": exists})
        coverage[domain] = {
            "scenario": scenario,
            "scenario_exists": bool(scenario) and scenario in available,
            "invariants_all_ok": bool(inv_report.get(str(scenario), {}).get("all_ok")),
            "invariant_checks": inv_report.get(str(scenario), {}).get("checks", []),
            "assets": asset_rows,
            "cases": case_rows,
            "pending": sorted(pending),
        }
    return coverage


def write_coverage(path: Path, data: Dict[str, object], root: Path) -> Dict[str, object]:
    data = dict(data)
    data["coverage"] = compute_coverage(root, data)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return data["coverage"]


def verify_acks(data: Dict[str, object], today: Optional[date] = None) -> List[str]:
    today = today or date.today()
    problems: List[str] = []
    for idx, ack in enumerate(data.get("acks", [])):  # type: ignore[union-attr]
        if not isinstance(ack, dict):
            problems.append("acks[%d] 必须是对象" % idx)
            continue
        for field in ("domain", "owner", "reason", "expires"):
            if not str(ack.get(field, "")).strip():
                problems.append("acks[%d] 缺 %s（例外必须有人负责、有理由、有到期）" % (idx, field))
        raw = str(ack.get("expires", ""))
        try:
            expires = date.fromisoformat(raw)
        except ValueError:
            problems.append("acks[%d].expires 不是 ISO 日期: %s" % (idx, raw))
            continue
        if expires < today:
            problems.append("acks[%d] 已过期（%s），须重审或删除" % (idx, raw))
        if str(ack.get("domain")) not in data.get("domains", {}):  # type: ignore[union-attr]
            problems.append("acks[%d].domain 未登记: %s" % (idx, ack.get("domain")))
    return problems


def verify_static(root: Path, data: Dict[str, object]) -> List[str]:
    """静态清单自检：路径真实、引用有效、场景存在、派生段无漂移、acks 有效。"""
    problems: List[str] = []
    assets = data["assets"]
    available = set(_scenarios(root).names())
    for asset_id, entry in assets.items():  # type: ignore[union-attr]
        if not isinstance(entry, dict) or not entry.get("path") or not entry.get("role"):
            problems.append("assets.%s 缺 path / role" % asset_id)
            continue
        if not (root / str(entry["path"])).is_file():
            problems.append("造数资产 %s 不存在: %s" % (asset_id, entry["path"]))
    for domain, spec in data["domains"].items():  # type: ignore[union-attr]
        if not isinstance(spec, dict) or not spec.get("module_globs"):
            problems.append("domains.%s 缺 module_globs" % domain)
            continue
        for asset_id in spec.get("assets", []):
            if asset_id not in assets:  # type: ignore[operator]
                problems.append("domains.%s 引用了未登记资产 %s" % (domain, asset_id))
        scenario = spec.get("scenario")
        if scenario and scenario not in available:
            problems.append("domains.%s 的场景 %s 不在 scenarios.py" % (domain, scenario))
        if not spec.get("data_contract_globs") and spec.get("scenario"):
            problems.append("domains.%s 声明了场景却没有 data_contract_globs（同步义务无法触发）" % domain)
    problems += verify_acks(data)
    if data.get("coverage") != compute_coverage(root, data):
        problems.append("coverage 派生段已漂移，跑 python3 scripts/check-feature-data-impact.py --write-map")
    return problems


def datagen_list(root: Path) -> Dict[str, object]:
    """动态能力目录直接问唯一入口 datagen.py，不在本文件复制场景清单。"""
    proc = subprocess.run(
        [sys.executable, str(root / "scripts" / "e2e" / "datagen.py"), "list", "--json"],
        cwd=str(root), capture_output=True, text=True,
    )
    if proc.returncode != 0:
        raise DataAssetsError("datagen.py list 失败(rc=%s): %s" % (proc.returncode, proc.stderr[-500:]))
    try:
        return json.loads(proc.stdout)
    except json.JSONDecodeError as exc:
        raise DataAssetsError("datagen.py list 输出不是 JSON: %s" % exc) from exc
