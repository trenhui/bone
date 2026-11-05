#!/usr/bin/env python3
"""门禁自身存活判据（Liveness）——防「CI 从不运行」这类静默失效。

为什么需要这个门禁
------------------
2026-10-04 诊断发现本仓一个真实的静默失效：**判据质量很高，但接线全断**。

  - 唯一活跃开发分支是 `dev`（实测 `master..dev` = 254 提交、`dev..master` = 0，
    `origin/HEAD` 指向 master），而 `.github/workflows/ci.yml` 的触发器写的是
    `[main, master, develop]`（连真实分支名 `dev` 都没列）。
  - 结果：5 个 job、20+ 个已接线门禁**在 dev 上从不运行**，"已接线"三个字在事实上不成立。

把分支名改对只治标——分支还会再漂移，而且这次漂移没有任何人会收到信号。
所以本门禁断言的是**机制存活**，不是某一次的具体配置：

  R1 触发器（push / pull_request）**必须包含当前分支**。
     反例证伪：删掉 `dev` ⇒ 红。这是 P0-1 的直接防复发判据。
  R2 `backend-quality` 里显式标注 `(blocking)` 的门禁步骤数 ≥ MIN_BLOCKING。
     反例证伪：整段删掉 DDD 系列门禁 ⇒ 计数下降 ⇒ 红。
     口径刻意选「name 以 (blocking) 结尾」而不是「含 python3/mvn」——后者会把
     `Set up JDK` / `Upload artifacts` 这类非门禁步骤也算进去，虚高且随 CI 维护漂移。
  R3 每个 step 必须有 `run:` 或 `uses:`。
     反例证伪：某个门禁只剩 `- name: xxx Lint` 而没有命令（空壳步骤）⇒ 红。
     空壳比没有步骤更糟：它让人以为该维度已被覆盖。
  R4 每个门禁脚本必须被**活入口**调用（2026-11-05 新增）。
     背景：2026-11-05 实测 61 个 `scripts/**/check-*.py` 中，3 个判据与单测齐备却
     **全仓零调用**（check.sh / ci.yml / ci-local.sh 均未引用）——`check-claim-ai-evalset.py`
     监管用例集、`check-approver-reference-not-exposed.py` 凭据列泄漏守卫、
     `check-sql-entity-column-coverage.py` 读回缺列。三者已接线，但机制上无人拦下一位作者
     再写一个孤儿门禁。R1–R3 只看「已列出的步骤是否执行」，看不见「从未被列出的脚本」。
     反例证伪：把某门禁从 check.sh 与 ci.yml 同时删掉 ⇒ 红。
     口径：
       · 只认**入口文件**（check.sh / ci-local.sh / ci-check.sh / .github/workflows/*.yml）。
         在另一个门禁的注释里被提到不算接线——那个门禁自己也可能从未运行，
         且注释不是调用。
       · 有意暂缓接线的，登记在 `scripts/gate-liveness-allowlist.json`，须写明理由与裁决人。
  R4b 豁免只可收缩：条目指向的脚本已不存在、或已经接线，条目本身即判红。
     反例证伪：给已接线门禁补一条豁免 ⇒ 红。防「用豁免清单把 R4 慢慢泡成永远绿」。
  R4c 豁免理由必须可复核（≥ MIN_REASON_CHARS 字符且 owner 非空）。
     反例证伪：理由填「待评估」⇒ 红。
  R4d 入口文件一个都读不到 ⇒ 判红。
     反例证伪：改掉入口文件名 ⇒ 全部脚本都"无引用"，若判据写成"没有入口则跳过"就会恒绿。

与「门禁跑通了就有效」的区别
----------------------------
R1/R2/R3 判的都是**门禁有没有被执行**，判据内容本身的正确性由各门禁自己的负向探针保证
（例：`scripts/ci/probe-security-fail-open-default.py`）。两者合起来才构成"有效门禁"。

用法
----
    python3 scripts/check-gate-liveness.py [--strict]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CI_YML = REPO / ".github" / "workflows" / "ci.yml"

# R4：门禁脚本 ↔ 活入口。入口清单刻意写成**具体文件**而不是 glob：
# 新增一个 runner 时必须显式加进来，否则它里面挂的门禁会被判为孤儿（宁可误报，不可漏报）。
SCRIPTS_REL = Path("scripts")
WORKFLOWS_REL = Path(".github") / "workflows"
ENTRY_SCRIPTS: tuple[Path, ...] = (
    SCRIPTS_REL / "check.sh",
    SCRIPTS_REL / "ci-local.sh",
    SCRIPTS_REL / "ci-check.sh",
)
ALLOWLIST_REL = SCRIPTS_REL / "gate-liveness-allowlist.json"
# 理由太短必然是敷衍（「待评估」「稍后接」）。20 字符约等于一句能复核的话。
MIN_REASON_CHARS = 20

# 2026-10-04 实测基线：修复 P0-1 并补齐 6 个此前零保护的门禁后，(blocking) 步骤共 20 条。
# 阈值按实测值下取 15，留出加新步骤的余量；收紧阈值本身是符合预期的（基线只可收缩的反向亦成立：
# 阈值上调属自愿收口，下调（即删门禁）必须走 baseline）。
MIN_BLOCKING = 15

STEP_RE = re.compile(r"^\s*-\s+name:\s*(?P<name>.*?)\s*$")
TRIGGER_KEY_RE = re.compile(r"^  (?P<key>on|push|pull_request):\s*$")
BRANCHES_RE = re.compile(r"branches:\s*\[(?P<branches>[^\]]*)\]")
BLOCKING_NAME_RE = re.compile(r"\(blocking\)\s*$")
# 触发块只出现在文件头部（jobs 之前），出现第二次即为解析异常。
TRIGGER_HEAD_LIMIT = 40

RED = "\033[0;31m"
GREEN = "\033[0;32m"
RESET = "\033[0m"


def current_branch() -> str:
    """当前（源）分支：PR 场景优先用 GITHUB_HEAD_REF（github.ref 会是 refs/pull/N/merge）。"""
    for key in ("GITHUB_HEAD_REF", "GITHUB_REF_NAME"):
        val = __import__("os").environ.get(key)
        if val and val not in ("merge", "refs/heads/merge"):
            return val.strip()
    try:
        out = subprocess.run(
            ["git", "rev-parse", "--abbrev-ref", "HEAD"],
            cwd=REPO,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if out.returncode == 0 and out.stdout.strip() and out.stdout.strip() != "HEAD":
            return out.stdout.strip()
    except (OSError, subprocess.SubprocessError):
        pass
    return ""


def base_branch() -> str:
    """PR 的**目标**分支（GITHUB_BASE_REF）。仅 pull_request 事件有值。

    为什么必须区分：`pull_request.branches` 过滤的是目标分支（PR 合到哪），
    而 `push.branches` 过滤的是源分支（谁被推）。用源分支去比对 pull_request 列表，
    会让**任何从特性分支发起的 PR 都被误判为"CI 不会触发"**——CI 其实会跑。
    这种误报的后果是作者直接把门禁关掉，比不报更糟。
    """
    val = __import__("os").environ.get("GITHUB_BASE_REF")
    return val.strip() if val else ""


def _indent_of(line: str) -> int:
    return len(line) - len(line.lstrip())


def parse_triggers(text: str) -> dict[str, list[str]]:
    """返回 {'push': [...], 'pull_request': [...]}；解析不出来时返回空且由调用方判红。"""
    lines = text.splitlines()[:TRIGGER_HEAD_LIMIT]
    triggers: dict[str, list[str]] = {}
    for line in lines:
        key = TRIGGER_KEY_RE.match(line)
        if key:
            current = key.group("key")
            if current == "on":
                current = ""  # on 之后紧跟的子键才是 push / pull_request
                continue
            triggers[current] = []
            continue
        m = BRANCHES_RE.search(line)
        if m and current in ("push", "pull_request"):
            triggers[current] = [b.strip() for b in m.group("branches").split(",") if b.strip()]
    return triggers


def trigger_violations(
    head: str, base: str, triggers: dict[str, list[str]]
) -> tuple[list[str], list[str]]:
    """R1：分支覆盖判据。返回 (违规, 说明)。纯函数，便于单测。

    语义（`push` 与 `pull_request` 的 branches 过滤对象不同，不能混用）：
      · push.branches 过滤**源分支**——它决定"这条分支被推上去时会不会跑 CI"。
      · pull_request.branches 过滤**目标分支**——它决定"合到哪条分支的 PR 会跑 CI"。
    故：
      · 在 PR 上下文（base 非空）只校验 base；源分支是特性分支，本来就不在 push 列表里。
      · 非 PR 上下文：源分支在 push 列表里 ⇒ 通过；源分支是**受保护分支**（出现在
        pull_request 列表里）却不在 push 列表 ⇒ 判红，这正是 P0-1「dev 不在触发器里」
        的原形态；源分支两边都不在 ⇒ 只是特性分支，记说明不判红（覆盖由 PR 到 dev 承担）。
    """
    violations: list[str] = []
    notes: list[str] = []
    if not triggers:
        violations.append("R1: 未在 ci.yml 头部解析到 push / pull_request 的 branches 列表")
        return violations, notes
    push_listed = triggers.get("push")
    pr_listed = triggers.get("pull_request")
    if not head and not base:
        notes.append("R1 跳过：无法判定当前分支（非 git 工作树且无 GITHUB_HEAD_REF）")
        return violations, notes

    if base:
        if pr_listed and base not in pr_listed:
            violations.append(
                f"R1: ci.yml 的 pull_request.branches = {pr_listed} 不含本 PR 的目标分支 "
                f"{base!r} ⇒ 该 PR 不会触发 CI，全部门禁形同未接线"
            )
        return violations, notes

    if push_listed and head in push_listed:
        return violations, notes
    if pr_listed and head in pr_listed:
        violations.append(
            f"R1: ci.yml 的 push.branches = {push_listed} 不含受保护分支 {head!r}"
            " ⇒ 直接推到该分支的提交不会触发 CI"
        )
        return violations, notes
    if push_listed is not None:
        notes.append(
            f"R1 说明：{head!r} 既不在 push.branches 也不在 pull_request.branches，"
            "按特性分支处理（CI 覆盖由 PR 到受保护分支承担）"
        )
    return violations, notes


def parse_jobs(text: str) -> dict[str, list[str]]:
    """job 名 → 该 job 的全部行。只收 `  job:`（2 空格），跳过 on 下的触发器键。"""
    jobs: dict[str, list[str]] = {}
    cur: str | None = None
    for line in text.splitlines():
        m = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
        if m:
            cur = m.group(1)
            jobs[cur] = []
        elif cur is not None and _indent_of(line) >= 2:
            jobs[cur].append(line)
    return jobs


def parse_steps(job_lines: list[str]) -> list[tuple[str, bool]]:
    """[(step name, 是否真的有 run:/uses:)] —— 空壳 step（只有 name）返回 False。"""
    steps: list[tuple[str, bool]] = []
    for idx, line in enumerate(job_lines):
        m = STEP_RE.match(line)
        if not m:
            continue
        nxt = job_lines[idx + 1] if idx + 1 < len(job_lines) else ""
        has_body = bool(re.match(r"^\s+(run|uses|with):\s*\S", nxt))
        steps.append((m.group("name"), has_body))
    return steps


def gate_scripts(root: Path) -> list[Path]:
    """scripts/**/check-*.py，排除 tests/ 下的测试夹具。"""
    base = root / SCRIPTS_REL
    out: list[Path] = []
    if not base.is_dir():
        return out
    for path in base.rglob("check-*.py"):
        if "tests" in path.parts:
            continue
        out.append(path)
    return sorted(out)


def entry_corpus(root: Path) -> dict[str, str]:
    """活入口 → 文本。缺某个文件不算错（裁剪检出），但一个都没有由 R4d 判红。"""
    corpus: dict[str, str] = {}
    for rel in ENTRY_SCRIPTS:
        path = root / rel
        if path.is_file():
            corpus[rel.as_posix()] = path.read_text(encoding="utf-8", errors="ignore")
    wf_dir = root / WORKFLOWS_REL
    if wf_dir.is_dir():
        for path in sorted(wf_dir.glob("*.yml")) + sorted(wf_dir.glob("*.yaml")):
            corpus[path.relative_to(root).as_posix()] = path.read_text(
                encoding="utf-8", errors="ignore"
            )
    return corpus


INVOCATION_RE = re.compile(r"\b(python3?|bash|sh)\b")


def is_referenced(name: str, corpus: dict[str, str]) -> bool:
    """入口里是否有**调用行**引用了该门禁。

    两条口径都刻意收紧：
      1. 必须出现在同一行且有 `python3`/`bash`/`sh`——只在注释里写「同 foo.py 口径」
         不算接线：注释不会执行，而且写注释的人可能正在维护一个同样从未运行的门禁。
      2. 名字后必须是非文件名字符——否则 check-openapi-contract.py 会因为
         check-openapi-contract-extra.py 的存在被误判成已接线（前缀相同 ≠ 同一个门禁）。
    """
    pattern = re.compile(re.escape(name) + r"(?![A-Za-z0-9_.-])")
    for text in corpus.values():
        for line in text.splitlines():
            if pattern.search(line) and INVOCATION_RE.search(line):
                return True
    return False


def load_allowlist(root: Path) -> tuple[dict[str, dict], list[str]]:
    """返回 (script → 条目, 违规)。清单缺失视为"无豁免"，不是错误。"""
    path = root / ALLOWLIST_REL
    if not path.is_file():
        return {}, []
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return {}, [f"R4c: 豁免清单解析失败 {ALLOWLIST_REL.as_posix()}: {exc}"]
    entries = doc.get("unwired") if isinstance(doc, dict) else None
    if not isinstance(entries, list):
        return {}, [f"R4c: {ALLOWLIST_REL.as_posix()} 顶层必须有 unwired 数组"]
    out: dict[str, dict] = {}
    violations: list[str] = []
    for idx, entry in enumerate(entries):
        if not isinstance(entry, dict):
            violations.append(f"R4c: unwired[{idx}] 必须为对象")
            continue
        script = entry.get("script")
        if not isinstance(script, str) or not script.strip():
            violations.append(f"R4c: unwired[{idx}].script 必须非空")
            continue
        key = script.strip()
        if key in out:
            violations.append(f"R4c: {key} 在豁免清单中重复登记")
        out[key] = entry
    return out, violations


def orphan_violations(root: Path) -> tuple[list[str], dict[str, int]]:
    """R4 族：每个门禁脚本都必须被活入口调用。纯函数，单测可直接注入夹具。"""
    gates = gate_scripts(root)
    stats = {"gates": len(gates), "wired": 0, "exempt": 0}
    if not gates:
        return [], stats
    corpus = entry_corpus(root)
    if not corpus:
        expected = [p.as_posix() for p in ENTRY_SCRIPTS]
        return (
            [
                f"R4d: 找不到任何入口文件（期望 {expected} 与 "
                f"{WORKFLOWS_REL.as_posix()}/*.yml）；入口路径失效会让 R4 恒绿"
            ],
            stats,
        )
    allowlist, violations = load_allowlist(root)
    for gate in gates:
        rel = gate.relative_to(root).as_posix()
        if is_referenced(gate.name, corpus):
            stats["wired"] += 1
            if rel in allowlist:
                violations.append(
                    f"R4b: {rel} 已接线，但豁免清单仍有条目（豁免只可收缩，请删除该条目）"
                )
            continue
        entry = allowlist.get(rel)
        if entry is None:
            violations.append(
                f"R4: {rel} 未被任何活入口调用"
                "（check.sh / ci-local.sh / ci-check.sh / workflows）；"
                "判据写了却从不执行＝该维度没有保护。请接线，或登记豁免并写明理由"
            )
            continue
        stats["exempt"] += 1
        reason = entry.get("reason")
        owner = entry.get("owner")
        if not isinstance(reason, str) or len(reason.strip()) < MIN_REASON_CHARS:
            violations.append(
                f"R4c: {rel} 的豁免理由不足 {MIN_REASON_CHARS} 字符或为空"
                "（须写明：为什么现在不接、接了会违反什么约束、由谁解除）"
            )
        if not isinstance(owner, str) or not owner.strip():
            violations.append(f"R4c: {rel} 的豁免缺 owner（无人负责的豁免等于永久豁免）")
    known = {gate.relative_to(root).as_posix() for gate in gates}
    for script in allowlist:
        if script not in known:
            violations.append(
                f"R4b: 豁免清单条目 {script} 指向的门禁脚本不存在（条目过期，请删除）"
            )
    return violations, stats


def main() -> int:
    parser = argparse.ArgumentParser(description="门禁自身存活判据")
    parser.add_argument("--strict", action="store_true", help="任何违规即 exit 1")
    args = parser.parse_args()

    if not CI_YML.is_file():
        print(f"{RED}❌ {CI_YML} 不存在，无法判定门禁存活{RESET}")
        return 1
    text = CI_YML.read_text(encoding="utf-8")

    violations: list[str] = []
    notes: list[str] = []

    # R1：分支覆盖（push 看源分支，pull_request 看目标分支——两者的过滤对象不同）
    branch = current_branch()
    base = base_branch()
    triggers = parse_triggers(text)
    r1_violations, r1_notes = trigger_violations(branch, base, triggers)
    violations.extend(r1_violations)
    notes.extend(r1_notes)

    # R2 / R3：backend-quality 的 blocking 门禁步骤数与空壳
    jobs = parse_jobs(text)
    bq = jobs.get("backend-quality")
    if bq is None:
        violations.append("R2: ci.yml 找不到 backend-quality job")
        bq = []
    steps = parse_steps(bq)
    blocking = [n for n, _ in steps if BLOCKING_NAME_RE.search(n)]
    if len(blocking) < MIN_BLOCKING:
        violations.append(
            f"R2: backend-quality 中标注 (blocking) 的门禁仅 {len(blocking)} 条，"
            f"低于基线 {MIN_BLOCKING} ⇒ 疑似门禁被整体删除而未收窄本判据"
        )
    empty = [n for n, ok in steps if not ok]
    if empty:
        violations.append(f"R3: 存在空壳步骤（只有 name、没有 run/uses）：{empty}")

    # R4 族：门禁脚本 ↔ 活入口（孤儿检测）。R1–R3 只能看见"已列出的步骤"，
    # 看不见"从未被任何入口列出的脚本"——那类门禁的判据质量再高也等于零。
    r4_violations, gate_stats = orphan_violations(REPO)
    violations.extend(r4_violations)

    if violations:
        for v in violations:
            print(f"{RED}❌ {v}{RESET}")
        print("提示：修 ci.yml 触发器后需重跑本门禁；删除门禁请同步下调 MIN_BLOCKING 并说明理由。")
        return 1 if args.strict else 0

    if base:
        coverage = f"PR 目标分支 {base} 在触发器内"
    elif branch and branch in (triggers.get("push") or []):
        coverage = f"分支 {branch} 在触发器内"
    else:
        coverage = f"{branch or '(未知)'} 按特性分支处理（CI 覆盖经 PR 到受保护分支）"

    print(
        f"{GREEN}✅ 门禁存活：{coverage}；"
        f"backend-quality blocking 门禁 {len(blocking)} 条（基线 {MIN_BLOCKING}）；"
        f"空壳步骤 0 条；"
        f"门禁脚本 {gate_stats['wired']}/{gate_stats['gates']} 已接线"
        f"（豁免 {gate_stats['exempt']}）{RESET}"
    )
    for n in notes:
        print(f"   ℹ️  {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
