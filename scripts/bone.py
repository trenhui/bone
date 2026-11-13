#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""bone.py —— 工程脚本统一入口（agent / 本地 / CI 共用同一条命令）。

为什么要有它
------------
治理前至少 5 条命令才能跑完门禁：`check.sh`（59 步）、`ci-local.sh`（17 步）、
`ci.yml`（37 条显式）、`e2e/run.sh all`（只覆盖 11 个 Python）、再加 12 个顶层
`verify_*` 手工执行；输出全是纯文本，agent 无法判断「跑哪些才算跑完」。

本入口提供稳定契约，不新增第 4 套执行清单：

- **执行真源仍是 `check.sh` 的调用行**，`registry/checks.json` 是它的派生物，
  `bone.py sync --check` 双向对账，杜绝清单漂移。
- `check` 只是「按注册表调用 + 结构化汇总」，不改任何判据实现；
  `check.sh` 仍是 pre-commit 唯一入口，`ci.yml` 仍显式逐条标注 blocking。
- `list --format json` 是能力目录：agent 一条命令即可发现全部判据与外部门禁。
- 统一退出码 `0 合规 / 1 违规 / 2 运行错误`，统一 `text|json|junit` 输出。

常用命令
--------
    python3 scripts/bone.py doctor
    python3 scripts/bone.py list --format json
    python3 scripts/bone.py list --external --format json
    python3 scripts/bone.py sync --check
    python3 scripts/bone.py check --only check-orm-banned --format json
    python3 scripts/bone.py check --group paging --format junit
    python3 scripts/bone.py check --only check-orm-banned --dry-run --format json
    python3 scripts/bone.py selftest --only-contract
"""

from __future__ import annotations

import argparse
import fnmatch
import json
import os
import re
import shlex
import subprocess
import sys
import time
from pathlib import Path
from typing import Dict, List, Optional, Sequence

SCRIPTS = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS))

from lib import data_assets, gate, registry, waivers  # noqa: E402

SCHEMA_CHECK = "bone.check.v1"
SCHEMA_LIST = "bone.list.v1"
SCHEMA_EXTERNAL_RUN = "bone.external.run.v1"
SCHEMA_VERIFY = "bone.verify.v1"
SCHEMA_DATA = "bone.data.v1"
EXTERNAL_KNOWN_FAILURES = "scripts/registry/external-known-failures.txt"
CONTRACT_TESTS = (
    "test_gate_lib.py",
    "test_bone_registry.py",
    "test_scripts_catalog.py",
)
OPTIONAL_DEPS = ("yaml", "pymysql")


def _root(explicit: Optional[str]) -> Path:
    return gate.repo_root(explicit)


def _load_registry(root: Path, registry_path: Optional[str]) -> Dict[str, object]:
    path = Path(registry_path) if registry_path else root / registry.DEFAULT_REGISTRY
    data = registry.load(path)
    problems = registry.validate_checks(root, data)
    if problems:
        raise registry.RegistryError("注册表校验失败:\n  " + "\n  ".join(problems))
    return data


def _changed_files(root: Path) -> List[str]:
    args = ["git", "diff", "--name-only"]
    cached = ["git", "diff", "--cached", "--name-only"]
    out: List[str] = []
    for cmd in (cached, args):
        try:
            probe = subprocess.run(cmd, cwd=str(root), capture_output=True, text=True, check=True)
            out += [ln.strip() for ln in probe.stdout.splitlines() if ln.strip()]
        except (OSError, subprocess.CalledProcessError):
            pass
    return sorted(set(out))


def _select(
    checks: Sequence[Dict[str, object]],
    only: Optional[List[str]],
    groups: Optional[List[str]],
) -> List[Dict[str, object]]:
    ids = {x for token in (only or []) for x in token.split(",") if x}
    gs = {x for token in (groups or []) for x in token.split(",") if x}
    return [
        c for c in checks
        if (not ids or str(c["id"]) in ids) and (not gs or str(c["group"]) in gs)
    ]


def cmd_list(args: argparse.Namespace) -> int:
    root = _root(args.root)
    try:
        data = _load_registry(root, args.registry)
    except registry.RegistryError as exc:
        print(json.dumps({"schema": SCHEMA_LIST, "error": str(exc)}, ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA
    checks = _select(list(data["checks"]), args.only, args.group)  # type: ignore[arg-type]
    payload: Dict[str, object] = {
        "schema": SCHEMA_LIST,
        "root": str(root),
        "source": data["source"],
        "counts": {"listed": len(checks), "total": data["counts"]},  # type: ignore[index]
        "usage": "python3 scripts/bone.py check --only <id> --format json",
        "checks": [
            {
                "id": c["id"], "group": c["group"], "path": c["path"],
                "args": c["args"], "scope": c["scope"], "cmd": c["cmd"],
            }
            for c in checks
        ],
    }
    if args.external:
        try:
            entries = registry.load_external(root / registry.DEFAULT_EXTERNAL)
            payload["external"] = entries
            payload["external_count"] = len(entries)
        except registry.RegistryError as exc:
            print(json.dumps({"schema": SCHEMA_LIST, "error": str(exc)}, ensure_ascii=False, indent=2))
            return gate.EXIT_INFRA
    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("%-42s %-14s %s" % ("ID", "GROUP", "PATH"))
        for c in checks:
            print("%-42s %-14s %s" % (c["id"], c["group"], c["path"]))
        print("\n共 %d / %s 条；--format json 取机器可读目录" % (len(checks), data["counts"]))
        if args.external:
            print("库外登记 %d 条（见 registry/external.json）" % payload.get("external_count", 0))
    return gate.EXIT_OK


def _run_items(
    items: Sequence[Dict[str, object]],
    *,
    root: Path,
    env: Dict[str, str],
    changed: Sequence[str],
    run: bool,
    timeout: int,
    fail_fast: bool,
    output_format: str,
    junit_dir: Optional[str],
    schema: str,
    suite: str,
    source: str,
    report_only: bool = False,
    ratchet_baseline: Optional[Path] = None,
) -> int:
    """库内判据与库外判据共用的执行/汇总层：同一份结构化结果与退出码语义。"""
    started = time.monotonic()
    results: List[Dict[str, object]] = []
    for item in items:
        scope = list(item.get("scope") or [])
        skipped = bool(changed and scope and not any(
            fnmatch.fnmatch(f, pat) for f in changed for pat in scope
        ))
        if skipped:
            results.append({
                "id": item["id"], "group": item["group"], "path": item["path"],
                "cmd": item["cmd"], "status": "skip", "rc": 0,
                "duration_ms": 0, "stdout_tail": "", "stderr_tail": "",
                "reason": "scope 与变更文件无交集",
            })
            continue
        if not run:
            results.append({
                "id": item["id"], "group": item["group"], "path": item["path"],
                "cmd": item["cmd"], "status": "dry-run", "rc": 0,
                "duration_ms": 0, "stdout_tail": "", "stderr_tail": "",
            })
            continue
        cmd = list(item["cmd"])  # type: ignore[arg-type]
        if cmd and cmd[0] == "python3":
            cmd = [sys.executable] + cmd[1:]
        # 库外脚本可能用相对 cwd 读同目录资产（实测 check-contrast.py 直接
        # open("bone-ui-schema-designer.html")）。cwd 假设显式登记在 external.json，
        # 不再靠「从哪个目录手工启动」这种隐式约定。
        run_cwd = root / str(item["cwd"]) if item.get("cwd") else root
        # entry 里的脚本路径是相对仓库根的；切换 cwd 后必须绝对化，
        # 否则执行器会把它当成相对新 cwd（实测 check-contrast.py 因此找不到文件）。
        if item.get("cwd") and len(cmd) > 1:
            script = Path(cmd[1])
            if not script.is_absolute():
                cmd[1] = str((root / script).resolve())
        t0 = time.monotonic()
        try:
            proc = subprocess.run(
                cmd, cwd=str(run_cwd), env=env, capture_output=True, text=True,
                timeout=timeout,
            )
            rc, out, err = proc.returncode, proc.stdout, proc.stderr
            status = "pass" if rc == 0 else "fail" if rc == 1 else "error"
        except subprocess.TimeoutExpired as exc:
            rc, status = 124, "error"
            out = (exc.stdout or b"").decode("utf-8", "replace") if isinstance(exc.stdout, bytes) else (exc.stdout or "")
            err = "判据超时（%ss）" % timeout
        except OSError as exc:
            rc, status, out, err = 127, "error", "", str(exc)
        results.append({
            "id": item["id"], "group": item["group"], "path": item["path"],
            "cmd": item["cmd"], "cwd": str(item.get("cwd") or "."),
            "status": status, "rc": rc,
            "duration_ms": int((time.monotonic() - t0) * 1000),
            "stdout_tail": out[-2000:], "stderr_tail": err[-2000:],
        })
        if fail_fast and status in ("fail", "error"):
            break

    counts = {s: sum(1 for r in results if r["status"] == s)
              for s in ("pass", "fail", "error", "skip", "dry-run")}
    payload: Dict[str, object] = {
        "schema": schema,
        "root": str(root),
        "source": source,
        "changed_files": len(changed),
        "summary": {
            "total": len(items), **counts,
            "duration_ms": int((time.monotonic() - started) * 1000),
        },
        "checks": results,
    }
    if ratchet_baseline is not None:
        try:
            known = gate.load_baseline(ratchet_baseline)
            current_fail = [str(r["path"]) for r in results if r["status"] == "fail"]
            new_fail, stale = gate.ratchet(current_fail, known)
            payload["ratchet"] = {
                "baseline": str(ratchet_baseline),
                "new_failures": new_fail,
                "stale_entries": stale,
            }
        except gate.InfraError as exc:
            payload["ratchet"] = {"error": str(exc)}
    if output_format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    elif output_format == "junit":
        print(gate.render_junit(suite, results))
    else:
        for r in results:
            print("%-8s %-42s rc=%-4s %dms" % (r["status"], r["id"], r["rc"], r["duration_ms"]))
            if r["status"] in ("fail", "error"):
                print((str(r["stdout_tail"]) + "\n" + str(r["stderr_tail"])).strip()[:2000])
        s = payload["summary"]
        print("合计 %s 条: pass=%s fail=%s error=%s skip=%s dry-run=%s" % (
            s["total"], s["pass"], s["fail"], s["error"], s["skip"], s["dry-run"]))
    if junit_dir:
        dest = Path(junit_dir)
        dest.mkdir(parents=True, exist_ok=True)
        (dest / ("%s.xml" % suite)).write_text(
            gate.render_junit(suite, results), encoding="utf-8"
        )
    ratchet_info = payload.get("ratchet")
    if isinstance(ratchet_info, dict):
        if ratchet_info.get("error"):
            return gate.EXIT_INFRA
        # 新增欠债必须判红，即使处于 shadow 观察态；观察态只豁免基线里的既有欠债。
        if ratchet_info.get("new_failures"):
            return gate.EXIT_VIOLATION
    if counts["fail"] and not report_only:
        return gate.EXIT_VIOLATION
    if counts["error"]:
        return gate.EXIT_INFRA
    return gate.EXIT_OK


def cmd_check(args: argparse.Namespace) -> int:
    root = _root(args.root)
    try:
        data = _load_registry(root, args.registry)
    except registry.RegistryError as exc:
        print(json.dumps({"schema": SCHEMA_CHECK, "error": str(exc)}, ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA
    selected = _select(list(data["checks"]), args.only, args.group)  # type: ignore[arg-type]
    if not selected:
        print(json.dumps({"schema": SCHEMA_CHECK, "error": "--only/--group 没有匹配到任何判据"},
                         ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA

    changed = _changed_files(root) if args.changed else []
    env = os.environ.copy()
    if args.changed:
        env["BONE_CHANGED_FILES"] = "\n".join(changed)
    return _run_items(
        list(selected), root=root, env=env, changed=changed,
        run=not args.dry_run, timeout=args.timeout, fail_fast=args.fail_fast,
        output_format=args.format, junit_dir=args.junit_dir,
        schema=SCHEMA_CHECK, suite="bone.check", source=registry.DEFAULT_REGISTRY,
    )


def cmd_external(args: argparse.Namespace) -> int:
    """按 registry/external.json 调用库外判据，与库内判据共用结构化结果契约。

    默认 dry-run：库外脚本依赖 `__dirname` / `docs.manifest.json` / 同目录资产，
    物理搬迁会先破坏路径解析；这里让它们「不搬家也能被统一入口调用」，
    实跑必须显式 `--run`，避免 agent 误触发别人的文档机检。
    """
    root = _root(args.root)
    try:
        entries = registry.load_external(root / registry.DEFAULT_EXTERNAL)
    except registry.RegistryError as exc:
        print(json.dumps({"schema": SCHEMA_EXTERNAL_RUN, "error": str(exc)},
                         ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA
    invokable = [e for e in entries if e.get("invokable") is True]
    ids = {x for token in (args.only or []) for x in token.split(",") if x}
    selected = [
        e for e in invokable
        if not ids or Path(str(e["path"])).stem in ids or str(e["path"]) in ids
    ]
    if not selected:
        print(json.dumps({"schema": SCHEMA_EXTERNAL_RUN,
                          "error": "--only 没匹配到 invokable 的库外判据"},
                         ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA
    items: List[Dict[str, object]] = []
    for entry in selected:
        rel = str(entry["path"])
        items.append({
            "id": Path(rel).stem,
            "group": "external",
            "path": rel,
            "cmd": shlex.split(str(entry.get("entry", ""))),
            "cwd": entry.get("cwd"),
            "scope": [],
        })
    return _run_items(
        items, root=root, env=os.environ.copy(), changed=[],
        run=args.run, timeout=args.timeout, fail_fast=args.fail_fast,
        output_format=args.format, junit_dir=args.junit_dir,
        schema=SCHEMA_EXTERNAL_RUN, suite="bone.external",
        source=registry.DEFAULT_EXTERNAL, report_only=args.report_only,
        ratchet_baseline=(
            Path(args.ratchet_baseline) if args.ratchet_baseline
            else root / EXTERNAL_KNOWN_FAILURES if args.ratchet else None
        ),
    )


def cmd_waivers(args: argparse.Namespace) -> int:
    """统一豁免台账体检：过期/缺 owner/未登记台账一律判红，待认领只告警。"""
    root = _root(args.root)
    path = Path(args.waivers) if args.waivers else root / waivers.DEFAULT_PATH
    try:
        data = waivers.load(path)
    except gate.InfraError as exc:
        print(json.dumps({"schema": waivers.SCHEMA, "error": str(exc)},
                         ensure_ascii=False, indent=2))
        return gate.EXIT_INFRA
    problems, warnings = waivers.verify(root, data)
    entries = data["entries"] if isinstance(data.get("entries"), list) else []
    scanned = waivers.scan(root)
    summary = {
        "scanned": len(scanned),
        "registered": len(entries),
        "active": sum(1 for e in entries if isinstance(e, dict) and e.get("status") == "active"),
        "legacy": sum(1 for e in entries if isinstance(e, dict) and e.get("status") == "legacy"),
        "expired": sum(1 for p in problems if "已过期" in p),
        "expiring_soon": sum(1 for w in warnings if "临近到期" in w),
        "unregistered": sum(1 for p in problems if "未登记" in p),
        "unowned": sum(1 for w in warnings if "待认领" in w),
        "problems": len(problems),
    }
    payload: Dict[str, object] = {
        "schema": waivers.SCHEMA, "root": str(root), "registry": str(path),
        "summary": summary, "problems": problems, "warnings": warnings,
        "entries": entries,
    }
    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for problem in problems:
            print("FAIL  " + problem)
        for warning in warnings[:8]:
            print("WARN  " + warning)
        if len(warnings) > 8:
            print("WARN  ... 其余 %d 条见 --format json" % (len(warnings) - 8))
        print("\n豁免台账: 扫描 %d / 登记 %d / active %d / legacy %d / 过期 %d / 未登记 %d / 待认领 %d"
              % (summary["scanned"], summary["registered"], summary["active"],
                 summary["legacy"], summary["expired"], summary["unregistered"],
                 summary["unowned"]))
    return gate.EXIT_OK if not problems else gate.EXIT_VIOLATION


def cmd_sync(args: argparse.Namespace) -> int:
    root = _root(args.root)
    source = Path(args.source) if args.source else root / registry.DEFAULT_SOURCE
    reg_path = Path(args.registry) if args.registry else root / registry.DEFAULT_REGISTRY
    problems: List[str] = []
    try:
        # 形态校验必须先于生成/对账：调用行若带环境变量前缀或复杂参数，
        # 注册表解析会静默丢参，之后所有 diff 都只是「自洽地错」。
        shape = registry.verify_call_shapes(source)
        if args.check:
            problems = shape + registry.diff(source, reg_path)
        else:
            data = registry.build(source)
            problems = shape + registry.validate_checks(root, data)
            if not problems:
                registry.write(reg_path, data)
    except (registry.RegistryError, OSError) as exc:
        problems = [str(exc)]
    try:
        ext = registry.load_external(root / registry.DEFAULT_EXTERNAL)
        problems += registry.verify_external(
            root, ext, root / registry.DEFAULT_SOURCE, root / ".github/workflows/ci.yml"
        )
        # 已知欠债基线必须指向真实登记的判据，否则「豁免」会变成永久黑洞。
        known = {str(e["path"]) for e in ext}
        try:
            for rel in gate.load_baseline(root / EXTERNAL_KNOWN_FAILURES):
                if rel not in known:
                    problems.append("已知欠债基线条目不在 external.json: %s" % rel)
        except gate.InfraError as exc:
            problems.append(str(exc))
    except (registry.RegistryError, OSError) as exc:
        problems.append(str(exc))
    # 豁免台账治理并入同一道对账：过期/缺 owner/新台账未登记都在 sync --check 判红，
    # 避免又多一个「只在单独命令里才跑」的守卫。
    try:
        wdata = waivers.load(root / waivers.DEFAULT_PATH)
        wproblems, _ = waivers.verify(root, wdata)
        problems += wproblems
    except gate.InfraError as exc:
        problems.append(str(exc))
    print(json.dumps({
        "schema": "bone.sync.v1",
        "mode": "check" if args.check else "write",
        "source": str(source),
        "registry": str(reg_path),
        "ok": not problems,
        "summary": {"ok": not problems, "problems": len(problems)},
        "problems": problems,
    }, ensure_ascii=False, indent=2))
    return gate.EXIT_OK if not problems else gate.EXIT_VIOLATION


def _step_failures(out: str, rc: int, limit: int = 10) -> List[Dict[str, object]]:
    """从结构化步骤里抽出失败判据，避免 CI 只看到「某步 rc=1」却要重跑才能定位。

    `check` / `external` 的 JSON 输出是判据数组，整段 stdout 的尾部通常只是最后一个
    （往往通过的）判据，对定位毫无帮助。这里只取非 pass 项的 id / path / 输出。
    """
    if not rc:
        return []
    try:
        payload = json.loads(out)
    except (json.JSONDecodeError, TypeError):
        return []
    if not isinstance(payload, dict) or not isinstance(payload.get("checks"), list):
        return []
    failures: List[Dict[str, object]] = []
    for item in payload["checks"]:
        if not isinstance(item, dict) or item.get("status") == "pass":
            continue
        detail = "%s\n%s" % (item.get("stdout_tail") or "", item.get("stderr_tail") or "")
        failures.append({
            "id": item.get("id"), "path": item.get("path"),
            "status": item.get("status"), "rc": item.get("rc"),
            "detail": detail.strip()[:800],
        })
        if len(failures) >= limit:
            break
    return failures


def _step_summary(step: Dict[str, object], out: str, err: str,
                  rc: int) -> Dict[str, object]:
    """把一步的输出归一成健康度摘要。

    结构化步骤约定输出纯 JSON 且带 `summary`；退出 0 却没有可解析结果属于
    「跑通了但什么都没验」的假绿，由调用方升级为运行错误，不能兜底成通过。
    非结构化步骤（unittest discover）只从尾部取 Ran N tests 作为计数。
    """
    if step["json"]:
        try:
            payload = json.loads(out)
        except (json.JSONDecodeError, TypeError):
            return {"error": "结构化输出缺失或不可解析"}
        if not isinstance(payload, dict):
            return {"error": "结构化输出不是 JSON 对象"}
        given = payload.get("summary")
        if isinstance(given, dict):
            return given
        problems = payload.get("problems")
        return {
            "ok": payload.get("ok"),
            "problems": len(problems) if isinstance(problems, (list, tuple)) else 0,
            "derived": True,
        }
    match = re.search(r"Ran (\d+) tests?", (out + "\n" + err)[-4000:])
    return {
        "tests": int(match.group(1)) if match else None,
        "rc": rc, "derived": True,
    }


def cmd_verify(args: argparse.Namespace) -> int:
    """一条命令跑完全部自检，输出统一健康度 JSON。

    之前 agent / CI 要自己拼 6 条命令（sync、waivers、catalog、若干单文件测试、
    doctor、external shadow），既容易漏跑，结果格式也各不相同。这里把它们收敛为
    单一入口：各步仍调用既有实现（不复制判据），只做聚合与统一退出码。
    """
    root = _root(args.root)
    plan = [
        {"name": "sync", "argv": ["sync", "--check"], "self": True, "json": True},
        {"name": "waivers", "argv": ["waivers", "--format", "json"], "self": True, "json": True},
    ]
    if not args.quick:
        plan += [
            {"name": "checks", "argv": ["check", "--format", "json"], "self": True, "json": True},
            {"name": "external", "argv": [
                "external", "--run", "--report-only", "--ratchet", "--format", "json",
            ], "self": True, "json": True},
            {"name": "tests", "argv": [
                "-m", "unittest", "discover", "-s", "scripts/tests", "-p", "test_*.py",
            ], "self": False, "json": False},
        ]
    plan.append(
        {"name": "doctor", "argv": ["doctor", "--format", "json"], "self": True, "json": True}
    )

    started = time.monotonic()
    results: List[Dict[str, object]] = []
    for step in plan:
        base = [sys.executable]
        if step["self"]:
            base.append(str(SCRIPTS / "bone.py"))
        cmd = base + list(step["argv"])  # type: ignore[arg-type]
        t0 = time.monotonic()
        try:
            proc = subprocess.run(
                cmd, cwd=str(root), capture_output=True, text=True, timeout=args.timeout
            )
            rc, out, err = proc.returncode, proc.stdout, proc.stderr
        except subprocess.TimeoutExpired:
            rc, out, err = 124, "", "步骤超时（%ss）" % args.timeout
        summary = _step_summary(step, out, err, rc)
        if step["json"] and summary.get("error"):
            if rc == 0:
                rc = gate.EXIT_INFRA
            err = (err + "\n" + str(summary["error"])).strip()
        results.append({
            "name": step["name"], "rc": rc,
            "duration_ms": int((time.monotonic() - t0) * 1000),
            "summary": summary,
            "failures": _step_failures(out, rc),
            "stdout_tail": out[-600:] if rc else "",
            "stderr_tail": err[-400:] if rc else "",
        })
        if args.fail_fast and rc:
            break

    failed = [r for r in results if r["rc"] != 0]
    # shadow 观察态的失败不阻断，但必须显式出现在顶层健康度里：
    # 否则 agent 只看 failed=0 会把「已登记欠债」误读成全绿。
    observed_debt = 0
    for r in results:
        s = r["summary"]
        if r["rc"] == 0 and isinstance(s, dict) and isinstance(s.get("fail"), int):
            observed_debt += int(s["fail"])
    payload: Dict[str, object] = {
        "schema": SCHEMA_VERIFY,
        "root": str(root),
        "mode": "quick" if args.quick else "full",
        "summary": {
            "steps": len(results), "failed": len(failed),
            "observed_debt": observed_debt,
            "duration_ms": int((time.monotonic() - started) * 1000),
        },
        "steps": results,
    }
    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for r in results:
            print("%-10s rc=%-4s %6dms  %s" % (
                r["name"], r["rc"], r["duration_ms"],
                json.dumps(r["summary"], ensure_ascii=False) if r["summary"] else ""))
            if r["rc"]:
                print((str(r["stdout_tail"]) + "\n" + str(r["stderr_tail"])).strip()[:1000])
        print("\n%s 自检: %d 步 / 失败 %d 步 / 已登记欠债 %d 条" % (
            "快速" if args.quick else "全量", len(results), len(failed), observed_debt))
    if any(r["rc"] == 1 for r in failed):
        return gate.EXIT_VIOLATION
    if failed:
        return gate.EXIT_INFRA
    return gate.EXIT_OK


def cmd_selftest(args: argparse.Namespace) -> int:
    root = _root(args.root)
    sync = cmd_sync(argparse.Namespace(root=args.root, source=None, registry=None, check=True))
    if sync != 0:
        return sync
    if args.only_contract:
        commands = [[sys.executable, str(root / "scripts/tests" / name), "-v"]
                    for name in CONTRACT_TESTS]
    else:
        commands = [[sys.executable, "-m", "unittest", "discover",
                     "-s", "scripts/tests", "-p", "test_*.py", "--verbose"]]
    failed: List[str] = []
    for cmd in commands:
        proc = subprocess.run(cmd, cwd=str(root), capture_output=True, text=True)
        print("$ %s" % " ".join(cmd))
        print(proc.stdout[-4000:])
        if proc.stderr:
            print(proc.stderr[-2000:])
        if proc.returncode != 0:
            failed.append(" ".join(cmd))
    print(json.dumps({"schema": "bone.selftest.v1", "commands": len(commands),
                      "failed": failed}, ensure_ascii=False, indent=2))
    return gate.EXIT_OK if not failed else gate.EXIT_VIOLATION


def cmd_doctor(args: argparse.Namespace) -> int:
    root = _root(args.root)
    items: List[Dict[str, object]] = []

    def add(name: str, ok: bool, detail: str) -> None:
        items.append({"name": name, "status": "ok" if ok else "error", "detail": detail})

    add("python >= 3.8", sys.version_info >= (3, 8), sys.version.split()[0])
    try:
        probe = subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=str(root),
                              capture_output=True, text=True, check=True)
        add("git 仓库", True, probe.stdout.strip())
    except (OSError, subprocess.CalledProcessError) as exc:
        add("git 仓库", False, str(exc))
    add("check.sh 存在", (root / "scripts/check.sh").is_file(), str(root / "scripts/check.sh"))
    try:
        data = _load_registry(root, args.registry)
        add("注册表", True, "%s 条判据" % data["counts"]["checks"])  # type: ignore[index]
    except registry.RegistryError as exc:
        add("注册表", False, str(exc))
    try:
        ext = registry.load_external(root / registry.DEFAULT_EXTERNAL)
        problems = registry.verify_external(
            root, ext, root / registry.DEFAULT_SOURCE, root / ".github/workflows/ci.yml"
        )
        add("库外登记", not problems, "%d 条；%s" % (len(ext), "; ".join(problems)))
    except (registry.RegistryError, OSError) as exc:
        add("库外登记", False, str(exc))
    for dep in OPTIONAL_DEPS:
        probe = subprocess.run([sys.executable, "-c", "import %s" % dep],
                               capture_output=True, text=True)
        add("依赖 %s" % dep, probe.returncode == 0,
            "已安装" if probe.returncode == 0 else "缺失：pip install -r scripts/requirements-dev.txt")
    bad = [i for i in items if i["status"] != "ok"]
    payload: Dict[str, object] = {
        "schema": "bone.doctor.v1", "root": str(root),
        "ok": not bad, "failed": len(bad),
        "summary": {"items": len(items), "failed": len(bad)},
        "items": items,
    }
    if args.format == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        for i in items:
            print("%-6s %-24s %s" % (i["status"].upper(), i["name"], i["detail"]))
        print("\n结论: %s" % ("环境就绪" if not bad else "%d 项不就绪" % len(bad)))
    return gate.EXIT_OK if not bad else gate.EXIT_INFRA


def cmd_data(args: argparse.Namespace) -> int:
    """造数能力统一入口：发现(list) / 体检(validate) / 取数(fixture|scenario)。

    只做编排：静态清单读 `registry/data-assets.json`，场景与原子值一律委托
    `scripts/e2e/datagen.py` 与 `e2e_lib/scenarios.py`，不复制第二套造数逻辑，
    也不在这里另写场景清单（场景名由 datagen list --json 动态给出）。
    """
    root = _root(args.root)
    reg_path = Path(args.registry) if args.registry else root / data_assets.DEFAULT_PATH
    fmt = getattr(args, "format", "json")

    if args.data_cmd in ("fixture", "scenario"):
        cmd = [sys.executable, str(root / "scripts" / "e2e" / "datagen.py")]
        if args.data_cmd == "fixture":
            cmd += ["fixture", args.kind, "--count", str(args.count)]
        else:
            cmd += [args.name]
        if args.seed:
            cmd += ["--seed", args.seed]
        cmd += ["--format", fmt if fmt in ("json", "env", "md") else "json"]
        return subprocess.run(cmd, cwd=str(root)).returncode

    if args.data_cmd == "validate":
        proc = subprocess.run(
            [sys.executable, str(root / "scripts" / "check-feature-data-impact.py"),
             "--staged", "--strict", "--json"],
            cwd=str(root), capture_output=True, text=True,
        )
        try:
            child = json.loads(proc.stdout)
        except json.JSONDecodeError:
            child = {"ok": False, "error": "判据输出不可解析", "raw": proc.stdout[-500:]}
        payload: Dict[str, object] = {
            "schema": SCHEMA_DATA, "mode": "validate", "rc": proc.returncode,
            "report": child,
        }
        if fmt == "json":
            print(json.dumps(payload, ensure_ascii=False, indent=2))
        else:
            print("造数资产体检: %s（判据 rc=%s）" % (
                "合规" if proc.returncode == 0 else "不合规", proc.returncode))
            for problem in child.get("problems", []):  # type: ignore[union-attr]
                print("  ❌ %s" % problem)
        return proc.returncode

    try:
        data = data_assets.load(reg_path)
        dynamic = data_assets.datagen_list(root)
        invariants, inv_problems = data_assets.scenario_report(root)
        static_problems = data_assets.verify_static(root, data)
    except gate.InfraError as exc:
        print("❌ 造数能力清单不可用: %s" % exc, file=sys.stderr)
        return gate.EXIT_INFRA
    problems = static_problems + inv_problems
    payload = {
        "schema": SCHEMA_DATA, "mode": "list", "registry": str(reg_path),
        "policy": data["policy"], "assets": data["assets"], "domains": data["domains"],
        "scenarios": dynamic.get("scenarios", []), "fixtures": dynamic.get("fixtures", []),
        "invariants": invariants, "coverage": data["coverage"],
        "health": {"ok": not problems, "problems": problems},
    }
    if fmt == "json":
        print(json.dumps(payload, ensure_ascii=False, indent=2))
    else:
        print("造数唯一入口: %s" % data["policy"]["entry"])  # type: ignore[index]
        print("企业场景（同 seed 同数据，均带 invariants）：")
        for item in dynamic.get("scenarios", []):
            print("  %-14s %s" % (item["name"], item["desc"]))
        print("原子值: %s" % ", ".join(dynamic.get("fixtures", [])))
        print("资产 %d 项 / 功能域 %d 个 / 清单体检: %s" % (
            len(data["assets"]), len(data["domains"]),  # type: ignore[arg-type]
            "合规" if not problems else "%d 项问题" % len(problems)))
        for problem in problems:
            print("  ❌ %s" % problem)
    return gate.EXIT_OK


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="bone.py",
        description="Bone 工程脚本统一入口（能力目录 / 门禁调用 / 注册表对账 / 环境体检）",
    )
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--root", default=None, help="仓库根（默认 git rev-parse 解析）")
    sub = parser.add_subparsers(dest="command", required=True)

    p_list = sub.add_parser("list", parents=[common], help="输出能力目录")
    p_list.add_argument("--format", choices=("json", "text"), default="json")
    p_list.add_argument("--only", action="append", help="按 id 过滤（可重复/逗号分隔）")
    p_list.add_argument("--group", action="append", help="按判据族过滤（可重复/逗号分隔）")
    p_list.add_argument("--external", action="store_true", help="附带库外门禁登记")
    p_list.add_argument("--registry", default=None)

    p_check = sub.add_parser("check", parents=[common], help="按注册表调用判据并结构化汇总")
    p_check.add_argument("--format", choices=("json", "text", "junit"), default="text")
    p_check.add_argument("--only", action="append", help="只跑指定 id（可重复/逗号分隔）")
    p_check.add_argument("--group", action="append", help="只跑指定判据族")
    p_check.add_argument("--changed", action="store_true", help="传入变更文件并按 scope 跳过无关判据")
    p_check.add_argument("--dry-run", action="store_true", help="只列出将执行的命令")
    p_check.add_argument("--fail-fast", action="store_true", help="首个失败即停止")
    p_check.add_argument("--timeout", type=int, default=300, help="单判据超时秒数（默认 300）")
    p_check.add_argument("--junit-dir", default=None, help="额外写出 JUnit XML")
    p_check.add_argument("--registry", default=None)

    p_ext = sub.add_parser(
        "external", parents=[common],
        help="按 external.json 调用库外判据（默认 dry-run，--run 才实跑）",
    )
    p_ext.add_argument("--format", choices=("json", "text", "junit"), default="json")
    p_ext.add_argument("--only", action="append", help="按脚本名或路径过滤（可重复/逗号分隔）")
    p_ext.add_argument("--run", action="store_true", help="真正执行（默认只列出命令）")
    p_ext.add_argument(
        "--report-only", action="store_true",
        help="shadow 观察态：违规如实记录但不阻断退出码（运行错误仍阻断）",
    )
    p_ext.add_argument(
        "--ratchet", action="store_true",
        help="与已知欠债基线对比：新增失败判红，已修复条目提示收缩",
    )
    p_ext.add_argument(
        "--ratchet-baseline", default=None,
        help="自定义基线路径（负向变异测试用；默认 registry/external-known-failures.txt）",
    )
    p_ext.add_argument("--fail-fast", action="store_true", help="首个失败即停止")
    p_ext.add_argument("--timeout", type=int, default=300, help="单判据超时秒数（默认 300）")
    p_ext.add_argument("--junit-dir", default=None, help="额外写出 JUnit XML")

    p_w = sub.add_parser(
        "waivers", parents=[common],
        help="统一豁免台账：到期硬失败 / owner 必填 / 新台账必须登记",
    )
    p_w.add_argument("--format", choices=("json", "text"), default="json")
    p_w.add_argument("--waivers", default=None, help="自定义台账路径（负向变异测试用）")

    p_verify = sub.add_parser(
        "verify", parents=[common],
        help="一条命令跑完全部自检（sync / waivers / 59 判据 / 库外 shadow / 单测 / doctor）",
    )
    p_verify.add_argument("--format", choices=("json", "text"), default="json")
    p_verify.add_argument("--quick", action="store_true",
                          help="只跑 sync/waivers/doctor，跳过 59 判据、库外实跑与全量单测")
    p_verify.add_argument("--fail-fast", action="store_true", help="首个失败步骤即停止")
    p_verify.add_argument("--timeout", type=int, default=600, help="单步超时秒数（默认 600）")

    p_sync = sub.add_parser("sync", parents=[common], help="从 check.sh 生成 / 对账注册表")
    p_sync.add_argument("--check", action="store_true", help="只比对，不写文件（供门禁使用）")
    p_sync.add_argument("--source", default=None, help="调用真源（测试夹具用）")
    p_sync.add_argument("--registry", default=None)

    p_test = sub.add_parser("selftest", parents=[common], help="跑门禁自身单测与契约测试")
    p_test.add_argument("--only-contract", action="store_true", help="只跑共享库/注册表/编目契约测试")

    p_doc = sub.add_parser("doctor", parents=[common], help="环境与依赖体检（缺依赖 exit 2）")
    p_doc.add_argument("--format", choices=("json", "text"), default="text")
    p_doc.add_argument("--registry", default=None)

    p_data = sub.add_parser(
        "data", parents=[common],
        help="造数能力统一入口（AI 造数前先 data list，禁止另写一套）",
    )
    p_data.add_argument("--registry", default=None, help="自定义能力清单（测试夹具用）")
    dsub = p_data.add_subparsers(dest="data_cmd", required=True)
    for name, help_text in (
        ("list", "列出造数资产 / 企业场景 / 原子值 / 不变量 / 健康度"),
        ("validate", "体检造数清单与功能变更同步义务（含场景不变量）"),
        ("fixture", "取原子值（person/company/sku/amount/doc-no/tax-no/equipment）"),
        ("scenario", "取企业场景数据（场景名见 data list）"),
    ):
        dp = dsub.add_parser(name, help=help_text)
        dp.add_argument("--format", choices=("json", "text", "env", "md"), default="json")
        dp.add_argument("--seed", default=None, help="固定 seed ⇒ 同数据可复跑")
        if name == "fixture":
            dp.add_argument("kind")
            dp.add_argument("--count", type=int, default=1)
        if name == "scenario":
            dp.add_argument("name")
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(list(argv) if argv is not None else sys.argv[1:])
    handlers = {
        "list": cmd_list, "check": cmd_check, "external": cmd_external,
        "waivers": cmd_waivers, "verify": cmd_verify, "sync": cmd_sync,
        "selftest": cmd_selftest, "doctor": cmd_doctor, "data": cmd_data,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
