#!/usr/bin/env python3
"""分页入参命名审计：统计对外 Query/Req 入参类的分页字段命名，拦截「第三套命名」。

背景（2026-10-02 E2E 实测）:
    Spring 对多余的 query 参数**静默忽略**并回落默认值 —— 传`pageSize=1` 而后端字段是 `size` 时，
    请求照样 200，只是 `size` 回显后端默认值 100。前端于是出现「只拉 1 条实际拉回 100 条、
    pageNum>1 翻页静默回到第 1 页」，且**没有任何报错**。

    实测同一批端点上并存三族命名:
        page/pageSize|size ...... 8端点   /api/v1/iam/audit/logs、/api/v1/generator/{templates,data-sources}
        pageNum/pageSize ....... 18 端点  /api/v1/system/*、/api/v1/metadata/entities、/api/v1/masterdata/*
        cursor/limit ........... 4 端点   /api/v1/notification/messages、/api/v1/extension/audit-logs
    而 doc/architecture/Bone-API-规范.md §5 规定 offset 分页一律用 page/size，且自述为
    Advisory 层级、**无机器门禁**，故 pageNum 族长期存活。

本脚本做什么:
    1. 统计各分页命名族在**对外入参类**中的分布（🔴 阻断：新引入第三套命名）
    2. 输出存量清单与收敛成本（🟡 警告：pageNum 族存量，供路线决策）
    3. 校验 Bone-API-规范.md 的分页章节与实测是否一致（🟡 警告：文档漂移）

刻意不做的事（重要）:
    - **不把 SDK 内部的 pageNum 算作违规**。`bone-metadata-sdk` 的 `FluentQuery.page(int pageNum,
      int pageSize)`、`Criteria.page()` 是 SDK 公共 API 命名，改它会波及全部持久化调用方，
      属于另一条独立议题，不在本门禁射程内。否则本脚本会误报 37 处。
    - **不自动改代码**。存量收敛是 L3 级（动18 个 query 类 + 回归全部调用方），
      本脚本只提供决策依据。

用法:
    python3 scripts/check-paging-param-names.py              # 阻断模式（新命名 → exit 1）
    python3 scripts/check-paging-param-names.py --report-only # 只出报告，恒 exit 0（存量清零阶段用）
"""

import argparse
import re
import sys
from collections import defaultdict
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SKIP = {"target", "node_modules", "dist", "build", ".git", "generated-code"}

# 对外入参类的命名特征：被 Spring 以 @ModelAttribute / 显式参数绑定的分页载体
QUERY_CLASS_RE = re.compile(r"(Qry|Query|PageQuery|PageReq|Request|DTO)$")
# 对外入参类里可能出现的分页字段
FIELD_RE = re.compile(r"\b(page|size|pageNum|pageSize|cursor|limit|offset)\b")
# 认定「分页入参类」：类名匹配 QUERY_CLASS_RE 且字段里出现分页命名
PAGE_NUM_RE = re.compile(r"\b(pageNum|pageSize)\b")
CURSOR_RE = re.compile(r"\b(cursor|limit)\b")
PLAIN_RE = re.compile(r"\b(page|size)\b")

# SDK 内部命名豁免：这些路径下的 pageNum/pageSize 是 SDK 公共 API，不算对外入参违规
SDK_PATH_MARKERS = ("bone-metadata-sdk", "/sdk/")

# 规范文档
SPEC_DOC = "doc/architecture/Bone-API-规范.md"


def is_sdk_path(rel: str) -> bool:
    return any(m in rel for m in SDK_PATH_MARKERS)


def iter_java_files():
    for p in REPO.rglob("*.java"):
        parts = set(p.parts)
        if parts & SKIP:
            continue
        s = p.parts
        if "src" not in s or ("test" in s):
            continue
        yield p


def split_classes(text: str):
    """按顶层类/record/interface 声明切块。返回 [(类名, 块文本), ...]。

    不用朴素按行匹配：同一文件里可能有多个类（主类 + DTO + 内部类），
    不切块会把 DTO 的 pageNum 记到 Controller 头上。
    """
    parts = re.split(r"\n(?=(?:public\s+|final\s+|abstract\s+)*(?:class|record|interface)\s)", text)
    out = []
    for blk in parts:
        m = re.search(r"\b(?:class|record|interface)\s+(\w+)", blk)
        if m:
            out.append((m.group(1), blk))
    return out


# 提取「真实的字段声明名」，而不是在类体里搜单词。
# 这是本脚本第一版的教训：按单词搜 `pageSize` 就把class 归入已知族，
# 于是 `pageIndex + pageSize` 这种混搭命名会被当成合法的 pagnum 族放行 ——
# 负向探针注入 pageIndex/pageNo 后脚本仍报 PASS，等于假门禁。
# 必须按声明抽字段名，才能识别「族内混搭」与「第三套命名」。
FIELD_DECL_RE = re.compile(
    r"^\s*private\s+(?:final\s+)?[\w<>,.\[\]\s?]+?\s+(\w+)\s*[=;]", re.MULTILINE
)
# 认定为分页字段的候选名（用于把类体里的分页字段挑出来）
PAGING_CANDIDATE_RE = re.compile(r"^(page|size|page[A-Z]\w*|pageNum|pageSize|size[A-Z]\w*|"
                                  r"cursor|limit|offset|perPage|pageSize)$", re.IGNORECASE)

# 允许的分页字段名白名单（三族各自允许的字段名）。
# 新增命名若不在此表中 → 判为「未知命名」并阻断。
ALLOWED_FIELDS = {
    "plain": {"page", "size"},
    "pagnum": {"pageNum", "pageSize"},
    "cursor": {"cursor", "limit"},
}

def extract_paging_fields(block: str):
    """从类体里抽出分页相关字段声明名（集合）。

    刻意不做「非分页词」白名单豁免：像 pageIndex/pageNo/perPage 这类词本身就是
    「白名单外的分页命名」，必须报出来让门禁拦住。豁免它们等于给假门禁开后门
    （第一版就是这么漏掉 pageIndex 的）。
    """
    names = set()
    for m in FIELD_DECL_RE.finditer(block):
        fname = m.group(1)
        low = fname.lower()
        if "page" in low or "size" in low or "limit" in low or "cursor" in low:
            names.add(fname)
    return names


def classify(paging_fields):
    """按实际字段组合归族。返回 (族名 or None, 未知字段列表)。"""
    if not paging_fields:
        return None, []
    unknown = [f for f in paging_fields
               if not any(f in allowed for allowed in ALLOWED_FIELDS.values())]
    if unknown:
        return None, sorted(unknown)
    # 归族：取该类实际用到的字段所属的族；混搭（如 page + pageNum）本身也应被报出来
    fams = {fam for fam, allowed in ALLOWED_FIELDS.items() if paging_fields & allowed}
    if len(fams) == 1:
        return fams.pop(), []
    return "MIXED", sorted(paging_fields)


def collect():
    """返回 (groups, unknown_hits, mixed_hits)。

    groups: 三族 [(相对路径, 类名)]；unknown_hits: 未知命名的类；mixed_hits: 族内混搭的类。
    """
    groups = {"plain": [], "pagnum": [], "cursor": []}
    unknown_hits = []
    mixed_hits = []
    for p in iter_java_files():
        rel = p.relative_to(REPO).as_posix()
        if is_sdk_path(rel):
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
        except OSError:
            continue
        for cname, blk in split_classes(text):
            if not QUERY_CLASS_RE.search(cname):
                continue
            fields = extract_paging_fields(blk)
            if not fields:
                continue
            fam, detail = classify(fields)
            rec = (rel, cname)
            # 注意判定顺序：classify 的第二个返回值是「细节」，在不同分支含义不同
            # （MIXED 分支返回的是字段列表，不是 unknown 列表）。必须先按 fam 分派，
            # 否则 MIXED 会因为 detail 非空而被误判成「未知命名」。
            # 这正是第一版把page+pageNum 混搭误报为 unknown 的原因。
            if fam is None:
                unknown_hits.append((rel, cname, detail, sorted(fields)))
            elif fam == "MIXED":
                mixed_hits.append((rel, cname, detail))
            elif fam:
                groups[fam].append(rec)
    return groups, unknown_hits, mixed_hits


KNOWN_FAMILIES = {"plain", "pagnum", "cursor"}


def check_spec_drift():
    """规范文档的分页章节是否与「机器门禁已接入」这一事实一致。

    历史判据（2026-10-03 前）：`re.search(r"Advisory|建议|非强制", text)` 命中即警告
    「规范自述为 Advisory（无机器门禁）」。该判据有两个问题：
      ① **关键词式判据**——规范正文大量用「建议」表达正常的设计取向，不是声明"无门禁"，
         2026-10-03 门禁真正接入 `check.sh` 后仍持续误报；
      ② 只看文档措辞、不看真实接入状态，文档改个词就能骗过自己。
    现改为**反查真实接入点**：本脚本是否已被 `check.sh` / `ci-check.sh` / `.github/workflows` 调用。
    """
    spec = REPO / SPEC_DOC
    if not spec.exists():
        return [f"规范文档不存在: {SPEC_DOC}"]
    warns = []
    text = spec.read_text(encoding="utf-8", errors="ignore")
    if re.search(r"pageNum", text):
        warns.append("规范文档自身出现 pageNum —— 需确认是「禁止」语境还是「允许」语境")

    # 反查真实接入点：只看门禁脚本名是否被门禁载体调用。
    carriers = (
        (REPO / "scripts" / "check.sh", "scripts/check.sh"),
        (REPO / "scripts" / "ci-check.sh", "scripts/ci-check.sh"),
        (REPO / ".github" / "workflows" / "ci.yml", ".github/workflows/ci.yml"),
    )
    self_name = "check-paging-param-names.py"
    wired = [
        label
        for path, label in carriers
        if path.is_file() and self_name in path.read_text(encoding="utf-8", errors="ignore")
    ]
    if not wired:
        warns.append(
            "本门禁未被任何门禁载体调用（scripts/check.sh / scripts/ci-check.sh / .github/workflows）"
            "—— 规范 §5.1「新增端点只允许 page/size」未被机械执行，这是 pageNum 族长期存活的根因"
        )
    return warns


def find_dup_layers(groups):
    """找出「web 层 Req + application 层 Query 同名并存」的双层重复。

    为什么要单独报: 实测 bone-system 里 LogPageReq(web) 与 LogPageQuery(application)
    字段完全相同，靠 MapStruct Assembler 逐字段拷贝转换。改分页字段名要同时动两个类 +
    Assembler，**收敛成本比报告里按「18 个 query 类」估算的更高**。这个信息直接影响路线选型。
    """
    dup = defaultdict(list)
    for rel, cname in groups["pagnum"]:
        stem = re.sub(r"(PageReq|PageQuery|PageQry|Req|Query|Qry)$", "", cname)
        dup[stem].append((cname, rel))
    out = []
    for stem, items in dup.items():
        layers = {c for c, _ in items}
        has_req = any(c.endswith(("PageReq", "Req")) for c in layers)
        has_qry = any(c.endswith(("PageQuery", "PageQry", "Query", "Qry")) for c in layers)
        if has_req and has_qry and len(items) > 1:
            out.append((stem, items))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true", help="只出报告，恒 exit 0")
    args = ap.parse_args()

    groups, unknown_hits, mixed_hits = collect()
    total = sum(len(v) for v in groups.values())

    print("=" * 72)
    print("分页入参命名审计（对外入参类，不含SDK 内部命名）")
    print("=" * 72)
    print()
    for key, label in (("plain", "page/size（规范族）"), ("pagnum", "pageNum/pageSize（存量违规）"),
                       ("cursor", "cursor/limit（游标族）")):
        print(f"  {label:34} {len(groups[key]):3} 个入参类")
    print(f"  {'合计':34} {total:3}")
    print()

    if groups["pagnum"]:
        print(f"🟡 存量 pageNum/pageSize 入参类 {len(groups['pagnum'])} 个（L3 级收敛，超出 AI 自主权）:")
        for rel, cname in groups["pagnum"]:
            print(f"    {rel}  ::  {cname}")
        print()
        dups = find_dup_layers(groups)
        if dups:
            print(f"⛔  其中 {len(dups)} 组是「web 层 Req + application 层 Query」双层字段重复：")
            for stem, items in sorted(dups):
                names = ", ".join(c for c, _ in items)
                print(f"    · {stem}: {names}")
            print("    → 改字段名需同时动两层类 + MapStruct Assembler，收敛成本高于按「query 类个数」估算。")
            print()

    spec_warns = check_spec_drift()

    print("── 阻断项 ──")
    blocked = False
    if unknown_hits:
        print(f"  🔴 出现白名单外的分页字段命名 {len(unknown_hits)} 处（禁止第三套命名）:")
        for rel, cname, unknown, fields in unknown_hits:
            print(f"      {rel} :: {cname}")
            print(f"        未知字段={unknown} 实际分页字段={fields}")
        blocked = True
    else:
        print("  ✅ 未发现白名单外的分页字段命名")

    if mixed_hits:
        print(f"  🔴 族内混搭 {len(mixed_hits)} 处（同一类里跨族字段并存，必有一族不生效）:")
        for rel, cname, fields in mixed_hits:
            print(f"      {rel} :: {cname}  字段={fields}")
        blocked = True
    else:
        print("  ✅ 未发现族内混搭")

    if spec_warns:
        print("\n🟡 规范文档与实测的一致性:")
        for w in spec_warns:
            print(f"    · {w}")

    print()
    if args.report_only:
        print("[report-only] 恒 exit 0")
        return 0
    if blocked:
        print("结果: FAIL（分页命名不合规）")
        return 1
    print("结果: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
