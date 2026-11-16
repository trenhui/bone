#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-error-code-landing.py 的契约测试。

门禁判据：``application/`` 与 ``adapter/web/`` 层的业务异常必须携带稳定业务码；
``BizException.of(...)``（9 个重载全丢码）一律违规，``new BizException(...)`` 仅当
第 3 实参是业务码（字符串字面量 / ``XxxErrorCodes.CONST`` / ``errorCode`` 变量）时合规。
``domain/`` 与 ``infrastructure/`` 只报存量、不阻断。

锁定内容：
- ``strip_code_keep_lines``：注释/字符串剥离后**行数守恒**、且不会用 ``re.S`` 把整文件吃空；
- ``split_top_level_args``：括号 / 尖括号内的逗号不切分；
- ``is_code_like`` / ``classify_new``：业务码识别与各重载的违规形态；
- ``extract_after``：配平取实参、未配平返回 None；
- ``EXEMPT_NAME_RE``、``STRICT_MARKERS``：豁免文件与严格层标记；
- 组合链路：剥注释后统计违规行号，注释/字符串里的假命中不计入。

未覆盖：``scan()`` 的目录遍历与 ``main()`` 的基线比对/退出码（会扫全仓、读 baseline 文件）。

另锁定两处已知实现细节（本次未修改门禁）：
1. ``split_top_level_args`` 的 docstring 声称跳过字符串内逗号，实测并不感知字符串——
   因为调用前 ``strip_code_keep_lines`` 已把字符串内容抹成空格，所以实际不触发；
2. ``extract_after`` 用 ``for i in range(...)`` 迭代，函数体内改写 ``i`` 无法跳读，
   字符串里的括号仍会计入深度（见 ``test_string_paren_is_not_skipped_current_behavior``）。
   同样因为调用前已剥离字符串内容，门禁实际扫描不受影响。
"""

import importlib.util
import os
import unittest

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-error-code-landing.py",
)
_spec = importlib.util.spec_from_file_location("check_error_code_landing", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _violation_lines(src):
    """模拟 scan() 的正文侧逻辑：先剥离，再按行号收集违规。"""
    code = mod.strip_code_keep_lines(src)
    of_lines = [code[: m.start()].count("\n") + 1 for m in mod.BIZEX_OF_RE.finditer(code)]
    new_bad = []
    for m in mod.BIZEX_NEW_RE.finditer(code):
        seg = mod.extract_after(code, m.end() - 1)
        bad, form = mod.classify_new(seg)
        if bad:
            new_bad.append((code[: m.start()].count("\n") + 1, form))
    return of_lines, new_bad


class StripCodeTest(unittest.TestCase):
    """strip_code_keep_lines：剥离注释与字符串，同时保持行数一一对应。"""

    def test_line_count_is_preserved(self):
        src = (
            "class A {\n"
            "  // 单行注释\n"
            "  /* 块注释\n"
            "     跨两行 */\n"
            '  String s = "含\\"转义的字符串";\n'
            "}\n"
        )

        out = mod.strip_code_keep_lines(src)

        self.assertEqual(out.count("\n"), src.count("\n"))

    def test_line_comment_is_removed(self):
        src = "x = 1; // BizException.of(y)\ny = 2;\n"

        out = mod.strip_code_keep_lines(src)

        self.assertNotIn("BizException", out)
        self.assertIn("x = 1;", out)

    def test_block_comment_does_not_eat_whole_file(self):
        # 锁定 re.S 缺陷模式：两个块注释之间与之后的正文必须保留
        src = "A = 1;\n/* 第一个注释 */\nKEEP_ME;\n/* 第二个注释 */\nB = 2;\n"

        out = mod.strip_code_keep_lines(src)

        self.assertIn("KEEP_ME", out)
        self.assertIn("B = 2;", out)
        self.assertNotIn("第一个注释", out)

    def test_string_literal_content_is_blinded(self):
        # 字符串里的"调用"不是调用；真实调用必须保留
        src = 'String tip = "BizException.of(x)";\nBizException.of(y);\n'

        out = mod.strip_code_keep_lines(src)

        self.assertNotIn("BizException.of(x)", out)
        self.assertEqual(out.count("BizException.of("), 1)

    def test_char_literal_does_not_swallow_following_code(self):
        # 字符字面量里的双引号不能把状态机切进字符串态
        src = "char q = '\"';\nBizException.of(x);\n"

        out = mod.strip_code_keep_lines(src)

        self.assertEqual(out.count("BizException.of("), 1)


class SplitArgsTest(unittest.TestCase):
    """split_top_level_args：只按顶层逗号切分。"""

    def test_nested_parentheses_are_not_split(self):
        self.assertEqual(
            mod.split_top_level_args("f(a, b), c"), ["f(a, b)", "c"]
        )

    def test_generics_are_not_split(self):
        self.assertEqual(
            mod.split_top_level_args("Map<String, List<Integer>> m, 2"),
            ["Map<String, List<Integer>> m", "2"],
        )

    def test_plain_arguments_are_split(self):
        self.assertEqual(
            mod.split_top_level_args('400, "订单不存在", OrderErrorCodes.NOT_FOUND'),
            ["400", '"订单不存在"', "OrderErrorCodes.NOT_FOUND"],
        )

    def test_string_comma_is_split_when_called_directly(self):
        # 已知实现细节：函数本身不感知字符串（docstring 说法与代码不符）；
        # 实际调用前字符串内容已被 strip_code_keep_lines 抹成空格，故不触发误切。
        self.assertEqual(
            mod.split_top_level_args('"a,b", c'), ['"a', 'b"', "c"]
        )


class IsCodeLikeTest(unittest.TestCase):
    """is_code_like：第 3 实参是否业务码。"""

    def test_string_literal_is_code_like(self):
        self.assertTrue(mod.is_code_like('"BP_ORDER_NOT_FOUND"'))

    def test_errorcodes_constant_is_code_like(self):
        self.assertTrue(mod.is_code_like("OrderErrorCodes.NOT_FOUND"))

    def test_errorcode_variable_is_code_like(self):
        self.assertTrue(mod.is_code_like("errorCode"))

    def test_throwable_null_and_cause_are_not_code_like(self):
        for arg in ("null", "(Throwable) null", "cause", "e", "ex", "throwable", ""):
            with self.subTest(arg=arg):
                self.assertFalse(mod.is_code_like(arg))

    def test_non_code_argument_is_not_code_like(self):
        for arg in ("orderId", "new IllegalStateException()", "42"):
            with self.subTest(arg=arg):
                self.assertFalse(mod.is_code_like(arg))


class ClassifyNewTest(unittest.TestCase):
    """classify_new：new BizException(...) 各重载的合规性。"""

    def test_three_args_with_code_passes(self):
        bad, form = mod.classify_new('400, "订单不存在", OrderErrorCodes.NOT_FOUND')

        self.assertFalse(bad)
        self.assertIn("带errorCode", form)

    def test_four_args_with_code_passes(self):
        bad, form = mod.classify_new('400, "订单不存在", "BP_X", cause')

        self.assertFalse(bad)
        self.assertIn("4参", form)

    def test_three_args_with_null_is_violation(self):
        bad, form = mod.classify_new('400, "订单不存在", null')

        self.assertTrue(bad)
        self.assertIn("第3参非业务码", form)

    def test_two_args_is_violation(self):
        bad, form = mod.classify_new('400, "订单不存在"')

        self.assertTrue(bad)
        self.assertEqual(form, "new BizException(2参) ⇒ errorCode=null")

    def test_one_arg_is_violation(self):
        bad, form = mod.classify_new('"实体不存在"')

        self.assertTrue(bad)
        self.assertEqual(form, "new BizException(1参) ⇒ errorCode=null")


class ExtractAfterTest(unittest.TestCase):
    """extract_after：从 '(' 取配平实参。"""

    def test_string_paren_is_not_skipped_current_behavior(self):
        # 已知实现缺陷（本次不改门禁）：for-range 内改写 i 不能跳读，字符串里的 '('
        # 仍计入深度 ⇒ 配平失败返回 None。scan() 调用前字符串内容已被抹成空格，
        # 所以门禁实际扫描不受影响；此处锁定当前行为。
        text = 'throw new BizException(400, "a(b", cause);'

        self.assertIsNone(mod.extract_after(text, text.index("(")))

    def test_unbalanced_returns_none(self):
        text = 'new BizException(400, "x"'

        self.assertIsNone(mod.extract_after(text, text.index("(")))


class ConstantsTest(unittest.TestCase):
    """模块级常量：豁免文件名与严格层标记。"""

    def test_exempt_file_names(self):
        for name in (
            "CommonErrorCodes.java",
            "OrderErrors.java",
            "BizException.java",
            "GlobalExceptionHandler.java",
        ):
            with self.subTest(name=name):
                self.assertIsNotNone(mod.EXEMPT_NAME_RE.search(name))

    def test_normal_service_is_not_exempt(self):
        self.assertIsNone(
            mod.EXEMPT_NAME_RE.search("MetaEntityApplicationService.java")
        )

    def test_strict_markers_are_application_and_web_only(self):
        self.assertEqual(mod.STRICT_MARKERS, ("/application/", "/adapter/web/"))
        self.assertEqual(mod.ADVISORY_MARKERS, ("/domain/", "/infrastructure/"))


class ViolationScanChainTest(unittest.TestCase):
    """组合链路：剥离 + 正则 + 分类，断言具体行号与形态。"""

    SRC = (
        "class FooApplicationService {\n"                                  # 1
        '  void a() { throw BizException.of("实体不存在"); }\n'             # 2 违规
        '  // throw BizException.of("实体不存在");\n'                       # 3 注释
        '  String tip = "BizException.of(x)";\n'                           # 4 字符串
        '  void b() { throw new BizException(1, "m"); }\n'                 # 5 违规
        '  void c() { throw new BizException(1, "m", "BP_ORDER_NOT_FOUND"); }\n'  # 6 合规
        "}\n"
    )

    def test_only_real_violations_are_reported_with_line_numbers(self):
        of_lines, new_bad = _violation_lines(self.SRC)

        self.assertEqual(of_lines, [2])
        self.assertEqual(
            new_bad, [(5, "new BizException(2参) ⇒ errorCode=null")]
        )

    def test_clean_source_has_no_violation(self):
        src = 'class Ok {\n  void a() { throw new BizException(400, "m", "BP_X"); }\n}\n'

        of_lines, new_bad = _violation_lines(src)

        self.assertEqual(of_lines, [])
        self.assertEqual(new_bad, [])

    def test_empty_source_has_no_violation(self):
        self.assertEqual(_violation_lines(""), ([], []))


if __name__ == "__main__":
    unittest.main(verbosity=2)
