#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-frontend-route-param.py  (G31 · PRD v0.33 §6.1)

判据：元数据「数据」页 RuntimeDataManagement 必须消费 HashRouter 的 `:id` 参数，
     把实体上下文绑定到路由，而非「只读 ?entity= 全局第一个」——否则会静默看错实体的数据
     （融合方案 C3 / W0-1）。

机器可验（变异验证）：
  · 删掉 `const { id: pathId } = useParams<...>()`  →  `pathId` / `resolvedId` 缺失 ⇒ 本门禁变红；
  · 删掉 `resolvedId = embeddedEntityId ?? pathId ?? null` → `resolvedId` 缺失 ⇒ 变红；
  · 把 `if (resolvedId) return;` 改回「无条件加载全局选择器」 → 变红。
⇒ 任何把 `:id` 绑定回退到旧「静默全局」写法的改动都会被本门禁抓出。

用法：python3 scripts/check-frontend-route-param.py [--root <repo-root>]
"""
import os
import re
import sys

TARGET_REL = "bone-frontend/apps/bone-metadata-app/src/pages/RuntimeDataManagement.tsx"


def fail(msg):
    print(f"[G31] ❌ {msg}")
    sys.exit(1)


def main():
    root = None
    if "--root" in sys.argv:
        idx = sys.argv.index("--root")
        if idx + 1 < len(sys.argv):
            root = sys.argv[idx + 1]
    if root is None:
        # 取 git 仓库根
        here = os.path.dirname(os.path.abspath(__file__))
        root = (
            os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
            or here
        )

    path = os.path.join(root, TARGET_REL)
    if not os.path.isfile(path):
        fail(f"找不到目标文件：{TARGET_REL}（root={root}）")

    with open(path, "r", encoding="utf-8") as fh:
        content = fh.read()

    checks = []

    # 1) 必须引入并使用 useParams
    checks.append(("导入/调用 useParams", "useParams" in content))

    # 2) 必须从 useParams 解构出 id（容忍 `useParams<{ id` 或 `{ id: xxx }` 两种写法）
    has_id_destructure = bool(
        re.search(r"useParams<\s*\{[^}]*\bid\b", content)
        or re.search(r"\{\s*id\s*:\s*\w+", content)
    )
    checks.append(("从 useParams 解构出 id", has_id_destructure))

    # 3) 必须存在 pathId（解构出的局部变量名，变异后会消失）
    checks.append(("存在 pathId 局部变量", "pathId" in content))

    # 4) 必须存在 embeddedEntityId（嵌入详情「数据」Tab 的入参，变异后会消失）
    checks.append(("存在 embeddedEntityId 入参", "embeddedEntityId" in content))

    # 5) 必须存在 resolvedId（把 id 绑定到实体解析结果，变异后会消失）
    checks.append(("存在 resolvedId 绑定", "resolvedId" in content))

    # 6) resolvedId 必须确实由 pathId 派生（防止「声明了但不使用」的假绿）
    binds_param = bool(re.search(r"resolvedId\s*=\s*[^;]*pathId", content))
    checks.append(("resolvedId 由 pathId 派生", binds_param))

    # 7) resolvedId 必须用于短路全局选择器（即「绑了 id 就直接用，不落全局默认」）
    uses_resolved = bool(re.search(r"if\s*\(\s*resolvedId\s*\)", content))
    checks.append(("resolvedId 用于短路全局选择器", uses_resolved))

    ok = True
    for name, passed in checks:
        mark = "✅" if passed else "❌"
        print(f"  {mark} {name}")
        if not passed:
            ok = False

    if not ok:
        fail(
            "RuntimeDataManagement 未正确绑定路由 :id —— 存在「静默看错实体数据」回归风险。"
            "需保留 useParams→pathId→resolvedId→短路全局选择器 的绑定链。"
        )
    print("[G31] ✅ 路由 :id 绑定链完整（数据 Tab 不会静默串实体）")


if __name__ == "__main__":
    main()
