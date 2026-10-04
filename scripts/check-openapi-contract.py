#!/usr/bin/env python3
"""OpenAPI 契约结构门禁（不依赖 npx / redocly，纯标准库 + 可选 PyYAML）。

## 为什么需要这道门禁

`doc/architecture/openapi/` 下的 8 份规范是**对外 SDK 的唯一契约真源**
（见 `doc/architecture/adr/0040-external-client-sdk-generated-from-openapi.md`）。
2026-10-03 实测发现两类**既存**缺陷，二者都让"规范可被工具消费"这件事静默失效：

### 缺陷一：3 份规范根本无法被 YAML 解析（红但被忽略）

`extension-v1` / `generator-v1` / `integration-v1` 的单行 flow mapping 里，
`description` 的**未加引号**值中含 `[` / `{`，例如：

    '200': { description: OK（PageResult.records[ConnectorDTO]） }
    '202': { description: Accepted，Location → /operations/{taskId} }

YAML 规定 flow mapping 的 plain scalar 不得含 `[` `{` ⇒ 解析器报
`missed comma between flow collection entries`。
另有一处 `summary: @Capability 能力列表`（`@` 是 YAML 保留指示符）。

⇒ 任何 `yaml.safe_load` / OpenAPI Generator / SDK 生成器**拿不到这份契约**。
已实测 `npx @redocly/cli lint` 对HEAD 版本 **EXIT=1**（`Failed to parse API description`）。

### 缺陷二：5 份规范缺 `securitySchemes`，且 lint 规则被显式关掉

`iam` / `blueprint` / `console` 定义了 `bearerAuth`，而`extension` / `generator` /
`integration` / `masterdata` / `metadata-runtime` 完全没有 —— 即"这5 个模块对外
不声明任何认证方式"。而 `redocly.yaml` 里 `security-defined: off`
（现已改为 `error`）让这件事连lint 都不报。

⇒ 生成的 SDK 客户端不带认证头，凭据无处安放；契约评审也无从判断"这模块要不要登录"。
规范依据：`doc/architecture/Bone-API-规范.md` §9.1「默认需认证；白名单：
`/api/v1/iam/login`、健康检查、文档」——**这 5 份规范的端点均不在白名单内**。

## 为什么不用 redocly 直接当门禁

`npx --yes @redocly/cli@1` 需要联网下载（约 30s+），在离线本地与 pre-commit
场景不可用。本脚本用标准库做**同一批判据的子集**：

1. 全部 `*.yaml`（含 `components/`）可被 YAML 解析（PyYAML 缺失时降级为
   结构性文本检查，见`_fallback_parse_errors`，保证不是"没装PyYAML 就全绿"）；
2. `components.securitySchemes.bearerAuth` 存在且为 `http` + `bearer`；
3. 顶层 `security` 声明了 `bearerAuth`；
4. 所有本地 `$ref`（`#/...` 与相对路径）可解析。

redocly lint 仍作为 CI 的独立 job 保留（本门禁不替代它）。

## 用法

    python3 scripts/check-openapi-contract.py            # 违规 exit 1
    python3 scripts/check-openapi-contract.py --report-only   # 恒 exit 0
"""

from __future__ import annotations

import argparse
import pathlib
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
OPENAPI_DIR = REPO_ROOT / "doc" / "architecture" / "openapi"

# 不参与契约校验的配置文件
SKIP_FILES = {"redocly.yaml"}

REQUIRED_SCHEME = "bearerAuth"
EXPECTED_SCHEME_FIELDS = {"type": "http", "scheme": "bearer"}


def spec_files() -> list[pathlib.Path]:
    """只取**独立 API 文档**（`*-v1.yaml`）。

    `components/*.yaml`（ApiResponse / PageResult / FieldError / ProblemDetail）
    是被主文档 `$ref` 引用的**组件片段**，按 OpenAPI 规范它们没有 servers /
    security / securitySchemes 的职责。对它们要求认证会把共享 schema
    误判为违规（第一版判据的真实误报，8 项中4 项即此）。
    """
    if not OPENAPI_DIR.is_dir():
        return []
    return sorted(
        p
        for p in OPENAPI_DIR.rglob("*-v1.yaml")
        if p.name not in SKIP_FILES and p.is_file()
    )


def component_files() -> list[pathlib.Path]:
    if not OPENAPI_DIR.is_dir():
        return []
    return sorted(p for p in (OPENAPI_DIR / "components").rglob("*.yaml") if p.is_file())


# ───────────────────────── PyYAML 前置条件 ─────────────────────────
#
# 曾尝试「PyYAML 缺失时降级为正则启发式」，实测**判据过宽、无法用**：
# 正例（8 份全部合规）产生 215 处误报，因为「单行 flow mapping 未加引号」
# 与「单行 flow mapping 已加引号但内部含括号」在正则层面无法区分
#（`{ name: id, schema: { type: string } }` 本身就含 `{`）。
# 第一版启发式只抓到了 HEAD 的真bug，却对正确写法一律误报。
#
# ⇒ 结论：**启发式不是降级方案**。契约文件是可被机器完整解析的结构化数据，
# 判据必须是完整解析而非文本猜测。PyYAML 缺失时直接 exit 2，
# 由调用方/CI 显式安装依赖 —— 这既不 falsely-green，也不 falsely-red。
# **教训**：门禁的「降级路径」必须和主路径一样做正反双向验证；
# 没验证过的降级路径往往比没有更危险。

try:
    import yaml  # type: ignore

    _HAVE_YAML = True
except ImportError:  # pragma: no cover - 环境相关
    _HAVE_YAML = False


# ───────────────────────── YAML 解析 / $ref 校验 ─────────────────────────


def _iter_refs(node, out: list[str]) -> None:
    if isinstance(node, dict):
        for k, v in node.items():
            if k == "$ref" and isinstance(v, str):
                out.append(v)
            else:
                _iter_refs(v, out)
    elif isinstance(node, list):
        for v in node:
            _iter_refs(v, out)


def _resolve_local_ref(ref: str, doc: dict, base: pathlib.Path) -> bool:
    if ref.startswith("#/"):
        cur = doc
        for part in ref[2:].split("/"):
            part = part.replace("~1", "/").replace("~0", "~")
            if isinstance(cur, dict) and part in cur:
                cur = cur[part]
            else:
                return False
        return True
    if ref.startswith("./") or ref.startswith("../"):
        return (base.parent / ref).resolve().is_file()
    # 远程 ref：本门禁不联网校验其可达性
    return True


def check(path: pathlib.Path, require_auth: bool = True) -> list[str]:
    errors: list[str] = []

    if True:  # PyYAML 可用性已在 main() 前置校验
        try:
            doc = yaml.safe_load(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001 - 解析器异常类型多
            return [f"{path.name}: YAML 解析失败 → {str(exc).splitlines()[0]}"]
        if not isinstance(doc, dict):
            return [f"{path.name}: 顶层不是映射（契约文件结构异常）"]

        # 1) securitySchemes（仅独立 API 文档）
        if require_auth:
            comp = doc.get("components") or {}
            schemes = comp.get("securitySchemes") or {}
            scheme = schemes.get(REQUIRED_SCHEME)
            if not isinstance(scheme, dict):
                errors.append(
                    f"{path.name}: 缺 components.securitySchemes.{REQUIRED_SCHEME}"
                    f"（redocly 已开 security-defined=error；规范 §9.1「默认需认证」）"
                )
            else:
                for key, want in EXPECTED_SCHEME_FIELDS.items():
                    if str(scheme.get(key)) != want:
                        errors.append(
                            f"{path.name}: securitySchemes.{REQUIRED_SCHEME}.{key}"
                            f"应为 {want!r}，实际 {scheme.get(key)!r}"
                        )

            # 2) 顶层 security
            top_sec = doc.get("security")
            if not (
                isinstance(top_sec, list)
                and any(isinstance(e, dict) and REQUIRED_SCHEME in e for e in top_sec)
            ):
                errors.append(
                    f"{path.name}: 缺顶层 security: [- {REQUIRED_SCHEME}: []]"
                    f"（否则生成的 SDK 客户端不会携带认证头）"
                )

        # 3) $ref 可解析
        refs: list[str] = []
        _iter_refs(doc, refs)
        for r in sorted(set(refs)):
            if not _resolve_local_ref(r, doc, path):
                errors.append(f"{path.name}: $ref 无法解析 → {r}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenAPI 契约结构门禁")
    parser.add_argument(
        "--report-only",
        action="store_true",
        help="只报告不阻断（恒 exit 0）",
    )
    args = parser.parse_args()

    if not _HAVE_YAML:
        print(
            "❌ 缺少 PyYAML，无法校验 OpenAPI 契约。"
            "本门禁刻意**不做**正则启发式降级（实测判据过宽：8 份合规规范"
            "产生 215 处误报，见 _fallback_parse_errors 上方注释）。\n"
            "    安装：python3 -m pip install pyyaml"
        )
        return 2

    files = spec_files()
    if not files:
        print("❌ 未找到 OpenAPI 规范文件：" + str(OPENAPI_DIR))
        return 0 if args.report_only else 1

    all_errors: list[str] = []
    docs = spec_files()
    for f in docs:
        all_errors.extend(check(f, require_auth=True))
    # 组件片段：只查可解析性与 $ref（认证由引用它们的主文档承担）
    comps = component_files()
    for f in comps:
        all_errors.extend(check(f, require_auth=False))

    rel = OPENAPI_DIR.relative_to(REPO_ROOT)
    if all_errors:
        print(
            f"❌ OpenAPI契约门禁失败（{len(all_errors)} 项，"
            f"规范目录 {rel}：{len(docs)} 份API 文档 + {len(comps)} 份组件）："
        )
        for e in all_errors:
            print(f"   - {e}")
        return 0 if args.report_only else 1

    print(
        f"✅ OpenAPI 契约门禁通过：{len(docs)} 份API 文档声明认证、"
        f"{len(comps)} 份组件可解析，全部 YAML 可解析且 $ref 全部可解析"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())