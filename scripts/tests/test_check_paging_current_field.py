#!/usr/bin/env python3
"""check-paging-current-field.py 的判据自测。

门禁判据（详见被测脚本 docstring）：前端 TS/TSX 禁止消费后端 `PageResult` 的
`list` 兼容键（@Deprecated getter 的产物），权威字段是 `records`。命中分类：
  R1   `.data.list` / `.data?.list` —— 直接读响应对象的 list 键；
  R2a  `normalizePage(...)` / `unwrapPage(...)` 结果紧跟 `.list`；
  R2b  任意 `xxxPage(...)` 调用结果紧跟 `.list`；
  R2c  归一函数结果先赋给变量、再由该变量读 `.list`（跨行间接数据流）；
  R3   类型注解里在 `PageResult` 附近声明 `list: T[]` 字段。
刻意豁免：整行注释（`//` / `*` / `/*` 开头）、`list(` 方法调用、
文件内自声明 `ApiResponse<{ list: ... }>` 响应包装（整文件豁免）。

本测试用临时 .ts 文件直接驱动 `scan()`，覆盖正向无违规、每条规则的负向命中、
三类豁免与空输入边界。不调用 `main()`（会全仓扫描 + 打印 + exit）。
未覆盖：`iter_sources()` 的目录过滤（依赖真实 bone-frontend 树）与 `main()` 的
报告/退出码编排——二者只做 IO，判据逻辑全部在 `scan()` 内。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-paging-current-field.py",
)
_spec = importlib.util.spec_from_file_location("check_paging_current_field", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class ScanTest(unittest.TestCase):
    """scan() 的规则命中与豁免分支。"""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="paging-current-field-"))

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _scan(self, src, name="probe.ts"):
        path = self.root / name
        path.write_text(src, encoding="utf-8")
        return mod.scan(path)

    @staticmethod
    def _rids(hits):
        return [h[1] for h in hits]

    # ---- 正向 ----------------------------------------------------------
    def test_records_read_is_clean(self):
        hits = self._scan("const rows = res.data.records;\n")
        self.assertEqual(hits, [])

    def test_empty_file_is_clean(self):
        self.assertEqual(self._scan(""), [])

    # ---- R1 ------------------------------------------------------------
    def test_data_list_is_r1(self):
        hits = self._scan("const rows = res.data.list;\n")

        self.assertEqual(self._rids(hits), ["R1"])
        self.assertEqual(hits[0][0], 1)
        self.assertIn("`.data.list`", hits[0][2])

    def test_optional_chain_data_list_is_r1(self):
        hits = self._scan("const rows = res.data?.list;\n")

        self.assertEqual(self._rids(hits), ["R1"])

    def test_list_method_call_is_not_r1(self):
        # `list(` 是方法调用而非属性读取，必须豁免
        self.assertEqual(self._scan("const rows = res.data.list();\n"), [])

    # ---- R2 ------------------------------------------------------------
    def test_normalize_page_result_list_is_r2a(self):
        hits = self._scan("const rows = normalizePage(raw).list;\n")

        # 该写法同时命中 R2a 与 R2b（两条正则都匹配），断言 R2a 在命中集合里
        self.assertIn("R2a", self._rids(hits))

    def test_paged_api_call_result_list_is_r2b(self):
        hits = self._scan("const rows = pageUsers(1).list;\n")

        self.assertEqual(self._rids(hits), ["R2b"])

    def test_indirect_normalize_var_list_is_r2c(self):
        hits = self._scan("const p = normalizePage(raw);\nreturn p.list;\n")

        self.assertEqual(self._rids(hits), ["R2c"])
        self.assertEqual(hits[0][0], 2)  # 报在读取行而非赋值行

    # ---- R3 ------------------------------------------------------------
    def test_type_annotation_list_field_is_r3(self):
        hits = self._scan("export interface FooPageResult {\n  list: T[]\n}\n")

        self.assertEqual(self._rids(hits), ["R3"])
        self.assertEqual(hits[0][0], 2)

    # ---- 豁免 ----------------------------------------------------------
    def test_line_comment_is_exempt(self):
        self.assertEqual(self._scan("// const rows = res.data.list;\n"), [])

    def test_api_response_self_declared_file_is_exempt(self):
        src = (
            "type R = ApiResponse<{ list: Config[] }>;\n"
            "const rows = res.data.list;\n"
        )
        self.assertEqual(self._scan(src), [])

    def test_plain_object_list_declaration_does_not_exempt(self):
        # 判据要求声明里带 ApiResponse，普通 `{ list: X[] }` 不得整文件放行
        src = "type R = { list: Config[] };\nconst rows = res.data.list;\n"

        hits = self._scan(src)

        self.assertEqual(self._rids(hits), ["R1"])
        self.assertEqual(hits[0][0], 2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
