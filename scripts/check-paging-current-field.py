#!/usr/bin/env python3
"""分页当前页字段门禁：禁止消费 `list`（后端 @Deprecated 兼容getter 的产物）。

背景（2026-10-03）：
    后端 `bone-core`的 `PageResult` 有 3 个 `@Deprecated` 兼容 getter
    （`getList`/`getPageNum`/`getPageSize`），而 Jackson **默认不因 `@Deprecated` 忽略它们**，
    故响应里 `records` 与 `list` 两组键同时存在（实测 14 个键，`Bone-API-规范.md` §5.3）。
    权威字段是 `records`（§3.3明文「禁止 list/items」）。

    收敛动作分两步且**顺序不可颠倒**：
      ① 前端全部改读 `records`（本门禁负责防回退）
      ② 后端给3 个废弃 getter 加 `@JsonIgnore`
    若跳过① 直接做②，响应里就只剩 `pageNum`/`pageSize`，前端读 `list` 的地方直接白屏。
    本门禁是① 的护栏：让「读 list」变成机器可拦的违规，而不是靠人记。

本脚本查什么（前端 TS/TSX）：
    R1  `.data.list` / `.data?.list`   —— 直接读响应对象的 list 键
    R2  `.list` 紧跟在 `xxxPage(...)` / `normalizePage(...)` 之后 —— 归一函数返回值的 list
    R3  `<Type>.list` 显式类型注解引用       —— 类型层面依赖 list

刻意**豁免**的三类（否则误报，且它们确实不是分页 current-page 键）：
    - `xxxApi.list(...)`  —— 「列出某类资源」的普通方法名（metadataTemplateApi.list 等），与分页无关
    - `Array.prototype` 的 `.list`（如 `Set` 转数组的中间变量名 `list`）
    - `list(` 作为函数调用 —— 那是方法调用而非属性读取
    - **自声明的 `{ list: T[] }` 响应包装** —— 如 `configService.list()` 打的是
      `/system/config/page` 但由前端自己声明 `ApiResponse<{ list: SystemConfig[] }>`，
      它不经bone-core `PageResult`，故 `list` 就是该端点的**唯一**契约，不在收敛范围内。
      判据：文件内出现 `ApiResponse<{ list: ... }>`（可能与读取行不在同一行，
      如声明在上一行、读取在下一行）则该文件的 `list` 读取整体豁免。
      早期版本误用过两个更严的判据，各有缺陷：
        ①精确到「同一行」—— 漏掉跨行声明的真实豁免（configService 即此类）；
        ②「出现 `{ list: X[] }` 字面量就整文件豁免」—— 被负向探针抓出假门禁，
           读 `bone-core` 分页 `list` 的文件只要碰巧也声明过 `{ list: X[] }` 就被整file 放行。
      现判据要求声明里带 `ApiResponse`（即明确是响应包装类型），
      能匹配上的都是「自己定义响应契约」的场景。

用法：
    python3 scripts/check-paging-current-field.py # 阻断模式（有违规 → exit 1）
    python3 scripts/check-paging-current-field.py --report-only  # 只出报告，恒 exit 0
"""

import argparse
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
FRONTEND = REPO / "bone-frontend"
SKIP_DIRS = {"node_modules", "dist", "build", ".git", ".next", "coverage"}
SCAN_EXT = {".ts", ".tsx"}

# R1：读响应对象的 list 键。data后面必须紧跟 .list（可选链也算）
R1 = re.compile(r"\.data\??\.list\b(?!\s*\()")
# R2：归一函数 / 分页 API 调用后紧跟 .list
R2 = re.compile(r"(?:normalizePage|unwrapPage)\([^()]*\)\s*\??\.list\b")
R2_CALL = re.compile(r"\w*[Pp]age\w*\([^()]*\)\s*\??\.list\b")
# R2c：归一函数结果先赋给变量、再由该变量读 .list（间接数据流，跨行）。
# 早期版本只查「调用紧跟 .list」这一种形态，负向探针打出漏报：
#   const p = normalizePage(raw); ... return p.list;
# 这种是页面里最常见的写法，必须覆盖。
R2_VAR_DECL = re.compile(r"(?:const|let|var)\s+(\w+)\s*(?::[^=]+)?=\s*(?:normalizePage|unwrapPage)\(")
R2_VAR_USE = re.compile(r"^\s*\w+\s*\??=\s*(\w+)\s*\??\.\s*list\b|\breturn\s+(\w+)\s*\??\.\s*list\b")
# R3：类型注解里显式声明 list 字段
R3 = re.compile(r"PageResult(?:IamCompat)?\s*<[^>]*>\s*;?\s*$")
R3_FIELD = re.compile(r"^\s*list\s*:\s*T\[\]")

RULES = [
    ("R1", "读响应对象的 `.data.list`（权威字段应为 `.data.records`）", R1),
    ("R2a", "`normalizePage(...)` / `unwrapPage(...)` 结果读 `.list`", R2),
    ("R2b", "分页 API 调用结果读 `.list`", R2_CALL),
]

# 自声明响应包装：`ApiResponse<{ list: X[] }...>`。仅豁免「声明与读取在同一行」的情形。
SELF_DECLARED_LIST = re.compile(r"ApiResponse\s*<[^>]*\{\s*list\s*:")


def iter_sources():
    for p in FRONTEND.rglob("*"):
        if p.suffix not in SCAN_EXT:
            continue
        parts = set(p.parts)
        if parts & SKIP_DIRS:
            continue
        yield p


def scan(path: Path):
    try:
        src = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return []
    hits = []
    # 自声明响应包装的文件整体豁免（见 SELF_DECLARED_LIST 注释）。
    # 判据要求带 ApiResponse<{ list: ... }>，即前端自己定义了响应契约。
    self_declared = bool(SELF_DECLARED_LIST.search(src))
    # R2c 的间接数据流：先收集「由归一函数赋值的变量名」，再扫这些变量的 .list 读取。
    # 单遍正则做不到（赋值行与读取行的先后不固定），故先扫一遍建表。
    normalize_vars = set()
    for line in src.split("\n"):
        m = R2_VAR_DECL.search(line)
        if m:
            normalize_vars.add(m.group(1))
    for lineno, line in enumerate(src.split("\n"), 1):
        stripped = line.strip()
        # 整行注释不算违规
        if stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
            continue
        if self_declared:
            continue
        for rid, desc, pat in RULES:
            if pat.search(line):
                hits.append((lineno, rid, desc, stripped[:100]))
        # R2c：归一函数结果变量的 .list 读取
        if normalize_vars:
            m = R2_VAR_USE.search(line)
            if m and (m.group(1) in normalize_vars or m.group(2) in normalize_vars):
                hits.append((lineno, "R2c",
                             "归一函数结果变量读 `.list`（间接数据流）", stripped[:100]))
        if R3_FIELD.match(line) and "PageResult" in "\n".join(src.split("\n")[max(0, lineno - 6): lineno]):
            hits.append((lineno, "R3", "类型注解里声明 `list` 字段（应为 `records`）", stripped[:100]))
    return hits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true")
    args = ap.parse_args()

    files = 0
    total = 0
    per_file = []
    for p in iter_sources():
        files += 1
        hits = scan(p)
        if hits:
            rel = p.relative_to(REPO).as_posix()
            per_file.append((rel, hits))
            total += len(hits)

    print("=" * 72)
    print("分页当前页字段门禁（禁止消费 list，权威字段是 records）")
    print("=" * 72)
    print(f"扫描 {files} 个前端源文件（已排除 node_modules/dist/build）\n")

    if per_file:
        print(f"🔴 发现 {total} 处读取废弃字段 `list` 的违规：\n")
        for rel, hits in per_file:
            print(f"  {rel}")
            for lineno, rid, desc, text in hits:
                print(f"    [{rid}] 第 {lineno} 行 · {desc}")
                print(f"         {text}")
            print()
    else:
        print("✅ 未发现读取 `list` 的消费点（全部已用权威字段 `records`）\n")

    if args.report_only:
        print("[report-only] 恒 exit 0")
        return 0
    if total:
        print("结果: FAIL —— list 是后端 @Deprecated 兼容 getter 的产物，@JsonIgnore 收敛后将消失。")
        print("       改用 `records`（Bone-API-规范 §3.3 / §5.3）。")
        return 1
    print("结果: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())