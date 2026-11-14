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
    COMET_EXPECTED_VERSION  期望版本，默认 0.5.2
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
  * 0.5.2 基线升级（2026-11-14）：CLI 已升 0.5.2，旧常量（期望 0.5.1 / 0.5.1 包
    sha256 / 3570 文件）会让 C1、C3 恒红，是「文档状态与实证不一致」的典型。新常量
    全部由官方包实算：tarball sha256=2003eb17…d1339、package/ 下普通文件 3656；
    实测本地安装 3656/3656 逐字节一致（differ=0 / missing=0），不是靠放宽阈值刷绿。
  * doctor 0.5.2 新增的 "current selection" 告警是**工作区运行态**（无 selection 时
    Router 只读推断），不是安装缺陷；C5 据此精确化为「安装完整性相关告警」，运行态告警
    显式登记但不计入 fail。分类自适应（形态不符即判红），并配 2 条变异探针防静态放水。
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
# 0.5.2 基线（2026-11-14 实测）：npm view @rpamis/comet@0.5.2 → shasum
# 11866c3e3a2e78a3792cd15b3afe038b9a326a6a；npm pack 复现 tarball 的 sha256、
# 以及 C3 同口径的 package/ 下普通文件数均由官方包实算，不沿用 0.5.1 的常量。
EXPECTED_VERSION = os.environ.get("COMET_EXPECTED_VERSION", "0.5.2")
PKG_ROOT = Path(os.environ.get(
    "COMET_PKG_ROOT", "/opt/homebrew/lib/node_modules/@rpamis/comet"))
NPM_PKG = "@rpamis/comet"
TARBALL_DEFAULT = os.environ.get(
    "COMET_TARBALL", f"/tmp/rpamis-comet-{EXPECTED_VERSION}.tgz")
TARBALL_SHA256 = "2003eb1797fcb693ec1c3160fcd97c86b66c0530e9f73ff78804e9d9b3dd1339"
EXPECTED_PKG_FILES = 3656
CACHE_DIR = Path(os.environ.get(
    "COMET_BASELINE_CACHE", Path.home() / ".cache" / "comet-baseline"))

# 已知上游缺陷：Trae 与 Trae CN 共用同一个 .trae/hooks.json，doctor 按两平台分别校验，
# 二者互斥，必然有且仅有 1 个 project 级 warn。**该白名单自适应**：仅当缺陷仍在时生效，
# 上游修复（两平台各有独立 hook 文件且各自 platform 正确）后自动失效。
UPSTREAM_DEFECT_WARNS = ("hooks: Trae (project)", "hooks: Trae CN (project)")
# CodeGraph 是可选能力、global 级用户目录 skills 不计入项目安装缺陷。
IGNORABLE_CHECK_PREFIXES = ("skills: ", "CodeGraph")

# 0.5.2 新增的 doctor 检查："current selection" 在多个 active change 且未显式
# select 时返回 warn（Router 退化为只读推断）。这是**工作区运行态**，不是安装缺陷；
# 把它计入「安装完整性」C5 属于判据语义错位（误报比漏报更致命）。
# 但绝不做静态白名单：只在告警形态确为「selection 缺失 + Router 只读推断」时归类，
# 且必须在报告中显式列出。任何 fail/error、措辞变化或新增同类告警仍按未登记告警判红。
WORKSPACE_SELECTION_CHECK = "current selection"
WORKSPACE_SELECTION_MARKER = "Router can infer"


def is_workspace_state_warn(item: dict) -> bool:
    """自适应识别工作区运行态告警；形态不符即不豁免，交给 unexpected 判红。"""
    return (item.get("check") == WORKSPACE_SELECTION_CHECK
            and item.get("status") == "warn"
            and WORKSPACE_SELECTION_MARKER in item.get("message", ""))

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
    # 工作区运行态告警单独分类：不计入安装完整性缺陷，但必须显式出现在报告里，
    # 且只能在形态完全匹配时豁免（见 is_workspace_state_warn）。
    state_warns = [x for x in warns if is_workspace_state_warn(x)]
    unexpected = [x for x in warns
                  if x.get("check") not in allowed_known
                  and not is_workspace_state_warn(x)
                  and not any(x.get("check", "").startswith(p) for p in IGNORABLE_CHECK_PREFIXES)
                  and "(global)" not in x.get("check", "")]
    classified = [x["check"] for x in warns if x.get("check") in allowed_known]
    ok = d.get("healthy") is True and not fails and not unexpected
    rep.add("C5 doctor 健康", ok,
            f"healthy={d.get('healthy')} pass={sum(x['status']=='pass' for x in results)} "
            f"fail={len(fails)} warn={len(warns)}; 上游缺陷白名单="
            f"{'已失效(上游已修复)' if split else '生效中(缺陷仍在)'}; "
            f"命中={classified or '无'}; "
            f"工作区运行态告警(显式登记,不计入安装缺陷)={[x['check'] for x in state_warns] or '无'}; "
            f"未登记 project 级告警={[x['check'] for x in unexpected] or '无'}")


# 识别 hook 配置里承载命令的「事件」键（不同平台命名大小写不一）。
_EVENT_KEYS = {"PreToolUse", "PostToolUse", "preToolUse", "postToolUse",
               "UserPromptSubmit", "Notification", "Stop", "SubagentStop"}


def _walk_hook_leaves(obj, event=None):
    """递归遍历 hook 配置，产出 (事件名, 叶子命令条目) 序列。

    叶子 = 直接携带 command/bash/powershell/args 的 dict；事件名沿父链继承，
    用于把 router 条目归到具体事件（如 PreToolUse），以便检测同事件重复接线。
    """
    out: list[tuple[str | None, dict]] = []
    if isinstance(obj, dict):
        if any(k in obj for k in ("command", "bash", "powershell", "args")):
            out.append((event, obj))
        for k, v in obj.items():
            out.extend(_walk_hook_leaves(v, k if k in _EVENT_KEYS else event))
    elif isinstance(obj, list):
        for v in obj:
            out.extend(_walk_hook_leaves(v, event))
    return out


def _extract_platforms(leaf: dict, raw: str, platforms: set[str]) -> None:
    """从单个叶子条目抽取 --platform（明文 args / command / 内嵌 base64 node -e）。"""
    args = leaf.get("args") or []
    for i, a in enumerate(args):
        if a == "--platform" and i + 1 < len(args):
            platforms.add(str(args[i + 1]))
    cmd = str(leaf.get("command", ""))
    m = re.search(r"--platform[ =]+[\"']?([\w-]+)", cmd)
    if m:
        platforms.add(m.group(1))
    for b in B64_RE.findall(raw):
        try:
            dec = base64.b64decode(b).decode("utf-8", "replace")
        except Exception:
            continue
        m = re.search(r'"--platform"\s*,\s*"([^"]+)"', dec)
        if m:
            platforms.add(m.group(1))
        m2 = re.search(r"--platform[ =]+[\"']?([\w-]+)", dec)
        if m2:
            platforms.add(m2.group(1))


def _platforms_in_text(blob: str, platforms: set[str]) -> None:
    m = re.search(r'"--platform"\s*,\s*"([^"]+)"', blob)
    if m:
        platforms.add(m.group(1))
    m2 = re.search(r"--platform[ =]+[\"']?([\w-]+)", blob)
    if m2:
        platforms.add(m2.group(1))


def _router_entries_by_event(path: Path) -> tuple[dict[str, int], set[str]]:
    """返回 (每个事件下的 router 条目计数, platform 集合)。

    「唯一」判据的精确度量：router 是 comet-hook-router 的调用。bash+powershell
    双写同一调用属可移植写法，算 1 个叶子；但若**同一事件**下出现 2+ 个 distinct
    router 叶子 = 该写入事件被重复接线（重复 Guard），会让一次写入被双次拦截/加锁。
    """
    text = path.read_text(encoding="utf-8", errors="replace")
    per_event: dict[str, int] = {}
    platforms: set[str] = set()
    parsed = False
    try:
        obj = json.loads(text)
        parsed = isinstance(obj, dict)
    except (ValueError, TypeError):
        obj = None
    if parsed:
        for ev, leaf in _walk_hook_leaves(obj):
            raw = json.dumps(leaf, ensure_ascii=False)
            # router 常以 base64(node -e ...) 形式内嵌，需解码后判定是否真的调用 router
            is_router = "comet-hook-router" in raw
            if not is_router:
                for b in B64_RE.findall(raw):
                    try:
                        if "comet-hook-router" in base64.b64decode(b).decode("utf-8", "replace"):
                            is_router = True
                            break
                    except Exception:
                        pass
            if not is_router:
                continue
            key = ev or "(unsplit)"
            per_event[key] = per_event.get(key, 0) + 1
            _extract_platforms(leaf, raw, platforms)
    if not per_event:
        # 兜底：结构化解析未命中（解析失败/非标准形状）时退化为全文本扫描（无事件粒度）。
        # 用集合去重解码后的 base64，避免 bash+powershell 双写同一调用被计成 2 个条目。
        blobs = {text}
        for b in B64_RE.findall(text):
            try:
                blobs.add(base64.b64decode(b).decode("utf-8", "replace"))
            except Exception:
                pass
        for blob in blobs:
            if "comet-hook-router" not in blob:
                continue
            per_event["(unsplit)"] = per_event.get("(unsplit)", 0) + 1
            _platforms_in_text(blob, platforms)
    return per_event, platforms


def trae_hook_split() -> bool:
    """上游是否已修复缺陷：Trae 与 Trae CN 各有独立 hook 文件且 platform 各自正确。"""
    trae_p = REPO / ".trae/hooks.json"
    trae_cn_p = REPO / ".trae-cn/hooks.json"
    if not trae_p.is_file() or not trae_cn_p.is_file():
        return False
    _, t = _router_entries_by_event(trae_p)
    _, tc = _router_entries_by_event(trae_cn_p)
    return t == {"trae"} and tc == {"trae-cn"}


def c6_hook_uniqueness(rep: Report, split: bool | None = None) -> None:
    """每个平台配置里，一次写入事件最多进入一个 Guard（router 调用唯一且 platform 匹配）。

    判定（精确，杜绝假绿/漏报）：
      * 该平台配置至少接线一个 router（否则平台无 Guard，缺失保护）；
      * 任一事件下的 router 叶子条数 ≤ 1（>1 = 同事件重复接线 = 重复 Guard，必须判红）；
      * platform 集合 ⊆ 该平台的允许集合（剔除上游已知缺陷时收紧）。

    自媒体化：上游缺陷仍在时 .trae/hooks.json 允许 {trae,trae-cn} 且 .trae-cn 可选；
    修复后 .trae 仅 {trae}、.trae-cn 必须存在且仅 {trae-cn}。"""
    if split is None:
        split = trae_hook_split()
    details: list[str] = []
    bad: list[str] = []
    for rel, (allowed_cfg, required_cfg) in HOOK_TARGETS.items():
        p = REPO / rel
        if not p.is_file():
            required = split if rel == ".trae-cn/hooks.json" else True
            if required:
                bad.append(f"{rel} 缺失")
            details.append(f"{rel}={'缺失(必填)' if required else 'MISSING(允许)'}")
            continue
        per_event, platforms = _router_entries_by_event(p)
        total = sum(per_event.values())
        max_per_event = max(per_event.values()) if per_event else 0
        if allowed_cfg is None:
            allowed = {"trae", "trae-cn"} if (rel == ".trae/hooks.json" and not split) else (
                {"trae"} if rel == ".trae/hooks.json" else {"trae-cn"})
        else:
            allowed = allowed_cfg
        # 唯一判据：至少 1 个 router，且任一事件下不得重复接线；platform 必须匹配。
        ok = total >= 1 and max_per_event <= 1 and bool(platforms) and platforms.issubset(allowed)
        if not ok:
            bad.append(f"{rel}: router={total}(单事件最多{max_per_event}), "
                       f"platform={sorted(platforms)} 允许={sorted(allowed)}")
        details.append(f"{rel}: router={total} 单事件最多{max_per_event} platform={sorted(platforms) or '?'}")
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

    # 0.5.2：工作区运行态告警的分类必须自适应且可证伪，不能变成静态放水。
    state_warn = json.dumps({"healthy": True, "results": [
        {"check": "current selection", "status": "warn",
         "message": "missing; Router can infer classic:some-change read-only"}]})
    state_warn_changed = json.dumps({"healthy": True, "results": [
        {"check": "current selection", "status": "warn",
         "message": "selection points at an archived change"}]})
    probe(c5_doctor, lambda *a, **k: (0, state_warn), expect_fail=False,
          label="C5 正例：selection 缺失的只读推断告警属运行态，判绿")
    probe(c5_doctor, lambda *a, **k: (0, state_warn_changed), expect_fail=True,
          label="C5 变异：同名告警措辞变化(非只读推断)必须判红")

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

    # C6 三个自测共用：按路径返回正确的 platform，使「重复接线 / 平台错配」各自独立成变量。
    def _router_fake(path, *, dup: bool = False, trae_platform: str = "trae-cn"):
        s = str(path)
        plat = "github-copilot"
        if s.endswith(".trae/hooks.json"):
            plat = trae_platform
        elif s.endswith(".codebuddy/settings.json"):
            plat = "codebuddy"
        elif s.endswith(".claude/settings.local.json"):
            plat = "claude"
        return ({"preToolUse": 2 if dup else 1}, {plat})

    # C6 自适应：上游修复后 .trae/hooks.json 仍写 trae-cn 必须判红（白名单自动失效）
    with patch.object(mod, "trae_hook_split", return_value=True), \
         patch.object(mod, "_router_entries_by_event", side_effect=lambda p, *a: _router_fake(p)):
        r = Report(); c6_hook_uniqueness(r)
        cases.append(("C6 自适应：修复后 .trae 仍 trae-cn 必须判红", len(r.failed) == 1,
                      f"期望 1 个 FAIL，实际 {len(r.failed)} 个"))

    # C6 变异：同一事件下 2 个 distinct router 条目 = 重复 Guard 接线，必须判红
    with patch.object(mod, "trae_hook_split", return_value=False), \
         patch.object(mod, "_router_entries_by_event", side_effect=lambda p, *a: _router_fake(p, dup=True)):
        r = Report(); c6_hook_uniqueness(r)
        cases.append(("C6 变异：同事件 2 个 router 条目(重复 Guard)必须判红",
                      len(r.failed) == 1, f"期望 1 个 FAIL，实际 {len(r.failed)} 个"))

    # C6 正例：单事件单 router 且 platform 匹配必须判绿
    with patch.object(mod, "trae_hook_split", return_value=False), \
         patch.object(mod, "_router_entries_by_event", side_effect=lambda p, *a: _router_fake(p)):
        r = Report(); c6_hook_uniqueness(r)
        cases.append(("C6 正例：单事件单 router 且 platform 匹配判绿",
                      len(r.failed) == 0, f"期望全 PASS，实际 {len(r.failed)} 个 FAIL"))

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
