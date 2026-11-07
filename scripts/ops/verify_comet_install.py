#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Comet 工具链完整性基线校验（只读、可重复、可证伪、fail-closed）。

用法:
    python3 scripts/ops/verify_comet_install.py                 # 严格模式（默认）
    python3 scripts/ops/verify_comet_install.py --fetch-tarball # 自动 npm pack 复现官方包
    python3 scripts/ops/verify_comet_install.py --no-strict    # 降级：C3 缺失不阻拦
    python3 scripts/ops/verify_comet_install.py --self-test     # 变异验证判据有判别力
    python3 scripts/ops/verify_comet_install.py --json          # 供 CI/脚本消费

环境变量:
    COMET_EXPECTED_VERSION  期望版本，默认 0.5.1
    COMET_PKG_ROOT          npm 全局包目录，默认 Homebrew 的 node_modules 路径
    COMET_TARBALL           官方 tarball 显式路径（优先于自动候选）
    COMET_BASELINE_CACHE   npm pack 缓存目录，默认 ~/.cache/comet-baseline

设计纪律（承 2026-11-06/07/11-07 三次真实事故）:
  * 「comet --version 正确 + doctor 全绿」不足以证明安装完整：0.5.1 曾出现
    CLI/Runtime 完好但 assets 缺 115 文件、eval/ 缺 425 文件、dist/*.d.ts 缺 696 文件，
    而 version/doctor/一次 update 全部照常通过。故 C3 必须对**官方 tarball 全包**逐字节比对。
  * 完整性终验必须可移交：上一版把 tarball 写死在 /tmp（本机会话残留），团队 clone 后
    必然缺失，而脚本仍打印「✅ 基线一致」、EXIT=0、--json failed=0 —— 这是典型的
    report-only 假绿。本版改为 **fail-closed**：C3 缺失时严格模式判 UNVERIFIED（exit 2），
    并用 --fetch-tarball 让任何人都能一条命令复现官方包（npm pack sha256 已实测稳定）。
  * 已知上游缺陷白名单（Trae/Trae CN 共用 .trae/hooks.json）**自适应**：仅当缺陷仍在时容忍，
    上游修复后白名单自动失效，残留告警立即判红；不再依赖人手记得去删白名单。
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
import shutil
import subprocess
import sys
import tarfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
EXPECTED_VERSION = os.environ.get("COMET_EXPECTED_VERSION", "0.5.1")
PKG_ROOT = Path(os.environ.get(
    "COMET_PKG_ROOT", "/opt/homebrew/lib/node_modules/@rpamis/comet"))
NPM_PKG = "@rpamis/comet"
TARBALL_DEFAULT = os.environ.get(
    "COMET_TARBALL", f"/tmp/rpamis-comet-{EXPECTED_VERSION}.tgz")
TARBALL_SHA256 = "4942b8b896a94e0b9ece98fdf502f53a75922c95be9e14b4a704545941b33d6d"
EXPECTED_PKG_FILES = 3570
CACHE_DIR = Path(os.environ.get(
    "COMET_BASELINE_CACHE", Path.home() / ".cache" / "comet-baseline"))

# 已知上游缺陷：Trae 与 Trae CN 共用同一个 .trae/hooks.json，doctor 按两平台分别校验，
# 二者互斥，必然有且仅有 1 个 project 级 warn。**该白名单自适应**：仅当缺陷仍在时生效，
# 上游修复（两平台各有独立 hook 文件且各自 platform 正确）后自动失效。
UPSTREAM_DEFECT_WARNS = ("hooks: Trae (project)", "hooks: Trae CN (project)")
# CodeGraph 是可选能力、global 级用户目录 skills 不计入项目安装缺陷。
IGNORABLE_CHECK_PREFIXES = ("skills: ", "CodeGraph")

# 平台 hook 配置 -> (允许的 platform 集合 | None, 是否必填 | None)。
# 两处用 None 表示「自适应」：依赖上游缺陷是否仍存在（见 trae_hook_split）。
#   - .trae/hooks.json：缺陷仍在时允许 {trae, trae-cn}；修复后只允许 {trae}
#   - .trae-cn/hooks.json：缺陷仍在时非必填（文件本就不存在）；修复后必须存在且只允许 {trae-cn}
HOOK_TARGETS: dict[str, tuple[set[str] | None, bool | None]] = {
    ".codebuddy/settings.json": ({"codebuddy"}, True),
    ".claude/settings.local.json": ({"claude"}, True),
    ".github/hooks/comet-guard.json": ({"github-copilot"}, True),
    ".trae/hooks.json": (None, True),
    ".trae-cn/hooks.json": (None, None),
}
B64_RE = re.compile(r"Buffer\.from\('([A-Za-z0-9+/=]+)','base64'\)")


class Report:
    def __init__(self) -> None:
        self.checks: list[dict] = []

    def add(self, cid: str, ok: bool | None, detail: str) -> None:
        self.checks.append({"id": cid,
                            "status": "PASS" if ok else ("SKIP" if ok is None else "FAIL"),
                            "detail": detail})

    @property
    def failed(self) -> list[dict]:
        return [c for c in self.checks if c["status"] == "FAIL"]

    def render(self, strict: bool = True) -> str:
        lines = [f"Comet 完整性基线  期望版本={EXPECTED_VERSION}  包目录={PKG_ROOT}", ""]
        for c in self.checks:
            lines.append(f"  [{c['status']:4}] {c['id']}: {c['detail']}")
        n_pass = sum(c["status"] == "PASS" for c in self.checks)
        n_skip = sum(c["status"] == "SKIP" for c in self.checks)
        n_fail = len(self.failed)
        if n_fail:
            concl = f"❌ 存在不一致 (FAIL={n_fail})"
        elif n_skip and strict:
            concl = (f"⚠ 未完整验证 (SKIP={n_skip}) — 用 --fetch-tarball 复现官方包，"
                     f"或 --no-strict 明确降级")
        else:
            concl = "✅ 基线一致"
        lines += ["", f"结论: PASS={n_pass}  SKIP={n_skip}  FAIL={n_fail}  {concl}"]
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


def c3_full_package(rep: Report, tarball: Path | None) -> dict:
    """全包逐字节比对：tarball 内 package/ 下每个普通文件 vs 本地安装（排除 node_modules）。"""
    if tarball is None or not tarball.is_file():
        rep.add("C3 全包逐字节一致", None,
                "未提供可用的 tarball（用 --fetch-tarball 自动复现官方包，或 --no-strict 降级）")
        return {"same": 0, "differ": 0, "missing": 0, "total": 0}

    if sha256_file(tarball) != TARBALL_SHA256:
        rep.add("C3 全包逐字节一致", False,
                f"{tarball} 的 sha256 与基线不符，拒绝用不可信包做完整性证明")
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
            "(门槛 assets>=153, SKILL.md>=13, eval>=425；仅作轻量兜底，强校验见 C3)")


def c5_doctor(rep: Report, split: bool | None = None) -> None:
    if split is None:
        split = trae_hook_split()
    rc, out = run(["comet", "doctor", "--json"])
    try:
        d = json.loads(out[out.index("{"):out.rindex("}") + 1])
    except (ValueError, json.JSONDecodeError):
        rep.add("C5 doctor 健康", False, f"doctor --json 无法解析 (rc={rc})")
        return
    results = d.get("results", [])
    fails = [x for x in results if x.get("status") in ("fail", "error")]
    warns = [x for x in results if x.get("status") == "warn"]
    # 自适应：上游缺陷仍在时容忍已知互斥告警；修复后白名单自动失效。
    allowed_known = set() if split else set(UPSTREAM_DEFECT_WARNS)
    unexpected = [x for x in warns
                  if x.get("check") not in allowed_known
                  and not any(x.get("check", "").startswith(p) for p in IGNORABLE_CHECK_PREFIXES)
                  and "(global)" not in x.get("check", "")]
    classified = [x["check"] for x in warns if x.get("check") in allowed_known]
    ok = d.get("healthy") is True and not fails and not unexpected
    rep.add("C5 doctor 健康", ok,
            f"healthy={d.get('healthy')} pass={sum(x['status']=='pass' for x in results)} "
            f"fail={len(fails)} warn={len(warns)}; 上游缺陷白名单="
            f"{'已失效(上游已修复)' if split else '生效中(缺陷仍在)'}; "
            f"命中={classified or '无'}; 未登记 project 级告警={[x['check'] for x in unexpected] or '无'}")


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
        m = re.search(r'"--platform"\s*,\s*"([^"]+)"', blob)
        if m:
            platforms.add(m.group(1))
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


def trae_hook_split() -> bool:
    """上游是否已修复缺陷：Trae 与 Trae CN 各有独立 hook 文件且 platform 各自正确。"""
    trae_p = REPO / ".trae/hooks.json"
    trae_cn_p = REPO / ".trae-cn/hooks.json"
    if not trae_p.is_file() or not trae_cn_p.is_file():
        return False
    _, t = _router_platforms(trae_p)
    _, tc = _router_platforms(trae_cn_p)
    return t == {"trae"} and tc == {"trae-cn"}


def c6_hook_uniqueness(rep: Report, split: bool | None = None) -> None:
    """一次写入事件最多进入一个 Guard：每个平台配置里 router 的 platform 必须唯一且匹配。
    自媒体化：上游缺陷仍在时 .trae/hooks.json 允许 {trae,trae-cn} 且 .trae-cn 可选；
    修复后 .trae 仅 {trae}、.trae-cn 必须存在且仅 {trae-cn}。"""
    if split is None:
        split = trae_hook_split()
    details: list[str] = []
    bad: list[str] = []
    for rel, (allowed_cfg, required_cfg) in HOOK_TARGETS.items():
        p = REPO / rel
        if not p.is_file():
            if rel == ".trae-cn/hooks.json":
                req = split            # 缺陷仍在时该文件本就不存在；修复后必存在
                required = req
            else:
                required = True if required_cfg is True else True  # 其它平台文件缺失即红
                req = False
            if required:
                bad.append(f"{rel} 缺失")
            details.append(f"{rel}={'缺失(必填)' if required else 'MISSING(允许)'}")
            continue
        n, platforms = _router_platforms(p)
        if allowed_cfg is None:
            if rel == ".trae/hooks.json":
                allowed = {"trae", "trae-cn"} if not split else {"trae"}
            else:  # .trae-cn/hooks.json
                allowed = {"trae-cn"}
        else:
            allowed = allowed_cfg
        ok = n >= 1 and platforms and platforms.issubset(allowed)
        if not ok:
            bad.append(f"{rel}: router={n}, platform={sorted(platforms)} 允许={sorted(allowed)}")
        details.append(f"{rel}: router={n} platform={sorted(platforms) or '?'}")
    rep.add("C6 Hook Router 唯一", not bad, "; ".join(details)
            + (f" | 异常={bad}" if bad else "")
            + f" | 上游缺陷白名单={'已失效(上游已修复)' if split else '生效中'}")


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


def resolve_tarball(args) -> Path | None:
    """候选链：显式 --tarball > COMET_TARBALL 环境变量 > /tmp 本机包 > 缓存目录 > --fetch-tarball。"""
    explicit = Path(args.tarball)
    if explicit.is_file():
        return explicit
    for cand in (os.environ.get("COMET_TARBALL"),
                TARBALL_DEFAULT,
                str(CACHE_DIR / f"rpamis-comet-{EXPECTED_VERSION}.tgz")):
        if cand and Path(cand).is_file():
            return Path(cand)
    if args.fetch_tarball:
        return fetch_tarball()
    return None


def fetch_tarball() -> Path | None:
    """用 npm pack 复现官方包到缓存目录，并校验 sha256；任何不一致都返回 None（绝不降级放行）。"""
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    dest = CACHE_DIR / f"rpamis-comet-{EXPECTED_VERSION}.tgz"
    if dest.is_file():
        return dest
    npm = shutil.which("npm")
    if not npm:
        sys.stderr.write("[fetch] 未找到 npm，无法复现官方包\n")
        return None
    rc, _ = run([npm, "pack", f"{NPM_PKG}@{EXPECTED_VERSION}",
                 "--pack-destination", str(CACHE_DIR), "--silent"], cwd=CACHE_DIR)
    if rc != 0:
        sys.stderr.write(f"[fetch] npm pack 失败 rc={rc}\n")
        return None
    produced = sorted(CACHE_DIR.glob("rpamis-comet-*.tgz"),
                      key=lambda p: p.stat().st_mtime)
    got = produced[-1] if produced else None
    if got is None:
        return None
    if sha256_file(got) != TARBALL_SHA256:
        sys.stderr.write(f"[fetch] 复现包 sha256 与基线不符，拒绝使用（已保留 {got} 供排查）\n")
        return None
    if got != dest:
        shutil.move(str(got), str(dest))
    return dest


def verdict(rep: Report, strict: bool = True) -> int:
    """退出码：FAIL→1；strict 且存在 SKIP(未验证)→2；否则 0。"""
    if rep.failed:
        return 1
    if strict and any(c["status"] == "SKIP" for c in rep.checks):
        return 2
    return 0


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

    def run_healthy(*a, **k):
        return 0, healthy

    def run_unhealthy(*a, **k):
        return 0, unhealthy

    def run_unseen(*a, **k):
        return 0, unseen_warn

    # 缺陷仍在（split=False）：已知告警被白名单吸收 → 判绿
    with patch.object(mod, "trae_hook_split", return_value=False):
        probe(c5_doctor, run_healthy, expect_fail=False,
              label="C5 正例：缺陷仍在时白名单告警判绿")
    probe(c5_doctor, run_unhealthy, expect_fail=True,
          label="C5 变异：doctor 判红必须传导")
    probe(c5_doctor, run_unseen, expect_fail=True,
          label="C5 变异：未登记 project 告警必须判红")
    # 上游已修复（split=True）：同样的 Trae warn 必须判红（白名单自动失效）
    with patch.object(mod, "trae_hook_split", return_value=True):
        probe(c5_doctor, run_healthy, expect_fail=True,
              label="C5 自适应：上游修复后残留 Trae warn 必须判红")

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

    # 严格模式：存在 SKIP 必须使 verdict 返回非 0（fail-closed，杜绝假绿）
    r = Report()
    r.add("C3 全包逐字节一致", None, "未提供 tarball")
    r.add("C1 版本一致", True, "ok")
    cases.append(("verdict 变异：严格模式下 SKIP 必须 exit 2", verdict(r, strict=True) == 2,
                  f"期望 2，实际 {verdict(r, strict=True)}"))
    cases.append(("verdict 正例：--no-strict 下 SKIP 不阻拦", verdict(r, strict=False) == 0,
                  f"期望 0，实际 {verdict(r, strict=False)}"))

    # C6 自适应：上游修复后 .trae/hooks.json 仍写 trae-cn 必须判红
    def fake_platforms_only_traecn(path, *a):
        return 1, {"trae-cn"}

    with patch.object(mod, "trae_hook_split", return_value=True), \
         patch.object(mod, "_router_platforms", side_effect=fake_platforms_only_traecn):
        r = Report()
        c6_hook_uniqueness(r)
        cases.append(("C6 自适应：修复后 .trae 仍 trae-cn 必须判红", len(r.failed) == 1,
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
    ap.add_argument("--fetch-tarball", action="store_true",
                    help="本地无官方 tarball 时，用 npm pack 复现并校验 sha256 后比对")
    ap.add_argument("--no-strict", action="store_true",
                    help="降级：C3 缺失时仍判绿（仅本地快速体检用，CI/交接勿用）")
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--json", action="store_true")
    args = ap.parse_args()
    if args.self_test:
        return self_test()

    strict = not args.no_strict
    rep = Report()
    c1_version(rep)
    c2_entrypoint(rep)
    tb = resolve_tarball(args)
    c3_full_package(rep, tb)
    c4_assets_and_eval(rep, {})
    c5_doctor(rep)
    c6_hook_uniqueness(rep)
    c7_project_config_tracked(rep)
    c8_no_generated_pollution(rep)

    if args.json:
        out = {
            "expectedVersion": EXPECTED_VERSION,
            "strict": strict,
            "checks": rep.checks,
            "verified": sum(c["status"] == "PASS" for c in rep.checks),
            "skipped": sum(c["status"] == "SKIP" for c in rep.checks),
            "failed": len(rep.failed),
            "verdict": verdict(rep, strict),
        }
        print(json.dumps(out, ensure_ascii=False, indent=2))
    else:
        print(rep.render(strict))
    return verdict(rep, strict)


if __name__ == "__main__":
    sys.exit(main())
