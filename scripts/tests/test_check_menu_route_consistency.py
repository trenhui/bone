#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-menu-route-consistency.py（G32 · 菜单↔路由一致性）的判据单测。

门禁判据：0024 菜单种子里每个 `/<app>#/<hash>` 菜单项，其 hash 必须被对应微应用
HashRouter 里的**真实**（非重定向）路由覆盖；命中 `ToEntities`/`Navigate` 这类
302 兼容路由视为死链菜单。

本测试锁定：
  · `parse_seed_menus`：只提取 `'/app#/hash'` 形态，裸容器路径 `/metadata` 不算菜单；
  · `parse_routes`：提取 `<Route path=... element={<Comp`，并按组件名标记重定向；
  · `route_serves`：真实路由命中、参数路由去参归一、重定向不算可达、无路由不算可达；
  · `KNOWN_DEAD_ALLOWLIST` 的已知死链登记项。

已知缺陷（测试按当前行为锁定，未改门禁）：`route_serves` 命中重定向路由时返回
`(False, False)`，即 `is_redirect` 永远不会为真，main 里「（命中 302 重定向路由）」
的文案分支不可达。该缺陷不改变红/绿结论（死链仍判红），只影响提示精度。

未覆盖：`main()` 的编排（遍历菜单、跳过未知 app、告警/失败分支、退出码）——
按测试纪律不调用 main()。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-menu-route-consistency.py",
)
_spec = importlib.util.spec_from_file_location("check_menu_route_consistency", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


SEED_FIXTURE = """INSERT INTO iam_menu (path) VALUES
  ('/metadata#/apps'),
  ('/metadata#/relations'),
  ('/iam#/users'),
  ('/metadata');
"""

APP_FIXTURE = """<Route path="/apps" element={<AppList />} />
<Route path="/apps/:id" element={<AppDetail />} />
<Route path="/" element={<Navigate to="/apps" replace />} />
<Route path="/legacy" element={<ToEntities />} />
<Route element={<LateComp />} path="/late" />
"""


class MenuRouteConsistencyTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="menu-route-consistency-")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, name, body):
        path = os.path.join(self.root, name)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(body)
        return path

    def test_parse_seed_menus_extracts_app_and_hash(self):
        seed = self._write("0024_seed.sql", SEED_FIXTURE)

        menus = mod.parse_seed_menus(seed)

        self.assertIn(("metadata", "/apps"), menus)
        self.assertIn(("metadata", "/relations"), menus)
        self.assertIn(("iam", "/users"), menus)
        # 裸容器根路径（无 #hash）不是本门禁的菜单项
        self.assertNotIn(("metadata", ""), menus)

    def test_parse_routes_marks_redirect_components(self):
        app = self._write("App.tsx", APP_FIXTURE)

        routes = mod.parse_routes(app)

        self.assertIn(("/apps", False), routes)
        self.assertIn(("/apps/:id", False), routes)
        self.assertIn(("/", True), routes)      # <Navigate/> 即重定向
        self.assertIn(("/legacy", True), routes)  # ToEntities 是 302 兼容路由
        # 当前解析契约要求 path 在 element 之前，反序写法不被采集（锁现状）
        self.assertNotIn(("/late", False), routes)

    def test_parse_routes_missing_file_returns_empty(self):
        self.assertEqual(mod.parse_routes(os.path.join(self.root, "nope.tsx")), [])

    def test_real_route_serves_hash(self):
        routes = [("/users", False)]

        self.assertEqual(mod.route_serves(routes, "/users"), (True, False))

    def test_param_route_is_normalized(self):
        # `/apps/:id` 落地 `/apps`：参数段被剥离后视为覆盖
        routes = [("/apps/:id", False)]

        self.assertEqual(mod.route_serves(routes, "/apps"), (True, False))
        # 但带参数值的深链目前不识别（锁现状）
        self.assertEqual(mod.route_serves(routes, "/apps/123"), (False, False))

    def test_redirect_route_is_not_reachable(self):
        # 命中重定向不算可达；且 is_redirect 未被回传（已知缺陷，锁当前行为）
        routes = [("/", True)]

        self.assertEqual(mod.route_serves(routes, "/"), (False, False))

    def test_unknown_hash_is_dead_link(self):
        routes = [("/apps", False), ("/", True)]

        self.assertEqual(mod.route_serves(routes, "/nonsense"), (False, False))

    def test_empty_routes_are_not_served(self):
        self.assertEqual(mod.route_serves([], "/apps"), (False, False))

    def test_known_dead_allowlist_contains_relations(self):
        # 0027 已软删但仍留在 0024 种子的死链，靠这份白名单避免永久红
        self.assertIn(("metadata", "/relations"), mod.KNOWN_DEAD_ALLOWLIST)


if __name__ == "__main__":
    unittest.main(verbosity=2)
