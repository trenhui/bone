"""表征测试：锁定 check-openapi-contract.py 的契约结构判据（ADR-0040 对外 SDK 唯一契约真源）。

做法：importlib 按字符串路径加载门禁脚本（文件名含连字符，无法 import），
monkeypatch 其模块级 OPENAPI_DIR 到临时目录，构造最小化 OpenAPI 规范，断言 main() 的
退出码与诊断文案。**不改门禁脚本本身**。

判据（[15/35] OpenAPI 契约结构门禁）：
  * 独立 API 文档（*-v1.yaml）必须可 YAML 解析、声明 components.securitySchemes.bearerAuth
    （type=http, scheme=bearer）、顶层 security 含 bearerAuth、所有本地 $ref 可解析；
  * 组件片段（components/*.yaml）只查可解析性与 $ref（认证由引用它的主文档承担）。

运行：python3 scripts/tests/test_check_openapi_contract.py -v
"""

from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
_GATE_PATH = (SCRIPTS_DIR / "check-openapi-contract.py").resolve()
_SPEC = importlib.util.spec_from_file_location("check_openapi_contract", str(_GATE_PATH))
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)

# 合法独立 API 文档：声明 bearerAuth + 顶层 security，并 $ref 一个存在的组件文件。
AUTH_OK = """\
openapi: "3.0.3"
info:
  title: demo
  version: "1.0"
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
security:
  - bearerAuth: []
paths:
  /x:
    get:
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                $ref: './components/schema.yaml'
"""
COMPONENT = "type: object\nproperties:\n  id:\n    type: string\n"

# 缺 securitySchemes + 缺顶层 security ⇒ 应失败。
BAD_NO_AUTH = """\
openapi: "3.0.3"
info:
  title: demo
  version: "1.0"
paths: {}
"""

# '@' 是 YAML 保留指示符 ⇒ 解析失败。
BROKEN_YAML = """\
openapi: "3.0.3"
info:
  title: @Capability 能力列表
  version: "1.0"
"""

# 顶层 security 与 securitySchemes 都在，但 $ref 指向不存在的节点 ⇒ 应失败。
BROKEN_REF = """\
openapi: "3.0.3"
info:
  title: demo
  version: "1.0"
components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
security:
  - bearerAuth: []
paths:
  /x:
    get:
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/NonExistent'
"""


class OpenApiContractGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="openapi-"))
        self._orig_dir = gate.OPENAPI_DIR
        self._orig_root = gate.REPO_ROOT
        gate.OPENAPI_DIR = self.tmp
        # main() 末尾用 OPENAPI_DIR.relative_to(REPO_ROOT) 拼打印路径，须同步 monkeypatch
        gate.REPO_ROOT = self.tmp

    def tearDown(self) -> None:
        gate.OPENAPI_DIR = self._orig_dir
        gate.REPO_ROOT = self._orig_root
        import shutil

        shutil.rmtree(self.tmp, ignore_errors=True)

    def _run(self) -> tuple[int, str]:
        saved = sys.argv
        sys.argv = ["check-openapi-contract.py"]
        buf = io.StringIO()
        try:
            with contextlib.redirect_stdout(buf):
                rc = gate.main()
        finally:
            sys.argv = saved
        return rc, buf.getvalue()

    def test_valid_with_resolvable_ref_passes(self) -> None:
        (self.tmp / "demo-v1.yaml").write_text(AUTH_OK, encoding="utf-8")
        (self.tmp / "components").mkdir()
        (self.tmp / "components" / "schema.yaml").write_text(COMPONENT, encoding="utf-8")
        rc, _ = self._run()
        self.assertEqual(rc, 0)

    def test_missing_security_scheme_fails(self) -> None:
        (self.tmp / "bad-v1.yaml").write_text(BAD_NO_AUTH, encoding="utf-8")
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("securitySchemes", out)

    def test_unparseable_yaml_fails(self) -> None:
        (self.tmp / "broken-v1.yaml").write_text(BROKEN_YAML, encoding="utf-8")
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("解析失败", out)

    def test_unresolved_ref_fails(self) -> None:
        (self.tmp / "ref-v1.yaml").write_text(BROKEN_REF, encoding="utf-8")
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("$ref", out)


if __name__ == "__main__":
    unittest.main()
