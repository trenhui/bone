#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""check-i18n-sync.py 的判据单测。

门禁判据（编号见脚本 docstring，对应方案 §8.2.2）：
  1 代码常量 ⊆ 台账 §6；2/3 代码常量 ⊆ en-US / zh-CN 的 errors；4 两份语言包
  errors key 对称；5 台账有代码无（警告）；6 fallback 不混 errorCode 形态；
  9 enums 覆盖率；11 白名单只可收缩。

本测试通过 monkeypatch `mod.REPO` 指向临时夹具仓库，覆盖：
  · `CODE_RE`：容忍多余空格，只认 `public static final String X = "CODE"`；
  · `code_codes`：只扫 `*ErrorCodes.java` / `*Errors.java`（防 ADDRESS 等误采）；
  · `ledger_codes`：只取 `## 6.` 区间的表格行（§3 前缀表、§7 附录不入集）；
  · `baseline_codes`：文件缺失返回 has_baseline=False，存在则显式解析两组清单；
  · `error_keys` / `load_locale`：缺失语言包返回 None；
  · 缺译、代码未登记台账、语言包不对称、白名单悬空条目的集合判定语义；
  · `CODE_SHAPE_RE` 识别 fallback 里的 errorCode 形态 key；
  · `grep_hits` 的相对路径:行号、`walk` 跳过构建产物与后缀过滤；
  · `report` 的条数/截断输出。

未覆盖：`main()` 的编排与退出码（--report-only、各检查项打印顺序、enums 覆盖
循环）——按测试纪律不调用 main()（默认会对真实仓库全量 rglob）。main 的每个判据
输入均由上述纯函数覆盖。
"""

import contextlib
import importlib.util
import io
import os
import json
import shutil
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-i18n-sync.py",
)
_spec = importlib.util.spec_from_file_location("check_i18n_sync", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)


def _write(root, rel, body):
    path = Path(root) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding="utf-8")
    return path


class I18nSyncTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="i18n-sync-")
        self._saved_repo = mod.REPO
        mod.REPO = Path(self.root)

    def tearDown(self):
        mod.REPO = self._saved_repo
        shutil.rmtree(self.root, ignore_errors=True)

    def _error_codes(self, body=None):
        return _write(
            self.root,
            "bone-framework/bone-core/src/main/java/com/bone/core/FooErrorCodes.java",
            body
            or 'public class FooErrorCodes {\n'
            '    public static final String IAM_001 = "IAM_001";\n'
            '    public static final String IAM_002 = "IAM_002";\n'
            "}\n",
        )

    def _locale(self, name, doc):
        return _write(
            self.root, mod.LOCALE_DIR + "/" + name, json.dumps(doc, ensure_ascii=False)
        )

    def _ledger(self, body):
        return _write(self.root, mod.LEDGER, body)

    def _baseline(self, doc):
        return _write(self.root, mod.BASELINE, json.dumps(doc))

    # ---- 正则/常量契约 ---------------------------------------------------
    def test_code_re_matches_only_public_constants(self):
        text = (
            '    public static final String   IAM_003 = "IAM_003";\n'
            '    private static final String HIDDEN = "HIDDEN_001";\n'
            '    public static final String lower = "lower_case";\n'
        )

        self.assertEqual(mod.CODE_RE.findall(text), ["IAM_003"])

    def test_code_shape_re_identifies_errorcode_shape(self):
        self.assertIsNotNone(mod.CODE_SHAPE_RE.fullmatch("IAM_001"))
        self.assertIsNone(mod.CODE_SHAPE_RE.fullmatch("login.title"))
        self.assertIsNone(mod.CODE_SHAPE_RE.fullmatch("en"))

    # ---- 代码侧 ---------------------------------------------------------
    def test_code_codes_only_scans_error_code_files(self):
        self._error_codes()
        # 同仓其他常量文件不应被采集（防 ADDRESS / EMAIL 之类误判为错误码）
        _write(
            self.root,
            "bone-core/src/main/java/com/bone/core/User.java",
            'public static final String ADDRESS_CITY = "ADDRESS_CITY";\n',
        )

        self.assertEqual(mod.code_codes(), {"IAM_001", "IAM_002"})

    # ---- 台账侧 ---------------------------------------------------------
    def test_ledger_codes_limited_to_section_6(self):
        self._ledger(
            "## 3. 前缀表\n"
            "| `IAM_` | 身份域 |\n"
            "## 6. 错误码台账\n"
            "| `IAM_001` | 用户不存在 |\n"
            "| `IAM_002` | 密码错误 |\n"
            "## 7. 附录\n"
            "| `IAM_999` | 不该采集 |\n"
        )

        codes = mod.ledger_codes()

        self.assertEqual(codes, {"IAM_001", "IAM_002"})
        self.assertNotIn("IAM_", codes)
        self.assertNotIn("IAM_999", codes)

    def test_ledger_codes_missing_file_or_section_is_empty(self):
        self.assertEqual(mod.ledger_codes(), set())

        self._ledger("## 6. 错误码台账\n| `IAM_001` | x |\n")  # 无 §7 终止标记
        self.assertEqual(mod.ledger_codes(), set())

    # ---- 白名单 ---------------------------------------------------------
    def test_baseline_codes_missing_file(self):
        self.assertEqual(mod.baseline_codes(), (set(), set(), False))

    def test_baseline_codes_parsed_explicitly(self):
        self._baseline({"untranslated": ["IAM_009"], "unregistered": ["IAM_010"]})

        self.assertEqual(
            mod.baseline_codes(), ({"IAM_009"}, {"IAM_010"}, True)
        )

    # ---- 语言包 ---------------------------------------------------------
    def test_error_keys_and_load_locale(self):
        self._locale(
            "en-US.json",
            {"errors": {"IAM_001": "User not found"}, "enums": {"status": {"A": "A"}}},
        )

        self.assertEqual(mod.error_keys("en-US.json"), {"IAM_001"})
        self.assertEqual(mod.error_keys("zh-CN.json"), None)
        self.assertEqual(mod.load_locale("en-US.json")["enums"], {"status": {"A": "A"}})

    # ---- 判据语义（main 中的集合运算） -----------------------------------
    def test_missing_translation_and_asymmetric_locale(self):
        self._error_codes()
        self._ledger("## 6. 错误码\n| `IAM_001` | x |\n| `IAM_002` | y |\n## 7. 附录\n")
        self._locale("en-US.json", {"errors": {"IAM_001": "User not found"}})
        self._locale("zh-CN.json", {"errors": {"IAM_001": "用户不存在", "IAM_002": "密码错误"}})

        codes = mod.code_codes()
        en = mod.error_keys("en-US.json")
        zh = mod.error_keys("zh-CN.json")

        # 台账覆盖：正向无缺口、反向精确报出未登记码
        self.assertEqual(codes - mod.ledger_codes(), set())
        self.assertEqual(codes - en, {"IAM_002"})
        self.assertEqual(codes - zh, set())
        self.assertEqual(en ^ zh, {"IAM_002"})

    def test_unregistered_code_is_reported(self):
        self._error_codes()
        self._ledger("## 6. 错误码\n| `IAM_001` | x |\n## 7. 附录\n")

        unregistered = mod.code_codes() - mod.ledger_codes()

        self.assertEqual(unregistered, {"IAM_002"})

    def test_baseline_only_shrinks(self):
        # 代码已无定义（IAM_GONE）或译文已补齐（IAM_001）⇒ 豁免已不起作用 ⇒ 须收缩
        excluded = {"IAM_001", "IAM_GONE"}
        codes = {"IAM_001"}
        en = {"IAM_001"}

        stale_untranslated = {c for c in excluded if c not in codes or c in en}

        unregistered_baseline = {"IAM_002", "IAM_GONE"}
        ledger = {"IAM_002"}
        stale_unregistered = {
            c for c in unregistered_baseline if c not in codes or c in ledger
        }

        self.assertEqual(stale_untranslated, {"IAM_001", "IAM_GONE"})
        self.assertEqual(stale_unregistered, {"IAM_002", "IAM_GONE"})

    def test_fallback_errorcode_shaped_keys_detected(self):
        fallback = {"IAM_001": "x", "login.title": "y"}

        bad = [k for k in fallback if mod.CODE_SHAPE_RE.fullmatch(k)]

        self.assertEqual(bad, ["IAM_001"])

    # ---- 文本扫描 -------------------------------------------------------
    def test_grep_hits_reports_relative_file_and_line(self):
        _write(self.root, "apps/bone-shell/src/a.ts", "const a = 1;\nfoo.toLocaleString();\n")
        _write(self.root, "packages/shared-utils/src/b.ts", "ok\n")
        _write(self.root, "docs/c.ts", "foo.toLocaleString();\n")

        hits = mod.grep_hits({".ts"}, r"\.toLocaleString\(", {"apps", "packages"})

        self.assertEqual(hits, ["apps/bone-shell/src/a.ts:2"])

    def test_walk_skips_build_dirs_and_filters_suffix(self):
        _write(self.root, "apps/x.ts", "a\n")
        _write(self.root, "apps/node_modules/y.ts", "b\n")
        _write(self.root, "target/z.ts", "c\n")
        _write(self.root, "apps/note.md", "d\n")

        found = {p.relative_to(self.root).as_posix() for p in mod.walk({".ts"})}

        self.assertEqual(found, {"apps/x.ts"})

    def test_report_counts_and_truncates(self):
        items = [f"ITEM_{i:02d}" for i in range(12)]
        buf = io.StringIO()

        with contextlib.redirect_stdout(buf):
            mod.report("测试标题", items)

        out = buf.getvalue()
        self.assertIn("测试标题", out)
        self.assertIn("（12 条）", out)
        self.assertIn("ITEM_00", out)
        self.assertIn(" ...", out)
        self.assertNotIn("ITEM_11", out)  # 超过 10 条只打印前 10


if __name__ == "__main__":
    unittest.main(verbosity=2)
