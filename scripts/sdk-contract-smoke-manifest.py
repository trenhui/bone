#!/usr/bin/env python3
"""从 OpenAPI 规范生成 SDK 契约烟测清单（TSV）。

清单是**规范真源的派生物**，不是生成物的派生物 —— 这样"规范改了、生成物没跟上"
会被驱动器的反射查找抓出来变红。若反过来从生成物推导，等于用生成物验证生成物。

输出格式（制表符分隔，无第三方 JSON 库依赖，驱动器直接 readLine + split）：
    domain \t tag \t apiClass \t apiClientClass \t method \t auth
`auth` 为 `bearer` 或 `none`。

清单**不列参数**：调用参数由驱动器按生成物的实际方法签名逐位合成。
这不是偷懒，而是有意的职责划分 ——
  · 清单负责"每个 tag 至少要有一个操作被真调用"（源自规范）；
  · 参数合成负责"怎么把这次调用发出去"（属于生成器细节，会随生成器版本漂移）。
若让清单复述参数列表，就得把 path/query/header/body 的**顺序**也写死，
而这个顺序是 openapi-generator 的实现细节（实测 body 参数总是排在最后），
生成器一升级就全线误报"参数不匹配"—— 假红比不测更糟。
"path 参数是否声明"这类真缺陷由门禁 check-openapi-path-params.py 静态把关，
不需要靠烟测间接发现。

另有一处必须区分：**`security: []` 的公开端点不能要求 Authorization 头**
（`POST /login`、`GET /sso/callback` 在规范里显式声明公开）。对它们断言
"必须带头"是把正确行为判成失败 —— 实测已踩。

选操作规则：每个 tag 取**参数最少**的 operation，参数少的调用最不易被默认值/签名变动打破。
"""
import os
import sys

import yaml

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC_DIR = os.path.join(ROOT, "doc", "architecture", "openapi")
OUT = os.path.join(ROOT, "target", "sdk", "smoke-manifest.tsv")

METH = ("get", "post", "put", "delete", "patch")


def deref(node, doc):
    if isinstance(node, dict) and "$ref" in node:
        ref = node["$ref"]
        if ref.startswith("#/"):
            cur = doc
            for part in ref[2:].split("/"):
                cur = cur[part]
            return cur
        return None
    return node


def param_count(item, op, doc):
    """path + query + header + 必填 body 的总数（用于挑最简调用）。"""
    n = 0
    for pr in [x for x in (deref(y, doc) for y in (item.get("parameters") or [])) if x]:
        if pr.get("in") in ("path", "query", "header"):
            n += 1
    for pr in (deref(y, doc) for y in (op.get("parameters") or [])):
        if pr and pr.get("in") in ("path", "query", "header"):
            n += 1
    if op.get("requestBody"):
        n += 1
    return n


def main():
    rows = []
    for fn in sorted(os.listdir(SPEC_DIR)):
        if not fn.endswith("-v1.yaml"):
            continue
        domain = fn[: -len("-v1.yaml")].replace("-", "")
        doc = yaml.safe_load(open(os.path.join(SPEC_DIR, fn), encoding="utf-8"))

        by_tag = {}
        for _p, item in doc.get("paths", {}).items():
            for m, op in item.items():
                if m not in METH:
                    continue
                if not op.get("operationId"):
                    print(f"FAIL: {fn} {m.upper()} 缺 operationId", file=sys.stderr)
                    return 1
                tag = (op.get("tags") or ["Default"])[0]
                # operation 级 security: [] 表示公开端点（覆盖顶层 security）
                public = "security" in op and not op.get("security")
                by_tag.setdefault(tag, []).append(
                    (op["operationId"], param_count(item, op, doc), public)
                )

        for tag, ops in sorted(by_tag.items()):
            oid, _n, public = min(ops, key=lambda x: x[1])
            api_class = tag[0].upper() + tag[1:] + "Api"
            rows.append(
                [
                    domain,
                    tag,
                    "com.bone.sdk.%s.api.%s" % (domain, api_class),
                    "com.bone.sdk.%s.ApiClient" % domain,
                    oid[0].lower() + oid[1:] + "WithHttpInfo",
                    "none" if public else "bearer",
                ]
            )

    if not rows:
        print("FAIL: 清单为空", file=sys.stderr)
        return 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as fh:
        fh.write(
            "# domain\ttag\tapiClass\tapiClientClass\tmethod\tauth\n"
            + "\n".join("\t".join(r) for r in rows)
            + "\n"
        )
    pub = sum(1 for r in rows if r[5] == "none")
    print(
        f"清单: {len(rows)} 个 (domain,tag) 调用（公开端点 {pub} 个，不校验 Authorization）"
        f" -> {os.path.relpath(OUT, ROOT)}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())