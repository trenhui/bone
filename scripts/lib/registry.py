#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""门禁注册表 —— 由 `scripts/check.sh` 的调用行机械生成，不手写第二份清单。

设计要点（为什么这样而不是手写 YAML 清单）
------------------------------------------
治理前门禁清单有 6 份副本（check.sh 的 [N/59]、ci-check.sh 的 [N/17]、ci.yml
显式逐条、gate-state.json「当前 37 步」、README 表格、各文档里的历史编号），
彼此漂移，甚至要再写一道「守护三份一致」的判据。

这里的取舍是：**执行真源仍是 check.sh 的调用行**，注册表是它的派生物；
`bone.py sync --check` 做双向对账，一旦有人加了判据没接线、或接线改了没同步，
CI / pre-commit 立刻判红。这样不会出现第 7 份需要人肉维护的清单。

库外门禁（doc/、tools/ 下的同类判据）不物理搬迁，登记在
`registry/external.json`：保留原生命周期，但纳入能力目录与接线校验。
"""

from __future__ import annotations

import json
import re
import shlex
from pathlib import Path
from typing import Dict, Iterable, List, Optional, Sequence, Set, Tuple

from . import gate

SCHEMA_CHECKS = "bone.registry.v1"
SCHEMA_EXTERNAL = "bone.external.v1"
DEFAULT_SOURCE = "scripts/check.sh"
DEFAULT_REGISTRY = "scripts/registry/checks.json"
DEFAULT_EXTERNAL = "scripts/registry/external.json"

# 只认 scripts/ 下的 check-*.py 调用行；echo / 注释里的文本不算接线。
CALL_RE = re.compile(r"python3\s+(scripts/[A-Za-z0-9._/-]+\.py)([^;&|>]*)")
CHECK_NAME_RE = re.compile(r"check-[A-Za-z0-9-]+\.py$")

DISPOSITIONS = ("register", "move_candidate", "consolidate_candidate", "deprecate")
CALLERS = ("scripts/check.sh", ".github/workflows/ci.yml", "manual", "other")


class RegistryError(gate.InfraError):
    """注册表损坏 / 缺失 —— 属于运行错误（EXIT_INFRA），不是业务违规。"""


def _calls(source: Path) -> List[Tuple[str, List[str]]]:
    """解析出 (脚本路径, 参数列表)，排除注释行与 echo 文本。"""
    found: List[Tuple[str, List[str]]] = []
    for raw in source.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or re.match(r"^echo\b", line):
            continue
        for match in CALL_RE.finditer(line):
            path, tail = match.group(1), match.group(2).strip()
            if not CHECK_NAME_RE.search(path):
                continue
            try:
                args = shlex.split(tail) if tail else []
            except ValueError:
                args = []
            found.append((path, args))
    return found


def _group(check_id: str) -> str:
    parts = check_id.split("-")
    return parts[1] if len(parts) > 2 else "root"


def build(source: Path) -> Dict[str, object]:
    """从 check.sh 生成注册表数据。同一脚本多次调用（如 --self-test）合并为一个条目。"""
    if not source.is_file():
        raise RegistryError("门禁调用真源不存在: %s" % source)
    ordered: List[str] = []
    variants: Dict[str, List[List[str]]] = {}
    for path, args in _calls(source):
        if path not in variants:
            ordered.append(path)
            variants[path] = []
        if args not in variants[path]:
            variants[path].append(args)

    checks: List[Dict[str, object]] = []
    for path in ordered:
        vv = variants[path]
        check_id = Path(path).stem
        main = next((a for a in vv if "--self-test" not in a), [])
        self_tests = [a for a in vv if "--self-test" in a]
        checks.append(
            {
                "id": check_id,
                "path": path,
                "group": _group(check_id),
                "args": main,
                "self_tests": self_tests,
                # 空 scope = 静态全树判据，--changed 时不得跳过（无法安全归因）。
                "scope": [],
                "cmd": ["python3", path] + main,
            }
        )
    groups = sorted({str(c["group"]) for c in checks})
    return {
        "schema": SCHEMA_CHECKS,
        "source": DEFAULT_SOURCE,
        "counts": {"checks": len(checks), "groups": len(groups)},
        "groups": groups,
        "checks": checks,
    }


def write(path: Path, data: Dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def load(path: Path) -> Dict[str, object]:
    if not path.is_file():
        raise RegistryError("注册表不存在，请先运行: python3 scripts/bone.py sync（%s）" % path)
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("schema") != SCHEMA_CHECKS:
        raise RegistryError("注册表 schema 不是 %s: %s" % (SCHEMA_CHECKS, path))
    if not isinstance(data.get("checks"), list):
        raise RegistryError("注册表缺少 checks 列表: %s" % path)
    return data


def diff(source: Path, registry: Path) -> List[str]:
    """返回注册表与调用真源的差异描述；空列表表示已同步。"""
    expected = build(source)
    try:
        actual = load(registry)
    except RegistryError as exc:
        return [str(exc)]
    exp_checks = {c["id"]: c for c in expected["checks"]}  # type: ignore[index]
    act_checks = {c["id"]: c for c in actual["checks"]}  # type: ignore[index]
    problems: List[str] = []
    for cid in sorted(set(exp_checks) - set(act_checks)):
        problems.append("check.sh 已调用但注册表缺失: %s" % cid)
    for cid in sorted(set(act_checks) - set(exp_checks)):
        problems.append("注册表残留已删接线: %s" % cid)
    for cid in sorted(set(exp_checks) & set(act_checks)):
        if exp_checks[cid] != act_checks[cid]:
            problems.append("参数/分组漂移: %s" % cid)
    if expected["counts"] != actual.get("counts"):
        problems.append("counts 漂移: 期望 %s / 实际 %s" % (expected["counts"], actual.get("counts")))
    if expected["groups"] != actual.get("groups"):
        problems.append("groups 漂移")
    return problems


def load_external(path: Path) -> List[Dict[str, object]]:
    if not path.is_file():
        raise RegistryError("库外门禁登记表不存在: %s" % path)
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("schema") != SCHEMA_EXTERNAL:
        raise RegistryError("库外登记表 schema 不是 %s: %s" % (SCHEMA_EXTERNAL, path))
    entries = data.get("entries")
    if not isinstance(entries, list):
        raise RegistryError("库外登记表缺少 entries 列表")
    return entries


def verify_external(
    root: Path,
    entries: Sequence[Dict[str, object]],
    check_source: Path,
    ci_source: Path,
) -> List[str]:
    """库外登记的机械对账：路径存在、处置合法、声明的调用者真的在调用。"""
    problems: List[str] = []
    check_text = check_source.read_text(encoding="utf-8") if check_source.is_file() else ""
    ci_text = ci_source.read_text(encoding="utf-8") if ci_source.is_file() else ""
    seen: Set[str] = set()
    for entry in entries:
        rel = str(entry.get("path", ""))
        if not rel:
            problems.append("库外登记条目缺 path")
            continue
        if rel in seen:
            problems.append("库外登记重复: %s" % rel)
        seen.add(rel)
        if not (root / rel).is_file():
            problems.append("库外登记的路径不存在: %s" % rel)
        if entry.get("disposition") not in DISPOSITIONS:
            problems.append("库外登记 disposition 非法: %s (%s)" % (rel, entry.get("disposition")))
        invokable = entry.get("invokable")
        if not isinstance(invokable, bool):
            problems.append("库外登记 invokable 必须是 bool: %s" % rel)
        elif invokable:
            try:
                parts = shlex.split(str(entry.get("entry", "")))
            except ValueError as exc:
                problems.append("库外登记 entry 无法解析: %s (%s)" % (rel, exc))
                parts = []
            if not parts or parts[0] not in ("python3", "node"):
                problems.append(
                    "invokable 条目的 entry 必须是 python3/node 命令（当前 %r）: %s"
                    % (parts[:1], rel)
                )
            elif len(parts) < 2 or not parts[1].endswith(Path(rel).name):
                problems.append("invokable 条目的 entry 与 path 不一致: %s" % rel)
        cwd = entry.get("cwd")
        if cwd is not None:
            if (not isinstance(cwd, str) or not cwd or Path(cwd).is_absolute()
                    or ".." in Path(cwd).parts):
                problems.append("cwd 必须是仓库根下的相对路径: %s (%r)" % (rel, cwd))
            elif not (root / cwd).is_dir():
                problems.append("cwd 指向的目录不存在: %s (%s)" % (rel, cwd))
        if entry.get("disposition") in ("move_candidate", "consolidate_candidate"):
            blocker = entry.get("migration_blocker")
            if not isinstance(blocker, str) or not blocker.strip():
                problems.append("建议归位/合并的条目必须写清 migration_blocker: %s" % rel)
        callers = entry.get("callers", [])
        if not isinstance(callers, list):
            problems.append("库外登记 callers 必须是数组: %s" % rel)
            continue
        bad = [c for c in callers if c not in CALLERS]
        if bad:
            problems.append("库外登记 caller 非法: %s (%s)" % (rel, bad))
        if "scripts/check.sh" in callers and rel not in check_text:
            problems.append("声明由 check.sh 调用，但调用行里找不到: %s" % rel)
        if ".github/workflows/ci.yml" in callers and rel not in ci_text:
            problems.append("声明由 ci.yml 调用，但 ci.yml 里找不到: %s" % rel)
    return problems


def validate_checks(root: Path, data: Dict[str, object]) -> List[str]:
    """注册表自身完整性：id 唯一、脚本存在、cmd 形状合法。"""
    problems: List[str] = []
    seen: Set[str] = set()
    for item in data["checks"]:  # type: ignore[index]
        cid = str(item.get("id", ""))
        rel = str(item.get("path", ""))
        if not cid or cid in seen:
            problems.append("id 为空或重复: %s" % cid)
        seen.add(cid)
        if not (root / rel).is_file():
            problems.append("注册表指向不存在的脚本: %s" % rel)
        cmd = item.get("cmd")
        if not isinstance(cmd, list) or not cmd or cmd[0] != "python3":
            problems.append("cmd 形状非法: %s" % cid)
    return problems


def source_paths(source: Path) -> Set[str]:
    """真源中所有 python3 调用的脚本路径（供库外对账使用）。"""
    return {p for p, _ in _calls(source)}


ALLOWED_CALL_PREFIX = re.compile(r"(?:if\s+!|else|then|do|&&|\|\|)")
COMPLEX_ARG_RE = re.compile(r"--[A-Za-z0-9-]+=['\"]|`|\$\(|\btee\b|\bxargs\b|\$\{")


def verify_call_shapes(source: Path) -> List[str]:
    """守住「注册表解析得对」的前提：调用行必须是简单形态。

    `_calls()` 按 `python3 <path> [args]` 提取参数，遇到环境变量前缀
    （`BONE_X=1 python3 ...`）、引号内含分号的参数、命令替换、管道，
    会静默丢参或截断 —— 判据于是以默认语义运行（例如 report-only 返 0），
    全量跑仍然「绿」，这是最难发现的假绿。这里把前提变成机械判据。
    """
    if not source.is_file():
        return ["调用真源不存在: %s" % source]
    problems: List[str] = []
    for lineno, raw in enumerate(source.read_text(encoding="utf-8").splitlines(), 1):
        stripped = raw.strip()
        if not stripped or stripped.startswith("#") or re.match(r"^echo\b", stripped):
            continue
        match = re.search(
            r"python3\s+(scripts/[A-Za-z0-9._/-]*check-[A-Za-z0-9-]+\.py)", raw
        )
        if not match:
            continue
        prefix = raw[: match.start()].strip()
        if prefix and not ALLOWED_CALL_PREFIX.fullmatch(prefix):
            problems.append(
                "L%d 调用行前缀未登记（环境变量/重定向会让注册表静默丢参）: %r"
                % (lineno, prefix)
            )
        tail = re.split(r";|&&|\|\||>", raw[match.end():], maxsplit=1)[0]
        if COMPLEX_ARG_RE.search(tail):
            problems.append(
                "L%d 调用行含复杂参数（引号值/命令替换/管道，注册表会截断）: %r"
                % (lineno, tail.strip())
            )
    return problems
