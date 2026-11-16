#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""gatekit 扩展层 —— 只补共享框架未覆盖的「调用层」能力，不是第二套框架。

单一框架真源
------------
`scripts/lib/gatekit/`（Report / gate / repo_root / 退出码 / --self-test /
--report-only / --root）是全仓判据的共享框架，由同批治理落地并入库。
**新判据一律 `from gatekit import gate, Report, repo_root`，不要基于本模块另起炉灶。**

本模块只负责 gatekit 没有、而统一入口 `scripts/bone.py` 与注册表需要的横切工具：

- `git_files` / `iter_files`：git 已知文件枚举（用 `git ls-files`，不走进 node_modules）；
- `load_baseline` / `ratchet`：存量豁免与「只可收缩」棘轮；
- `Finding` / `CheckResult` / `render_junit`：结构化 finding 与 JUnit/JSON 输出，
  供 agent 与 CI 解析（注意 `CheckResult` 是 CLI 汇总结果，不是 gatekit 的判据 Report）；
- `run_gate`：需要 `--format json|junit` 结构化输出时的可选入口；
  普通判据仍用 `gatekit.gate`，避免两套 argparse 方言。

退出码常量、`InfraError`、`repo_root` 全部委托给 gatekit，保证 0/1/2 语义只有一处定义。
零第三方依赖（标准库 only），Python 3.8+。
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import xml.sax.saxutils as _sax
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Callable, Dict, Iterable, List, Optional, Sequence, Tuple, Union

# gatekit 作为顶层包导入：把 scripts/lib 加进 sys.path（仓库根本身是 Python 包，
# 不能用 `import lib.gatekit`，会被 bone 包名空间吞掉）。
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gatekit as _gk  # noqa: E402

SCHEMA_VERSION = "bone.gate.v1"
EXIT_OK = _gk.OK
EXIT_VIOLATION = _gk.VIOLATION
EXIT_INFRA = _gk.ERROR

SEVERITY_BLOCKING = ("error", "warning")
FORMAT_CHOICES = ("text", "json", "junit")


class InfraError(_gk.GateError):
    """运行前提不成立（不是业务违规），与 gatekit.GateError 同源，退出码 2。"""


@dataclass(frozen=True)
class Finding:
    """结构化 finding —— agent / CI 消费的最小单元。"""

    id: str
    severity: str  # error | warning | info
    message: str
    file: str = ""
    line: int = 0

    def to_dict(self) -> Dict[str, object]:
        return asdict(self)


@dataclass
class GateContext:
    """结构化版判据上下文。root / files 由 run_gate 解析，判据只读。"""

    root: Path
    files: List[str] = field(default_factory=list)
    report_only: bool = False
    output_format: str = "text"
    baseline: Optional[Path] = None
    argv: List[str] = field(default_factory=list)


@dataclass
class CheckResult:
    """统一入口的汇总结果（不是 gatekit.Report）。"""

    name: str
    findings: List[Finding] = field(default_factory=list)
    errors: List[str] = field(default_factory=list)
    meta: Dict[str, object] = field(default_factory=dict)

    def add(
        self,
        id: str,
        severity: str,
        message: str,
        file: str = "",
        line: int = 0,
    ) -> "CheckResult":
        self.findings.append(Finding(id, severity, message, file, line))
        return self

    def error(self, message: str) -> "CheckResult":
        self.errors.append(message)
        return self

    @property
    def violations(self) -> List[Finding]:
        return [f for f in self.findings if f.severity in SEVERITY_BLOCKING]

    @property
    def ok(self) -> bool:
        return not self.violations and not self.errors

    def exit_code(self, report_only: bool = False) -> int:
        if self.errors and not self.violations:
            return EXIT_INFRA
        if self.violations and not report_only:
            return EXIT_VIOLATION
        return EXIT_OK

    def to_dict(self, report_only: bool = False) -> Dict[str, object]:
        return {
            "schema": SCHEMA_VERSION,
            "gate": self.name,
            "status": (
                "error" if self.errors and not self.violations
                else "report-only" if self.violations and report_only
                else "pass" if self.ok
                else "fail"
            ),
            "exit_code": self.exit_code(report_only),
            "summary": {
                "findings": len(self.findings),
                "violations": len(self.violations),
                "errors": len(self.errors),
            },
            "findings": [f.to_dict() for f in self.findings],
            "errors": list(self.errors),
            "meta": dict(self.meta),
        }

    def render(self, output_format: str = "text", report_only: bool = False) -> str:
        payload = self.to_dict(report_only)
        if output_format == "json":
            return json.dumps(payload, ensure_ascii=False, indent=2)
        if output_format == "junit":
            case = {
                "id": self.name,
                "status": payload["status"],
                "duration_ms": 0,
                "output": "\n".join(f.message for f in self.findings),
                "error": self.violations[0].message if self.violations else "",
            }
            return render_junit(self.name, [case])
        lines = [
            "=" * 72,
            "  %s  %s" % ("OK" if self.ok else "FAIL", self.name),
            "=" * 72,
        ]
        lines += [
            "  [%s] %s%s%s" % (
                f.severity.upper(),
                f.message,
                (" (%s)" % f.file) if f.file else "",
                (":%d" % f.line) if f.line else "",
            )
            for f in self.findings
        ]
        lines += ["  [INFRA] " + e for e in self.errors]
        summary = payload["summary"]
        lines.append(
            "  合计: %d finding / %d 违规 / %d 运行错误 -> exit %d"
            % (summary["findings"], summary["violations"], summary["errors"], payload["exit_code"])
        )
        return "\n".join(lines)


def render_junit(suite_name: str, cases: Sequence[Dict[str, object]]) -> str:
    """把若干检查结果渲染为 JUnit XML（CI 与 agent 共用的机器可读出口）。"""
    failures = sum(1 for c in cases if c.get("status") not in ("pass", "skip", "report-only", "dry-run"))
    out = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        '<testsuites name="%s" tests="%d" failures="%d">'
        % (_sax.escape(suite_name), len(cases), failures),
        '<testsuite name="%s" tests="%d" failures="%d">'
        % (_sax.escape(suite_name), len(cases), failures),
    ]
    for case in cases:
        name = _sax.escape(str(case.get("id", "unnamed")))
        status = str(case.get("status", "error"))
        time_s = float(case.get("duration_ms", 0) or 0) / 1000.0
        out.append(
            '  <testcase name="%s" classname="bone.check" time="%.3f">' % (name, time_s)
        )
        if status in ("fail", "error"):
            body = str(case.get("error") or case.get("output") or status)
            out.append(
                '    <failure message="%s" type="%s">%s</failure>'
                % (_sax.escape(status), _sax.escape(status), _sax.escape(body))
            )
        elif status == "skip":
            out.append("    <skipped/>")
        out.append("  </testcase>")
    out += ["</testsuite>", "</testsuites>"]
    return "\n".join(out)


def repo_root(anchor: Optional[Union[str, Path]] = None, refresh: bool = False) -> Path:
    """委托 gatekit.repo_root；显式 anchor 优先（/tmp 夹具、干净检出、worktree）。"""
    if anchor is not None:
        return Path(anchor).expanduser().resolve()
    return _gk.repo_root()


GIT_ENV_KEYS = (
    "GIT_DIR",
    "GIT_WORK_TREE",
    "GIT_INDEX_FILE",
    "GIT_COMMON_DIR",
    "GIT_PREFIX",
)


def clean_git_env() -> Dict[str, str]:
    """剥离继承来的 GIT_* 后的环境副本。

    两处必须用：
    1. 枚举文件（见 `git_files`）——否则 `root` 被忽略，扫的是"外面那个仓库"；
    2. `git init <临时目录>` 建夹具——否则不是"在临时目录建仓库"，而是**重置外层仓库**，
       夹具里根本没有 `.git`，后续读索引直接失败（本仓库历史上红过两次，都是只在裸环境验证）。
    """
    return {k: v for k, v in os.environ.items() if k not in GIT_ENV_KEYS}


def git_files(root: Optional[Path] = None, include_untracked: bool = True) -> List[str]:
    """git 已知文件（tracked + 非忽略 untracked）的唯一枚举方式。

    用 `git ls-files` 而不是 `Path.rglob` / `os.walk`：后者会走进
    bone-frontend/node_modules（十万级文件，实测 321s vs 0.37s）。

    <p><b>为什么要剥掉继承来的 GIT_* 环境变量</b>：`GIT_DIR` / `GIT_WORK_TREE` /
    `GIT_INDEX_FILE` 的优先级高于 cwd，而 pre-commit 钩子（尤其 worktree 里）会把它们
    传给子进程。带着它们跑时，`git ls-files` 会忽略传入的 `root` 去枚举"外面那个仓库"，
    于是 `iter_files(tmp)` 返回的不是 tmp 的文件——根作用域静默失效，门禁扫错目录还自以为通过。
    剥离后「cwd = 传入 root」是唯一事实来源。
    """
    base = root or repo_root()
    args = ["git", "ls-files", "-z", "--cached"]
    if include_untracked:
        args += ["--others", "--exclude-standard"]
    try:
        probe = subprocess.run(
            args, cwd=str(base), capture_output=True, check=True, env=clean_git_env()
        )
    except (OSError, subprocess.CalledProcessError) as exc:
        raise InfraError("git ls-files 失败（不在 git 仓库？）: %s" % exc)
    return [p.decode("utf-8", "replace") for p in probe.stdout.split(b"\0") if p]


def iter_files(
    root: Optional[Path] = None,
    suffixes: Iterable[str] = (".java",),
    include_untracked: bool = True,
    prefixes: Iterable[str] = (),
) -> List[Path]:
    """按后缀（可选路径前缀）过滤出需要扫描的源码文件。"""
    base = root or repo_root()
    wanted = {s if s.startswith(".") else "." + s for s in suffixes}
    pref = tuple(prefixes)
    return [
        base / rel
        for rel in git_files(base, include_untracked)
        if (not pref or rel.startswith(pref))
        and Path(rel).suffix in wanted
        and (base / rel).is_file()
    ]


def load_baseline(path: Union[str, Path]) -> List[str]:
    """读取存量豁免集合。支持 JSON 数组 / {"entries": [...]} / 纯文本每行一条。"""
    p = Path(path)
    if not p.is_file():
        raise InfraError("baseline 不存在: %s" % p)
    text = p.read_text(encoding="utf-8")
    if p.suffix == ".json":
        data = json.loads(text)
        if isinstance(data, list):
            return [str(x) for x in data]
        if isinstance(data, dict):
            for key in ("entries", "violations", "items", "allowlist"):
                if isinstance(data.get(key), list):
                    return [str(x) for x in data[key]]
        raise InfraError("baseline JSON 结构无法识别: %s" % p)
    return [ln.strip() for ln in text.splitlines() if ln.strip() and not ln.startswith("#")]


def ratchet(
    current: Iterable[str], baseline: Iterable[str]
) -> Tuple[List[str], List[str]]:
    """只可收缩棘轮：返回 (新增违规, 已失效的 baseline 条目)。

    `current - baseline` 判红（新增欠债）；`baseline - current` 提示收缩
    （条目失效却还留着会泡松门禁）。存量 4 套手写实现统一收敛到这里。
    """
    cur, base = set(current), set(baseline)
    return sorted(cur - base), sorted(base - cur)


def build_parser(name: str, description: str = "") -> argparse.ArgumentParser:
    """结构化输出版判据的公共参数面；普通判据请用 gatekit.gate。"""
    parser = argparse.ArgumentParser(
        prog=name,
        description=description or ("Bone 门禁: %s（默认阻断，--report-only 仅观察）" % name),
    )
    parser.add_argument("--root", default=None, help="仓库根（默认 gatekit.repo_root 解析）")
    parser.add_argument("--format", choices=FORMAT_CHOICES, default="text", help="结果格式")
    parser.add_argument("--check", action="store_true", help="显式阻断模式（默认即阻断）")
    parser.add_argument("--report-only", action="store_true", help="只观察，违规也返回 0")
    parser.add_argument("--self-test", action="store_true", help="跑负向变异自证")
    parser.add_argument("--baseline", default=None, help="存量豁免清单路径")
    parser.add_argument("--junit-dir", default=None, help="额外写出 JUnit XML 的目录")
    return parser


def run_gate(
    name: str,
    judge: Callable[[GateContext], CheckResult],
    *,
    self_test: Optional[Callable[[GateContext], CheckResult]] = None,
    argv: Optional[Sequence[str]] = None,
    description: str = "",
) -> int:
    """需要 --format json|junit 时使用的可选入口；普通判据用 gatekit.gate。"""
    parser = build_parser(name, description)
    args = parser.parse_args(list(argv) if argv is not None else sys.argv[1:])
    try:
        root = repo_root(args.root)
        if not (root / "scripts").is_dir():
            raise InfraError("--root 不像仓库根（缺 scripts/）: %s" % root)
        ctx = GateContext(
            root=root,
            files=git_files(root),
            report_only=args.report_only,
            output_format=args.format,
            baseline=Path(args.baseline) if args.baseline else None,
            argv=list(argv) if argv is not None else sys.argv[1:],
        )
        report = self_test(ctx) if args.self_test and self_test else judge(ctx)
    except InfraError as exc:
        report = CheckResult(name).error(str(exc))
    except Exception as exc:  # 判据自身崩溃不能被误读为「合规」
        report = CheckResult(name).error("判据异常 %s: %s" % (type(exc).__name__, exc))

    report_only = args.report_only
    rendered = report.render(args.format, report_only)
    print(rendered)
    if args.junit_dir:
        dest = Path(args.junit_dir)
        dest.mkdir(parents=True, exist_ok=True)
        case = {
            "id": name,
            "status": report.to_dict(report_only)["status"],
            "duration_ms": 0,
            "output": rendered,
            "error": report.violations[0].message if report.violations else "",
        }
        (dest / ("%s.xml" % name.replace("/", "_"))).write_text(
            render_junit(name, [case]), encoding="utf-8"
        )
    return report.exit_code(report_only)
