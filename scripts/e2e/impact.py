#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""接口变更 ↔ e2e 用例 影响分析 —— 把「改了接口要补/改用例」变成可执行判据。

解决什么问题
------------
后端 Controller 改了路径、加了字段、删了接口，e2e 用例往往没人同步；等到联调才发现
「用例调的是旧接口」。靠人记纪律不可靠，本工具提供三件可自动化的能力：

  1. **建立映射**：扫描全部 `*Controller.java` 的 HTTP 端点，与 `scripts/e2e/**` 里
     实际调用过的路径做匹配，输出 `impact-map.json`（端点 → 覆盖用例）。
  2. **变更体检**：只针对本次 diff 里真正改到的映射行（`@GetMapping(...)` 等；类级
     `@RequestMapping` 变动视为整文件触及），列出「端点已有用例 / 端点零覆盖」。
     `--strict` 时零覆盖直接退出 1 —— 提交/推送前即可阻断。**不**把改动 Controller 里
     历史遗留的零覆盖端点一起算进来，否则门禁天天误伤、最终会被整体关掉。
  3. **给出下一步**：零覆盖端点生成可复制的脚手架命令，避免「知道要补但不知道怎么补」。

匹配口径
--------
路径按段比较：`{}`（含 `{id}` / `${id}`）视为通配；用例路径可以比端点短，但端点多出来的
段必须全是变量段（`/orders` 覆盖 `/orders/{id}`，不覆盖 `/orders/{id}/cancel`）。
两侧先剥网关前缀（`/api/v1`、`/v1`）：端点是 `@RequestMapping` 的绝对路径，用例写的是
相对 `config.BASE` 的路径；不剥前缀，真实存在的用例会被判「零覆盖」，反过来逼人再写一份
重复脚本。绝不因为「同一个 Controller 有用例」就认为所有端点都被覆盖。

用法::

    python3 scripts/e2e/impact.py                     # 全量映射报告
    python3 scripts/e2e/impact.py --staged --strict    # pre-commit：只看暂存
    python3 scripts/e2e/impact.py --rev origin/main...HEAD --strict   # pre-push
    python3 scripts/e2e/impact.py --write-map          # 重新生成 impact-map.json
"""
import argparse
import json
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
EXTRACTOR = os.path.join(HERE, "_extract_endpoints.py")
MAP_FILE = os.path.join(HERE, "impact-map.json")

CASE_SUFFIXES = (".py", ".mjs", ".cjs", ".sh")
# 不参与「用例覆盖」统计的目录：共享库无业务路径；templates 是占位路径，会把覆盖率刷成假绿
SKIP_DIRS = {"e2e_lib", "templates", "logs", "__pycache__", "node_modules"}
SKIP_FILES = {"_extract_endpoints.py", "impact.py", "check-data-realism.py", "datagen.py"}

# 允许带查询串：用例实际会写 "/api/v1/orders?page=1"，若把 `?` 排除在外，
# 带分页的调用就匹配不到，端点会被误判成「零覆盖」。查询串由 normalize_path 剥掉。
PATH_LITERAL = re.compile(r"""["'`](/[A-Za-z0-9_\-./{}$:?=&%,+]*?)["'`]""")
# 变更行里真正改到接口的行：方法级映射（新增/改路径/删接口）。类级 @RequestMapping
# 改动会影响整个 Controller 的所有端点，视为「整文件触及」。
MAPPING_LINE = re.compile(r"@(?:Get|Post|Put|Delete|Patch)Mapping\b")
CLASS_MAPPING_LINE = re.compile(r"@RequestMapping\b")
QUOTED = re.compile(r'"([^"]*)"')


def normalize_path(path):
    """统一路径形态：去查询串/末尾斜杠，变量段归一成 `{}`（保留网关前缀，见 `_segments`）。"""
    path = path.split("?", 1)[0].strip()
    if len(path) > 1:
        path = path.rstrip("/")
    path = re.sub(r"\{[^}]*\}", "{}", path)
    path = re.sub(r"\$\{[^}]*\}", "{}", path)
    return path or "/"


# 网关前缀：端点是绝对路径（/api/v1/claims/...），用例写的是相对 config.BASE 的
# 路径（/claims/...）。不剥掉它，两边永远对不上：真实存在的用例被判「零覆盖」，
# 反过来逼人再写一份重复脚本——正是「每个 agent 各造一套」的老毛病。
API_PREFIX = re.compile(r"^(?:api/)?v\d+(?:/|$)")


def _api_relative(path):
    """把 `/api/v1/x`、`/v1/x` 收敛成 `x`；不带前缀的原样返回。"""
    p = normalize_path(path).lstrip("/")
    return p[API_PREFIX.match(p).end():] if API_PREFIX.match(p) else p


def _segments(path):
    return [s for s in _api_relative(path).split("/") if s]


def case_serves(case_path, endpoint_path):
    """用例里出现的路径是否真的打到了这个端点（按段比较）。

    <p><b>变量只配变量</b>：`{}` 不是万能通配，只与端点里的 `{}` 配对；字面段必须逐段相等。
    早期版本写成「任一侧是 `{}` 就算命中」，于是用例里的 `/claims/{cid}/adjust/claim`
    被记成覆盖了 `/claims/tasks/{taskId}/claim`——两条完全不同的接口，只因第二段是变量。
    这类假阳性比漏报更糟：它让"没测过的端点"显示已覆盖，门禁据此放行。
    """
    case_segs = _segments(case_path)
    ep_segs = _segments(endpoint_path)
    if not case_segs:
        return False
    for i, seg in enumerate(case_segs):
        if i >= len(ep_segs):
            return False
        ep = ep_segs[i]
        if seg == "{}" and ep == "{}":
            continue
        if seg != ep:
            return False
    # 用例短于端点：多出来的端点段必须全是变量段，否则是不同资源的父路径
    return all(seg == "{}" for seg in ep_segs[len(case_segs):])


def iter_case_files(root=HERE):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(
            d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")
        )
        for name in sorted(filenames):
            if name in SKIP_FILES or not name.endswith(CASE_SUFFIXES):
                continue
            yield os.path.join(dirpath, name), os.path.relpath(os.path.join(dirpath, name), root)


def collect_case_paths(root=HERE):
    """{相对路径: [归一化后的路径...]}，只保留以 `/` 开头的字面量。"""
    out = {}
    for full, rel in iter_case_files(root):
        try:
            with open(full, encoding="utf-8", errors="ignore") as fh:
                text = fh.read()
        except OSError:
            continue
        paths = sorted({normalize_path(m.group(1)) for m in PATH_LITERAL.finditer(text)})
        if paths:
            out[rel.replace(os.sep, "/")] = paths
    return out


def load_endpoints(endpoints_file=None):
    if endpoints_file:
        with open(endpoints_file, encoding="utf-8") as fh:
            return json.load(fh)
    proc = subprocess.run(
        [sys.executable, EXTRACTOR], cwd=REPO, capture_output=True, text=True
    )
    if proc.returncode != 0:
        raise RuntimeError("端点提取失败：%s" % (proc.stderr.strip() or proc.returncode))
    return json.loads(proc.stdout or "[]")


def map_endpoints(endpoints, case_paths):
    """[(endpoint, [覆盖用例相对路径...])]，覆盖判定走 case_serves。"""
    rows = []
    for ep in endpoints:
        covered = sorted(
            rel for rel, paths in case_paths.items()
            if any(case_serves(p, ep["path"]) for p in paths)
        )
        rows.append((ep, covered))
    return rows


def changed_java_files(mode, rev=None):
    if mode == "cached":
        cmd = ["git", "-C", REPO, "diff", "--cached", "--name-only", "--diff-filter=ACMR"]
    elif mode == "rev":
        cmd = ["git", "-C", REPO, "diff", "--name-only", "--diff-filter=ACMR", rev]
    else:
        cmd = ["git", "-C", REPO, "status", "--porcelain"]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError("git 调用失败：%s" % proc.stderr.strip())
    if mode == "status":
        files = [line[3:] for line in proc.stdout.splitlines() if len(line) > 3]
    else:
        files = proc.stdout.splitlines()
    return sorted({f.strip() for f in files if f.strip().endswith(".java")})


def touched_endpoints(mode, rev, changed_files):
    """本次 diff 真正改到的接口：{文件: None | set(路径后缀)}。

    None 表示「整文件触及」（类级 @RequestMapping 变了，或该模式无法取 diff）。
    这样 --strict 只要求**本次改动引入/修改的端点**有用例，而不是把改动 Controller
    里历史遗留的零覆盖端点一起算进来——否则门禁天天误伤，最终只会被整体关掉。
    """
    if mode == "status" or not changed_files:
        return {}
    cmd = ["git", "-C", REPO, "diff", "--unified=0"]
    cmd += ["--cached"] if mode == "cached" else [rev]
    cmd += ["--"] + list(changed_files)
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise RuntimeError("git diff 失败：%s" % proc.stderr.strip())

    touched = {}
    current = None
    for line in proc.stdout.splitlines():
        if line.startswith("+++ b/"):
            current = line[6:].strip()
            touched.setdefault(current, set())
            continue
        if not line.startswith(("+", "-")) or line.startswith(("+++", "---")):
            continue
        body = line[1:]
        if CLASS_MAPPING_LINE.search(body):
            if current:
                touched[current] = None      # 整文件触及
            continue
        if not MAPPING_LINE.search(body):
            continue
        if current is None:
            continue
        if touched.get(current) is None:
            continue
        literals = [v for v in QUOTED.findall(body) if v.startswith("/")]
        if not literals:
            touched[current] = None          # 无字面量（@GetMapping / 常量拼接）→ 无法精确定位
        else:
            touched[current].update(normalize_path(v) for v in literals)
    return touched


def endpoint_touched(ep, touched):
    """端点是否在本次变更范围内：文件被改 + 命中具体映射后缀（或整文件触及）。"""
    f = ep.get("file")
    if f not in touched:
        return False
    suffixes = touched[f]
    if suffixes is None:
        return True
    ep_path = normalize_path(ep["path"])
    return any(ep_path.endswith(sfx) for sfx in suffixes)


def analyze(endpoints, case_paths, changed_files, touched=None):
    """把端点分成「变更涉及的」与其余；返回 (已覆盖, 未覆盖, 变更文件集合)。"""
    changed = {f.replace(os.sep, "/") for f in changed_files}
    rows = [r for r in map_endpoints(endpoints, case_paths) if r[0].get("file") in changed]
    if touched:
        rows = [r for r in rows if endpoint_touched(r[0], touched)]
    covered = [r for r in rows if r[1]]
    uncovered = [r for r in rows if not r[1]]
    return covered, uncovered, changed


def _stub_hint(ep):
    name = re.sub(r"[^a-zA-Z0-9]+", "-", ep["path"]).strip("-").lower() or "api"
    return "python3 scripts/e2e/run.sh new py-sweep %s --desc \"%s %s 覆盖\" --register" % (
        name, ep["http"], ep["path"])


def main(argv=None):
    parser = argparse.ArgumentParser(description="接口变更 ↔ e2e 用例影响分析")
    parser.add_argument("--staged", action="store_true", help="只看 git 暂存区（pre-commit）")
    parser.add_argument("--rev", help="git diff 范围（pre-push，如 origin/main...HEAD）")
    parser.add_argument("--status", action="store_true", help="看工作区未提交改动（默认）")
    parser.add_argument("--strict", action="store_true", help="变更端点零覆盖时退出 1")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--write-map", action="store_true", help="生成 impact-map.json")
    parser.add_argument("--print-case-files", action="store_true",
                        help="只输出受影响用例文件（每行一个），供 pre-push 直接消费")
    parser.add_argument("--endpoints-file", help="离线用例/测试用：直接读端点 JSON")
    parser.add_argument("--case-root", default=HERE, help="用例根目录（测试用）")
    args = parser.parse_args(argv)

    mode = "cached" if args.staged else ("rev" if args.rev else "status")
    try:
        endpoints = load_endpoints(args.endpoints_file)
        case_paths = collect_case_paths(args.case_root)
        changed = changed_java_files(mode, args.rev)
        touched = touched_endpoints(mode, args.rev, changed)
    except (RuntimeError, OSError, ValueError) as exc:
        print("错误: %s" % exc, file=sys.stderr)
        return 2

    rows = map_endpoints(endpoints, case_paths)
    covered, uncovered, changed_set = analyze(endpoints, case_paths, changed, touched)
    controllers = sorted({e["file"] for e in endpoints if e.get("file") in changed_set})

    if args.write_map:
        doc = {
            "_note": "由 scripts/e2e/impact.py --write-map 生成的派生文件，勿手改。",
            "endpoints": [
                {"http": ep["http"], "path": ep["path"], "controller": ep["controller"],
                 "file": ep.get("file"), "cases": cases}
                for ep, cases in rows
            ],
            "summary": {
                "endpoints": len(rows),
                "covered": sum(1 for _, c in rows if c),
                "uncovered": sum(1 for _, c in rows if not c),
            },
        }
        with open(MAP_FILE, "w", encoding="utf-8") as fh:
            json.dump(doc, fh, ensure_ascii=False, indent=1)
            fh.write("\n")
        print("已生成 %s（端点 %d，已覆盖 %d，零覆盖 %d）" % (
            os.path.relpath(MAP_FILE, REPO), doc["summary"]["endpoints"],
            doc["summary"]["covered"], doc["summary"]["uncovered"]))

    if args.print_case_files:
        for rel in sorted({c for _, cases in covered for c in cases}):
            print(rel)
        return 0

    if args.json:
        print(json.dumps({
            "mode": mode,
            "changed_java": changed,
            "changed_controllers": controllers,
            "covered": [{"http": e["http"], "path": e["path"], "cases": c} for e, c in covered],
            "uncovered": [{"http": e["http"], "path": e["path"], "controller": e["controller"]}
                          for e, _ in uncovered],
        }, ensure_ascii=False, indent=2))
    else:
        print("影响分析（%s）：变更 Java %d 个，其中 Controller %d 个" % (
            mode, len(changed), len(controllers)))
        if not changed:
            print("  本次没有 Java 变更，无需同步用例。")
        elif not controllers:
            print("  变更未落在 *Controller.java 上（DTO/Service 改动请人工确认对应用例）。")
        elif touched and not any(v is None or v for v in touched.values()):
            print("  改动未触及任何 @XxxMapping 行（纯实现/DTO 调整），无需同步用例。")
        else:
            print("\n  本次改动触及 %d 个端点，已覆盖 %d 个：" % (len(covered) + len(uncovered), len(covered)))
            for ep, cases in covered:
                print("    ✓ %-6s %-42s ← %s" % (ep["http"], ep["path"], ", ".join(cases[:3])
                                               + (" 等" if len(cases) > 3 else "")))
            if uncovered:
                print("\n  ❌ 零覆盖 %d 个端点（本次变更引入/涉及，必须补用例）：" % len(uncovered))
                for ep, _ in uncovered:
                    print("    ✗ %-6s %-42s [%s]" % (ep["http"], ep["path"], ep["controller"]))
                print("\n  建议命令（生成骨架后再补断言）：")
                for ep, _ in uncovered:
                    print("    " + _stub_hint(ep))

    if args.strict and uncovered:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
