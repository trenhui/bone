#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T6 质量度量（report-only，不阻断，不接 CI）。

把《Bone AI 原生工程基础设施改造方案》11.7.2「质量度量口径模板」落成可运行脚本。

口径对齐 11.7.2：
  设计一次通过率     ← 待补（需 Comet state + PR 勾选项）         [反模式：不用 LOC 代理]
  AI 缺陷逃逸率      ← 待补（需 PR 勾选项 + 缺陷来源标签）       [反模式：不用接受率代理]
  人工返工率         ← 待补（需 PR 勾选项「是否发生人工返工」）
  DORA·部署频率      ← git：窗口内 origin/master 提交数（proxy：合入即发布单元）
  DORA·变更前置时间  ← 待补（dev→master 为 fast-forward，git 无法还原特性分支前置时间）
  DORA·变更失败率    ← git：窗口内 revert/hotfix/fixup/回滚 关键词提交占比（估算）
  DORA·服务恢复时间  ← 待补（需 incident/告警系统数据）

纪律（11.7.2）：① 团队级视角，禁止个人排名；② 满意度类给 6 个月窗口；
③ 接受率 / LOC 列为「明确不采用」反模式；④ 先盘点 Comet state / PR 勾选项 / 熔断计数
三类是否够用，再决定是否补最小采集字段。

退出码恒为 0（report-only）。

用法:
  python3 scripts/analyze-metrics.py [--days 90] [--json]
"""
import argparse
import json
import re
import subprocess
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
HOTFIX_RE = re.compile(r"\b(revert|hotfix|fixup|回滚|hot-fix)\b", re.I)


def run(cmd):
    return subprocess.run(cmd, cwd=str(ROOT), capture_output=True, text=True)


def git_log(since, extra=None):
    cmd = ["git", "log", f"--since={since}", "--format=%H%x1f%aI%x1f%s"]
    if extra:
        cmd += extra
    cmd += ["origin/master"]
    r = run(cmd)
    if r.returncode != 0:
        raise RuntimeError(f"git log failed: {r.stderr.strip()}")
    rows = []
    for line in r.stdout.splitlines():
        if not line.strip():
            continue
        h, ai, s = line.split("\x1f", 2)
        rows.append((h, ai, s))
    return rows


def tags_in_window(since_dt):
    r = run([
        "git", "for-each-ref", "--format=%(creatordate:iso-strict) %(refname:short)",
        "refs/tags",
    ])
    if r.returncode != 0:
        return []
    out = []
    for line in r.stdout.splitlines():
        if not line.strip():
            continue
        date_str, _, name = line.partition(" ")
        try:
            d = datetime.fromisoformat(date_str.replace("Z", "+00:00"))
        except Exception:
            continue
        if d >= since_dt:
            out.append(name)
    return out


def analyze(days):
    since_dt = datetime.now(timezone.utc) - timedelta(days=days)
    since = since_dt.strftime("%Y-%m-%d")
    rows = git_log(since)
    n = len(rows)

    hotfix = [r for r in rows if HOTFIX_RE.search(r[2])]
    fail_rate = (len(hotfix) / n) if n else 0.0

    tags = tags_in_window(since_dt)

    # 满意度/AI 类指标：需外部数据源，严格待补（不编造）
    return {
        "window_days": days,
        "since": since,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "dora": {
            "deploy_frequency": {
                "value": n,
                "unit": "commits-to-master",
                "note": "proxy：当前 dev→master 为 fast-forward，每次合入视作一个发布单元",
            },
            "lead_time_for_changes": {
                "value": "待补",
                "note": "dev→master 为 fast-forward，git 历史无法还原特性分支前置时间；"
                        "精确值需 CI/PR 系统提供合并耗时",
            },
            "change_failure_rate": {
                "value": round(fail_rate, 4),
                "unit": "ratio",
                "sample_hotfix_commits": len(hotfix),
                "note": "估算（基于提交消息关键词 revert/hotfix/fixup/回滚）；"
                        "非真实 incident 口径",
            },
            "time_to_restore": {
                "value": "待补",
                "note": "需 incident/告警系统数据",
            },
        },
        "ai_assisted": {
            "design_first_pass_rate": {"value": "待补", "note": "需 Comet state + PR 勾选项"},
            "ai_defect_escape_rate": {
                "value": "待补",
                "note": "需 PR 勾选项 + 缺陷来源标签；反模式：不用接受率代理",
            },
            "manual_rework_rate": {
                "value": "待补",
                "note": "需 PR 勾选项「是否发生人工返工」",
            },
        },
        "release_tags_in_window": tags,
        "anti_patterns_excluded": ["接受率（虚荣指标）", "LOC（零效用）", "个人排名"],
        "discipline": "团队级视角；满意度类给 6 个月窗口；先盘点 Comet state/PR 勾选项/"
                     "熔断计数三类是否够用，再决定是否补最小采集字段",
    }


def print_report(r):
    print("=" * 72)
    print("T6 质量度量报告（report-only）")
    print(f"  窗口 = 近 {r['window_days']} 天（自 {r['since']}）")
    print(f"  生成时间 = {r['generated_at']}")
    print("=" * 72)
    d = r["dora"]
    print("\n[DORA 四指标]")
    print(f"  部署频率        ：{d['deploy_frequency']['value']} {d['deploy_frequency']['unit']}")
    print(f"                    {d['deploy_frequency']['note']}")
    print(f"  变更前置时间    ：{d['lead_time_for_changes']['value']}")
    print(f"                    {d['lead_time_for_changes']['note']}")
    print(f"  变更失败率      ：{d['change_failure_rate']['value']} "
          f"(样本 hotfix/fixup 提交 {d['change_failure_rate']['sample_hotfix_commits']})")
    print(f"                    {d['change_failure_rate']['note']}")
    print(f"  服务恢复时间    ：{d['time_to_restore']['value']}")
    print(f"                    {d['time_to_restore']['note']}")
    print("\n[AI 辅助质量指标]")
    a = r["ai_assisted"]
    for k, label in [
        ("design_first_pass_rate", "设计一次通过率"),
        ("ai_defect_escape_rate", "AI 缺陷逃逸率"),
        ("manual_rework_rate", "人工返工率"),
    ]:
        print(f"  {label}：{a[k]['value']}")
        print(f"                  {a[k]['note']}")
    if r["release_tags_in_window"]:
        print(f"\n[窗口内发布标签] {', '.join(r['release_tags_in_window'])}")
    else:
        print("\n[窗口内发布标签] 无（或 tag 无 creatordate）")
    print(f"\n明确不采用的反模式：{', '.join(r['anti_patterns_excluded'])}")
    print(f"纪律：{r['discipline']}")


def main():
    ap = argparse.ArgumentParser(description="T6 质量度量（report-only）")
    ap.add_argument("--days", type=int, default=90, help="统计窗口天数（默认 90）")
    ap.add_argument("--json", action="store_true", help="输出 JSON")
    args = ap.parse_args()
    try:
        r = analyze(args.days)
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
