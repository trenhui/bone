#!/usr/bin/env python3
"""主键注解落位校验：@Id 必须标注在名为 id 的字段上。

设计依据: 2026-10-07 全模块联调实测（9 个实体中招，集成/主数据/系统三个模块）。

为什么需要这个门禁（背景）:
    bone-metadata-sdk 判定主键的依据是 **实体内带 @Id 注解的字段**
    （TableMetadataResolver → `field.isAnnotationPresent(Id.class)`）。
    于是「@Id 落到哪个字段」直接决定所有按主键 SQL 的 where 条件。

    实测事故形态：`@Id` 与 `id` 字段之间被插入了一段 javadoc（讲 @Deleted 的），
    spotless 不会报错、javac 也不会报错，但语义上 @Id 已经吸附到了 `deleted` 字段：

        public class Connector extends TenantAggregateRoot<Long> {
          @Id

          /** 逻辑删除标记。… */
          @Deleted private Boolean deleted = false;

          @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
          private Long id;
        }

    后果（全部静默，调用方拿到的 HTTP 码看起来"正常"）:
      - findById(id)   → WHERE deleted = <雪花ID>  ⇒ **恒 404**；
                         更糟的是传一个小值（如 0）会命中全部 deleted=0 的行，
                         querySingle 抛「期望 1 行得到 N 行」⇒ 500。
      - update(entity) → WHERE deleted = <id>      ⇒ **影响行数 0，更新静默丢失**。
      - deleteById(id) → 软删 UPDATE 的 where 同样是 deleted ⇒ 删不掉；
                         极端情况下会命中整表。

    迷惑性在于：**列表接口完全正常**（findPage 不带主键谓词），所以"列表有数据、
    点进去详情 404" 这种症状很容易被误判成前端或权限问题。

判据（必须能被反例证伪）:
    逐文件扫描源码（**文件系统遍历，不用 git grep** —— .gitignore 的 `!**/src/main/**`
    反向规则会让 git grep 整片跳过未跟踪的 src/main 文件，刚写完的违规实体提交前扫不到）。
    对每个 `@Id` 注解，向后跳过空行、注释/javadoc、其它注解，取**第一个字段声明**：
      - 字段名 == "id"      ⇒ 通过
      - 字段名 != "id"      ⇒ 违规（如 deleted / code / xxxId）
      - 找不到字段声明      ⇒ 违规（孤立注解，多半是被 javadoc 顶开了）

用法:
    python3 scripts/check-id-annotation.py              # 阻断模式（有违规 → exit 1）
    python3 scripts/check-id-annotation.py --report-only # 只出报告，恒 exit 0

豁免: 无。当前仓库 63 处 @Id 全部标注在 id 字段上（2026-10-08 全量核实），
      故本门禁零豁免、零基线，新增违规一律阻断。
"""

import argparse
import os
import re
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOTS = ["bone-platform", "bone-engine", "bone-framework", "bone-tool"]
SKIP_DIRS = {"target", "node_modules", "build", "generated-code", ".git", "__pycache__"}

# 只认 bone-core 的 @Id（本仓无 jakarta/javax.persistence.Id，来源唯一）。
# 行首要允许缩进；注解与字段同行的写法（`@Id private Long id;`）也要能识别。
ID_LINE = re.compile(r"^\s*@Id\s*$")
ID_INLINE = re.compile(r"^\s*@Id\s+(?:private|protected|public)\b.*$")

# 字段声明：`private Long id;` / `private Boolean deleted = false;`
FIELD_DECL = re.compile(
    r"^\s*(?:private|protected|public)\s+(?:final\s+)?(?:static\s+)?"
    r"[\w<>\[\],.?\s]+?\s(\w+)\s*(?:=|;)"
)

# 扫描注解区时要跳过的行：空行 / 行注释 / 块注释与 javadoc / 其它注解。
# 事故正是靠"javadoc 夹在 @Id 与字段之间"绕过的，所以注释行必须跳过继续找字段。
def is_skippable(line: str) -> bool:
    s = line.strip()
    if not s:
        return True
    if s.startswith("//"):
        return True
    if s.startswith("*") or s.startswith("/*"):
        return True
    if s.startswith("@"):
        return True
    return False


def field_after(lines, idx, window=40):
    """返回 @Id 实际吸附到的字段名；找不到返回 None。"""
    j = idx + 1
    end = min(len(lines), idx + 1 + window)
    while j < end and is_skippable(lines[j]):
        j += 1
    if j >= end:
        return None
    m = FIELD_DECL.match(lines[j])
    return m.group(1) if m else None


def iter_java_files():
    for root in JAVA_ROOTS:
        base = os.path.join(REPO, root)
        if not os.path.isdir(base):
            continue
        for dirpath, dirnames, filenames in os.walk(base):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for fn in filenames:
                if fn.endswith(".java"):
                    yield os.path.join(dirpath, fn)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--report-only", action="store_true", help="只出报告，恒 exit 0")
    args = ap.parse_args()

    violations = []
    checked = 0
    for path in iter_java_files():
        try:
            src = open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        if "@Id" not in src:
            continue
        lines = src.split("\n")
        for i, line in enumerate(lines):
            if ID_LINE.match(line):
                checked += 1
                name = field_after(lines, i)
                if name != "id":
                    violations.append((path, i + 1, name))
            elif ID_INLINE.match(line):
                # 注解与字段同行（`@Id private Long id;`）：必须先剥掉 `@Id ` 前缀，
                # 否则字段正则（要求行首是修饰符）会失配 ⇒ 把合法写法误报成违规。
                m = FIELD_DECL.match(re.sub(r"^\s*@Id\s+", "", line))
                checked += 1
                name = m.group(1) if m else None
                if name != "id":
                    violations.append((path, i + 1, name))

    rel = lambda p: os.path.relpath(p, REPO)
    print(f"@Id 注解落位校验：扫描 {checked} 处 @Id，违规 {len(violations)} 处")
    for p, ln, name in violations:
        got = name if name else "<未找到字段声明（多半被 javadoc/空行顶开）>"
        print(f"  ❌ {rel(p)}:{ln}  @Id 落在字段 `{got}`，应为 `id`")

    if violations:
        print()
        print("后果：findById 恒 404、update/软删静默失效（where 条件变成那个字段）；")
        print("      列表接口不受影响，所以症状表现为「列表有数据、点开详情 404」。")
        print("修法：把 @Id 移到 @GeneratedValue 之上、紧贴 `private Long id;` 之前。")
        if not args.report_only:
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
