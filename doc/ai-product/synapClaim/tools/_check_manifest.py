#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""文档族索引一致性校验（防「登记与实际脱钩」）。

为什么需要这一道：P10 之前，文档族只有"语义机检"（§ 引用 / 契约漂移 / DDL / 数值）。
四道机检都假设"文件就在硬编码的那个路径上"——一旦文档被移动或新建而未登记，
它们不会报错，只是**校验了不存在的文件**（`sections()` 对缺失文件返回空集合，
于是引用统统解析失败或统统跳过）。这类"机检照跑、覆盖归零"的失效最难发现。

本脚本校验 7 条不变量：

  M1 索引可解析：`docs.manifest.json` 合法，id / path 唯一，必填字段齐全。
  M2 登记即存在：每条 path 在磁盘上真实存在。
  M3 存在即登记：磁盘上的 .md 全部在索引中（新增文档必须先登记）。
  M4 目录号 = 层号：`L{n}` 的文档必须位于对应编号目录（根目录只允许 L0 的 README）。
  M5 短名唯一：`shortNames` 全局无重复（重复会让《名称》§X 解析到错的文档）。
  M6 路径不悬空：正文中反引号包裹的路径 / markdown 链接必须指向真实文件。
  M7 AI 索引不漂移：`AI-INDEX.md` 必须与 `docs.manifest.json` 派生的内容逐字节一致
     （AI-INDEX 是生成物；改了 manifest 不重生成 ⇒ AI 仍按旧路由检索 ⇒ 静默失效）。
  M8 AI 路由有效：`aiRouting` 的每个 id 都必须在 `docs` 中真实登记
     （路由指向幽灵文档时，重生成索引也救不回来，须独立校验）。
  M9 front-matter 一致：每份文档头部必须有 `doc_id` / `role` / `currency`，
     且与 manifest 逐字段相同（front-matter 是绕过 manifest 直读单文件时，
     AI 判断"证据还是现状"的唯一依据，缺失即失效）。

用法：python3 _check_manifest.py
退出码：任一不变量被破坏 -> 1；否则 0。
"""

import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join(HERE, "docs.manifest.json")

REQUIRED_FIELDS = ("id", "path", "layer", "diataxis", "summary")
problems = []


def add(rule, msg):
    problems.append((rule, msg))


def load():
    if not os.path.exists(MANIFEST):
        add("M1", "docs.manifest.json 不存在")
        return None
    try:
        with open(MANIFEST, encoding="utf-8") as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        add("M1", f"docs.manifest.json 解析失败：{e}")
        return None


def check_shape(m):
    layer_dirs = m.get("layerDirs") or {}
    docs = m.get("docs") or []
    if not layer_dirs:
        add("M1", "缺少 layerDirs")
    if not docs:
        add("M1", "docs 为空")
        return layer_dirs, docs

    seen_id, seen_path = {}, {}
    for d in docs:
        for f in REQUIRED_FIELDS:
            if not d.get(f):
                add("M1", f"条目 {d.get('id', '?')} 缺少字段 {f}")
        i, p = d.get("id"), d.get("path")
        if i in seen_id:
            add("M1", f"id 重复：{i}")
        if p in seen_path:
            add("M1", f"path 重复：{p}")
        seen_id[i] = seen_path[p] = True
        if d.get("layer") not in layer_dirs:
            add("M1", f"{i} 的 layer={d.get('layer')} 未在 layerDirs 声明")
    _check_ai_routing(m, seen_id)
    return layer_dirs, docs


def _check_ai_routing(m, known_ids):
    """M8：AI 路由表引用的 id 必须真实存在。

    为什么独立成条：路由表是 AI 的确定性入口。若它指向一个不存在的 id，
    AI 会照着去读一份并不存在的文档——这不是"索引漂移"能覆盖的
    （重生成索引后内容完全一致，但路由本身就是坏的），必须独立校验。
    """
    routing = m.get("aiRouting")
    if routing is None:
        return
    if not isinstance(routing, list) or not routing:
        add("M8", "aiRouting 必须是非空数组")
        return
    for r in routing:
        if not isinstance(r, dict) or not r.get("intent") or not r.get("docs"):
            add("M8", f"路由条目缺 intent 或 docs：{r!r}")
            continue
        for doc_id in r["docs"]:
            if doc_id not in known_ids:
                add("M8", f"路由「{r['intent']}」指向不存在的 id：{doc_id}")


def check_paths_exist(docs):
    for d in docs:
        if not os.path.exists(os.path.join(HERE, d["path"])):
            add("M2", f"登记了但文件不存在：{d['path']}（id={d['id']}）")


def check_all_registered(docs, layer_dirs):
    registered = {d["path"] for d in docs}
    on_disk = set()
    for layer_dir in layer_dirs.values():
        base = os.path.join(HERE, layer_dir)
        for root, _, files in os.walk(base):
            for f in files:
                if f.endswith(".md"):
                    on_disk.add(os.path.relpath(os.path.join(root, f), HERE))
    for f in os.listdir(HERE):
        if f.endswith(".md"):
            on_disk.add(f)
    for p in sorted(on_disk - registered):
        add("M3", f"磁盘存在但未登记（新增文档必须登记）：{p}")
    # 未知顶层目录里的 md：说明有人绕开了层结构
    known = set(layer_dirs.values())
    for entry in sorted(os.listdir(HERE)):
        full = os.path.join(HERE, entry)
        if not os.path.isdir(full) or entry in known or entry.startswith("."):
            continue
        if entry == "synap-claim-oracle":
            continue  # 已由 layerDirs 的 REF 覆盖
        for root, _, files in os.walk(full):
            for f in files:
                if f.endswith(".md"):
                    add("M3", f"位于未声明目录 {entry}/ 下：{os.path.relpath(os.path.join(root, f), HERE)}")


def check_dir_matches_layer(docs, layer_dirs):
    for d in docs:
        p, layer = d["path"], d["layer"]
        expect = layer_dirs[layer].rstrip("/")
        if "/" not in p:
            # 根目录只允许 L0（README 是唯一入口）
            if layer != "L0":
                add("M4", f"{p} 在根目录，但不属于 L0")
            continue
        top = p.split("/")[0]
        if top != expect:
            add("M4", f"{p} 位于 {top}/，但声明 layer={layer}（应在 {expect}/）")


def check_short_names(docs):
    seen = {}
    for d in docs:
        for sn in d.get("shortNames", []):
            if sn in seen:
                add("M5", f"短名重复：{sn}（{seen[sn]} 与 {d['id']}）")
            seen[sn] = d["id"]


_PATH_TOKEN = re.compile(r'`([^`\n]{2,200}?\.(?:md|html|js|json))`')
_MD_LINK = re.compile(r'\]\(([^)\n]{2,200}?\.(?:md|html|js|json))(?:#[^)]*)?\)')


def check_path_tokens(docs, layer_dirs):
    """正文里出现的路径必须真实存在。

    只校验「反引号包裹的路径」与「markdown 链接」两种显式形态：它们在语义上就是
    "去这里找"，写错即失效；正文叙述里出现的裸目录名（如"本包见 08-go-live"）不校验，
    否则会把 `07-delivery/04-05` 这类简写误判为坏路径。
    """
    known_prefixes = tuple(layer_dirs.values())
    for d in docs:
        full = os.path.join(HERE, d["path"])
        if not os.path.exists(full):
            continue
        with open(full, encoding="utf-8") as f:
            text = f.read()
        doc_dir = os.path.dirname(d["path"])
        for tok in _PATH_TOKEN.findall(text) + _MD_LINK.findall(text):
            tok = tok.strip()
            if tok.startswith(("http://", "https://", "mailto:")):
                continue
            if "/" not in tok:
                continue
            if not tok.startswith(known_prefixes) and not tok.startswith(("./", "../")):
                continue
            # 根相对（以层目录开头）的路径不拼接文档所在目录——正文里 `04-design/x.md`
            # 一律表示"从文档族根开始"，只有 `./` `../` 才是相对当前文件的写法。
            target = os.path.normpath(tok) if tok.startswith(known_prefixes) \
                else os.path.normpath(os.path.join(doc_dir, tok))
            if not os.path.exists(os.path.join(HERE, target)):
                add("M6", f"{d['path']} 引用了不存在的路径：{tok}")


def check_ai_index_drift(m):
    """M7：AI-INDEX.md 是由 manifest 派生的生成物，必须与派生结果逐字节一致。

    为什么这道必须有：AI-INDEX.md 是给 Agent 的确定性路由表。若改了 manifest
    （新增/移动/撤销文档）却不重生成，索引会静默指向旧路径——AI 读了索引、
    照着找一份已不存在的文档，而所有其他门禁仍然全绿。这是典型的
    「配了不生效」：机制存在但没有任何东西强制它保持同步。
    """
    ai = m.get("aiIndex") or {}
    idx_rel = ai.get("path")
    if not idx_rel:
        return  # 未启用生成式索引，本规则不适用
    idx_path = os.path.join(HERE, idx_rel)
    if not os.path.exists(idx_path):
        add("M7", f"AI 索引不存在：{idx_rel}（应由 {ai.get('generator', 'tools/_gen_ai_index.py')} 生成）")
        return
    try:
        import _gen_ai_index as G
    except ImportError:
        add("M7", "无法导入 tools/_gen_ai_index.py，无法校验 AI 索引漂移")
        return
    with open(idx_path, encoding="utf-8") as f:
        cur = f.read()
    if cur != G.render(m):
        add("M7", f"{idx_rel} 与 manifest 不一致（索引已漂移）；修复：python3 tools/_gen_ai_index.py")


def check_front_matter(docs):
    """M9：每份文档必须带与 manifest 一致的 front-matter。

    为什么必须有：front-matter 是「绕过 manifest 直读单文件」时唯一能看到权威/时效
    的地方。RAG 检索往往只命中正文片段而不带 manifest——此时文档头部的
    `role: evidence / currency: frozen` 就是 AI 判断"这是证据还是现状"的唯一依据。
    缺了它，冻结证据会被当成现行设计引用。
    """
    for d in docs:
        full = os.path.join(HERE, d["path"])
        if not os.path.exists(full):
            continue
        with open(full, encoding="utf-8") as f:
            text = f.read()
        if not text.startswith("---"):
            add("M9", f"{d['path']} 缺少 front-matter（应含 doc_id / role / currency）")
            continue
        # 取 front-matter 段（首个 --- 之后的下一个 ---）
        fm = ""
        for line in text.split("\n")[1:30]:
            if line.strip() == "---":
                break
            fm += line + "\n"
        else:
            add("M9", f"{d['path']} 的 front-matter 未闭合")
            continue
        got = {}
        for line in fm.split("\n"):
            if ":" in line:
                k, _, v = line.partition(":")
                got[k.strip()] = v.strip().strip('"')
        for field, want in (("doc_id", d["id"]), ("role", d.get("role")),
                            ("currency", d.get("currency"))):
            if field not in got:
                add("M9", f"{d['path']} 的 front-matter 缺少 {field}")
            elif got[field] != str(want):
                add("M9", f"{d['path']} 的 front-matter {field}={got[field]!r} 与 manifest {want!r} 不一致")


def main():
    m = load()
    if m is None:
        report()
        return
    layer_dirs, docs = check_shape(m)
    if docs:
        check_paths_exist(docs)
        check_all_registered(docs, layer_dirs)
        check_dir_matches_layer(docs, layer_dirs)
        check_short_names(docs)
        check_path_tokens(docs, layer_dirs)
        check_front_matter(docs)
        check_ai_index_drift(m)
    report(len(docs), layer_dirs)


def report(n=0, layer_dirs=None):
    print("=== 文档索引一致性（manifest ↔ 磁盘）===")
    if problems:
        seen = set()
        for rule, msg in problems:
            if (rule, msg) in seen:
                continue
            seen.add((rule, msg))
            print(f"  【{rule}】{msg}")
        print(f"共 {len(seen)} 处问题")
        sys.exit(1)
    dirs = ", ".join(f"{k}→{v}" for k, v in (layer_dirs or {}).items())
    print(f"【OK】{n} 条登记 ↔ 磁盘一致；目录号=层号；短名唯一；正文路径无悬空；AI 索引不漂移；AI 路由有效")
    print(f"      层目录：{dirs}")


if __name__ == "__main__":
    main()
