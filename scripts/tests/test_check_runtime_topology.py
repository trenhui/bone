"""表征测试：锁定 check-runtime-topology.py 的判据（R1/R2/R3）。

门禁脚本文件名含连字符，无法按名 import；用 importlib 按 **str 路径** 加载
（Py3.14 的 SourceFileLoader.get_data 对 Path 会报 FileNotFoundError，见既有单测同款注释）。
**不改门禁脚本本身**——只断言其当前契约不被回归。

为什么必须有这层单测：这条门禁存在的意义是"抓住图与代码漂移"，若判据本身回归
（比如把 R1 写成永远成立），图会重新开始静默过期，而 check.sh 依旧全绿。

运行：``python3 scripts/tests/test_check_runtime_topology.py -v``
"""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

SCRIPTS_DIR = Path(__file__).resolve().parent.parent
_GATE_PATH = str(SCRIPTS_DIR / "check-runtime-topology.py")
_SPEC = importlib.util.spec_from_file_location("check_runtime_topology", _GATE_PATH)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)


def _svg(*ports: int) -> str:
    """构造只含端口文本的最小 SVG。"""
    body = "".join(f'<text x="0" y="{i * 12}">:{p}</text>' for i, p in enumerate(ports))
    return f'<svg xmlns="http://www.w3.org/2000/svg">{body}</svg>'


class ParseServerPortTest(unittest.TestCase):
    def test_plain_port(self):
        self.assertEqual(gate.parse_server_port("server:\n  port: 8083\n"), 8083)

    def test_placeholder_default(self):
        # bone-iam / gateway / claim 都是 ${VAR:PORT} 形式，必须取默认值
        self.assertEqual(
            gate.parse_server_port("server:\n  port: ${BONE_SERVER_PORT:8081}\n"), 8081
        )

    def test_nested_redis_port_not_mistaken(self):
        """spring.redis.port 不是服务端口；误取会让 R1/R2 全盘错判。"""
        text = "spring:\n  redis:\n    port: ${BONE_REDIS_PORT:6379}\n"
        self.assertIsNone(gate.parse_server_port(text))

    def test_no_port(self):
        self.assertIsNone(gate.parse_server_port("spring:\n  application:\n    name: x\n"))

    def test_port_outside_server_block_ignored(self):
        text = "app:\n  port: 9999\nserver:\n  port: 8085\n"
        self.assertEqual(gate.parse_server_port(text), 8085)


class ValidateTest(unittest.TestCase):
    BACKEND = {"mod/a": 8081, "mod/b": 8082}
    FRONTEND = {"app-a": 3003}
    REQUIRED = {"mod/a": "svc-a", "mod/b": "svc-b"}

    def _validate(self, svg: str):
        return gate.validate(svg, self.BACKEND, self.FRONTEND, self.REQUIRED, {3306})

    def test_clean_graph_passes(self):
        svg = _svg(8081, 8082, 3003, 3306) + "svc-a svc-b"
        errors, _ = self._validate(svg)
        self.assertEqual(errors, [])

    def test_phantom_port_detected(self):
        """R1：端口改了但图没改（图上是旧端口）→ 必须红。"""
        svg = _svg(8081, 8082, 3003, 9999) + "svc-a svc-b"
        errors, _ = self._validate(svg)
        self.assertTrue(any("R1" in e and "9999" in e for e in errors), errors)

    def test_missing_required_module_detected(self):
        """R2：整块模块漏画 → 必须红（blueprint/extension-studio 就是这样丢的）。"""
        svg = _svg(8081, 3003) + "svc-a svc-b"
        errors, _ = self._validate(svg)
        self.assertTrue(any("R2" in e and "8082" in e for e in errors), errors)

    def test_missing_label_detected(self):
        """R2：端口在但标签写错/缺失也应红（读者靠标签认服务）。"""
        svg = _svg(8081, 8082, 3003) + "svc-a"
        errors, _ = self._validate(svg)
        self.assertTrue(any("svc-b" in e for e in errors), errors)

    def test_missing_frontend_app_detected(self):
        """R3：前端新增子应用未上图 → 必须红。"""
        svg = _svg(8081, 8082) + "svc-a svc-b"
        errors, _ = self._validate(svg)
        self.assertTrue(any("R3" in e and "3003" in e for e in errors), errors)

    def test_undrawn_backend_port_is_warning_not_error(self):
        """真源有、图上无，只提示不判红：不强迫把非开发者面向的模块塞进图。"""
        # 注意必须保留 b(8082)：若把它从真源里删掉，图上的 8082 会变成幽灵端口而触发 R1，
        # 测的就不是"未画入只提示"这条契约了。
        self.BACKEND = {"mod/a": 8081, "mod/b": 8082, "mod/c": 8099}
        svg = _svg(8081, 8082, 3003) + "svc-a svc-b"
        errors, warnings = gate.validate(svg, self.BACKEND, self.FRONTEND, self.REQUIRED, {3306})
        self.assertEqual(errors, [])
        self.assertTrue(any("8099" in w for w in warnings), warnings)


class RepoTopologyTest(unittest.TestCase):
    def test_repository_topology_is_consistent(self):
        """仓库内真实拓扑图必须与代码真源一致（这条失败就是图又漂了）。"""
        svg_path = SCRIPTS_DIR.parent / "doc/design/assets/readme-runtime-topology.svg"
        if not svg_path.is_file():
            self.skipTest("拓扑图不存在（裁剪检出）")
        backend = gate.collect_backend_ports(SCRIPTS_DIR.parent)
        frontend = gate.collect_frontend_ports(SCRIPTS_DIR.parent)
        errors, _ = gate.validate(svg_path.read_text(encoding="utf-8"), backend, frontend)
        self.assertEqual(errors, [])


if __name__ == "__main__":
    unittest.main()
