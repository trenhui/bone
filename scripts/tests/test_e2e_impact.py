#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""impact.py（接口变更 ↔ e2e 用例映射）的判据测试。

重点覆盖两类最容易「假绿」的地方：
  * 路径匹配不能过宽：`/orders` 可以覆盖 `/orders/{id}`，但**不能**覆盖
    `/orders/{id}/cancel` —— 否则任何列表接口的调用都会把子资源端点洗成「已覆盖」；
  * 变更分析只看本次改到的 Controller 文件，不能因为「仓库里别处有用例」就放行。
"""
import importlib.util
import json
import os
import tempfile
import unittest

TESTS = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(os.path.dirname(TESTS), "e2e", "impact.py")
_spec = importlib.util.spec_from_file_location("e2e_impact", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


ENDPOINTS = [
    {"module": "m", "controller": "OrderController", "file": "svc/adapter/web/OrderController.java",
     "http": "GET", "path": "/api/v1/orders", "has_var": False},
    {"module": "m", "controller": "OrderController", "file": "svc/adapter/web/OrderController.java",
     "http": "GET", "path": "/api/v1/orders/{id}", "has_var": True},
    {"module": "m", "controller": "OrderController", "file": "svc/adapter/web/OrderController.java",
     "http": "POST", "path": "/api/v1/orders/{id}/cancel", "has_var": True},
    {"module": "m", "controller": "ClaimController", "file": "svc/adapter/web/ClaimController.java",
     "http": "POST", "path": "/api/v1/claims", "has_var": False},
]


class PathMatchTest(unittest.TestCase):
    def test_normalize_strips_query_and_vars(self):
        self.assertEqual(mod.normalize_path("/api/v1/orders/{id}?x=1"), "/api/v1/orders/{}")
        self.assertEqual(mod.normalize_path("/api/v1/orders/"), "/api/v1/orders")

    def test_collection_covers_detail_but_not_subresource(self):
        self.assertTrue(mod.case_serves("/api/v1/orders", "/api/v1/orders/{id}"))
        self.assertFalse(mod.case_serves("/api/v1/orders", "/api/v1/orders/{id}/cancel"))

    def test_variable_case_path_matches(self):
        self.assertTrue(mod.case_serves("/api/v1/orders/${orderId}", "/api/v1/orders/{id}"))

    def test_unrelated_path_does_not_match(self):
        self.assertFalse(mod.case_serves("/api/v1/claims", "/api/v1/orders/{id}"))

    def test_case_path_without_gateway_prefix_matches(self):
        """用例里 `config.BASE` 已含 `/api/v1`，写的是相对路径 `/claims/...`。

        端点是带前缀的绝对路径。不收敛前缀，真实存在的用例会被判「零覆盖」，
        提交门禁就会逼人再写一份重复脚本（正是「每个 agent 各造一套」的老毛病）。
        """
        self.assertTrue(mod.case_serves("/orders/{orderId}", "/api/v1/orders/{id}"))
        self.assertTrue(mod.case_serves(
            "/claims/{}/ai/feedback", "/api/v1/claims/{claimId}/ai/feedback"))
        self.assertTrue(mod.case_serves("/claims/search", "/api/v1/claims/search"))
        # 收敛前缀不等于放宽路径：不同资源仍然不匹配
        self.assertFalse(mod.case_serves("/claims", "/api/v1/orders"))


class MappingTest(unittest.TestCase):
    def test_map_endpoints_assigns_cases(self):
        case_paths = {
            "chains.py": ["/api/v1/orders", "/api/v1/orders/{}"],
            "claim-sweep.py": ["/api/v1/claims"],
        }
        rows = dict(
            (ep["path"], cases) for ep, cases in mod.map_endpoints(ENDPOINTS, case_paths)
        )
        self.assertEqual(rows["/api/v1/orders"], ["chains.py"])
        self.assertEqual(rows["/api/v1/orders/{id}"], ["chains.py"])
        self.assertEqual(rows["/api/v1/orders/{id}/cancel"], [])
        self.assertEqual(rows["/api/v1/claims"], ["claim-sweep.py"])

    def test_analyze_only_reports_changed_controllers(self):
        case_paths = {"chains.py": ["/api/v1/orders"]}
        covered, uncovered, changed = mod.analyze(
            ENDPOINTS, case_paths, ["svc/adapter/web/OrderController.java"]
        )
        paths = {ep["path"] for ep, _ in covered}
        self.assertIn("/api/v1/orders", paths)
        self.assertEqual([ep["path"] for ep, _ in uncovered], ["/api/v1/orders/{id}/cancel"])
        self.assertEqual(changed, {"svc/adapter/web/OrderController.java"})

    def test_unchanged_controller_is_not_evaluated(self):
        covered, uncovered, _ = mod.analyze(
            ENDPOINTS, {}, ["svc/adapter/web/OtherController.java"]
        )
        self.assertEqual((covered, uncovered), ([], []))

    def test_endpoint_touched_filters_by_mapping_suffix(self):
        detail = ENDPOINTS[1]   # /api/v1/orders/{id}
        order_file = "svc/adapter/web/OrderController.java"
        self.assertTrue(mod.endpoint_touched(
            detail, {order_file: {"/orders/{}"}}))
        self.assertFalse(mod.endpoint_touched(
            detail, {order_file: {"/orders/{id}/cancel"}}))
        # None = 整文件触及（类级 @RequestMapping 变动）
        self.assertTrue(mod.endpoint_touched(detail, {order_file: None}))

    def test_analyze_ignores_untouched_uncovered_endpoints(self):
        """只改了一个映射行时，同一 Controller 里历史零覆盖端点不能算作本次阻断理由。"""
        order_file = "svc/adapter/web/OrderController.java"
        covered, uncovered, _ = mod.analyze(
            ENDPOINTS, {"chains.py": ["/api/v1/orders"]}, [order_file],
            {order_file: {"/orders"}},
        )
        self.assertEqual([ep["path"] for ep, _ in covered], ["/api/v1/orders"])
        self.assertEqual(uncovered, [])

    def test_collect_case_paths_skips_shared_lib_and_templates(self):
        root = tempfile.mkdtemp(prefix="impact-cases-")
        try:
            os.makedirs(os.path.join(root, "e2e_lib"))
            os.makedirs(os.path.join(root, "templates"))
            with open(os.path.join(root, "case.py"), "w", encoding="utf-8") as fh:
                fh.write('CALL = "/api/v1/orders?page=1"\n')
            with open(os.path.join(root, "e2e_lib", "lib.py"), "w", encoding="utf-8") as fh:
                fh.write('P = "/api/v1/should-not-count"\n')
            with open(os.path.join(root, "templates", "t.py"), "w", encoding="utf-8") as fh:
                fh.write('P = "/api/v1/template-placeholder"\n')
            paths = mod.collect_case_paths(root)
            self.assertEqual(list(paths), ["case.py"])
            self.assertEqual(paths["case.py"], ["/api/v1/orders"])
        finally:
            import shutil
            shutil.rmtree(root, ignore_errors=True)

    def test_endpoints_file_mode_and_strict_exit(self):
        root = tempfile.mkdtemp(prefix="impact-cli-")
        try:
            ep_file = os.path.join(root, "eps.json")
            with open(ep_file, "w", encoding="utf-8") as fh:
                json.dump(ENDPOINTS, fh)
            with open(os.path.join(root, "case.py"), "w", encoding="utf-8") as fh:
                fh.write('CALL = "/api/v1/orders"\n')
            # 临时目录不是 git 仓库 → status 模式会返回 2（git 调用失败），这条只验参数解析路径
            rc = mod.main(["--endpoints-file", ep_file, "--case-root", root, "--json"])
            self.assertIn(rc, (0, 2))
        finally:
            import shutil
            shutil.rmtree(root, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
