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
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
CI_YML = REPO / ".github" / "workflows" / "ci.yml"

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
    """当前分支：PR 场景优先用 GITHUB_HEAD_REF（github.ref 会是 refs/pull/N/merge）。"""
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

    # R1：触发器必须包含当前分支
    branch = current_branch()
    triggers = parse_triggers(text)
    if not triggers:
        violations.append("R1: 未在 ci.yml 头部解析到 push / pull_request 的 branches 列表")
    if not branch:
        notes.append("R1 跳过：无法判定当前分支（非 git 工作树且无 GITHUB_HEAD_REF）")
    else:
        for kind in ("push", "pull_request"):
            listed = triggers.get(kind)
            if listed is None:
                # 缺一个触发器不算致命（可能只配了 push），但若配置过就不得漏掉当前分支
                if not listed:
                    continue
            if listed is not None and branch not in listed:
                violations.append(
                    f"R1: ci.yml 的 {kind}.branches = {listed} 不含当前分支 {branch!r}"
                    " ⇒ 该分支上的 push/PR 不会触发 CI，全部门禁形同未接线"
                )

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

    if violations:
        for v in violations:
            print(f"{RED}❌ {v}{RESET}")
        print("提示：修 ci.yml 触发器后需重跑本门禁；删除门禁请同步下调 MIN_BLOCKING 并说明理由。")
        return 1 if args.strict else 0

    print(
        f"{GREEN}✅ 门禁存活：分支 {branch or '(未知)'} 在触发器内；"
        f"backend-quality blocking 门禁 {len(blocking)} 条（基线 {MIN_BLOCKING}）；"
        f"空壳步骤 0 条{RESET}"
    )
    for n in notes:
        print(f"   ℹ️  {n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
