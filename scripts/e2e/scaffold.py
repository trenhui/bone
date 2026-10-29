#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""新用例脚手架 —— 让「新增一条 e2e」= 30 秒，而不是抄一个旧脚本再踩一遍坑。

为什么需要它
------------
整合前后真正的差距不在「有没有共享库」，而在**新 agent 能不能不读历史就写对**。
实测反复出现的三个漂移入口（每出现一次就要花一轮排查）：

  1. 地址/口令抄进脚本 —— 换环境要改 N 个文件，且踩 gitleaks；
  2. 造数随手写「张三 / 900.00 / 单号 P1」—— 与 fixtures 口径不一致时，
     上下游对不上，红了分不清是数据问题还是代码问题；
  3. 断言只写「应该 200」不写实测值 —— 红了只能重跑一遍加日志。

模板把这三件事做成**默认正确**的骨架；scaffold 再补一道「生成即校验」
（py_compile / node --check），避免带病入库。

用法::

    python3 scripts/e2e/scaffold.py list
    python3 scripts/e2e/scaffold.py new py-sweep my-suite --desc "扫描 XX 端点" --register
    python3 scripts/e2e/scaffold.py new py-chain  my-chain --desc "XX 全链路" --register
    python3 scripts/e2e/scaffold.py new ui-probe  my-probe --desc "XX 页面回归"
    #   ↑ UI 探针落点为 ui/probe-my-probe.mjs，`run.sh node` 按 ui/probe-*.mjs 枚举，无需登记

    # 也可经统一入口（推荐，与 lint/selftest 同族）：
    ./run.sh new py-sweep my-suite --desc "..."

约束
----
  * 名字必须 kebab-case（只影响文件名与 JUnit 套件名；脏字符会写出坏 XML）；
  * 目标文件已存在时**默认不动**，须显式 --force（防误覆盖他人在途用例）；
  * 写完立即语法校验，不过就非零退出（不让坏文件进 lint.sh 才被发现）。
"""
import argparse
import datetime
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TPL_DIR = os.path.join(HERE, "templates")

# kind -> (模板文件, 目标目录, 文件名前缀, 目标扩展名, 归属 run.sh 分组数组名)
# UI 探针统一 `probe-` 前缀：`run.sh node` 是按 `ui/probe-*.mjs` 枚举的，
# 带前缀 = **生成即被 CI 分组收录**，不需要再手工改 run.sh。
KINDS = {
    "py-sweep": ("py-sweep.py.tpl", HERE, "", ".py", "PY_SWEEP"),
    "py-chain": ("py-chain.py.tpl", HERE, "", ".py", "PY_CHAIN"),
    # py-core 是「全链路三样板」，属既定业务资产，不开放模板生成（避免命名撞车）
    "ui-probe": ("ui-probe.mjs.tpl", os.path.join(HERE, "ui"), "probe-", ".mjs", None),
}

_NAME_RE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")


def _target_of(kind, name):
    """算出落点绝对路径与仓库相对路径。"""
    _, dest, prefix, ext, _ = KINDS[kind]
    filename = prefix + name + ext
    return os.path.join(dest, filename), os.path.relpath(os.path.join(dest, filename), HERE)


def _render_for(kind, text, name, desc):
    """占位符替换：`{{FILE}}` / `{{NAME}}` / `{{DESC}}` / `{{SUITE}}` / `{{DATE}}`。"""
    _, rel = _target_of(kind, name)
    return (
        text.replace("{{FILE}}", os.path.basename(rel))
        .replace("{{NAME}}", name)
        .replace("{{DESC}}", desc)
        .replace("{{SUITE}}", os.path.basename(rel)[: -len(_ext_of(kind))])
        .replace("{{DATE}}", datetime.date.today().isoformat())
    )


def _ext_of(kind):
    return KINDS[kind][3]


def _verify(path, kind):
    """生成即校验：Python 走 py_compile，Node 走 node --check（与 lint.sh 同口径）。"""
    if kind == "ui-probe":
        node = (
            subprocess.run(["bash", "-lc", "command -v node"], capture_output=True, text=True)
            .stdout.strip()
            or "/Users/renhui.trh/.workbuddy/binaries/node/versions/22.22.2-6/bin/node"
        )
        if not os.path.exists(node):
            return True, f"node 未找到（{node}），跳过语法校验"
        r = subprocess.run([node, "--check", path], capture_output=True, text=True)
        return r.returncode == 0, (r.stderr or r.stdout).strip()[:400]
    r = subprocess.run([sys.executable, "-m", "py_compile", path], capture_output=True, text=True)
    return r.returncode == 0, (r.stderr or r.stdout).strip()[:400]


def cmd_list():
    print("可用骨架（kind）：")
    print(f"  {'kind':10s} {'落点':30s} {'run.sh 分组':12s} 模板")
    for kind, (tpl, dest, prefix, ext, group) in KINDS.items():
        rel = os.path.relpath(dest, HERE) or "."
        place = f"{rel}/{prefix}<name>{ext}"
        print(f"  {kind:10s} {place:30s} {group or 'node(自动)':12s} templates/{tpl}")
    print("\n怎么选：")
    print("  扫一批端点/实体、找缺失与不合规  → py-sweep")
    print("  一段有先后依赖的业务链路（A→B→C，含负向）→ py-chain")
    print("  必须在真实浏览器里验（渲染/交互/菜单/弹窗）→ ui-probe")
    print("\n例：./run.sh new py-chain claim-refund --desc \"理赔退款全链路\"")


def _register_in_runsh(kind, filename):
    """把新用例写进 run.sh 的分组数组（Python 分组专用；UI 探针按前缀自动收录）。

    只在**精确匹配到目标数组那一行**时才改；匹配不到就原样返回 False 让调用方提示
    手工加——绝不靠模糊替换去动一个自己不拥有的入口脚本。
    """
    _, _, _, _, group = KINDS[kind]
    if not group:
        return None  # UI 探针：run.sh node 按 ui/probe-*.mjs 枚举，无需登记
    runsh = os.path.join(HERE, "run.sh")
    with open(runsh, encoding="utf-8") as fh:
        src = fh.read()
    m = re.search(rf"^{group}=\((.*)\)$", src, re.M)
    if not m:
        return False
    if filename in m.group(1):
        return True
    new_line = f"{group}=({m.group(1).strip()} {filename})"
    with open(runsh, "w", encoding="utf-8") as fh:
        fh.write(src[: m.start()] + new_line + src[m.end():])
    return True


def cmd_new(kind, name, desc, force, register):
    if kind not in KINDS:
        print(f"未知 kind: {kind}（可选：{' / '.join(KINDS)}）", file=sys.stderr)
        return 2
    if not _NAME_RE.match(name):
        print(f"名字须为 kebab-case（小写字母/数字/单连字符），实际: {name!r}", file=sys.stderr)
        return 2
    tpl_name, _, _, _, _ = KINDS[kind]
    tpl_path = os.path.join(TPL_DIR, tpl_name)
    if not os.path.isfile(tpl_path):
        print(f"模板缺失: {tpl_path}", file=sys.stderr)
        return 2
    target, rel = _target_of(kind, name)
    filename = os.path.basename(target)
    if os.path.exists(target) and not force:
        print(f"目标已存在（加 --force 覆盖）：{rel}", file=sys.stderr)
        return 1

    with open(tpl_path, encoding="utf-8") as fh:
        content = _render_for(kind, fh.read(), name, desc)
    with open(target, "w", encoding="utf-8") as fh:
        fh.write(content)
    print(f"✓ 已生成 {rel}")

    ok, why = _verify(target, kind)
    if not ok:
        print(f"✗ 生成即校验未通过（文件已保留，请修后再跑 lint）：\n{why}", file=sys.stderr)
        return 1
    print(f"✓ 语法校验通过（{'node --check' if kind == 'ui-probe' else 'py_compile'}）")

    # 登记：UI 探针靠 probe- 前缀自动进分组；Python 用例默认只提示，--register 才落笔
    state = _register_in_runsh(kind, filename) if register else None
    if state is None and kind != "ui-probe" and not register:
        pass  # 未要求登记：下一步里给命令
    elif state is True:
        print(f"✓ 已登记进 run.sh（{'node 分组按前缀自动收录' if kind == 'ui-probe' else '分组数组'}）")
    elif state is False:
        print("! run.sh 未找到目标分组行，请手工把文件名加进对应数组")

    print("\n下一步：")
    if kind == "ui-probe":
        print("  1) 填断言：搜模板里的「你的场景断言写在下面」")
        print("  2) 跑：`export NODE_PATH=~/.workbuddy/binaries/node/workspace/node_modules && "
              f"node scripts/e2e/{rel}`")
        print("  3) `run.sh node` 按 ui/probe-*.mjs 枚举，本文件已自动在分组内")
    else:
        print("  1) 填 TARGETS / build_steps：替换模板里的样板端点")
        print(f"  2) 跑：`python3 scripts/e2e/{rel}`")
        if state is True:
            print("  3) 已进 run.sh 分组，无需再手工登记")
        else:
            print(f"  3) 纳入 CI：`./run.sh new {kind} {name} --register`（或手工把 "
                  f"'{filename}' 加进 run.sh 的 {KINDS[kind][4]} 数组）")
    print("  4) 跑一遍 `./run.sh lint && ./run.sh selftest`：门禁全绿才算收口")
    return 0


def main():
    ap = argparse.ArgumentParser(description="E2E 新用例脚手架")
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="列出可用骨架与选择指引")

    new = sub.add_parser("new", help="生成一个新用例")
    new.add_argument("kind", choices=sorted(KINDS))
    new.add_argument("name", help="用例名（kebab-case），同时是文件名与 JUnit 套件名")
    new.add_argument("--desc", default="TODO：一句话说清这条用例要证明什么",
                     help="用例描述（写进文件头）")
    new.add_argument("--force", action="store_true", help="目标已存在时覆盖")
    new.add_argument("--register", action="store_true",
                     help="把新用例写进 run.sh 的分组数组（Python 用例；UI 探针按前缀自动收录）")

    args = ap.parse_args()
    if args.cmd == "list":
        cmd_list()
        return 0
    return cmd_new(args.kind, args.name, args.desc, args.force, args.register)


if __name__ == "__main__":
    raise SystemExit(main())
