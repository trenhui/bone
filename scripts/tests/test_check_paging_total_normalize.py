#!/usr/bin/env python3
"""check-paging-total-normalize.py 的判据自测。

门禁判据（详见被测脚本 docstring）：后端 `total` 经全局 Long→String 序列化器
输出为 JSON 字符串，前端若把 `.data.total` 直接塞进 number 语境即崩。命中分类：
  R3-1  `setXxx(data.total)` 未归一；
  R3-2  `total: data.total` 对象字段赋值未归一；
  R3-3  `value={data.total}`（antd Statistic / 分页）未归一；
  R3-4  `total`（或 `data.total`）参与算术/比较未归一。
豁免：`normalizeTotal(...)` / `Number(...)` / `?? 0` / `let total = 0` / `total += 1`。
另有按行状态机的去注释（保留行号）与「已归一 API 调用变量」豁免。

本测试锁定：`strip_comments()`（行注释、同行块注释、跨行块注释、行结构保持）、
`is_exempt()`（各豁免写法）、`R3_PATTERNS` 四条正则的正/负样例、
以及 `scan()` 的端到端命中（错误行号）与三类不该报的形态。
不调用 `main()`（全仓遍历 + 打印 + exit）。

未覆盖：`main()` 的目录筛选与报告编排。
⚠️ 已知行为（真实缺陷，本测试只锁定现状，未改门禁）：`collect_normalized_vars()`
的方法名回溯正则要求 `head` 以 `Promise<ApiResponse<` 结尾，而锚点正则命中的起点
在 `async` 处，两者永远接不上 ⇒ 真实代码里 `safe_methods` 恒为空、返回值恒为
`set()`，「已归一 API 变量」豁免实际失效（页面侧二次读 `res.data.total` 会被误报）。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-paging-total-normalize.py",
)
_spec = importlib.util.spec_from_file_location("check_paging_total_normalize", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


class StripCommentsTest(unittest.TestCase):
    def test_line_comment_is_removed(self):
        self.assertEqual(mod.strip_comments("const a = 1; // note\n"), "const a = 1; ")

    def test_single_line_block_comment_is_removed(self):
        self.assertEqual(mod.strip_comments("a /* x */ b\n"), "a  b")

    def test_multiline_block_comment_keeps_line_structure(self):
        out = mod.strip_comments("a\n/* x\ny */\nb\n")

        # 行数必须一一对应，否则报错行号会漂移
        self.assertEqual(out, "a\n\n\nb")

    def test_block_comment_prefix_does_not_hide_trailing_code(self):
        out = mod.strip_comments("/* x */ setTotal(res.data.total);\n")

        self.assertEqual(out, " setTotal(res.data.total);")


class IsExemptTest(unittest.TestCase):
    def test_known_exempt_forms(self):
        for line in (
            "total += 1;",
            "let total = 0;",
            "setTotal(normalizeTotal(res.data.total));",
            "setTotal(Number(res.data.total));",
            "total: res.data.total ?? 0,",
        ):
            self.assertTrue(mod.is_exempt(line), line)

    def test_plain_backend_total_is_not_exempt(self):
        self.assertFalse(mod.is_exempt("setTotal(res.data.total);"))


class R3PatternsTest(unittest.TestCase):
    def _labels(self, line):
        return [label for label, pattern in mod.R3_PATTERNS if pattern.search(line)]

    def test_set_state_without_normalize_is_r3_1(self):
        self.assertEqual(self._labels("setTotal(res.data.total)"), ["setState 未归一（R3-1）"])
        self.assertEqual(self._labels("setTotal(res.data?.total)"), ["setState 未归一（R3-1）"])

    def test_set_state_with_normalize_is_clean(self):
        self.assertEqual(self._labels("setTotal(normalizeTotal(res.data.total))"), [])
        self.assertEqual(self._labels("setTotal(Number(res.data.total))"), [])

    def test_number_field_assignment_is_r3_2(self):
        self.assertEqual(self._labels("total: res.data.total,"), ["number 字段赋值未归一（R3-2）"])

    def test_statistic_value_is_r3_3(self):
        self.assertEqual(
            self._labels("<Statistic value={res.data.total} />"),
            ["antd Statistic/分页 value 未归一（R3-3）"],
        )

    def test_arithmetic_on_total_is_r3_4(self):
        self.assertEqual(
            self._labels("Math.ceil(total / pageSize)"),
            ["total 参与算术/比较未归一（R3-4）"],
        )


class ScanTest(unittest.TestCase):
    """scan() 端到端：命中行号 + 去注释/豁免后不该报的形态。"""

    def setUp(self):
        self.root = Path(tempfile.mkdtemp(prefix="paging-total-"))

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _scan(self, src, name="probe.ts"):
        path = self.root / name
        path.write_text(src, encoding="utf-8")
        return mod.scan([path])

    def test_violation_is_reported_with_line_number(self):
        src = "function f() {\n  setTotal(res.data.total);\n}\n"

        findings = self._scan(src)

        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0][1], 2)
        self.assertEqual(findings[0][2], "setState 未归一（R3-1）")

    def test_normalized_call_is_clean(self):
        self.assertEqual(self._scan("setTotal(normalizeTotal(res.data.total));\n"), [])

    def test_commented_violation_is_not_reported(self):
        src = "// setTotal(res.data.total);\n/*\n setTotal(res.data.total);\n*/\n"

        self.assertEqual(self._scan(src), [])


class CollectNormalizedVarsTest(unittest.TestCase):
    def test_anchor_regex_matches_normalized_api_signature(self):
        src = (
            "const api = {\n"
            "  page: async (params: P): Promise<ApiResponse<NormalizedPageResult<X>>> => {},\n"
            "};\n"
        )

        self.assertTrue(mod.NORMALIZED_API_RE.search(src))

    def test_current_behavior_returns_empty_set(self):
        # 已知缺陷：方法名回溯恒失败，豁免变量集合恒为空（见文件 docstring）
        src = (
            "const api = {\n"
            "  page: async (params: P): Promise<ApiResponse<NormalizedPageResult<X>>> => {},\n"
            "};\n"
            "const res = await api.page({});\n"
        )

        self.assertEqual(mod.collect_normalized_vars(src), set())


if __name__ == "__main__":
    unittest.main(verbosity=2)
