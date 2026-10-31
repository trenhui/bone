# -*- coding: utf-8 -*-
"""文档族索引的唯一加载器。

为什么单独成模块：此前 `DOC_MAP`（_check_xref）、`MAIN`（_check_numbers）、
`RULEBOOK/CONTRACT/LANDING`（_check_contract_drift）、`DOC`（_check_ddl）四处各自
硬编码同一批文件路径，加上符号基线里 13 条路径——同一事实 5 份拷贝。移动一份文档
必然漏改一处，而漏改的后果不是报错，是**机检仍在跑、但校验的不是真源**（静默失焦）。

现在路径、短名、层目录一律来自 `docs.manifest.json`；脚本里只允许出现 doc id，
不允许出现文件名。
"""

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST_PATH = os.path.join(ROOT, "docs.manifest.json")


def load():
    with open(MANIFEST_PATH, encoding="utf-8") as f:
        return json.load(f)


_M = load()
LAYER_DIRS = _M["layerDirs"]
DOCS = _M["docs"]
BY_ID = {d["id"]: d for d in DOCS}
BY_PATH = {d["path"]: d for d in DOCS}


def rel(doc_id):
    """doc id → 相对 ROOT 的路径（与旧 DOC_MAP 取值同构，可直接 join）。"""
    try:
        return BY_ID[doc_id]["path"]
    except KeyError:
        raise SystemExit(f"docs.manifest.json 缺少 doc id：{doc_id}")


def path(doc_id):
    return os.path.join(ROOT, rel(doc_id))


def read(doc_id):
    with open(path(doc_id), encoding="utf-8") as f:
        return f.read()


def short_names():
    """短名 → 相对路径。短名用于解析《短名》§X 交叉引用。"""
    out = {}
    for d in DOCS:
        for sn in d.get("shortNames", []):
            out[sn] = d["path"]
    return out


def check_keys():
    """_check_numbers 的历史键（landing/prd/ai/fde/mvp）→ 相对路径。"""
    out = {}
    for d in DOCS:
        for k in d.get("checkKeys", []):
            out[k] = d["path"]
    return out


def in_layers(layers):
    """按 manifest 声明的 layer 取文档（辅以目录前缀兜底）。

    只按目录前缀匹配会漏掉根目录的 README —— 它的 layer 是 L0 但路径不含层目录。
    层归属以 manifest 的 `layer` 字段为准，目录前缀只作一致性兜底。
    """
    prefixes = tuple(LAYER_DIRS[l].rstrip("/") + "/" for l in layers)
    return [d["path"] for d in DOCS if d["layer"] in layers or d["path"].startswith(prefixes)]


def layer_docs(layer):
    d = LAYER_DIRS[layer].rstrip("/") + "/"
    return [x["path"] for x in DOCS if x["layer"] == layer or x["path"].startswith(d)]


# 正文里可能出现的“逻辑目录前缀”→ 实际层。正文写路径时用的是真实相对路径，
# 但历史文本与生成脚本仍可能用 `delivery/05-…` 这类逻辑前缀，故统一在此转换。
LEGACY_PREFIX = {
    "design/": "L4",
    "verification/": "L6",
    "delivery/": "L7",
    "reviews/": "L9",
    "archive/": "L10",
    "生产就绪完善（业界最佳实践）/": "L8",
}


def denormalize(text):
    """把历史逻辑前缀映射到真实目录，供“路径 token 存在性”校验使用。"""
    for old, layer in LEGACY_PREFIX.items():
        new = LAYER_DIRS[layer].rstrip("/") + "/"
        text = text.replace(old, new)
    return text
