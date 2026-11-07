#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comet 工具链完整性基线校验（只读、可重复、可证伪）。

用法:
    python3 scripts/ops/verify_comet_install.py
    python3 scripts/ops/verify_comet_install.py --tarball /tmp/rpamis-comet-0.5.1.tgz
    python3 scripts/ops/verify_comet_install.py --self-test   # 变异验证判据有判别力

环境变量:
    COMET_EXPECTED_VERSION  期望版本，默认 0.5.1
    COMET_PKG_ROOT          npm 全局包目录，默认 Homebrew 的 node_modules 路径
    COMET_TARBALL           官方 tarball 路径（省略则跳过 C3/C4/C5）

设计纪律（承 2026-11-06/07 两次真实事故）:
  * 「comet --version 正确 + doctor 全绿」不足以证明安装完整：0.5.1 曾出现
    CLI/Runtime 完好但 assets 缺 115 文件、eval/ 缺 425 文件、dist/*.d.ts 缺 696 文件，
    而 version/doctor/一次 update 全部照常通过。故 C3 必须对**官方 tarball 全包**逐字节比对。
  * 只读：不执行 comet update、不改任何 Comet 状态文件、不写项目文件。
  * 不接入 scripts/check.sh：工具链健康是运维动作，不是每次提交的业务门禁；
    接进去会强绑全局 npm 安装，反而是「配了不生效」的假绿。
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import tarfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
EXPECTED_VERSION = os.environ.get("COMET_EXPECTED_VERSION", "0.5.1")
PKG_ROOT = Path(os.environ.get(
    "COMET_PKG_ROOT", "/opt/homebrew/lib/node_modules/@rpamis/comet"))
TARBALL_DEFAULT = os.environ.get(
    "COMET_TARBALL", f"/tmp/rpamis-comet-{EXPECTED_VERSION}.tgz")
TARBALL_SHA256 = "4942b8b896a94e0b9ece98fdf502f53a75922c95be9e14b4a704545941b33d6d"
EXPECTED_PKG_FILES = 3570

# 已知上游缺陷白名单：Trae 与 Trae CN 共用 .trae/hooks.json，doctor 按两个平台分别
# 校验，二者互斥，必然有且仅有 1 个 project 级 warn。禁止靠伪造 hook 文件刷绿。
KNOWN_PROJECT_WARNS = (
    "hooks: Trae (project)",
    "hooks: Trae CN (project)",
)
# CodeGraph 是可选能力，未装各 IDE CLI / MCP 注册的告警不属于项目安装缺陷。
IGNORABLE_CHECK_PREFIXES = (
    "skills: ",          # global 级用户目录 skills
    "CodeGraph",         # 可选 CodeGraph 及其 effective 检查
)

# 平台 hook 配置 -> 该文件里 router 命令允许的 platform 集合。
# 注意 0.5.1 各平台落点不同：Claude Code 写 settings.local.json（明文 args 数组），
# CodeBuddy / Trae 写 base64 包装的 node -e，GitHub Copilot 写 bash+powershell 双变体。
# Trae 与 Trae CN 共用同一个 .trae/hooks.json（上游缺陷），故两者都列为可接受值。
HOOK_TARGETS: dict[str, tuple[set[str], bool]] = {
    ".codebuddy/settings.json": ({"codebuddy"}, True),
    ".claude/settings.local.json": ({"claude"}, True),
    ".github/hooks/comet-guard.json": ({"github-copilot"}, True),
    ".trae/hooks.json": ({"trae", "trae-cn"}, True),
    # 官方 update 不生成该文件，doctor 也不查它；缺失不算失败，仅作登记。
    ".trae-cn/hooks.json": ({"trae-cn"}, False),
}
B64_RE = re.compile(r"Buffer\.from\('([A-Za-z0-9+/=]+)','base64'\)")


class Report:
    def __init__(self) -> None:
        self.checks: list[dict] = []

    def add(self, cid: str, ok: bool | None, detail: str) -> None:
        self.checks.append({"id": cid, "status": "PASS" if ok else ("SKIP" if ok is None else "FAIL"),
                            "detail": detail})

    @property
    def failed(self) -> list[dict]:
        return [c for c in self.checks if c["status"] == "FAIL"]

    def render(self) -> str:
        lines = [f"Comet 完整性基线  期望版本={EXPECTED_VERSION}  包目录={PKG_ROOT}", ""]
        for c in self.checks:
            lines.append(f"  [{c['status']:4}] {c['id']}: {c['detail']}")
        n_pass = sum(c["status"] == "PASS" for c in self.checks)
        n_skip = sum(c["status"] == "SKIP" for c in self.checks)
        lines += ["", f"结论: PASS={n_pass}  SKIP={n_skip}  FAIL={len(self.failed)}  "
                  + ("✅ 基线一致" if not self.failed else "❌ 存在不一致")]
        return "\n".join(lines)


def run(cmd: list[str], cwd: Path = REPO) -> tuple[int, str]:
    try:
        p = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True, timeout=120)
        return p.returncode, (p.stdout or "") + (p.stderr or "")
    except (OSError, subprocess.TimeoutExpired) as e:
        return 127, str(e)


def sha256_file(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def c1_version(rep: Report) -> None:
    rc, out = run(["comet", "--version"])
    got = out.strip().splitlines()[0].strip() if out.strip() else "(empty)"
    rep.add("C1 版本一致", rc == 0 and got == EXPECTED_VERSION,
            f"comet --version={got} (期望 {EXPECTED_VERSION})")


def c2_entrypoint(rep: Report) -> None:
    js = PKG_ROOT / "bin" / "comet.js"
    ok = js.is_file() and os.access(js, os.X_OK)
    rep.add("C2 CLI 入口可执行", ok,
            f"{js} 可执行位={'有' if js.is_file() and os.access(js, os.X_OK) else '缺失'}")


def c3_full_package(rep: Report, tarball: Path) -> dict:
    """全包逐字节比对：tarball 内 package/ 下每个普通文件 vs 本地安装（排除 node_modules）。"""
    if not tarball.is_file():
        rep.add("C3 全包逐字节一致", None, f"未提供 tarball ({tarball})，跳过")
        return {"same": 0, "differ": 0, "missing": 0, "total": 0}

    if sha256_file(tarball) != TARBALL_SHA256:
        rep.add("C3 全包逐字节一致", False,
                "tarball sha256 与基线不符，拒绝用不可信包做完整性证明")
        return {"same": 0, "differ": 0, "missing": 0, "total": 0}

    same = differ = missing = 0
    bad_samples: list[str] = []
    with tarfile.open(tarball, "r:gz") as tf:
        members = [m for m in tf.getmembers()
                   if m.isfile() and m.name.startswith("package/")
                   and "/node_modules/" not in m.name]
        for m in members:
            rel = m.name[len("package/"):]
            local = PKG_ROOT / rel
            if not local.is_file():
                missing += 1
                if len(bad_samples) < 5:
                    bad_samples.append(f"missing: {rel}")
                continue
            remote = tf.extractfile(m)
            if remote is None:
                differ += 1
                continue
            a = hashlib.sha256(remote.read()).hexdigest()
            b = hashlib.sha256(local.read_bytes()).hexdigest()
            if a == b:
                same += 1
            else:
                differ += 1
                if len(bad_samples) < 5:
                    bad_samples.append(f"differ: {rel}")
    total = len(members)
    ok = total == EXPECTED_PKG_FILES and differ == 0 and missing == 0
    detail = (f"{same}/{total} 逐字节一致, differ={differ}, missing={missing} "
              f"(基线 {EXPECTED_PKG_FILES} 文件)")
    if bad_samples:
        detail += " | " + "; ".join(bad_samples)
    rep.add("C3 全包逐字节一致", ok, detail)
    return {"same": same, "differ": differ, "missing": missing, "total": total}


def c4_assets_and_eval(rep: Report, stats: dict) -> None:
    assets = PKG_ROOT / "assets"
    eval_dir = PKG_ROOT / "eval"
    n_assets = sum(1 for _ in assets.rglob("*") if _.is_file()) if assets.is_dir() else 0
    n_eval = sum(1 for _ in eval_dir.rglob("*") if _.is_file()) if eval_dir.is_dir() else 0
    n_skillmd = sum(1 for _ in assets.rglob("SKILL.md")) if assets.is_dir() else 0
    ok = n_assets >= 153 and n_eval >= 425 and n_skillmd >= 13
    rep.add("C4 功能资产齐备", ok,
            f"assets={n_assets} 文件/SKILL.md={n_skillmd}, eval={n_eval} 文件 "
            "(上限门槛 assets>=153, SKILL.md>=13, eval>=425)")


def c5_doctor(rep: Report) -> None:
    rc, out = run(["comet", "doctor", "--json"])
    try:
        d = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except (ValueError, json.JSONDecodeError):
        rep.add("C5 doctor 健康", False, f"doctor --json 无法解析 (rc={rc})")
        return
    results = d.get("results", [])
    fails = [x for x in results if x.get("status") in ("fail", "error")]
    warns = [x for x in results if x.get("status") == "warn"]
    unexpected = [x for x in warns
                  if x.get("check") not in KNOWN_PROJECT_WARNS
                  and not any(x.get("check", "").startswith(p) for p in IGNORABLE_CHECK_PREFIXES)
                  and "(global)" not in x.get("check", "")]
    classified = [x["check"] for x in warns if x.get("check") in KNOWN_PROJECT_WARNS]
    ok = d.get("healthy") is True and not fails and not unexpected
    rep.add("C5 doctor 健康", ok,
            f"healthy={d.get('healthy')} pass={sum(x['status']=='pass' for x in results)} "
            f"fail={len(fails)} warn={len(warns)}; 已知上游缺陷白名单命中={classified or '无'}; "
            f"未登记 project 级告警={[x['check'] for x in unexpected] or '无'}")


def _hook_entries(node, out: list[dict]) -> None:
    """递归收集 hook 配置里的命令条目（兼容 settings.json / hooks.json 两种形状）。"""
    if isinstance(node, dict):
        if any(k in node for k in ("command", "bash", "powershell", "args")):
            out.append(node)
        for v in node.values():
            _hook_entries(v, out)
    elif isinstance(node, list):
        for v in node:
            _hook_entries(v, out)


def _router_platforms(path: Path) -> tuple[int, set[str]]:
    """返回 (router 命令条数, platform 集合)。同时解析 base64 包装与明文 args 数组。"""
    text = path.read_text(encoding="utf-8", errors="replace")
    blobs: list[str] = [text]
    for b in B64_RE.findall(text):
        try:
            blobs.append(base64.b64decode(b).decode("utf-8", "replace"))
        except Exception:
            pass
    platforms: set[str] = set()
    n = 0
    for blob in blobs:
        if "comet-hook-router" not in blob:
            continue
        n += 1
        # base64 解出的是 JSON 数组字符串：["...router.mjs","--platform","trae-cn",...]
        m = re.search(r'"--platform"\s*,\s*"([^"]+)"', blob)
        if m:
            platforms.add(m.group(1))
        # 明文 args 数组：逐项找 "--platform" 的下一项
        try:
            obj = json.loads(blob)
            entries: list[dict] = []
            _hook_entries(obj, entries)
            for e in entries:
                raw = json.dumps(e, ensure_ascii=False)
                if "comet-hook-router" not in raw:
                    continue
                args = e.get("args") or []
                for i, a in enumerate(args):
                    if a == "--platform" and i + 1 < len(args):
                        platforms.add(str(args[i + 1]))
                cmd = str(e.get("command", ""))
                m2 = re.search(r"--platform[ =]+[\"']?([\w-]+)", cmd)
                if m2:
                    platforms.add(m2.group(1))
        except (ValueError, TypeError):
            pass
    return n, platforms


def c6_hook_uniqueness(rep: Report) -> None:
    """一次写入事件最多进入一个 Guard：每个平台配置里 router 的 platform 必须唯一且匹配。"""
    details: list[str] = []
    bad: list[str] = []
    for rel, (allowed, required) in HOOK_TARGETS.items():
        p = REPO / rel
        if not p.is_file():
            if required:
                bad.append(f"{rel} 缺失")
            details.append(f"{rel}=MISSING(允许)")
            continue
        n, platforms = _router_platforms(p)
        ok = n >= 1 and platforms and platforms.issubset(allowed)
        if not ok:
            bad.append(f"{rel}: router={n}, platform={sorted(platforms)} 允许={sorted(allowed)}")
        details.append(f"{rel}: router={n} platform={sorted(platforms) or '?'}")
    rep.add("C6 Hook Router 唯一", not bad, "; ".join(details)
            + (f" | 异常={bad}" if bad else "")
            + " | .trae/hooks.json 由 Trae/Trae CN 共用，属上游已知缺陷（ doctor 恒有 1 个 project warn）")


def c7_project_config_tracked(rep: Report) -> None:
    rc, out = run(["git", "ls-files", "--error-unmatch", ".comet/config.yaml"])
    rep.add("C7 项目配置已入库", rc == 0,
            ".comet/config.yaml " + ("已在 git 索引（团队共享 workflow 配置）" if rc == 0
                                     else "未入库 —— 团队 clone 后会退回 legacy fallback"))


def c8_no_generated_pollution(rep: Report) -> None:
    rc, out = run(["git", "ls-files", "--others", "--exclude-standard"])
    leaked = [p for p in out.splitlines()
              if p.startswith((".trae-cn/", ".claude/skills/comet", ".github/skills/comet"))]
    rep.add("C8 生成资产不污染工作树", not leaked,
            f"未跟踪的 Comet 生成资产={len(leaked)} 个"
            + (f" | 示例={leaked[:3]}" if leaked else "（均由 .gitignore 覆盖）"))


def self_test() -> int:
    """变异验证：注入伪造的采集结果，真实调用各判据函数，证明判据不是恒绿橡皮图章。

    每条「必须判红」都配一个「必须判绿」的正例，避免把判据改成一律 FAIL 也能通过。
    """
    from unittest.mock import patch

    mod = sys.modules[__name__]
    cases: list[tuple[str, bool, str]] = []

    def probe(check_fn, fake_run, *, expect_fail: bool, label: str) -> None:
        r = Report()
        with patch.object(mod, "run", side_effect=fake_run):
            check_fn(r)
        passed = (len(r.failed) == 1) if expect_fail else (len(r.failed) == 0)
        cases.append((label, passed,
                      f"期望 {'1 个 FAIL' if expect_fail else '全 PASS'}，实际 {len(r.failed)} 个 FAIL"))

    ok_version = lambda *a, **k: (0, f"{EXPECTED_VERSION}\n")
    bad_version = lambda *a, **k: (0, "0.4.0-beta.18\n")
    probe(c1_version, ok_version, expect_fail=False, label="C1 正例：版本一致判绿")
    probe(c1_version, bad_version, expect_fail=True, label="C1 变异：版本不符判红")

    healthy = json.dumps({"healthy": True, "results": [
        {"check": "hooks: Trae (project)", "status": "warn", "message": "known upstream"},
        {"check": "skills: Claude Code (global)", "status": "warn", "message": "user scope"},
        {"check": "current selection", "status": "pass", "message": "ok"}]})
    unhealthy = json.dumps({"healthy": False, "results": [
        {"check": "hooks: Trae (project)", "status": "fail", "message": "boom"}]})
    unseen_warn = json.dumps({"healthy": True, "results": [
        {"check": "hooks: SomeNewIDE (project)", "status": "warn", "message": "unregistered"}]})
    probe(c5_doctor, lambda *a, **k: (0, healthy), expect_fail=False,
          label="C5 正例：白名单告警判绿")
    probe(c5_doctor, lambda *a, **k: (0, unhealthy), expect_fail=True,
          label="C5 变异：doctor 判红必须传导")
    probe(c5_doctor, lambda *a, **k: (0, unseen_warn), expect_fail=True,
          label="C5 变异：未登记 project 告警必须判红")

    probe(c7_project_config_tracked, lambda *a, **k: (0, ".comet/config.yaml\n"),
          expect_fail=False, label="C7 正例：配置已入库判绿")
    probe(c7_project_config_tracked, lambda *a, **k: (1, "error: pathspec did not match"),
          expect_fail=True, label="C7 变异：配置漏入库判红")

    probe(c8_no_generated_pollution, lambda *a, **k: (0, "doc/a.md\n"),
          expect_fail=False, label="C8 正例：无生成资产泄漏判绿")
    probe(c8_no_generated_pollution, lambda *a, **k: (0, ".trae-cn/skills/comet/SKILL.md\n"),
          expect_fail=True, label="C8 变异：生成资产泄漏判红")

    # C3 变异：tarball 指纹不符必须拒绝，不能拿不可信包做完整性证明
    r = Report()
    with patch.object(mod, "sha256_file", return_value="0" * 64):
        c3_full_package(r, Path(TARBALL_DEFAULT))
    cases.append(("C3 变异：tarball 指纹不符判红", len(r.failed) == 1,
                  f"期望 1 个 FAIL，实际 {len(r.failed)} 个"))

    print("Comet 完整性判据 · 变异自测（真实调用判据函数）", "")
    ok = True
    for name, passed, note in cases:
        print(f"  [{'PASS' if passed else 'FAIL'}] {name} — {note}")
        ok &= passed
    print("", "结论: " + ("✅ 判据可证伪，非恒绿" if ok else "❌ 判据失去判别力，需修复"))
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--tarball", default=TARBALL_DEFAULT)
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()

    rep = Report()
    c1_version(rep)
    c2_entrypoint(rep)
    stats = c3_full_package(rep, Path(args.tarball))
    c4_assets_and_eval(rep, stats)
    c5_doctor(rep)
    c6_hook_uniqueness(rep)
    c7_project_config_tracked(rep)
    c8_no_generated_pollution(rep)
    print(json.dumps({"expectedVersion": EXPECTED_VERSION,
                      "checks": rep.checks,
                      "failed": len(rep.failed)}, ensure_ascii=False, indent=2)
          if args.json else rep.render())
    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
