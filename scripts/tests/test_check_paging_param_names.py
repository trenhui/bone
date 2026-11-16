#!/usr/bin/env python3
"""check-paging-param-names.py 的判据自测。

门禁判据（详见被测脚本 docstring）：对外分页入参类只允许三族命名
`plain`(page/size)、`pagnum`(pageNum/pageSize)、`cursor`(cursor/limit)；
出现白名单外字段（如 `pageIndex`/`pageNo`/`perPage`）判「未知命名」阻断，
同一类里跨族字段并存判 `MIXED` 阻断；SDK 内部的 pageNum/pageSize 豁免。

本测试锁定四个纯函数：`is_sdk_path`（SDK 路径豁免）、`split_classes`（按顶层
class/record/interface 切块）、`extract_paging_fields`（按字段声明抽名，不按单词
搜）、`classify`（归族 / 未知 / 混搭）、`find_dup_layers`（web Req + app Query 双层重复）。
未覆盖：`collect()`（全仓 rglob 扫描）、`check_spec_drift()`（读真实规范文档与
门禁载体）与 `main()` 的打印/退出码编排——它们只做 IO 与聚合，判据本体已在上述纯函数内。
"""

import importlib.util
import os
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-paging-param-names.py",
)
_spec = importlib.util.spec_from_file_location("check_paging_param_names", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class IsSdkPathTest(unittest.TestCase):
    def test_sdk_module_path_is_exempt(self):
        self.assertTrue(mod.is_sdk_path("bone-metadata-sdk/src/main/java/Foo.java"))

    def test_slash_sdk_marker_is_exempt(self):
        self.assertTrue(mod.is_sdk_path("somewhere/sdk/Foo.java"))

    def test_business_path_is_not_exempt(self):
        self.assertFalse(mod.is_sdk_path("bone-system/adapter/web/LogPageReq.java"))


class SplitClassesTest(unittest.TestCase):
    def test_splits_multiple_top_level_classes(self):
        text = "public class A {\n}\n\npublic class B {\n}\n"

        self.assertEqual([n for n, _ in mod.split_classes(text)], ["A", "B"])

    def test_record_and_interface_are_supported(self):
        text = (
            "public record Rec(int a) {\n}\n\n"
            "public interface Iface {\n}\n"
        )

        self.assertEqual([n for n, _ in mod.split_classes(text)], ["Rec", "Iface"])

    def test_text_without_declaration_returns_empty(self):
        self.assertEqual(mod.split_classes("// nothing here\n"), [])


class ExtractPagingFieldsTest(unittest.TestCase):
    def test_extracts_declared_paging_fields(self):
        block = (
            "    private Integer page;\n"
            "    private Integer size;\n"
            "    private String name;\n"
        )

        self.assertEqual(mod.extract_paging_fields(block), {"page", "size"})

    def test_unknown_mixed_naming_is_still_extracted(self):
        # 按声明抽字段名，才能识别 pageIndex 这种白名单外命名
        block = "    private Integer pageIndex;\n    private Integer pageSize;\n"

        self.assertEqual(
            mod.extract_paging_fields(block), {"pageIndex", "pageSize"}
        )

    def test_block_without_paging_field_returns_empty(self):
        self.assertEqual(mod.extract_paging_fields("    private String name;\n"), set())


class ClassifyTest(unittest.TestCase):
    def test_empty_fields_is_no_family(self):
        self.assertEqual(mod.classify(set()), (None, []))

    def test_plain_family(self):
        self.assertEqual(mod.classify({"page", "size"}), ("plain", []))

    def test_pagnum_family(self):
        self.assertEqual(mod.classify({"pageNum", "pageSize"}), ("pagnum", []))

    def test_cursor_family(self):
        self.assertEqual(mod.classify({"cursor", "limit"}), ("cursor", []))

    def test_unknown_field_is_reported(self):
        fam, detail = mod.classify({"pageIndex"})

        self.assertIsNone(fam)
        self.assertEqual(detail, ["pageIndex"])

    def test_cross_family_mix_is_mixed_not_unknown(self):
        # 关键回归：混搭不能因为 detail 非空被误判成「未知命名」
        fam, detail = mod.classify({"page", "pageNum"})

        self.assertEqual(fam, "MIXED")
        self.assertEqual(detail, ["page", "pageNum"])


class FindDupLayersTest(unittest.TestCase):
    def _groups(self, pagnum):
        return {"plain": [], "pagnum": pagnum, "cursor": []}

    def test_web_req_and_app_query_with_same_stem_is_dup(self):
        groups = self._groups(
            [
                ("a/LogPageReq.java", "LogPageReq"),
                ("b/LogPageQuery.java", "LogPageQuery"),
            ]
        )

        dups = mod.find_dup_layers(groups)

        self.assertEqual(len(dups), 1)
        self.assertEqual(dups[0][0], "Log")
        self.assertEqual(
            [c for c, _ in dups[0][1]], ["LogPageReq", "LogPageQuery"]
        )

    def test_single_layer_is_not_dup(self):
        groups = self._groups([("a/LogPageReq.java", "LogPageReq")])

        self.assertEqual(mod.find_dup_layers(groups), [])

    def test_different_stems_are_not_dup(self):
        groups = self._groups(
            [
                ("a/LogPageReq.java", "LogPageReq"),
                ("b/UserPageQuery.java", "UserPageQuery"),
            ]
        )

        self.assertEqual(mod.find_dup_layers(groups), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
