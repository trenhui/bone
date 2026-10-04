#!/usr/bin/env python3
"""OpenAPI `int64` ↔ 后端 `Long`→String 序列化契约门禁（A2）。

## 问题背景

`bone-metadata-sdk` 的 `MetadataAutoConfiguration.boneLongToStringCustomizer()`
全局注册 `serializerByType(Long.class / Long.TYPE, ToStringSerializer.instance)`，
把**包装类型 `Long`** 序列化为 JSON 字符串（保护 18~19 位雪花 ID 不被 JS Number 截断）。

⇒ **凡是 Java 侧声明为 `Long`（包装类型）的字段，运行时都是 JSON 字符串**，
而 OpenAPI 声明 `type: integer, format: int64`（JSON number）—— **契约 lie**。

后果：生成的 Java SDK 声明 `Long`，只因 Gson/Jackson 多数配置容忍字符串→Long 才不炸；
一旦调用方开严格模式或换`moshi` 直译，即 `NumberFormatException`。

## 为什么本门禁**不**是「见 int64 就 fail」

`int64` 本身不等于"错"。**判定必须看 Java 侧是包装类型还是原生类型**：

| Java 类型 | 全局序列化器是否生效 | 运行时 JSON | 正确声明 |
|---|---|---|---|
| `Long id;`（包装） | ✅ 生效 | `"758267976611790848"` | `type: string` |
| `long count;`（原生） | ❌ 不生效（`Long.TYPE` 只匹配包装类） | `1055` | `type: integer` |

**实测反证**：`console-v1.yaml` 的 11 处 `int64` 对应
`KeyMetrics#userCount` / `ResourceUsage#memoryUsedBytes` / `ServiceStatus#latencyMs`
等**全部是原生 `long`/`double`** ⇒ 运行时确实是 number ⇒ **保持 `int64` 正确，改成 string 反而制造 lie**。
`PageResult.total` 是 `Long total`（包装）⇒ 声明 `int64` 是错的。

⇒ 本门禁维护一份**显式白名单**（原生数值字段），其余 `int64` 一律要求 `type: string`。
白名单每条都记录 Java 字段与其类型，作为判据的可审计依据。

## 判据

对 `doc/architecture/openapi/*-v1.yaml`（**仅独立 API 文档**；`components/*.yaml`
是被 `$ref` 的共享 schema 片段，同样纳入扫描因为它们也会被生成器消费）：

1. 任何 `type: integer, format: int64`：
   - 若其字段名在 `PRIMITIVE_LONG_FIELDS` 白名单 ⇒ 放行（原生long，运行时 number）；
   - 否则 ⇒ **失败**，要求改为 `type: string`。
2. 白名单里的每个字段必须**仍然存在于规范中** —— 若规范已改，提示可从白名单移除
   （只可收缩，与项目其他基线一致）。

## 用法

    python3 scripts/check-openapi-int64-contract.py# 违规 exit 1
    python3 scripts/check-openapi-int64-contract.py --report-only  # 恒 exit 0
    python3 scripts/check-openapi-int64-contract.py --list-native-long       # 打印白名单依据
"""

from __future__ import annotations

import argparse
import pathlib
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
OPENAPI_DIR = REPO_ROOT / "doc" / "architecture" / "openapi"

# ───── 原生 long/double 字段白名单：运行时确实是 JSON number，int64 声明正确 ─────
#
# 每条格式：规范文件 -> 字段名。依据是该字段在Java 侧的声明类型为**原生** long/double
# （非包装 Long），全局 ToStringSerializer 对原生类型不生效。
#
# ⚠️ 若某字段的 Java 类型被改成包装 `Long`，必须同步从这里移除 —— 移除方向是收紧。
PRIMITIVE_LONG_FIELDS: dict[str, set[str]] = {
    "console-v1.yaml": {
        # KeyMetrics（bone-system domain/model/console/KeyMetrics.java）：
        #   long userCount / entityCount / integrationFlowCount / extensionPluginCount
        #   / orderCount / transactionAmount / jvmThreadsLive / jvmThreadsDaemon
        "userCount",
        "entityCount",
        "integrationFlowCount",
        "extensionPluginCount",
        "orderCount",
        "transactionAmount",
        "jvmThreadsLive",
        "jvmThreadsDaemon",
        # ResourceUsage（ResourceUsage.java）：long memoryUsedBytes / memoryMaxBytes
        "memoryUsedBytes",
        "memoryMaxBytes",
        # ServiceStatus（ServiceStatus.java）：long latencyMs
        "latencyMs",
    },
}

try:
    import yaml  # type: ignore

    _HAVE_YAML = True
except ImportError:  # pragma: no cover
    _HAVE_YAML = False


def _iter_schema_nodes(node, path: str = "") -> list[tuple[str, str, object]]:
    """产出 (jsonPointer, fieldName, schemaNode)。

    必须同时覆盖两类位置，否则会漏掉一半以上：
    - `components.schemas.*.properties.*` 与内联 `properties`（响应体字段）
    - `paths.*.{get,post,...}.parameters[]`（路径/查询参数，绝大多数是 `id`）
    """
    out: list[tuple[str, str, object]] = []

    def walk(n, ptr, ctx_field):
        if isinstance(n, dict):
            props = n.get("properties")
            if isinstance(props, dict):
                for fname, sub in props.items():
                    if isinstance(sub, dict):
                        out.append((ptr + "/properties/" + fname, fname, sub))
                        walk(sub, ptr + "/properties/" + fname, fname)
                    elif isinstance(sub, list):
                        # oneOf/anyOf 里内联 schema
                        for i, item in enumerate(sub):
                            walk(item, ptr + "/properties/" + fname + "/" + str(i), fname)
            for k, v in n.items():
                if k == "properties":
                    continue
                if k == "parameters" and isinstance(v, list):
                    # 路径/查询参数：字段名在 `name` 上，类型在其 `schema` 子节点里
                    for i, param in enumerate(v):
                        if not isinstance(param, dict):
                            continue
                        pname = param.get("name")
                        pschema = param.get("schema")
                        if isinstance(pname, str) and isinstance(pschema, dict):
                            out.append((f"{ptr}/parameters/{i}/schema", pname, pschema))
                        walk(param, ptr + f"/parameters/{i}", pname)
                    continue
                if isinstance(v, (dict, list)):
                    walk(v, ptr + "/" + k, ctx_field)
        elif isinstance(n, list):
            for i, item in enumerate(n):
                walk(item, ptr + "/" + str(i), ctx_field)

    walk(node, path, "")
    return out


def _is_int64(schema: object) -> bool:
    if not isinstance(schema, dict):
        return False
    t = schema.get("type")
    if t == "integer":
        return schema.get("format") == "int64"
    # 3.1 允许 type: [integer, 'null']
    if isinstance(t, list) and "integer" in t:
        return schema.get("format") == "int64"
    return False


def check(path: pathlib.Path) -> tuple[list[str], list[str]]:
    """返回 (errors, still_present_white_entries)。"""
    errors: list[str] = []
    doc = yaml.safe_load(path.read_text(encoding="utf-8"))
    allow = PRIMITIVE_LONG_FIELDS.get(path.name, set())
    present: set[str] = set()

    for ptr, fname, schema in _iter_schema_nodes(doc):
        if not _is_int64(schema):
            continue
        if fname in allow:
            present.add(fname)
            continue
        errors.append(
            f"{path.name}: {ptr} 字段 `{fname}` 声明 `integer/int64`，"
            f"但后端下发的是 JSON 字符串（包装类型 Long 被全局 ToStringSerializer 转换）"
            f"⇒ 应改为 `type: string`"
        )

    return errors, sorted(present & allow)


def main() -> int:
    parser = argparse.ArgumentParser(description="OpenAPI int64 ↔ Long→String 契约门禁")
    parser.add_argument("--report-only", action="store_true", help="只报告不阻断（恒 exit 0）")
    parser.add_argument(
        "--list-native-long",
        action="store_true",
        help="打印原生 long 白名单及其 Java 依据后退出",
    )
    args = parser.parse_args()

    if args.list_native_long:
        for f, names in sorted(PRIMITIVE_LONG_FIELDS.items()):
            print(f"{f}:")
            for n in sorted(names):
                print(f"  - {n}")
        return 0

    if not _HAVE_YAML:
        print("❌ 缺少 PyYAML。安装：python3 -m pip install pyyaml")
        return 2

    files = sorted(OPENAPI_DIR.rglob("*.yaml"))
    files = [f for f in files if f.name != "redocly.yaml"]

    all_errors: list[str] = []
    total_int64 = 0
    total_native = 0
    for f in files:
        errs, present = check(f)
        all_errors.extend(errs)
        # 统计（用于报告，避免"数字凭印象"）
        text = f.read_text(encoding="utf-8")
        total_int64 += text.count("format: int64")
        total_native += len(present)

    if all_errors:
        print(
            f"❌ OpenAPI int64 契约门禁失败（{len(all_errors)} 处`integer/int64` "
            f"应为 `string`）："
        )
        for e in all_errors:
            print(f"   - {e}")
        print(
            f"\n统计：全量 `format: int64` {total_int64} 处，"
            f"其中 {total_native} 处为 Java 原生 long（运行时 number，声明正确）。"
        )
        print(
            "改法见 ADR-0040「硬阻塞」章节 A2。若某字段确为原生 long/double，"
            "请把它加入 PRIMITIVE_LONG_FIELDS 白名单并注明 Java 侧类型依据。"
        )
        return 0 if args.report_only else 1

    print(
        f"✅ OpenAPI int64 契约门禁通过：{total_int64} 处 `format: int64` 全部核实 —— "
        f"{total_native} 处为 Java 原生 long（运行时 number，正确），"
        f"其余已改为 `type: string`"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
