"""gatekit 门禁迁移模板（可直接复制改造为新门禁 / 改造既有 check-*）。

这是「最小改动迁移范式」的单一真源。把既有手搓 argparse + sys.exit(main()) 的门禁
收敛为 gatekit，只需三件事：

  1) 顶部 bootstrap 把 scripts/lib 加进 sys.path 后 `from gatekit import run, Report, repo_root`；
  2) 把原 `main()` 的判据逻辑抽成 `judge(report, args)`，用 report.fail/info 收集结果；
  3) 保留 `main() -> int`（内部 return run(judge, ...)），`__main__` 负责 sys.exit(main())。

为何必须保留 `main() -> int`：既有 `test_check_*.py` 直接调用 `mod.main()` 并断言
返回码与打印内容；若改用 `gate()`（内部 sys.exit）会直接杀掉测试进程。故一律用
`run()`（返回 int）而非 `gate()`。

门禁专属参数（如 --allow-empty）通过 build_parser 追加，框架的公共参数
（--self-test / --report-only / --quiet / --root）自动提供。self_test 用于门禁自身
存活验证（删掉 README 应转红，参照变异验证思路）。
"""

import argparse
import sys
from pathlib import Path


def _gatekit_path() -> str:
    """向上找 scripts/lib（含 gatekit 子包），返回该目录，使其可作为顶层包导入。"""
    p = Path(__file__).resolve().parent
    for _ in range(8):
        cand = p / "lib" / "gatekit"
        if cand.is_dir():
            return str(p / "lib")
        if p.parent == p:
            break
        p = p.parent
    raise RuntimeError("找不到 scripts/lib/gatekit")


sys.path.insert(0, _gatekit_path())
from gatekit import run, Report, repo_root  # noqa: E402


def judge(report: Report, args: argparse.Namespace) -> None:
    """判据主体：只收集结果，不负责退出码 / 输出（框架统一处理）。"""
    root = repo_root()
    report.info(f"仓库根探测为：{root}")
    readme = root / "scripts" / "README.md"
    if not readme.is_file():
        report.fail("scripts/README.md 缺失：总索引是编目铁律的载体")
    else:
        report.info("scripts/README.md 存在")
    if args.dry_demo:
        report.info("--dry-demo 仅演示门禁专属参数已接入")


def build_parser(parser: argparse.ArgumentParser) -> None:
    """追加门禁专属参数（框架的公共参数已自动提供）。"""
    parser.add_argument(
        "--dry-demo", action="store_true",
        help="仅演示：门禁专属参数通过 build_parser 接入（模板示例）",
    )


def main() -> int:
    """保留 main() -> int：供 test_check_*.py 直接调用断言返回码，不 sys.exit。"""
    return run(judge, description="gatekit 门禁迁移模板（非生产，复制用）",
               build_parser=build_parser)


def self_test(report: Report) -> None:
    """门禁自身存活验证：repo_root 必须能探测到仓库根标记。"""
    root = repo_root()
    if not ((root / ".git").exists() or (root / "pom.xml").exists()):
        report.fail("self-test 失败：repo_root() 未探测到仓库根标记")
        return
    report.info("self-test 通过：repo_root 可用")


if __name__ == "__main__":
    sys.exit(main())
