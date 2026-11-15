#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""豁免台账治理 —— 解决「例外沉淀成永久例外」。

问题（治理前实测）
------------------
全仓 24 份 baseline / allowlist / ledger 分散在 `doc/architecture/`、`config/`、
`scripts/` 等处，格式 3 种；只有 `freeze-ledger.yaml` 在注释里声明了到期机制，
且数据里没有一条 `expires`。后果是豁免一旦写进去就没有复审压力，门禁判据
被一点点泡松，而无人能回答「这条例外谁负责、什么时候复审」。

业界做法与本模块取舍
--------------------
- **统一 schema**：`registry/waivers.json` 是 24 份台账的唯一元数据台账
  （元数据，不是第 25 份判据清单，也不复制各台账内容）。
- **到期硬失败**：`status: active` 的条目必须有 `owner` / `reason` / ISO `expires`；
  过期即判红，临近到期告警。
- **不编造存量数据**：23 份历史台账标 `status: legacy`，允许 owner/expires 为空，
  但会持续出现在 `warnings` 的待认领清单里；转 active 时必须补齐字段。
- **不得绕过**：`scan()` 用 git 已知文件枚举所有台账类文件，任何**新增但未登记**
  的台账立即判红；登记了不存在的路径也判红。
- **只可收缩**：删掉某份台账时必须同步删登记，否则判红；无法悄悄新增例外。

零第三方依赖，`date.today()` 可注入便于测试。
"""

from __future__ import annotations

import json
import re
from datetime import date
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

from . import gate

SCHEMA = "bone.waivers.v1"
DEFAULT_PATH = "scripts/registry/waivers.json"
NAME_RE = re.compile(
    r"(baseline|allowlist|exemption|ledger|waiver|whitelist|known[-_]fail)", re.I
)
SUFFIXES = {".json", ".txt", ".yaml", ".yml"}
# 归档 change 目录名里带 baseline，但那是 Comet 产物，不是豁免台账。
EXCLUDED_PREFIXES = ("openspec/changes/archive/",)
STATUSES = ("active", "legacy")


def scan(root: Path) -> Set[str]:
    """枚举仓库内所有台账类文件（相对路径）。统一台账自身是元数据，不计入。"""
    return {
        rel for rel in gate.git_files(root)
        if rel != DEFAULT_PATH
        and NAME_RE.search(Path(rel).name)
        and Path(rel).suffix in SUFFIXES
        and not rel.startswith(EXCLUDED_PREFIXES)
    }


def load(path: Path) -> Dict[str, object]:
    if not path.is_file():
        raise gate.InfraError("豁免台账不存在: %s" % path)
    return json.loads(path.read_text(encoding="utf-8"))


def verify(
    root: Path,
    data: Dict[str, object],
    today: Optional[date] = None,
) -> Tuple[List[str], List[str]]:
    """返回 (problems, warnings)。problems 非空即判红。"""
    today = today or date.today()
    problems: List[str] = []
    warnings: List[str] = []
    if not isinstance(data, dict) or data.get("schema") != SCHEMA:
        return ["豁免台账 schema 不是 %s" % SCHEMA], warnings
    entries = data.get("entries")
    if not isinstance(entries, list) or not entries:
        return ["豁免台账 entries 为空或非法"], warnings
    policy = data.get("policy") if isinstance(data.get("policy"), dict) else {}
    warn_days = int(policy.get("warn_within_days", 14))

    scanned = scan(root)
    registered: Set[str] = set()
    for entry in entries:
        if not isinstance(entry, dict):
            problems.append("登记条目不是对象: %r" % entry)
            continue
        rel = str(entry.get("path", ""))
        if not rel:
            problems.append("登记条目缺 path")
            continue
        if rel in registered:
            problems.append("重复登记: %s" % rel)
            continue
        registered.add(rel)
        if not (root / rel).is_file():
            problems.append("登记的台账文件不存在: %s" % rel)
        if rel not in scanned and rel != DEFAULT_PATH:
            problems.append("登记的并非台账类文件（或已不在 git 已知文件内）: %s" % rel)

        status = entry.get("status")
        if status not in STATUSES:
            problems.append("status 必须是 active/legacy: %s (%r)" % (rel, status))
            continue
        if status == "legacy":
            warnings.append("存量台账待认领（owner/expires 未定）: %s" % rel)
            continue
        for field in ("owner", "reason", "expires"):
            value = entry.get(field)
            if not isinstance(value, str) or not value.strip():
                problems.append("active 条目缺 %s: %s" % (field, rel))
        raw = str(entry.get("expires") or "")
        if not raw:
            continue
        try:
            expires = date.fromisoformat(raw)
        except ValueError:
            problems.append("expires 不是 ISO 日期: %s (%r)" % (rel, raw))
            continue
        if expires < today:
            problems.append("豁免已过期（须 owner 续期或移除例外）: %s expires=%s" % (rel, raw))
        elif (expires - today).days <= warn_days:
            warnings.append("豁免临近到期: %s expires=%s" % (rel, raw))

    for rel in sorted(scanned - registered):
        problems.append("新增台账文件未登记（不得绕过豁免治理）: %s" % rel)
    return problems, warnings
