#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-error-code-duplication.py 的契约测试。

门禁判据：`COMMON_*` 公共错误码只允许由 bone-core 的 CommonErrorCodes 以**字面量**声明；
其他模块再次以字面量声明同一码值即「重复定义」。`main()` 把实测重复与
`doc/architecture/error-code-duplication-baseline.json` 比对，未登记的重复在 --check 下阻断。

锁定内容：
- ``CODE_RE`` 的字面量声明契约，以及四类**不该命中**的形态：
  非 public、引用式赋值（``= CommonErrorCodes.X``）、非 ``COMMON_`` 前缀、非大写码值；
- ``scan()`` 的模块归属（``module#常量名``）、``*ErrorCodes.java`` 文件名过滤、
  ``/target/`` 产物目录排除、无文件时返回空映射。

未覆盖：``main()`` 的基线比对 / 新增重复报告 / 退出码（会扫全仓并读取基线文件，
按测试约定不在进程内调用 ``main()``）。

已知口径（非缺陷）：``CODE_RE`` 只做正则匹配、不剥离注释，被注释掉的声明仍会命中并计入
重复，这是与 check-i18n-sync.py 保持一致的既有行为，本文件按当前行为锁定。
"""

import importlib.util
import os
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-error-code-duplication.py",
)
_spec = importlib.util.spec_from_file_location("check_error_code_duplication", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _decl(name, value):
    return f'public static final String {name} = "{value}";\n'


class CodeRegexTest(unittest.TestCase):
    """CODE_RE：只认 public static final String X = "COMMON_XXX"; 形态。"""

    def test_literal_declaration_is_matched(self):
        # 正向：常量名与码值都要能取出，供 scan() 生成 module#常量名
        found = mod.CODE_RE.findall(_decl("COMMON_NOT_FOUND", "COMMON_NOT_FOUND"))

        self.assertEqual(found, [("COMMON_NOT_FOUND", "COMMON_NOT_FOUND")])

    def test_reference_assignment_is_not_matched(self):
        # 边界：改成引用式（推荐写法）后 i18n 门禁扫不到，本门禁也不该把它当"重复字面量"
        src = "public static final String COMMON_NOT_FOUND = CommonErrorCodes.COMMON_NOT_FOUND;\n"

        self.assertEqual(mod.CODE_RE.findall(src), [])

    def test_non_public_declaration_is_not_matched(self):
        src = 'private static final String COMMON_NOT_FOUND = "COMMON_NOT_FOUND";\n'

        self.assertEqual(mod.CODE_RE.findall(src), [])

    def test_non_common_prefix_is_not_matched(self):
        # 业务码不属 COMMON_*，由各模块自有 Errors 类定义，不在本门禁范围
        src = _decl("ORDER_NOT_FOUND", "ORDER_NOT_FOUND")

        self.assertEqual(mod.CODE_RE.findall(src), [])

    def test_lowercase_value_is_not_matched(self):
        src = 'public static final String COMMON_NOT_FOUND = "COMMON_not_found";\n'

        self.assertEqual(mod.CODE_RE.findall(src), [])

    def test_commented_out_declaration_still_matches(self):
        # 锁定当前口径：CODE_RE 无注释感知，注释里的声明仍会命中（与 check-i18n-sync 一致）
        src = '// public static final String COMMON_X = "COMMON_X";\n'

        self.assertEqual(mod.CODE_RE.findall(src), [("COMMON_X", "COMMON_X")])


class ScanGroupingTest(unittest.TestCase):
    """scan()：按码值聚合、按 `模块#常量名` 归属，并排除构建产物。"""

    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="errcode-dup-")
        self._saved_root = mod.ROOT
        mod.ROOT = Path(self.root)

    def tearDown(self):
        mod.ROOT = self._saved_root
        shutil.rmtree(self.root, ignore_errors=True)

    def _write(self, rel, body):
        path = Path(self.root, *rel.split("/"))
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")

    def test_duplicates_are_grouped_by_module(self):
        # 负向：两个模块各自字面量重定义同一码值 ⇒ 同一键下出现两个 owner
        self._write(
            "bone-core/src/main/java/com/bone/core/error/CommonErrorCodes.java",
            _decl("COMMON_FORBIDDEN", "COMMON_FORBIDDEN"),
        )
        self._write(
            "studio-generator/src/main/java/com/bone/studio/generator/StudioErrorCodes.java",
            _decl("COMMON_FORBIDDEN", "COMMON_FORBIDDEN"),
        )

        by_value = mod.scan()

        self.assertEqual(
            sorted(by_value["COMMON_FORBIDDEN"]),
            ["bone-core#COMMON_FORBIDDEN", "studio-generator#COMMON_FORBIDDEN"],
        )

    def test_single_definition_stays_single_owner(self):
        # 正向：只有真源一处定义 ⇒ owners 长度为 1（"是否重复"的判断留给 main）
        self._write(
            "bone-core/src/main/java/com/bone/core/error/CommonErrorCodes.java",
            _decl("COMMON_NOT_FOUND", "COMMON_NOT_FOUND"),
        )

        by_value = mod.scan()

        self.assertEqual(by_value["COMMON_NOT_FOUND"], ["bone-core#COMMON_NOT_FOUND"])

    def test_target_directory_is_skipped(self):
        # 边界：编译产物里的同名类不应算作"另一个模块的重定义"
        self._write(
            "studio-generator/target/classes/com/bone/StudioErrorCodes.java",
            _decl("COMMON_FORBIDDEN", "COMMON_FORBIDDEN"),
        )

        self.assertEqual(dict(mod.scan()), {})

    def test_files_without_errorcodes_suffix_are_ignored(self):
        # 边界：文件名必须是 *ErrorCodes.java；普通类里的字符串常量不参与统计
        self._write(
            "bone-core/src/main/java/com/bone/core/Foo.java",
            _decl("COMMON_FORBIDDEN", "COMMON_FORBIDDEN"),
        )

        self.assertEqual(dict(mod.scan()), {})

    def test_empty_tree_returns_empty_mapping(self):
        self.assertEqual(dict(mod.scan()), {})


if __name__ == "__main__":
    unittest.main(verbosity=2)
