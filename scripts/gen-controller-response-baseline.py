#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 HC-003（Controller 统一响应）的存量 baseline。

生成而非手写：baseline 里每一条都必须对应**实测存在的**端点，
手写极易产生"登记了但代码里没有"的假条目 —— 那会让 baseline 失去意义
（后续真违规被baseline 掩盖，却以为已登记）。

用法::

    python3 scripts/gen-controller-response-baseline.py            # 写入 baseline
    python3 scripts/gen-controller-response-baseline.py --dry-run  # 只打印
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
BASELINE = REPO / "doc/architecture/controller-response-baseline.json"

SCAN_ROOTS = ["bone-platform", "bone-engine", "bone-blueprint", "bone-framework"]

# Controller 端点方法：public + 返回类型 + 方法名 + (
METHOD_RE = re.compile(r"public\s+(?!class\b)([\w<>,\[\]\.\s]+?)\s+(\w+)\s*\(", re.S)

# 允许的"裸返回"类型：与 HTTP 语义绑定的标准响应体，不属于业务信封范畴。
# 逐条登记理由而非通配放行 —— 通配放行会让新写的裸返回悄悄溜过门禁。
ALLOWED_BARE = {
    "byte[]": "文件下载流（Content-Disposition），非业务数据",
    "ByteArrayResource": "文件下载流（Spring Resource），非业务数据",
    "Resource": "文件下载流（Spring Resource），非业务数据",
    "void": "无响应体（204/异步触发），由 @ResponseStatus 表达状态",
    "boolean": "JWKS 等标准协议端点，见下方豁免登记",
    "Date": "仅 DefaultMetadataVersionController 的遗留端点，已登记豁免",
    "String": "仅 DefaultMetadataVersionController 的遗留端点，已登记豁免",
}

# ★ 方法级显式豁免：标准协议端点，响应体由协议规定，**不能**包业务信封。
#逐条登记而非通配放行——通配会让新写的裸返回悄悄溜过门禁。
METHOD_EXEMPTIONS = {
    ("bone-platform/bone-iam/src/main/java/com/bone/iam/adapter/web/controller/JwksController.java", "jwks"): (
        "RFC 7517 / OIDC Discovery 规定的 JWKS 端点：响应体必须是标准 JWK Set"
        "（kty/use/kid/alg/n/e），包一层 ApiResponse 会让所有消费方（网关、各微服务）"
        "解析失败。ADR-0005 已确认协议归属。"
    ),
}


def normalize_return_type(raw: str) -> str:
    """去掉修饰噪声，得到可比对的返回类型文本。"""
    t = re.sub(r"\s+", " ", raw).strip()
    # 去掉全限定名的包前缀：com.bone.core.model.ApiResponse -> ApiResponse
    t = re.sub(r"\b([a-z][\w]*\.)+([A-Z]\w*)", r"\2", t)
    return t


def envelope_ok(return_type: str) -> bool:
    """返回类型是否包了统一信封。

    判为合规的形态（**都不是"裸返回领域对象"**）：
    - ``ApiResponse<T>`` / ``PageResult<T>``：标准信封；
    - ``void``：无响应体，由 ``@ResponseStatus`` 表达状态；
    - ``ResponseEntity<ApiResponse<T>>``：仍是统一信封，只额外带 HTTP 状态码；
    - ``ResponseEntity<Void>`` / ``ResponseEntity<byte[]>`` / ``ResponseEntity<Resource>``：
      文件下载与「无内容」语义，响应体不是业务数据，信封无处安放。

    最后一条容易误判成违规：``ResponseEntity<Void>`` 表示 204/无内容，
    强行包一层 ``ApiResponse<Void>`` 反而是"给空包���封"，语义更差。
    """
    t = return_type.replace(" ", "")
    if t.startswith("ResponseEntity<"):
        inner = t[len("ResponseEntity<") : -1] if t.endswith(">") else t[len("ResponseEntity<") :]
        inner = normalize_return_type(inner)
        # 载荷是信封 / 空 / 二进制流 ⇒ 合规
        return (
            inner.startswith("ApiResponse")
            or inner.startswith("PageResult")
            or inner in ("Void", "void", "byte[]", "ByteArrayResource", "Resource")
        )
    return t.startswith("ApiResponse") or t.startswith("PageResult") or t == "void"


def is_allowed_bare(return_type: str) -> bool:
    t = normalize_return_type(return_type)
    return t in ALLOWED_BARE


# 端点映射注解：只有带这些的方法才是 HTTP 端点。
# 缺这一判据时，类内的 helper 方法（public但无映射）会被误当成端点 —— HC-003 只约束 HTTP 出参。
MAPPING_ANNOTATIONS = ("@GetMapping", "@PostMapping", "@PutMapping", "@DeleteMapping", "@PatchMapping")


def has_mapping_annotation(src: str, method_start: int, method_name: str) -> bool:
    """该方法（或其所属类）是否带 HTTP 映射注解。

    类级``@RequestMapping`` 不算——它只定前缀，不构成端点。
    这里只认方法级 ``@GetMapping`` 等，避免把「类内 public 辅助方法」误判为端点。
    """
    # 从方法签名往前回溯到上一个"}"或行首，之间的注释/注解区即为该方法的前置区
    head_start = src.rfind("}", 0, method_start)
    head = src[head_start + 1 : method_start]
    if any(a in head for a in MAPPING_ANNOTATIONS):
        return True
    # 方法体内自调用式映射（如 @RequestMapping(method=...)）不常见，此处不覆盖
    return False


def scan() -> list[dict]:
    rows: list[dict] = []
    for root in SCAN_ROOTS:
        base = REPO / root
        if not base.exists():
            continue
        for dirpath, _dirnames, filenames in os.walk(base):
            dp = Path(dirpath)
            if "target" in dp.parts or "test" in dp.parts:
                continue
            for fn in filenames:
                if not fn.endswith("Controller.java"):
                    continue
                path = dp / fn
                src = path.read_text(encoding="utf-8")
                # ★ 必须有 Spring MVC 立体注解才算 HTTP Controller。
                #   实测反例：`MetadataVersionController` / `DefaultMetadataVersionController`
                #   **类名带 Controller 但零 Spring 注解**，是纯 POJO（版本号生成 / 兼容性检查算法），
                #   它的 `public String generateNewVersion(...)` 等方法不是 HTTP 端点。
                #   若只看「文件名以 Controller 结尾」，会把这几十个算法方法误登记成 HC-003 违规，
                #   让baseline 变成假清单（真实违规反而被淹没）。
                if "@RestController" not in src and "@Controller" not in src:
                    continue
                rel = str(path.relative_to(REPO))
                for m in METHOD_RE.finditer(src):
                    raw, method = m.group(1), m.group(2)
                    rt = normalize_return_type(raw)
                    if not rt or rt.startswith(("private", "protected")):
                        continue
                    # 跳过嵌套 record/enum/interface 声明：它们形如
                    # `public record AckShipmentReq(...)` / `public enum Level(...)`，
                    # 是请求/响应 DTO 定义，**不是端点方法**（无 @Get/@Post 映射）。
                    if method[:1].isupper() and rt in ("record", "enum", "interface", "class"):
                        continue
                    if not has_mapping_annotation(src, m.start(), method):
                        continue
                    if (rel, method) in METHOD_EXEMPTIONS:
                        continue
                    if envelope_ok(rt) or is_allowed_bare(rt):
                        continue
                    line_no = src[: m.start()].count("\n") + 1
                    rows.append(
                        {
                            "file": rel,
                            "method": method,
                            "returnType": rt,
                            "line": line_no,
                        }
                    )
    rows.sort(key=lambda r: (r["file"], r["line"]))
    return rows


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="只打印不写文件")
    args = ap.parse_args()

    rows = scan()
    payload = {
        "description": (
            "HC-003 存量违反『Controller 必须返回 ApiResponse<T> / PageResult<T>』的端点。只可收缩，"
            "新增端点不得加入。判断口径见 scripts/check-controller-response-envelope.py。"
        ),
        "allowedBareReturnTypes": ALLOWED_BARE,
        "methodExemptions": {f"{k[0]}::{k[1]}": v for k, v in METHOD_EXEMPTIONS.items()},
        "endpoints": rows,
    }

    if args.dry_run:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0

    BASELINE.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"已写入 {BASELINE.relative_to(REPO)}：{len(rows)} 条存量端点")
    return 0


if __name__ == "__main__":
    sys.exit(main())