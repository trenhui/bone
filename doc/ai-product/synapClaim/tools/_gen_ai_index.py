#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AI-INDEX.md 生成器（AI 机器优先入口索引）。

设计意图（Docs-as-Code / AI-consumable docs 实践）：
- 人类会先读 README，AI 不会先读 README——AI 靠相似度检索，容易把"证据/草案"当"现状"。
- 因此把「权威性(role)」「时效性(currency)」「被谁撤销(superseded_by)」写进**可被检索的工件本身**，
  并提供一份**确定性路由表**（aiRouting），让 Agent 先读索引再决定读哪份文档。
- 本文件是 AI-INDEX.md 的**唯一生成源**。AI-INDEX.md 是生成物，禁止手工编辑；
  改动请改 docs.manifest.json，然后重跑本脚本。

用法：
  python3 tools/_gen_ai_index.py            # 写入 AI-INDEX.md
  python3 tools/_gen_ai_index.py --check    # 只校验磁盘上的 AI-INDEX.md 是否与 manifest 一致（不一致 exit 1）

退出码：--check 模式下不一致 -> 1；否则 0。
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST_PATH = os.path.join(HERE, "docs.manifest.json")

# 语料模型：role -> (机器可读语义, AI 应当如何处理)
ROLE_MODEL = [
    ("ssot", "当前权威真源", "可引用为事实"),
    ("navigation", "导航/治理元文档", "用于路由，不含业务口径"),
    ("draft", "待合入草案（如 go-live 01–13）", "仅作计划/提案，不当现行契约"),
    ("evidence", "冻结的过程证据（L9/L10）", "仅回答「为什么/历史」，不当现状"),
    ("tooling", "工具/参考实现（oracle）", "非产品代码，不当设计引用"),
]
CURRENCY_MODEL = [
    ("current", "现行——默认可引用"),
    ("draft", "草案——仅在明确询问规划时引用"),
    ("frozen", "冻结——只作历史取证，禁止当作现状"),
]

HEADER = """# synapClaim · AI 入口索引（机器优先）

> **本文件由 `tools/_gen_ai_index.py` 从 `docs.manifest.json` 生成，禁止手工编辑。**
> 改内容请改 manifest 后重跑：`python3 tools/_gen_ai_index.py`；校验用 `--check`。
>
> 权威真源以 [`docs.manifest.json`](docs.manifest.json) 为准（含每份文档的 `role` / `currency` /
> `authority` / `superseded_by` 机器字段）。
>
> **黄金法则**：默认只信任 `currency=current`；`role=draft` 是待合入草案；
> `role=evidence`（`currency=frozen`）是历史取证，**不得当作当前设计引用**。
"""


def load():
    with open(MANIFEST_PATH, encoding="utf-8") as f:
        return json.load(f)


def render(m):
    docs = m["docs"]
    L = []
    A = L.append

    # front-matter 与其余文档同构：doc_id / role / currency 从 manifest 取，
    # 不在此硬编码——manifest note 明令「脚本内禁止硬编码文档路径」。
    ai_meta = m.get("aiIndex") or {}
    idx_rel = ai_meta.get("path", "AI-INDEX.md")
    self_doc = next((d for d in docs if d["path"] == idx_rel), None)
    A("---")
    A(f'doc_id: {self_doc["id"] if self_doc else "ai-index"}')
    A(f'role: {self_doc.get("role", "navigation") if self_doc else "navigation"}')
    A(f'currency: {self_doc.get("currency", "current") if self_doc else "current"}')
    A("---")
    A("")
    A(HEADER)
    A("")
    A("## 一、语料模型（检索前先理解）")
    A("")
    A("| `role` | 含义 | AI 如何处理 |")
    A("|---|---|---|")
    for role, meaning, how in ROLE_MODEL:
        A(f"| `{role}` | {meaning} | {how} |")
    A("")
    A("| `currency` | 含义 |")
    A("|---|---|")
    for cur, meaning in CURRENCY_MODEL:
        A(f"| `{cur}` | {meaning} |")
    A("")

    routing = m.get("aiRouting") or []
    A("## 二、按来意路由（ask → read）")
    A("")
    A("| 你想做什么 | 读这些（id） |")
    A("|---|---|")
    for r in routing:
        ids = " / ".join(f"`{i}`" for i in r["docs"])
        A(f"| {r['intent']} | {ids} |")
    A("")

    active = [d for d in docs if d.get("currency") == "current" and d.get("role") != "tooling"]
    draft = [d for d in docs if d.get("role") == "draft"]
    frozen = [d for d in docs if d.get("role") in ("evidence", "tooling")]

    A(f"## 三、ACTIVE 现行真源（currency=current，可引用为事实，共 {len(active)} 份）")
    A("")
    A("| id | 路径 | role | 一句话 |")
    A("|---|---|---|---|")
    for d in sorted(active, key=lambda x: x["path"]):
        A(f"| `{d['id']}` | `{d['path']}` | {d['role']} | {d.get('summary', '')} |")
    A("")

    A(f"## 四、DRAFT 草案（role=draft，待合入 L3，不当现行契约，共 {len(draft)} 份）")
    A("")
    for d in sorted(draft, key=lambda x: x["path"]):
        A(f"- `{d['id']}` → `{d['path']}`：{d.get('summary', '')}")
    A("")

    A(f"## 五、FROZEN 冻结证据（role=evidence/tooling，仅用于「为什么/历史」，禁止当作现状，共 {len(frozen)} 份）")
    A("")
    for d in sorted(frozen, key=lambda x: x["path"]):
        sup = d.get("superseded_by")
        note = d.get("supersede_note", "")
        extra = f"  ⚠️ 已被 `{sup}` 撤销/修正：{note}" if sup else ""
        A(f"- `{d['id']}` → `{d['path']}`：{d.get('summary', '')}{extra}")
    A("")

    A("## 六、manifest 字段图例")
    A("")
    A("- `role`：文档角色（ssot/navigation/draft/evidence/tooling）")
    A("- `currency`：时效（current/draft/frozen）")
    A("- `authority`：本文件独占真源的主题")
    A("- `superseded_by`：被哪份文档撤销/修正（仅证据类有）")
    A("- `aiRouting`：本索引第二节的路由表真源（在 manifest 中，不在本文件）")
    A("")
    return "\n".join(L) + "\n"


def main():
    m = load()
    content = render(m)
    idx_path = os.path.join(HERE, m.get("aiIndex", {}).get("path", "AI-INDEX.md"))

    if "--check" in sys.argv:
        if not os.path.exists(idx_path):
            print(f"【FAIL】索引不存在：{idx_path}")
            return 1
        cur = open(idx_path, encoding="utf-8").read()
        if cur != content:
            print("【FAIL】AI-INDEX.md 与 docs.manifest.json 不一致（索引已漂移）")
            print("       修复：python3 tools/_gen_ai_index.py")
            return 1
        print("【OK】AI-INDEX.md 与 docs.manifest.json 一致")
        return 0

    with open(idx_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"已生成 {m.get('aiIndex', {}).get('path', 'AI-INDEX.md')}（{len(content.splitlines())} 行）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
