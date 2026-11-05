#!/usr/bin/env python3
"""check-domain-model-layout.py 的 C6（package↔目录一致性）自测。

判据背景：2026-11-05 ClaimThresholds 实例——git mv 把类移进子包后 package 声明漏改，
必编译失败，但本门禁当时只看目录布局不看 package ⇒ 靠 mvn compile 才暴露（门禁在
pre-commit 阶段形同虚设）。C6 把暴露点前移到门禁：package 声明必须与文件所在目录一致。

夹具按 E-10 四层骨架最小构造（application + adapter 各一类触发四层判定），
同时覆盖 C2 平铺的sanity用例以证明夹具确实接到了原有判据。
"""

import importlib.util
import os
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "check-domain-model-layout.py",
)
_spec = importlib.util.spec_from_file_location("check_domain_model_layout", SCRIPT)
mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(mod)

PKG = "com.bone.demo"


def _write(root, rel, body):
    path = os.path.join(root, *rel.split("/"))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(body)


def _java(pkg, cls, body="public final class %s {}\n"):
    head = "package {};\n\n".format(pkg) if pkg else ""
    return head + (body % cls if "%s" in body else body)


class LayoutGateTest(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="domain-layout-")
        # 四层骨架：application + adapter 各一个类，触发 is_four_layer_module
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/application/DemoAppService.java",
            _java(f"{PKG}.application", "DemoAppService"),
        )
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/adapter/web/DemoController.java",
            _java(f"{PKG}.adapter.web", "DemoController"),
        )

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def _domain_problems(self):
        domain_dir = (
            Path(self.root) / "m/src/main/java" / PKG.replace(".", "/") / "domain"
        )
        found, _skipped = mod.find_domain_dirs(Path(self.root))
        self.assertTrue(found, "夹具必须被识别为四层模块，否则用例本身失效")
        problems = []
        for rel_module, d in found:
            problems.extend(mod.check_domain(rel_module, d, set()))
        return problems, domain_dir

    # ---- C6 正向：package 与目录一致不得误报 -----------------------------
    def test_matching_package_passes(self):
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/domain/model/config/Good.java",
            _java(f"{PKG}.domain.model.config", "Good"),
        )

        problems, _ = self._domain_problems()

        self.assertEqual(problems, [])

    # ---- C6：package 落后于目录（git mv 后漏改）必须红 -------------------
    def test_stale_package_is_reported(self):
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/domain/model/config/Bad.java",
            _java(f"{PKG}.domain.model", "Bad"),  # 声明还是移入前的旧包
        )

        problems, _ = self._domain_problems()

        self.assertTrue(
            any(p.startswith("C6") and "Bad.java" in p for p in problems), problems
        )

    # ---- C6：完全没有 package 声明（默认包）也必须红 ---------------------
    def test_missing_package_is_reported(self):
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/domain/model/config/NoPkg.java",
            "public final class NoPkg {}\n",
        )

        problems, _ = self._domain_problems()

        self.assertTrue(
            any(p.startswith("C6") and "NoPkg.java" in p for p in problems), problems
        )

    # ---- sanity：夹具同时接到原有 C2 判据 --------------------------------
    def test_flat_model_file_still_reported_as_c2(self):
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/domain/model/Flat.java",
            _java(f"{PKG}.domain.model", "Flat"),  # package 与目录一致 ⇒ 只该报 C2
        )

        problems, _ = self._domain_problems()

        self.assertTrue(any(p.startswith("C2") for p in problems), problems)
        self.assertFalse(any(p.startswith("C6") for p in problems), problems)

    # ---- 集成：main() 在 C6 命中时 exit 1 -------------------------------
    def test_main_exits_nonzero_on_c6(self):
        _write(
            self.root,
            f"m/src/main/java/{PKG.replace('.', '/')}/domain/model/config/Bad.java",
            _java(f"{PKG}.domain.model", "Bad"),
        )
        saved = sys.argv
        sys.argv = ["check-domain-model-layout.py", "--root", self.root]
        try:
            code = mod.main()
        finally:
            sys.argv = saved
        self.assertEqual(code, 1)


if __name__ == "__main__":
    unittest.main(verbosity=2)
