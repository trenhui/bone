#!/usr/bin/env python3
"""check-paging-param-ssot.py 的判据自测。

门禁判据（详见被测脚本 docstring）：对外分页入参类（类名匹配
`Qry|Query|PageQuery|PageReq|Request|DTO`）必须有唯一真源——
offset 族应 `extends PageParam / SortablePageParam`，cursor 族应有 `cursor` 字段
并继承 `CursorPageParam`；「只有 limit 没有 cursor」是单次查询规模上限，
归 `bounded`，不算分页范式、不判违规。

本测试锁定：
  - `paging_fields()`：按私有字段声明抽分页字段名（负数场景：无字段 / 非分页字段）；
  - 模块级正则契约：`SUFFIX_RE`（命名后缀）、`EXTENDS_RE`（三种真源基类）、
    `DECL_RE`（类名 + 类头，用于判继承）；
  - `scan()` 的分类结果：已继承类被直接收下（宣称合规）、cursor / bounded 分族、
    非后缀类与非分页类被跳过。
`scan()` 通过把模块级 `REPO` 重定向到临时目录来驱动，不扫真实仓库；不调用 `main()`。

未覆盖：`main()` 的 baseline 比对、报告与退出码编排。
⚠️ 已知行为（真实缺陷，本测试只锁定现状，未改门禁）：`scan()` 的族判定是
`"cursor" if "cursor" in fields else "bounded"`，**永远不会产出 `family == "offset"`**；
而 `main()` 只把 `offset` 当成阻断域，因此「裸 class 自带 page/size」这类 offset
违规永远不会被阻断（抵消了门禁 docstring 承诺的判据）。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-paging-param-ssot.py",
)
_spec = importlib.util.spec_from_file_location("check_paging_param_ssot", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class PagingFieldsTest(unittest.TestCase):
    def test_extracts_paging_fields_from_private_decls(self):
        block = (
            "    private Integer page;\n"
            "    private Integer size;\n"
            "    private String name;\n"
        )

        self.assertEqual(mod.paging_fields(block), {"page", "size"})

    def test_empty_block_has_no_fields(self):
        self.assertEqual(mod.paging_fields(""), set())

    def test_non_paging_field_is_ignored(self):
        self.assertEqual(mod.paging_fields("    private String name;\n"), set())


class RegexContractTest(unittest.TestCase):
    def test_suffix_re_matches_query_like_classes(self):
        for name in ("UserQry", "UserQuery", "UserPageQuery", "UserPageReq",
                     "UserRequest", "UserDTO"):
            self.assertTrue(mod.SUFFIX_RE.search(name), name)

    def test_suffix_re_rejects_controller(self):
        self.assertFalse(mod.SUFFIX_RE.search("UserController"))

    def test_extends_re_matches_three_sources_of_truth(self):
        for header in (" extends PageParam ", " extends SortablePageParam implements X",
                       " extends CursorPageParam"):
            self.assertTrue(mod.EXTENDS_RE.search(header), header)

    def test_extends_re_rejects_other_bases(self):
        for header in (" extends BaseEntity", " extends PageParameter"):
            self.assertFalse(mod.EXTENDS_RE.search(header), header)

    def test_decl_re_captures_name_and_header(self):
        matches = list(mod.DECL_RE.finditer("public class FooPageReq extends PageParam {\n"))

        self.assertEqual(len(matches), 1)
        self.assertEqual(matches[0].group(1), "FooPageReq")
        self.assertIn("extends PageParam", matches[0].group(2))


class ScanTest(unittest.TestCase):
    """scan() 的分族与过滤分支（REPO 指向临时夹具）。"""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="paging-param-ssot-"))
        self.src = self.root / "bone-apps" / "src"
        self.src.mkdir(parents=True)
        self._saved_repo = mod.REPO
        mod.REPO = self.root

    def tearDown(self):
        mod.REPO = self._saved_repo
        shutil.rmtree(self.root, ignore_errors=True)

    def _java(self, name, body):
        (self.src / name).write_text(body, encoding="utf-8")

    def test_class_extends_page_param_is_not_reported(self):
        # 分页字段在基类里，子类体内为空；仍必须被判合规（不能因体内无字段而漏收）
        self._java("OkPageReq.java", "public class OkPageReq extends PageParam {\n}\n")

        self.assertEqual(mod.scan(), [])

    def test_cursor_field_class_is_cursor_family(self):
        self._java(
            "CurPageQry.java",
            "public class CurPageQry {\n"
            "    private String cursor;\n"
            "    private Integer limit;\n"
            "}\n",
        )

        rows = mod.scan()

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["family"], "cursor")
        self.assertEqual(rows[0]["fields"], ["cursor", "limit"])

    def test_limit_only_class_is_bounded(self):
        self._java("LimQuery.java", "public class LimQuery {\n    private Integer limit;\n}\n")

        rows = mod.scan()

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["family"], "bounded")

    def test_bare_page_size_class_is_currently_bounded(self):
        # 已知缺陷：offset 族从未被产出，裸 page/size 类落到 bounded（main 不阻断）
        self._java(
            "FooPageReq.java",
            "public class FooPageReq {\n"
            "    private Integer page;\n"
            "    private Integer size;\n"
            "}\n",
        )

        rows = mod.scan()

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["family"], "bounded")
        self.assertEqual(rows[0]["fields"], ["page", "size"])

    def test_non_query_class_is_skipped(self):
        self._java(
            "PlainController.java",
            "public class PlainController {\n    private Integer page;\n}\n",
        )

        self.assertEqual(mod.scan(), [])

    def test_query_class_without_paging_field_is_skipped(self):
        self._java("NameQuery.java", "public class NameQuery {\n    private String name;\n}\n")

        self.assertEqual(mod.scan(), [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
