"""gatekit —— BONE 门禁 / 脚本共享框架（agent 可复用基础设施）。

为什么存在
----------
scripts/ 下 59 个 check-*.py 各自手搓 argparse / sys.exit / print / 仓库根定位，
导致：① 加新门禁要复制样板；② 退出码、输出格式不统一，agent 难以稳定解析；
③ 仓库根定位逻辑散落（仅 7/59 有统一实现）。gatekit 把这些收敛为一份。

用法（门禁迁移的最小改动）
------------------------
门禁脚本开头加一段 bootstrap 把 scripts/lib 加进 sys.path，再 import 框架：

    import sys
    from pathlib import Path
    def _gatekit_path() -> str:
        p = Path(__file__).resolve().parent
        for _ in range(8):
            if (p / "lib" / "gatekit").is_dir():
                return str(p / "lib")
            if p.parent == p:
                break
            p = p.parent
        raise RuntimeError("找不到 scripts/lib/gatekit")
    sys.path.insert(0, _gatekit_path())
    from gatekit import run, Report, repo_root

    def judge(report: Report, args) -> None:
        ...
        report.fail("xxx 违反判据")   # 违规：计入，最终退出码 1
        report.info("yyy 正常")        # 仅信息

    def main() -> int:                 # 保留 main() -> int 签名
        return run(judge, description="...")   # run() 返回退出码，不 sys.exit

    if __name__ == "__main__":
        sys.exit(main())

为何保留 `main() -> int`：既有 `test_check_*.py` 直接调用 `mod.main()` 并断言返回码与
打印内容；若用 `gate()`（内部 sys.exit）会直接杀掉测试进程。故门禁统一用 `run()`
（返回 int），由 `__main__` 负责 sys.exit。`example_gate.py` 是可直接复制的迁移模板。

注意：仓库根 bone/ 本身是一个 Python 包，故共享库以「scripts/lib 加入 sys.path 后
`import gatekit`」的方式导入，而非 `import lib.gatekit`（后者会被 bone 包名空间吞掉）。
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path
from typing import Callable, Optional

# 退出码契约（与 scripts/README.md §9.4 一致：0 合规 / 1 违规 / 2 运行期错误）
OK = 0
VIOLATION = 1
ERROR = 2

# 仓库根判定标记：命中任一即视为根（避免各门禁各写各的探测）
_ROOT_MARKERS = (".git", "pom.xml", "build.gradle", "settings.gradle", "go.mod")


def repo_root(start: Optional[Path] = None) -> Path:
    """统一仓库根探测（取代此前 52/59 门禁散落的定位逻辑）。

    优先读环境变量 BONE_REPO_ROOT（--root 注入），否则从 start 向上找 _ROOT_MARKERS。
    """
    env = os.environ.get("BONE_REPO_ROOT")
    if env:
        return Path(env).resolve()
    cur = (start or Path(__file__).resolve().parent).resolve()
    for _ in range(8):
        if any((cur / m).exists() for m in _ROOT_MARKERS):
            return cur
        if cur.parent == cur:
            break
        cur = cur.parent
    # 兜底：回退到本文件曾祖（scripts/lib/gatekit → scripts/lib → scripts → 仓库根）
    return Path(__file__).resolve().parents[3]


class GateError(Exception):
    """门禁运行期错误（连不上服务 / 读不到文件）—— 退出码 ERROR(2)，区别于判据违规。"""


class Report:
    """判据结果收集器：fail 计入违规、info 仅记录；render 输出、exit_code 给退出码。"""

    def __init__(self) -> None:
        self._failures: list[str] = []
        self._infos: list[str] = []
        self.quiet = False

    def fail(self, msg: str) -> None:
        self._failures.append(msg)

    def info(self, msg: str) -> None:
        self._infos.append(msg)

    @property
    def ok(self) -> bool:
        return not self._failures

    def render(self) -> None:
        if not self.quiet:
            for m in self._infos:
                print(f"[INFO] {m}")
        for m in self._failures:
            print(f"[FAIL] {m}")
        if self.ok:
            print(f"[OK] 通过（{len(self._infos)} 条信息，0 条违规）")
        else:
            print(f"[SUMMARY] 违规 {len(self._failures)} 条 / 信息 {len(self._infos)} 条")

    def exit_code(self) -> int:
        return OK if self.ok else VIOLATION


def run(
    main_fn: Callable[["Report", argparse.Namespace], None],
    *,
    description: str = "",
    build_parser: Optional[Callable[[argparse.ArgumentParser], None]] = None,
    argv: Optional[list[str]] = None,
) -> int:
    """执行判据并返回退出码（**不**调用 sys.exit）。

    门禁统一用本函数：保留 `main() -> int` 签名，由 `__main__` 负责 sys.exit。
    这样既有 `test_check_*.py` 直接调用 `mod.main()` 断言返回码时不会被 sys.exit 杀掉。

    main_fn 签名：(report: Report, args: argparse.Namespace) -> None
    build_parser: 可选，向 parser 追加门禁专属参数（如 --allow-empty）。
    argv: 显式传入参数列表（默认 sys.argv[1:]）；单测注入用，避免 mock sys.argv。
    约定：若模块定义了 module-level `self_test(report) -> None`，`--self-test` 会运行它
    （用于门禁自身存活验证，参照 check-gate-liveness 的变异验证思路）。
    """
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument("--self-test", action="store_true", help="运行门禁自身存活验证后退出")
    parser.add_argument("--report-only", action="store_true",
                        help="只观察不阻断：违规也返回退出码 0（须在 README 注明）")
    parser.add_argument("--quiet", action="store_true", help="仅输出违规行")
    parser.add_argument("--root", default=None, help="显式指定仓库根（默认自动探测）")
    if build_parser:
        build_parser(parser)
    args = parser.parse_args(list(argv) if argv is not None else sys.argv[1:])

    report = Report()
    report.quiet = args.quiet
    if args.root:
        os.environ["BONE_REPO_ROOT"] = args.root

    if args.self_test:
        mod = sys.modules.get(main_fn.__module__, None)
        fn = getattr(mod, "self_test", None) if mod is not None else None
        if fn is None:
            report.fail(f"模块 {main_fn.__module__} 未定义 self_test()，无法 --self-test")
        else:
            try:
                fn(report)
            except GateError as e:
                report.fail(f"self-test 运行期错误：{e}")
        report.render()
        return report.exit_code()

    try:
        main_fn(report, args)
    except GateError as e:
        print(f"[ERROR] {e}")
        return ERROR
    except FileNotFoundError as e:
        print(f"[ERROR] 文件不存在：{e}")
        return ERROR

    report.render()
    code = report.exit_code()
    if args.report_only:
        code = OK
    return code


def gate(
    main_fn: Callable[["Report", argparse.Namespace], None],
    *,
    description: str = "",
    build_parser: Optional[Callable[[argparse.ArgumentParser], None]] = None,
) -> None:
    """门禁入口：解析公共参数、运行 main_fn(report, args)、渲染并 sys.exit。

    等价于 `sys.exit(run(...))`。仅用于「脚本直接 python3 xxx.py 运行、不需要保留
    main() 返回码」的场景；要保留 main() 签名（配合 test_check_*.py）请用 run()。
    """
    sys.exit(run(main_fn, description=description, build_parser=build_parser))
