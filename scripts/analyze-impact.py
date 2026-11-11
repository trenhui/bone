#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T4 变更影响面分析（report-only，不阻断，不接 CI）。

把《Bone AI 原生工程基础设施改造方案》11.7.1「变更影响面报告字段模板」落成可运行脚本。
基于 git diff 做**静态**影响面分析，输出结构化报告。

字段映射（严格对齐 11.7.1）：
  变更符号         ← git diff -U0 + hunk 行号映射（类 / 方法）
  直接调用者       ← 静态引用检索（rg），标注覆盖率；本环境无 CodeGraph ⇒ 标注「静态检索」
  静态盲区清单     ← 显式列出：通用名噪声、反射/动态分派无法静态覆盖
  相关 ADR         ← doc/architecture/adr 中引用了该符号的文件
  运行时证据       ← 恒为「待补」（需 Trace / 事件 Topic 订阅 / 配置驱动依赖数据，严禁编造）
  受影响测试提示   ← 消费方 = 含该符号的 *Test.java / src/test 引用（仅提示，不构成测试依据）
  不确定性标注     ← 每个变更符号给 高/中/低 置信 + 理由（呼应概率性 IA）

fail-closed：影响面未知（无差异）→ 提示「扩大回归至 E2E 全量」；高风险路径
（claim/quota/permission/ddl/migration/event/iam/security/auth）固定附加「E2E 全量回归」。

退出码恒为 0（report-only）。

用法:
  python3 scripts/analyze-impact.py [--base HEAD~1] [--head HEAD] [--json]
"""
import argparse
import json
import os
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ADR_DIR = ROOT / "doc" / "architecture" / "adr"

CLASS_RE = re.compile(
    r"(?:public\s+)?(?:final\s+)?(?:abstract\s+)?(?:class|interface|enum)\s+([A-Z]\w+)"
)
METHOD_RE = re.compile(
    r"(?:public|private|protected|static|final|\s)+[\w<>\[\],\s\.]+\s+([a-z]\w*)\s*\("
)
KEYWORDS = {
    "if", "for", "while", "switch", "catch", "synchronized", "return",
    "do", "else", "try", "new", "throw",
}
HIGH_RISK = re.compile(
    r"(claim|quota|permission|ddl|migration|event|iam|security|auth|settlement|fund)",
    re.I,
)
GENERIC = {"get", "set", "do", "run", "add", "put", "find", "save", "load", "build", "create", "update", "delete", "check", "is", "to", "from"}


def run(cmd):
    r = subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True)
    return r


def git(*args):
    r = run(["git", *args])
    if r.returncode != 0:
        raise RuntimeError(f"git {' '.join(args)} -> {r.stderr.strip()}")
    return r.stdout


def find_references(name):
    """返回全仓库 *.java 中含该标识符（词边界）的行：[path:line:content, ...]。

    rg 优先（快、支持 \\b）；二进制缺失或失败时退回 git grep（走索引、全环境可用）。
    """
    wb = rf"\b{re.escape(name)}\b"
    try:
        r = run(["rg", "-n", "--glob", "*.java", "-e", wb, str(ROOT)])
    except FileNotFoundError:
        r = None
    if r is not None and r.returncode == 0:
        return r.stdout.splitlines()
    # git grep 兜底：POSIX ERE 词边界近似（[^A-Za-z0-9_] 而非 \\b，兼容 BSD/macOS）
    pat = rf"(^|[^A-Za-z0-9_]){re.escape(name)}($|[^A-Za-z0-9_])"
    g = run(["git", "grep", "-n", "-E", "-e", pat, "--", "*.java"])
    if g.returncode == 0:
        return g.stdout.splitlines()
    return []


def changed_java_files(base, head):
    out = git("diff", "--name-only", "--diff-filter=ACMR", f"{base}...{head}", "--", "*.java")
    return [f for f in out.splitlines() if f.strip()]


def _norm(path):
    pre = str(ROOT) + os.sep
    return path[len(pre):] if path.startswith(pre) else path


def classify_callers(name, defining_file):
    hits = find_references(name)
    callers_main, callers_test = [], []
    for h in hits:
        parts = h.split(":", 2)
        path = _norm(parts[0])
        if path == defining_file:
            continue
        if "/test/" in path or "src/test" in path or path.endswith("Test.java"):
            callers_test.append(h)
        else:
            callers_main.append(h)
    return callers_main, callers_test


def extract_symbols(base, head):
    """从 diff -U0 的 added 行提取类/方法符号。"""
    symbols = []
    for f in changed_java_files(base, head):
        diff = git("diff", "-U0", f"{base}...{head}", "--", f)
        for line in diff.splitlines():
            if not line.startswith("+"):
                continue
            body = line[1:].strip()
            m = CLASS_RE.search(body)
            if m:
                symbols.append(("class", m.group(1), f))
                continue
            m = METHOD_RE.search(body)
            if m and m.group(1) not in KEYWORDS:
                symbols.append(("method", m.group(1), f))
    return symbols


def classify_callers(name, defining_file):
    hits = find_references(name)
    callers_main, callers_test = [], []
    for h in hits:
        # rg 输出: path:line:col:content 或 path:line:content
        parts = h.split(":", 2)
        path = parts[0]
        if path == defining_file:
            continue
        if "/test/" in path or "src/test" in path or path.endswith("Test.java"):
            callers_test.append(h)
        else:
            callers_main.append(h)
    return callers_main, callers_test


def related_adrs(name):
    if len(name) <= 3 or name.lower() in GENERIC:
        return []
    if not ADR_DIR.exists():
        return []
    found = []
    for adr in ADR_DIR.glob("*.md"):
        try:
            text = adr.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if re.search(rf"\b{re.escape(name)}\b", text):
            found.append(adr.name)
    return found[:5]


def uncertainty(name, n_callers):
    if n_callers == 0:
        return "低", "无静态引用：可能为入口/数据对象/覆写或新符号，需运行时确认"
    if name.lower() in GENERIC or len(name) <= 3:
        return "低", f"名称过通用，静态匹配噪声高（{n_callers} 处疑似引用需人工甄别）"
    if n_callers > 30:
        return "低", f"引用数异常高（{n_callers}），多为同名/子串噪声，需按文件路径+签名二次甄别"
    return "中", f"存在 {n_callers} 处静态引用，置信中等（动态分派/反射可能漏判）"


def analyze(base, head):
    files = changed_java_files(base, head)
    if not files:
        return {
            "base": base,
            "head": head,
            "changed_java_files": [],
            "symbols": [],
            "fail_closed": "影响面未知（无 Java 差异）→ 扩大回归至 E2E 全量",
        }
    symbols = extract_symbols(base, head)
    out_syms = []
    high_risk_hit = False
    for kind, name, f in symbols:
        risk = HIGH_RISK.search(f) is not None
        high_risk_hit = high_risk_hit or risk
        cm, ct = classify_callers(name, f)
        conf, reason = uncertainty(name, len(cm) + len(ct))
        out_syms.append({
            "kind": kind,
            "symbol": name,
            "file": f,
            "high_risk_path": risk,
            "callers_main": cm,
            "callers_test": ct,
            "caller_count": len(cm) + len(ct),
            "related_adrs": related_adrs(name),
            "runtime_evidence": "待补（需 Trace 覆盖 / 事件 Topic 订阅 / 配置驱动依赖数据）",
            "uncertainty": {"level": conf, "reason": reason},
        })
    return {
        "base": base,
        "head": head,
        "changed_java_files": files,
        "symbols": out_syms,
        "static_blind_spots": [
            "反射 / 动态代理 / AOP 无法静态覆盖",
            "通用方法名（get/set/do/run…）静态匹配噪声高",
            "运行时依赖（配置驱动、事件订阅）需 Trace 数据补全",
        ],
        "fail_closed": (
            "高风险路径命中 ⇒ 固定附加「E2E 全量回归」"
            if high_risk_hit else
            "影响面未知 ⇒ 扩大回归至 E2E 全量"
        ),
    }


def print_report(r):
    print("=" * 72)
    print("T4 变更影响面报告（report-only）")
    print(f"  base = {r['base']}   head = {r['head']}")
    print(f"  生成时间 = {datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}")
    print("=" * 72)
    if not r["changed_java_files"]:
        print("⚠️ 无 Java 差异 →", r["fail_closed"])
        return
    print(f"\n变更 Java 文件数：{len(r['changed_java_files'])}")
    for f in r["changed_java_files"]:
        print(f"  · {f}")
    print(f"\n变更符号数：{len(r['symbols'])}")
    for s in r["symbols"]:
        tag = " [高风险路径]" if s["high_risk_path"] else ""
        print(f"\n  ▶ {s['kind']} {s['symbol']}  @ {s['file']}{tag}")
        print(f"    直接调用者 ：主代码 {len(s['callers_main'])} / 测试 {len(s['callers_test'])}（静态检索）")
        for c in s["callers_main"][:8]:
            print(f"        main  {c}")
        for c in s["callers_test"][:4]:
            print(f"        test  {c}")
        print(f"    运行时证据：{s['runtime_evidence']}")
        print(f"    不确定性  ：{s['uncertainty']['level']} — {s['uncertainty']['reason']}")
        if s["related_adrs"]:
            print(f"    相关 ADR  ：{', '.join(s['related_adrs'])}")
    print("\n静态盲区清单（显式列出，不隐藏）：")
    for b in r["static_blind_spots"]:
        print(f"  · {b}")
    print(f"\nfail-closed：{r['fail_closed']}")


def main():
    ap = argparse.ArgumentParser(description="T4 变更影响面分析（report-only）")
    ap.add_argument("--base", default="HEAD~1", help="差异基准（默认 HEAD~1）")
    ap.add_argument("--head", default="HEAD", help="差异终点（默认 HEAD）")
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()
    try:
        r = analyze(args.base, args.head)
    except RuntimeError as e:
        print(f"❌ {e}", file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(r, ensure_ascii=False, indent=2))
    else:
        print_report(r)
    return 0  # report-only：恒 0


if __name__ == "__main__":
    sys.exit(main())
