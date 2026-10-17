"""表征测试：锁定 check-controller-response-envelope.py（HC-003 统一响应信封门禁）。

做法：importlib 按字符串路径加载门禁脚本（文件名含连字符，无法 import），
monkeypatch 其模块级 REPO / BASELINE_PATH 到临时目录（baseline 不存在 ⇒ 无豁免/无存量
基线，所有违规按"新增"计，精确锁定判据）。在临时仓库的 bone-engine 下放置最小化
Controller.java，断言 main() 退出码与诊断。**不改门禁脚本本身**。

判据（[28/35] HC-003）：
  * 阻断①：端点返回类型不是统一信封（ApiResponse/PageResult/ResponseEntity<信封>/void）
    ⇒ envelope_rows 新增违规；
  * 阻断②：信封载荷是 ..domain.gateway.. 端口契约类型（如 ActionDefSpec）
    ⇒ gateway_rows 新增违规；
  * 报告级：信封载荷是 ..domain.. 其余（贫血实体）⇒ 仅报告、不阻断。

运行：python3 scripts/tests/test_check_controller_response_envelope.py -v
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
_GATE_PATH = (SCRIPTS_DIR / "check-controller-response-envelope.py").resolve()
_SPEC = importlib.util.spec_from_file_location(
    "check_controller_response_envelope", str(_GATE_PATH)
)
gate = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gate)


class ControllerEnvelopeGateTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = Path(tempfile.mkdtemp(prefix="hc003-"))
        self._orig_repo = gate.REPO
        self._orig_baseline = gate.BASELINE_PATH
        gate.REPO = self.tmp
        # baseline 不存在 ⇒ load_baseline() 返回空集合，全部违规按"新增"计
        gate.BASELINE_PATH = self.tmp / "controller-response-baseline.json"

    def tearDown(self) -> None:
        gate.REPO = self._orig_repo
        gate.BASELINE_PATH = self._orig_baseline
        import shutil

        shutil.rmtree(self.tmp, ignore_errors=True)

    def _ctrl(self, name: str, src: str) -> None:
        d = (
            self.tmp
            / "bone-engine"
            / "src"
            / "main"
            / "java"
            / "com"
            / "example"
        )
        d.mkdir(parents=True, exist_ok=True)
        (d / f"{name}Controller.java").write_text(src, encoding="utf-8")

    def _run(self) -> tuple[int, str]:
        saved = sys.argv
        sys.argv = ["check-controller-response-envelope.py"]
        buf = io.StringIO()
        try:
            with contextlib.redirect_stdout(buf):
                rc = gate.main()
        finally:
            sys.argv = saved
        return rc, buf.getvalue()

    def test_valid_envelope_passes(self) -> None:
        self._ctrl(
            "Valid",
            """
package com.example;
import org.springframework.web.bind.annotation.*;
import com.bone.foo.ApiResponse;
@RestController
public class ValidController {
    @GetMapping("/z")
    public ApiResponse<Void> getZ() { return null; }
}
""",
        )
        rc, _ = self._run()
        self.assertEqual(rc, 0)

    def test_raw_domain_return_fails(self) -> None:
        self._ctrl(
            "Raw",
            """
package com.example;
import org.springframework.web.bind.annotation.*;
@RestController
public class RawController {
    @GetMapping("/x")
    public ActionDefSpec getX() { return null; }
}
""",
        )
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("新增", out)

    def test_gateway_payload_fails(self) -> None:
        self._ctrl(
            "Gw",
            """
package com.example;
import org.springframework.web.bind.annotation.*;
import com.bone.foo.domain.gateway.ActionDefSpec;
@RestController
public class GwController {
    @GetMapping("/y")
    public com.bone.foo.ApiResponse<ActionDefSpec> getY() { return null; }
}
""",
        )
        rc, out = self._run()
        self.assertEqual(rc, 1)
        self.assertIn("端口契约", out)

    def test_domain_payload_only_reports(self) -> None:
        self._ctrl(
            "Dom",
            """
package com.example;
import org.springframework.web.bind.annotation.*;
import com.bone.foo.domain.Foo;
import com.bone.foo.ApiResponse;
@RestController
public class DomController {
    @GetMapping("/w")
    public ApiResponse<Foo> getW() { return null; }
}
""",
        )
        rc, out = self._run()
        self.assertEqual(rc, 0)
        self.assertIn("报告级", out)


if __name__ == "__main__":
    unittest.main()
