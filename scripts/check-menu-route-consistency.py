#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-menu-route-consistency.py  (G32 · PRD v0.33 §6.1)

判据：遍历 0024 菜单种子里每个 `path#hash` 菜单项，断言其 hash 段在对应微应用的
      HashRouter 里有**真实落地路由**；命中 `<Route ... element={<ToEntities/>}/>` 或
      `<Navigate/>` 这类「302 兼容」路由的，视为死链菜单（点了跳别处，信息架构自欺）。

这正是「关系管理菜单点了跳模型管理」这类跨文件漂移的根因——此前 check.sh 30 步里
没有任何一步校验「菜单 ↔ 路由」一致性。

机器可验（变异验证）：在 0024 种子里新增一个不存在的 hash（如 `/metadata#/nonsense`）
      ⇒ 本门禁变红；撤掉即恢复 ⇒ 证明有牙齿。

用法：
  python3 scripts/check-menu-route-consistency.py [--root <repo-root>]
                                            [--seed <seed.sql>] [--app <App.tsx>]   # 仅供变异测试
"""
import os
import re
import sys

SEED_REL = "scripts/migration/0024_seed_iam_menu_tree.sql"
APP_TOKEN_TO_DIR = {
    "metadata": "bone-metadata-app",
    "masterdata": "bone-masterdata-app",
    "iam": "bone-iam-app",
    "commerce": "bone-commerce-app",
    "integration": "bone-integration-app",
    "extension": "bone-extension-app",
    "generator": "bone-generator-app",
    "system": "bone-system-app",
}
# 已知「待移除 / 兼容遗留」的死链 hash：由 0027_menu_metadata_ia.sql 软删（已落库执行），
# 但 0024 种子里仍保留（种子不改，见融合方案「不改 0024」），故在此允许并报警告，而非永久红。
# 新增的未知死链一律硬失败。
KNOWN_DEAD_ALLOWLIST = {
    ("metadata", "/relations"),
}


def find_repo_root():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.popen("git rev-parse --show-toplevel 2>/dev/null").read().strip()
    return out or os.path.dirname(here)


def parse_seed_menus(seed_path):
    """返回 [(app, hash)]，仅取形如 '/<app>#/<hash>' 的菜单 path。"""
    with open(seed_path, "r", encoding="utf-8") as fh:
        text = fh.read()
    menus = []
    # 注意：app 组不含前导 '/'，便于与 APP_TOKEN_TO_DIR 的键对齐。
    for m in re.finditer(r"'(?:/([A-Za-z]+))#(/[^']+)'", text):
        menus.append((m.group(1), m.group(2)))
    return menus


def parse_routes(app_path):
    """返回路由集合 [(path, is_redirect)]。"""
    routes = []
    if not os.path.isfile(app_path):
        return routes
    with open(app_path, "r", encoding="utf-8") as fh:
        text = fh.read()
    for m in re.finditer(r"<Route\b[^>]*path=\"([^\"]+)\"[^>]*element=\{<(\w+)", text):
        path, comp = m.group(1), m.group(2)
        is_redirect = comp in ("ToEntities", "Navigate") or "Navigate" in comp
        routes.append((path, is_redirect))
    return routes


def route_serves(routes, hash_):
    """hash 是否被某条真实（非重定向）路由覆盖。"""
    norm_hash = hash_
    for rpath, is_redirect in routes:
        rnorm = re.sub(r"/:[A-Za-z]+", "", rpath)
        if rnorm == norm_hash or rpath == norm_hash:
            if not is_redirect:
                return True, is_redirect
    return False, False


def fail(msg):
    print(f"[G32] ❌ {msg}")
    sys.exit(1)


def main():
    root = None
    seed_override = None
    app_override = None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        a = args[i]
        if a == "--root" and i + 1 < len(args):
            root = args[i + 1]; i += 2; continue
        if a == "--seed" and i + 1 < len(args):
            seed_override = args[i + 1]; i += 2; continue
        if a == "--app" and i + 1 < len(args):
            app_override = args[i + 1]; i += 2; continue
        i += 1

    if root is None:
        root = find_repo_root()

    seed_path = seed_override or os.path.join(root, SEED_REL)
    if not os.path.isfile(seed_path):
        fail(f"找不到菜单种子：{seed_path}")

    menus = parse_seed_menus(seed_path)
    if not menus:
        fail("种子里没有解析到任何 `path#hash` 菜单项（解析逻辑可能漂移）")

    problems = []
    warnings = []
    checked = 0

    for app, hash_ in menus:
        if app not in APP_TOKEN_TO_DIR:
            # 未知微应用：跳过（未来可扩展），不阻断
            continue
        app_dir = APP_TOKEN_TO_DIR[app]
        app_path = app_override or os.path.join(
            root, "bone-frontend", "apps", app_dir, "src", "App.tsx"
        )
        routes = parse_routes(app_path)
        if not routes:
            # 找不到该应用的路由文件：跳过（避免误伤未纳入扫描范围的应用）
            warnings.append(f"{app}: 未找到路由文件 {app_path}，跳过校验")
            continue

        checked += 1
        served, is_redirect = route_serves(routes, hash_)
        if served:
            continue  # 真实落地路由，OK
        # 未命中真实路由
        if (app, hash_) in KNOWN_DEAD_ALLOWLIST:
            warnings.append(
                f"{app}#{hash_} 是已知死链（302 兼容，待 0027 软删）—— 允许但需跟踪"
            )
            continue
        problems.append(
            f"{app}#{hash_} 在 {app_dir}/src/App.tsx 没有对应真实路由"
            + ("（命中 302 重定向路由，属死链菜单）" if is_redirect else "（无任何路由）")
        )

    if warnings:
        for w in warnings:
            print(f"[G32] ⚠️  {w}")

    if problems:
        for p in problems:
            print(f"[G32] ❌ {p}")
        fail(
            f"发现 {len(problems)} 个死链/未可达菜单项。"
            "要么补真实路由，要么经 0027 类迁移软删并在 KNOWN_DEAD_ALLOWLIST 登记。"
        )

    print(f"[G32] ✅ 菜单↔路由可达性通过（校验 {checked} 个微应用菜单项，0 个死链）")


if __name__ == "__main__":
    main()
