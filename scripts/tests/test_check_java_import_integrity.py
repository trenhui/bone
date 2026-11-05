"""表征测试：锁定 check-java-import-integrity.py 的判据（R1/R2/R3 + MapStruct + 豁免棘轮）。

背景：这条门禁存在的唯一目的是拦住「引用方入库、被引用方只在工作树」——该形态已复发三次
（`ClaimThresholds` 让 dev tip 不可构建；`ClaimCaseTest` 无参调用编不过；`ClaimDicts` 待发）。
判据一旦悄悄失效，就会退化成「全绿但什么都没查」，而它的绿恰好会被当成「仓库完整」。

做法：门禁读的是 **git 索引**，所以夹具必须是**真仓库**——裸目录里没有索引，
「已 add / 未 add」根本无从区分（这正是 M11 那套夹具的理由）。每个用例 `git init`
一个临时仓库，`git add` 精确控制哪些文件进索引，断言 `main()` 退出码与诊断文案。
**不改门禁脚本本身的判定**——只验证其当前契约不被回归。

运行：``python3 scripts/tests/test_check_java_import_integrity.py -v``
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import unittest.mock
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent

# 文件名含连字符，不能 import；用 str 路径加载（Py3.14 SourceFileLoader 对 Path 有 get_data 问题）。
_GATE_PATH = str(SCRIPTS_DIR / "check-java-import-integrity.py")
_SPEC = importlib.util.spec_from_file_location("check_java_import_integrity", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)

OK_JAVA = "package com.bone.app;\n\nclass Ok {}\n"
SUB_JAVA = "package com.bone.app.sub;\n\nclass Other {}\n"
ALLOWLIST = "scripts/java-import-integrity-allowlist.json"


def user_importing(fq: str) -> str:
    return f"package com.bone.app;\n\nimport {fq};\n\nclass User {{}}\n"


def clean_git_env() -> dict:
    """剥离 GIT_INDEX_FILE / GIT_DIR / GIT_WORK_TREE 后的环境。

    pre-commit 会导出 GIT_DIR（linked worktree 下是 `.git/worktrees/<name>`）与 `commit
    --only` 的临时索引。带着它们跑 `git init <临时目录>` 不是「在临时目录建仓库」，而是
    **重置外层仓库**——夹具目录里根本没有 `.git`，后续门禁读索引直接失败。
    本轮就是这样红的：`test_空索引判红` 在本地裸跑绿、进 pre-commit 后红。
    """
    return {
        k: v
        for k, v in os.environ.items()
        if k not in ("GIT_INDEX_FILE", "GIT_DIR", "GIT_WORK_TREE")
    }


class _GitRepoCase(unittest.TestCase):
    """每个用例一个真 git 仓库：判据的真源是索引，只有真索引才能构造「未 add」。"""

    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="import-integrity-"))
        self.git("init", "-q")
        self.git("config", "user.email", "gate@test.local")
        self.git("config", "user.name", "gate")
        self.write("com/bone/app/Ok.java", OK_JAVA)

    def tearDown(self) -> None:
        shutil.rmtree(self.root, ignore_errors=True)

    def git(self, *args: str) -> str:
        """跑 git。剥离 GIT_INDEX_FILE/GIT_DIR/GIT_WORK_TREE：本测试可能在 pre-commit
        环境里跑，那套环境带着 `commit --only` 的临时索引，会把夹具的 add/commit 写到
        错误的索引上（M11 夹具同款处理）。"""
        return subprocess.run(
            ["git", *args],
            cwd=self.root,
            capture_output=True,
            text=True,
            check=True,
            env=clean_git_env(),
        ).stdout

    def write(self, rel: str, content: str) -> None:
        p = self.root / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")

    def add(self, *rels: str) -> None:
        self.git("add", "-f", *rels)

    def allowlist(self, entries) -> None:
        self.write(ALLOWLIST, json.dumps({"exemptions": entries}, ensure_ascii=False))

    def run_gate(self):
        buf = io.StringIO()
        old = sys.argv
        try:
            sys.argv = ["check-java-import-integrity.py", "--root", str(self.root)]
            with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                code = gate.main()
        finally:
            sys.argv = old
        return code, buf.getvalue()


class R1ClassMissingTest(_GitRepoCase):
    """R1：包在库内、类不在。"""

    def test_引用方入库_被引用方未入库必须判红(self):
        """最直接的形态：import 的类压根没有对应文件。"""
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Missing"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("R1:", out)
        self.assertIn("com.bone.app.Missing", out)
        self.assertIn("User.java:3", out)

    def test_两者都入库放行(self):
        self.write("com/bone/app/Missing.java", "package com.bone.app;\n\nclass Missing {}\n")
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Missing"))
        self.add("com/bone/app/Ok.java", "com/bone/app/Missing.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_外部依赖不判(self):
        """Spring/JDK 等外部包占全仓 import 的六成，误报一次就会有人关掉这条门禁。"""
        self.write(
            "com/bone/app/User.java",
            "package com.bone.app;\n\nimport java.util.List;\n"
            "import org.springframework.context.annotation.Bean;\n\nclass User {}\n",
        )
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_嵌套类只看最外层(self):
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Ok.Inner"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_static_import_缺失类同样判红(self):
        self.write(
            "com/bone/app/User.java",
            "package com.bone.app;\n\nimport static com.bone.app.Missing.X;\n\nclass User {}\n",
        )
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("com.bone.app.Missing", out)

    def test_通配import不判(self):
        """无从判定时不判（当前全仓 0 处通配 import；出现也不该拿误报换信任）。"""
        self.write(
            "com/bone/app/User.java",
            "package com.bone.app;\n\nimport com.bone.app.*;\n\nclass User {}\n",
        )
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)


class R2WholePackageMissingTest(_GitRepoCase):
    """R2：被引用的类是那个包里**唯一**的文件，且未入库 ⇒ 包整体不存在。

    这是本次真实缺陷（ClaimThresholds）的形态，也是首版判据放行的那条路径：
    「包不在库内 ⇒ 当外部依赖跳过」。只有用**缺陷态**去跑才会发现这个洞。

    夹具必须还原真实形状：`com.bone.app.sub` 自身在索引里一个类都没有，
    但它**是某个已入库包的父包**（`…sub.deeper`）——真实世界里就是
    `com.bone.claim.domain.model`（只有 ClaimThresholds，未入库）与
    `…domain.model.claim`（ClaimCase 等，已入库）的关系。
    """

    def test_整包未入库判红(self):
        self.write("com/bone/app/sub/deeper/Deep.java", "package com.bone.app.sub.deeper;\n\nclass Deep {}\n")
        self.write("com/bone/app/sub/Only.java", "package com.bone.app.sub;\n\nclass Only {}\n")
        self.write("com/bone/app/User.java", user_importing("com.bone.app.sub.Only"))
        # 刻意只 add 引用方与更深层的包；Only.java 留在工作树（真实缺陷就是这样产生的）
        self.add(
            "com/bone/app/Ok.java",
            "com/bone/app/sub/deeper/Deep.java",
            "com/bone/app/User.java",
        )
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("R2:", out)
        self.assertIn("com.bone.app.sub", out)

    def test_整包入库后放行(self):
        self.write("com/bone/app/sub/deeper/Deep.java", "package com.bone.app.sub.deeper;\n\nclass Deep {}\n")
        self.write("com/bone/app/sub/Only.java", "package com.bone.app.sub;\n\nclass Only {}\n")
        self.write("com/bone/app/User.java", user_importing("com.bone.app.sub.Only"))
        self.add(
            "com/bone/app/Ok.java",
            "com/bone/app/sub/deeper/Deep.java",
            "com/bone/app/sub/Only.java",
            "com/bone/app/User.java",
        )
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)


class R3NewPackageMissingTest(_GitRepoCase):
    """R3：新建子包、只有一个新文件、且未入库 ⇒ 包既不在库内也不在任何前缀里。"""

    def test_新子包未入库判红(self):
        self.write(
            "com/bone/app/brandnew/Thing.java",
            "package com.bone.app.brandnew;\n\nclass Thing {}\n",
        )
        self.write("com/bone/app/User.java", user_importing("com.bone.app.brandnew.Thing"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("R3:", out)

    def test_库外新包不判(self):
        """库外命名空间（如公司内部另一个 jar）不归本仓管，不判。"""
        self.write("com/bone/app/User.java", user_importing("com.other.lib.Widget"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)


class GeneratedClassTest(_GitRepoCase):
    """MapStruct 生成的 *Impl 只认「@Mapper 基接口」，不按名字通用豁免。"""

    def test_mapper基接口的Impl放行(self):
        self.write(
            "com/bone/app/OrderAssembler.java",
            "package com.bone.app;\n\n@Mapper(componentModel = \"spring\")\n"
            "public interface OrderAssembler {}\n",
        )
        self.write("com/bone/app/User.java", user_importing("com.bone.app.OrderAssemblerImpl"))
        self.add("com/bone/app/Ok.java", "com/bone/app/OrderAssembler.java",
                 "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_非mapper的Impl照样判红(self):
        """防「名字以 Impl 结尾就放行」——那等于给所有 *Impl 开后门。"""
        self.write("com/bone/app/Widget.java", "package com.bone.app;\n\nclass Widget {}\n")
        self.write("com/bone/app/User.java", user_importing("com.bone.app.WidgetImpl"))
        self.add("com/bone/app/Ok.java", "com/bone/app/Widget.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("com.bone.app.WidgetImpl", out)


class IndexIsTruthTest(_GitRepoCase):
    """真源是索引，不是工作区——否则本地改到一半会让无关提交变红。"""

    def test_只改工作区未暂存的import放行(self):
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Ok"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        # 之后才在工作区加一行引用一个还没 add 的新类
        self.write("com/bone/app/User.java", user_importing("com.bone.app.NotYetTracked"))
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_已暂存的import仍判红(self):
        self.write("com/bone/app/User.java", user_importing("com.bone.app.NotYetTracked"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)


class AllowlistTest(_GitRepoCase):
    """豁免只可收缩：reason 过短、owner 空、条目已失效都判红。"""

    def _violating_repo(self):
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Missing"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")

    def test_登记豁免后放行(self):
        self._violating_repo()
        self.allowlist([
            {
                "fqn": "com.bone.app.Missing",
                "reason": "由构建期代码生成器产出，不进源码树（示例）",
                "owner": "arch-team",
            }
        ])
        code, out = self.run_gate()
        self.assertEqual(code, 0, out)

    def test_豁免理由过短判红(self):
        self._violating_repo()
        self.allowlist([
            {"fqn": "com.bone.app.Missing", "reason": "生成", "owner": "arch-team"}
        ])
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("reason", out)

    def test_豁免缺owner判红(self):
        self._violating_repo()
        self.allowlist([
            {
                "fqn": "com.bone.app.Missing",
                "reason": "由构建期代码生成器产出，不进源码树（示例）",
                "owner": "",
            }
        ])
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("owner", out)

    def test_过期豁免判红(self):
        """类已经入库，条目还留着 ⇒ 白名单必须收缩，否则会越写越宽。"""
        self.write("com/bone/app/Missing.java", "package com.bone.app;\n\nclass Missing {}\n")
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Missing"))
        self.add("com/bone/app/Ok.java", "com/bone/app/Missing.java",
                 "com/bone/app/User.java")
        self.allowlist([
            {
                "fqn": "com.bone.app.Missing",
                "reason": "由构建期代码生成器产出，不进源码树（示例）",
                "owner": "arch-team",
            }
        ])
        code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("R5", out)


class AntiFalseGreenTest(_GitRepoCase):
    """防假绿：判据恒绿、空索引、非 git 目录都必须判红而不是「通过」。"""

    def test_判据被改成恒绿会被哨兵抓住(self):
        self.write("com/bone/app/User.java", user_importing("com.bone.app.Ok"))
        self.add("com/bone/app/Ok.java", "com/bone/app/User.java")
        original = gate.scan
        gate.scan = lambda sources: []  # 变异：判据永远返回「无违规」
        try:
            code, out = self.run_gate()
        finally:
            gate.scan = original
        self.assertEqual(code, 1, out)
        self.assertIn("哨兵", out)

    def test_空索引判红(self):
        """一条 .java 都没扫到，不能算通过。"""
        empty = Path(tempfile.mkdtemp(prefix="import-integrity-empty-"))
        try:
            subprocess.run(
                ["git", "init", "-q"],
                cwd=empty,
                check=True,
                capture_output=True,
                text=True,
                env=clean_git_env(),
            )
            (empty / "README.md").write_text("no java here\n", encoding="utf-8")
            buf = io.StringIO()
            old = sys.argv
            try:
                sys.argv = ["check-java-import-integrity.py", "--root", str(empty)]
                with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                    code = gate.main()
            finally:
                sys.argv = old
            self.assertEqual(code, 1, buf.getvalue())
            self.assertIn("空集合", buf.getvalue())
        finally:
            shutil.rmtree(empty, ignore_errors=True)

    def test_非git目录判红(self):
        bare = Path(tempfile.mkdtemp(prefix="import-integrity-nogit-"))
        try:
            (bare / "A.java").write_text("package p;\nclass A {}\n", encoding="utf-8")
            buf = io.StringIO()
            old = sys.argv
            try:
                sys.argv = ["check-java-import-integrity.py", "--root", str(bare)]
                with contextlib.redirect_stdout(buf), contextlib.redirect_stderr(buf):
                    code = gate.main()
            finally:
                sys.argv = old
            self.assertEqual(code, 1, buf.getvalue())
            self.assertIn("git", buf.getvalue())
        finally:
            shutil.rmtree(bare, ignore_errors=True)


class PreCommitEnvTest(_GitRepoCase):
    """门禁本身也会在 pre-commit 环境里被调用：那里导出 GIT_DIR / GIT_INDEX_FILE。

    本轮真红过：`test_空索引判红` 在本地裸跑绿、进了 pre-commit 就红——夹具用未剥离的
    环境跑 `git init`，实际重置的是外层仓库。这里把「被污染环境」本身变成一个用例，
    免得下一个人再次只在裸环境里验证。
    """

    def test_导出GIT_DIR时仍按夹具仓库判定(self):
        # 沿用 R2 的真实形状（被引用包没有类、但它是某个已入库包的父包），
        # 否则只会在 R3 命中，测不到「索引读的是夹具仓库」这一点。
        self.write(
            "com/bone/app/sub/deeper/Deep.java", "package com.bone.app.sub.deeper;\n\nclass Deep {}\n"
        )
        self.write("com/bone/app/sub/Only.java", SUB_JAVA)
        self.write("com/bone/app/User.java", user_importing("com.bone.app.sub.Only"))
        self.add(
            "com/bone/app/Ok.java",
            "com/bone/app/sub/deeper/Deep.java",
            "com/bone/app/User.java",
        )
        with unittest.mock.patch.dict(
            os.environ, {"GIT_DIR": "/nonexistent.git", "GIT_INDEX_FILE": "/nonexistent.idx"}, clear=False
        ):
            code, out = self.run_gate()
        self.assertEqual(code, 1, out)
        self.assertIn("R2:", out)


if __name__ == "__main__":
    unittest.main()
