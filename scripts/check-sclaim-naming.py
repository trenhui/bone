#!/usr/bin/env python3
"""SynapClaim 命名规范机检（规范正文：doc/ai-product/synapClaim/03-contract/SynapClaim-命名规范.md）。

判据（G1–G4，见规范 §8）：
  G1 系统缩写与路径构造：sclaimRoutes.ts 必须是 `sclaim` + `buildSclaimPath` 三级模板；
     模块值小写短横线，页面值在注册表内。
  G2 无遗留别名：生产源码不得出现 `/standalone` / `/claim-detail`
     （单测中的反例断言 *.test.* / *.spec.* 允许，用来证明旧路径确实失效）。
  G3 文档-代码一致：代码中已落地的每个模块都必须在规范 §4 注册表登记。
  G4 API 裁定不漂移：后端 / 网关 / 前端 service / E2E 不得出现 `/api/sclaim`
     （平台强制 `/api/v1/{domain}`，见 Bone-API-规范 §2.1/§2.2）。

用法：
  python3 scripts/check-sclaim-naming.py --self-test   # 4+ 负向探针，证明判据能变红
  python3 scripts/check-sclaim-naming.py --check       # 主判据（默认）
  python3 scripts/check-sclaim-naming.py --report-only # 只打印，不阻断

负向自测用 tempfile 建夹具，不依赖 /tmp 残留；换机/并行会话不会假红。
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

ROUTES_REL = "bone-frontend/apps/bone-claim-app/src/workbench/sclaimRoutes.ts"
DOC_REL = "doc/ai-product/synapClaim/03-contract/SynapClaim-命名规范.md"

ALLOWED_PAGES = {"list", "detail", "create", "edit", "recognition"}
LEGACY_PATHS = ("/standalone", "/claim-detail")
FORBIDDEN_API = "/api/sclaim"

LEGACY_SCAN_DIRS = (
    "bone-frontend/apps/bone-claim-app/src",
    "bone-frontend/apps/bone-shell/src",
    "scripts/e2e/ui",
)
API_SCAN_DIRS = (
    "bone-apps/synap-claim",
    "bone-platform/bone-gateway/src/main/resources",
    "bone-frontend/apps/bone-claim-app/src/services",
    "scripts/e2e",
)
WEB_SUFFIXES = {".ts", ".tsx", ".mjs", ".js", ".jsx"}
API_SUFFIXES = WEB_SUFFIXES | {".java", ".yml", ".yaml", ".py"}
SKIP_PARTS = {"node_modules", "target", "dist", ".git"}


def _read(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def _iter_files(root: Path, dirs: tuple[str, ...], suffixes: set[str]):
    for rel in dirs:
        base = root / rel
        if not base.exists():
            continue
        for path in base.rglob("*"):
            if not path.is_file() or path.suffix not in suffixes:
                continue
            if any(part in SKIP_PARTS for part in path.parts):
                continue
            yield path


def _extract_values(src: str, const_name: str) -> list[str]:
    """从 `export const X = { A: 'a', B: 'b' } as const;` 抽取值。"""
    match = re.search(
        r"export const " + re.escape(const_name) + r"\s*=\s*\{(.*?)\}\s*as const",
        src,
        re.S,
    )
    if not match:
        return []
    return re.findall(r":\s*['\"]([^'\"]+)['\"]", match.group(1))


def check(root: Path) -> list[str]:
    errors: list[str] = []

    # ---------- G1 系统缩写与路径构造 ----------
    routes = root / ROUTES_REL
    modules: list[str] = []
    if not routes.exists():
        errors.append(f"G1: 缺少规范真源 {ROUTES_REL}")
    else:
        src = _read(routes)
        if not re.search(r"export const SCLAIM_SYSTEM\s*=\s*['\"]sclaim['\"]", src):
            errors.append("G1: SCLAIM_SYSTEM 必须为 'sclaim'（系统缩写唯一真源）")
        if not re.search(
            r"return\s+`/\$\{SCLAIM_SYSTEM\}/\$\{module\}/\$\{page\}`", src
        ):
            errors.append("G1: buildSclaimPath 必须由 /${SCLAIM_SYSTEM}/${module}/${page} 构造")
        modules = _extract_values(src, "SCLAIM_MODULE")
        pages = _extract_values(src, "SCLAIM_PAGE")
        if not modules:
            errors.append("G1: 未找到 SCLAIM_MODULE 注册表")
        for module in modules:
            if not re.fullmatch(r"[a-z][a-z0-9-]*", module):
                errors.append(f"G1: 模块标识非法（须小写短横线）: {module}")
        if not pages:
            errors.append("G1: 未找到 SCLAIM_PAGE 注册表")
        for page in pages:
            if page not in ALLOWED_PAGES:
                errors.append(f"G1: 页面标识未在注册表登记: {page}")

    # ---------- G2 无遗留别名 ----------
    for path in _iter_files(root, LEGACY_SCAN_DIRS, WEB_SUFFIXES):
        if ".test." in path.name or ".spec." in path.name:
            continue
        text = _read(path)
        for legacy in LEGACY_PATHS:
            if legacy in text:
                rel = path.relative_to(root)
                errors.append(f"G2: {rel} 出现已废止路径 {legacy}")

    # ---------- G3 文档-代码一致 ----------
    doc = root / DOC_REL
    if not doc.exists():
        errors.append(f"G3: 缺少规范正文 {DOC_REL}")
    else:
        doc_text = _read(doc)
        if "## 4. 模块注册表" not in doc_text:
            errors.append("G3: 规范文档缺少 §4 模块注册表")
        for module in modules:
            # 表格里模块名可能用反引号包裹（`claim`），两种写法都算登记。
            row = r"^\|\s*`?" + re.escape(module) + r"`?\s*\|"
            if not re.search(row, doc_text, re.M):
                errors.append(f"G3: 模块 {module} 已在代码登记但未写入规范 §4 模块注册表")

    # ---------- G4 API 裁定不漂移 ----------
    for path in _iter_files(root, API_SCAN_DIRS, API_SUFFIXES):
        if FORBIDDEN_API in _read(path):
            rel = path.relative_to(root)
            errors.append(f"G4: {rel} 出现 {FORBIDDEN_API}（API 路径必须以 /api/v1 为准，见规范 §7.1）")

    return errors


# ---------------------------------------------------------------------------
# 负向自测：每个 G 注入一条反例，必须判红；夹具全部在 tempfile 内，跑完即清理。
# ---------------------------------------------------------------------------
_FIXTURE_ROUTES = """\
export const SCLAIM_SYSTEM = 'sclaim';

export const SCLAIM_MODULE = {
  CLAIM: 'claim',
  POLICY: 'policy',
} as const;

export const SCLAIM_PAGE = {
  LIST: 'list',
  DETAIL: 'detail',
} as const;

export function buildSclaimPath(module: string, page: string): string {
  return `/${SCLAIM_SYSTEM}/${module}/${page}`;
}
"""

_FIXTURE_DOC = """\
## 4. 模块注册表

| 模块标识 | 中文名 | 落地状态 |
|---|---|---|
| `claim` | 赔案 | 已落地 |
| `policy` | 保单 | 已落地 |
"""


def _build_fixture(root: Path) -> None:
    routes = root / ROUTES_REL
    routes.parent.mkdir(parents=True, exist_ok=True)
    routes.write_text(_FIXTURE_ROUTES, encoding="utf-8")
    doc = root / DOC_REL
    doc.parent.mkdir(parents=True, exist_ok=True)
    doc.write_text(_FIXTURE_DOC, encoding="utf-8")
    service = root / "bone-frontend/apps/bone-claim-app/src/services/demo.ts"
    service.parent.mkdir(parents=True, exist_ok=True)
    service.write_text("createApiClient('/api/v1/claims')", encoding="utf-8")


def self_test() -> int:
    cases: list[tuple[str, str, object]] = [
        (
            "G1",
            "系统缩写改成 sc 必须判红",
            lambda root: (root / ROUTES_REL).write_text(
                _FIXTURE_ROUTES.replace("SCLAIM_SYSTEM = 'sclaim'", "SCLAIM_SYSTEM = 'sc'"),
                encoding="utf-8",
            ),
        ),
        (
            "G1",
            "路径构造改成 /api/ 必须判红",
            lambda root: (root / ROUTES_REL).write_text(
                _FIXTURE_ROUTES.replace(
                    "return `/${SCLAIM_SYSTEM}/${module}/${page}`;",
                    "return `/api/${module}/${page}`;",
                ),
                encoding="utf-8",
            ),
        ),
        (
            "G1",
            "未登记页面标识必须判红",
            lambda root: (root / ROUTES_REL).write_text(
                _FIXTURE_ROUTES.replace("DETAIL: 'detail',", "DETAIL: 'detail',\n  VIEW: 'view',"),
                encoding="utf-8",
            ),
        ),
        (
            "G2",
            "源码出现 /standalone 必须判红",
            lambda root: (root / "bone-frontend/apps/bone-claim-app/src/demo.tsx").write_text(
                "const href = '/standalone#/case';", encoding="utf-8"
            ),
        ),
        (
            "G3",
            "代码新增模块未登记文档必须判红",
            lambda root: (root / ROUTES_REL).write_text(
                _FIXTURE_ROUTES.replace("  POLICY: 'policy',", "  POLICY: 'policy',\n  TASK: 'task',"),
                encoding="utf-8",
            ),
        ),
        (
            "G4",
            "前端 service 出现 /api/sclaim 必须判红",
            lambda root: (root / "bone-frontend/apps/bone-claim-app/src/services/demo.ts").write_text(
                "createApiClient('/api/sclaim/claim')", encoding="utf-8"
            ),
        ),
    ]

    failed = 0
    # 先证明夹具本身是绿的（否则负向判红没有意义）
    with tempfile.TemporaryDirectory(prefix="sclaim-green-") as td:
        root = Path(td)
        _build_fixture(root)
        baseline = check(root)
        if baseline:
            print("[SELF-TEST] ❌ 夹具基线非绿，负向探针不可信:")
            for item in baseline:
                print(f"  - {item}")
            return 1
        print("[SELF-TEST] ✅ 夹具基线为绿")

    for code, desc, mutate in cases:
        with tempfile.TemporaryDirectory(prefix="sclaim-red-") as td:
            root = Path(td)
            _build_fixture(root)
            mutate(root)
            errors = check(root)
            hit = [e for e in errors if e.startswith(code + ":")]
            if hit:
                print(f"[SELF-TEST] ✅ {code} {desc} → {hit[0]}")
            else:
                failed += 1
                print(f"[SELF-TEST] ❌ {code} {desc} → 未判红（门禁恒绿，不可信）")

    return 1 if failed else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--check", action="store_true", help="主判据（默认）")
    group.add_argument("--self-test", action="store_true", help="负向探针自测")
    group.add_argument("--report-only", action="store_true", help="只打印不阻断")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    errors = check(ROOT)
    if errors:
        print(f"[SCLAIM-NAMING] ❌ {len(errors)} 项违规：")
        for item in errors:
            print(f"  - {item}")
        return 0 if args.report_only else 1

    print("[SCLAIM-NAMING] ✅ SynapClaim 命名规范合规（G1–G4）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
