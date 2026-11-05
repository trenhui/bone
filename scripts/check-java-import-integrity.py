#!/usr/bin/env python3
"""G48 Java import 引用完整性：已入库的引用方不许指向未入库的类。

**为什么需要这条门禁**（2026-11-05 实证，同一形态已出现三次）：

`javac` 只认**当前检出**，不认**仓库内容**。于是「引用方入库、被引用方只在某人工作树」
这类提交在作者机器上编译通过、单测全绿，而**任何干净 clone 都编不过**。实测三次：

1. `97c6593a7`（规则库扩容）把 `QlExpressRuleEnginePortAdapter` 改成
   `claim.requiresSecondReview(ClaimThresholds.DUAL_REVIEW_AMOUNT)` 并 import 了
   `com.bone.claim.domain.model.ClaimThresholds`，而这个类**从未入库**；同一批里
   `ClaimCase.requiresSecondReview` 的单参签名也只在工作树。dev tip 因此不可构建。
2. 同批次 `ClaimCaseTest` 仍在无参调用 `requiresSecondReview()`，测试源编不过。
3. `ClaimDicts`（`/claim-config/dicts` 的返回类型）同样只在工作树，被在途的
   `ClaimConfigController` import —— 那个改动提交的那天就会变成同一种缺陷。

与 M11（迁移清单登记项必须在 git 索引）同源：**「工作区有」不等于「干净 clone 有」**，
故判据的真源取 **git 索引**，不是文件系统。

判据（三段，逐段收紧；每段都实测过误报为 0）
------------------------------------------------
R1 包在库内、类不在：import 的包段已出现在索引里（有已跟踪文件声明了该 `package`），
   但最外层类不在该包内 ⇒ 类的文件没入库（或类名拼错）。
R2 包不在库内、但是库内包的**前缀**：说明这个包层级属于本项目（例如
   `com.bone.claim.domain.model` 因 `…model.claim` 存在而是已知前缀），
   却一个类都没有 ⇒ 该包的文件整体未入库。
R3 包不在库内、也不在前缀里、但落在本项目拥有的命名空间下（根段取自库内包，实测为
   `com.bone`）：新建子包、只有一个新文件、且没入库 ⇒ 判红。

   为什么必须有 R2/R3：首版只写了 R1（「包在库内才判」），对本次真实缺陷**放行**——
   `ClaimThresholds` 是 `com.bone.claim.domain.model` 里的**唯一**文件，它没入库 ⇒
   该包在索引里根本不存在 ⇒ 被当成外部依赖跳过。是「用主树跑一遍」而不是「用修复后的树
   跑一遍」才发现的：拿修好的树自证，任何判据都会绿。
R4 生成类豁免只认「带 `@Mapper` 的基接口」的 `*Impl`（MapStruct 产物）；
   不接受「名字以 Impl 结尾就放行」——那等于给所有 `*Impl` 开后门。
R5 其余可登记豁免（`scripts/java-import-integrity-allowlist.json`）：理由 ≥20 字符、
   owner 非空；**条目已不违规（类补上了 / 引用没了）同样判红**——豁免只可收缩，
   否则白名单会越写越宽（本仓 i18n 白名单、ddl-required-columns 基线各修过一次）。

不判的情形（宁漏不误报）
------------------------
- 落在项目命名空间之外的 import（Spring/Jackson/JDK…，实测 17626 条，占 61%）；
- `import pkg.*` 通配（无从判定，出现即计数提示；当前全仓 0 处）；
- 同包内引用（Java 无需 import，本判据天然不涉及）；
- 只在**工作区**、尚未 staged 的 import —— 读的是索引内容，不是工作区内容。
  这一条是刻意的：否则本地改到一半（引用一个还没 add 的新类）会让**无关提交**变红，
  而那个人最后会去关掉门禁。

防假绿
------
内置哨兵 `_self_check()`：用合成样本跑判据，若「引用方在、被引用方缺」不再报错，
说明判据被改成了恒绿，直接判失败；同时有**反向对照**（合规样本必须零违规），
否则「见 import 就红」也能骗过哨兵。空集合同样判失败——一条都没扫到不是通过的理由。

用法:
  python3 scripts/check-java-import-integrity.py            # 完整报告（有违规即非零）
  python3 scripts/check-java-import-integrity.py --check    # CI 模式：只打违规行
  python3 scripts/check-java-import-integrity.py --root <dir>   # 单测注入
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from pathlib import Path
from typing import Dict, List, NamedTuple, Optional, Sequence, Set, Tuple

REPO_ROOT = Path(__file__).resolve().parent.parent
ALLOWLIST_REL = "scripts/java-import-integrity-allowlist.json"
MIN_REASON_LEN = 20
ROOT_DEPTH = 2  # 命名空间根取包名前两段（com.bone）

PKG_RX = re.compile(r"^[ \t]*package\s+([\w.]+)\s*;", re.M)
IMP_RX = re.compile(r"^[ \t]*import\s+(static\s+)?([\w.]+)\s*;", re.M)
# 顶层类型声明：class / interface / enum / record / @interface，带常见修饰符前缀。
# 用于兜住「一个文件里除主类外还有包私有顶层类」的情形——只按文件名登记会误报。
TYPE_RX = re.compile(
    r"^[ \t]*(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\s+)*"
    r"(?:@interface|class|interface|enum|record)\s+(\w+)",
    re.M,
)
MAPPER_RX = re.compile(r"^[ \t]*@Mapper\b", re.M)

# 违规种类 → (前缀标记, 人话说明)
KIND_CLASS = "R1"
KIND_PACKAGE = "R2"
KIND_NAMESPACE = "R3"


class Violation(NamedTuple):
    kind: str
    path: str
    line: int
    fq: str
    pkg: str
    outer: str

    def key(self) -> str:
        return f"{self.pkg}.{self.outer}"

    def explain(self) -> str:
        if self.kind == KIND_CLASS:
            return f"库内包 {self.pkg} 下没有 {self.outer}（该类的文件未入库？）"
        if self.kind == KIND_PACKAGE:
            return (
                f"包 {self.pkg} 在索引里没有任何类（整个包的文件都未入库？"
                f"本判据要求先 git add 被引用的类）"
            )
        return f"库内命名空间下没有包 {self.pkg}（新建子包只存在于工作树？先 git add）"


class Catalog(NamedTuple):
    packages: Dict[str, Set[str]]  # 包 → 已入库的顶层类型名
    prefixes: Set[str]  # 所有库内包的前缀（含自身）
    roots: Set[str]  # 命名空间根，如 com.bone
    mappers: Set[str]  # 带 @Mapper 的类型全名


class Index(NamedTuple):
    sources: List[Tuple[str, str]]
    catalog: Catalog


def _git_env() -> Dict[str, str]:
    """剥离 GIT_INDEX_FILE / GIT_DIR / GIT_WORK_TREE。

    **实测踩坑（与 M11 同款）**：`git commit --only` 会构造临时索引并通过 GIT_INDEX_FILE
    指过去，只含本次提交涉及的文件。若不剥离，本判据在 pre-commit 里读到的是那个临时索引，
    「已在真实索引、但本次没提交」的类会被当成不存在 ⇒ **每次 `commit --only` 都误红**。
    """
    return {
        k: v
        for k, v in os.environ.items()
        if k not in ("GIT_INDEX_FILE", "GIT_DIR", "GIT_WORK_TREE")
    }


def _read_blobs(root: Path, shas: Sequence[str]) -> Dict[str, bytes]:
    """一次 `git cat-file --batch` 取回全部 blob（3651 个文件逐个 show 太慢）。"""
    if not shas:
        return {}
    uniq = sorted(set(shas))
    proc = subprocess.run(
        ["git", "cat-file", "--batch"],
        cwd=str(root),
        env=_git_env(),
        input="".join(s + "\n" for s in uniq).encode(),
        capture_output=True,
        timeout=300,
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.decode("utf-8", "replace").strip() or "git cat-file 失败")
    out, pos, blobs = proc.stdout, 0, {}
    for sha in uniq:
        nl = out.find(b"\n", pos)
        if nl < 0:
            break
        header = out[pos:nl].decode("utf-8", "replace").split()
        pos = nl + 1
        if len(header) != 3:  # "<sha> missing" 之类
            blobs[sha] = b""
            continue
        size = int(header[2])
        blobs[sha] = out[pos : pos + size]
        pos += size + 1
    return blobs


def load_index(root: Path) -> Optional[Index]:
    """读 git 索引里的 Java 源（文件清单与内容都以索引为准）。"""
    try:
        ls = subprocess.run(
            ["git", "ls-files", "-s", "-z", "--", "*.java"],
            cwd=str(root),
            env=_git_env(),
            capture_output=True,
            timeout=120,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    if ls.returncode != 0:
        return None

    entries: List[Tuple[str, str]] = []
    for rec in ls.stdout.decode("utf-8", "replace").split("\0"):
        if not rec:
            continue
        meta, _, path = rec.partition("\t")
        fields = meta.split()
        if len(fields) < 2 or not path:
            continue
        entries.append((fields[1], path))
    if not entries:
        return Index([], Catalog({}, set(), set(), set()))

    blobs = _read_blobs(root, [sha for sha, _ in entries])
    sources = [
        (path, blobs.get(sha, b"").decode("utf-8", "replace")) for sha, path in entries
    ]
    return Index(sources, _catalog(sources))


def _package_of(text: str) -> str:
    m = PKG_RX.search(text)
    return m.group(1) if m else ""


def _catalog(sources: Sequence[Tuple[str, str]]) -> Catalog:
    packages: Dict[str, Set[str]] = defaultdict(set)
    mappers: Set[str] = set()
    for path, text in sources:
        pkg = _package_of(text)
        names = set(TYPE_RX.findall(text))
        names.add(Path(path).stem)  # 文件名兜底（含 package-info，无害）
        packages[pkg] |= names
        if MAPPER_RX.search(text):
            mappers |= {f"{pkg}.{n}" for n in names}
    prefixes: Set[str] = set()
    roots: Set[str] = set()
    for pkg in packages:
        parts = pkg.split(".")
        for i in range(1, len(parts) + 1):
            prefixes.add(".".join(parts[:i]))
        if len(parts) >= ROOT_DEPTH:
            roots.add(".".join(parts[:ROOT_DEPTH]))
    return Catalog(dict(packages), prefixes, roots, mappers)


def _split_import(fq: str) -> Optional[Tuple[str, str]]:
    """`com.a.B` → (包段, 最外层类)；无类名段（如 `import static a.b.*`）返回 None。"""
    parts = fq.split(".")
    idx = next((i for i, p in enumerate(parts) if p[:1].isupper()), None)
    if idx is None or idx == 0:
        return None
    return ".".join(parts[:idx]), parts[idx]


def _in_owned_namespace(fq: str, roots: Set[str]) -> bool:
    return any(fq == r or fq.startswith(r + ".") for r in roots)


def scan(sources: Sequence[Tuple[str, str]]) -> List[Violation]:
    """纯函数：索引源 → 违规列表（单测与哨兵都用它，不经文件系统）。"""
    cat = _catalog(sources)
    out: List[Violation] = []
    for path, text in sources:
        if path.endswith(("package-info.java", "module-info.java")):
            continue
        for m in IMP_RX.finditer(text):
            fq = m.group(2)
            if fq.endswith(".*"):
                continue
            split = _split_import(fq)
            if split is None:
                continue
            pkg, outer = split
            line = text.count("\n", 0, m.start()) + 1
            if pkg in cat.packages:
                if outer in cat.packages[pkg]:
                    continue
                # MapStruct：@Mapper 基接口的 *Impl。只认这个形态，不按名字通用豁免。
                if (
                    outer.endswith("Impl")
                    and outer[:-4] in cat.packages[pkg]
                    and f"{pkg}.{outer[:-4]}" in cat.mappers
                ):
                    continue
                out.append(Violation(KIND_CLASS, path, line, fq, pkg, outer))
            elif pkg in cat.prefixes:
                out.append(Violation(KIND_PACKAGE, path, line, fq, pkg, outer))
            elif _in_owned_namespace(fq, cat.roots):
                out.append(Violation(KIND_NAMESPACE, path, line, fq, pkg, outer))
            # 其余是外部依赖，不判
    return out


def _load_allowlist(root: Path) -> Tuple[Dict[str, dict], List[str]]:
    path = root / ALLOWLIST_REL
    if not path.exists():
        return {}, []
    try:
        doc = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        return {}, [f"allowlist 无法解析（{exc}）"]
    entries = doc.get("exemptions")
    if not isinstance(entries, list):
        return {}, [f"{ALLOWLIST_REL} 缺少 exemptions 数组"]
    by_fqn: Dict[str, dict] = {}
    errors: List[str] = []
    for i, raw in enumerate(entries):
        if not isinstance(raw, dict):
            errors.append(f"allowlist 第 {i + 1} 项不是对象")
            continue
        fqn = str(raw.get("fqn") or "").strip()
        reason = str(raw.get("reason") or "").strip()
        owner = str(raw.get("owner") or "").strip()
        if not fqn:
            errors.append(f"allowlist 第 {i + 1} 项缺 fqn")
            continue
        if len(reason) < MIN_REASON_LEN:
            errors.append(
                f"allowlist {fqn} 的 reason 少于 {MIN_REASON_LEN} 字符（写清为什么必须豁免）"
            )
        if not owner:
            errors.append(f"allowlist {fqn} 缺 owner")
        by_fqn[fqn] = raw
    return by_fqn, errors


def _self_check() -> List[str]:
    """哨兵：判据不能只问「你自证通过吗」——直接喂已知违规，必须报出来。"""
    base = [
        ("com/bone/app/Ok.java", "package com.bone.app;\n\nclass Ok {}\n"),
        ("com/bone/app/sub/Other.java", "package com.bone.app.sub;\n\nclass Other {}\n"),
    ]
    probes = (
        ("类缺失", "com/bone/app/User.java", "package com.bone.app;\nimport com.bone.app.Missing;\nclass User {}\n"),
        ("嵌套类缺失", "com/bone/app/User.java", "package com.bone.app;\nimport com.bone.app.Missing.Inner;\nclass User {}\n"),
        ("static 缺失", "com/bone/app/User.java", "package com.bone.app;\nimport static com.bone.app.Missing.X;\nclass User {}\n"),
        # 本次真实缺陷的形态：被引用类是那个包里**唯一**的文件，且未入库 ⇒ 包整体不存在
        ("整包未入库", "com/bone/app/User.java", "package com.bone.app;\nimport com.bone.app.solo.Solo;\nclass User {}\n"),
        # 新建子包、一个文件、未入库 ⇒ 包既不在库内也不在任何前缀里
        ("新子包未入库", "com/bone/app/User.java", "package com.bone.app;\nimport com.bone.app.brandnew.Thing;\nclass User {}\n"),
    )
    errs: List[str] = []
    for label, path, body in probes:
        got = scan(list(base) + [(path, body)])
        if not got:
            errs.append(f"哨兵未命中（{label}）⇒ 判据可能已退化成恒绿")
    # 反向对照：合规样本必须零违规，否则「见 import 就红」也能骗过哨兵。
    clean = list(base) + [
        ("com/bone/app/User.java", "package com.bone.app;\nimport com.bone.app.Ok;\nimport java.util.List;\nclass User {}\n"),
        # MapStruct：@Mapper 基接口的 Impl 必须放行
        ("com/bone/app/MapFace.java", "package com.bone.app;\n@Mapper(componentModel=\"spring\")\ninterface MapFace {}\n"),
        ("com/bone/app/User2.java", "package com.bone.app;\nimport com.bone.app.MapFaceImpl;\nclass User2 {}\n"),
    ]
    if scan(clean):
        errs.append(
            "哨兵反向对照失败：合规样本被判红 ⇒ 判据会把正常代码拦下（"
            + ", ".join(f"{v.kind}:{v.fq}" for v in scan(clean))
            + "）"
        )
    return errs


def check(root: Path) -> Tuple[List[str], List[str], int]:
    """返回 (错误, 提示, 扫描文件数)。错误非空即门禁失败。"""
    errors: List[str] = list(_self_check())
    index = load_index(root)
    if index is None:
        return errors + ["无法读取 git 索引（需在 git 仓库内运行；确认 git 可用）"], [], 0
    if not index.sources:
        return errors + ["索引里没有任何 .java 文件 —— 空集合不是通过的理由"], [], 0

    allow, allow_errors = _load_allowlist(root)
    errors.extend(allow_errors)

    used: Set[str] = set()
    violations = scan(index.sources)
    for v in violations:
        if v.key() in allow:
            used.add(v.key())
            continue
        errors.append(f"{v.kind}: {v.path}:{v.line} import {v.fq} —— {v.explain()}")

    # 过期豁免：条目还在，但已经不违规了 ⇒ 白名单必须收缩。
    for fqn in sorted(set(allow) - used):
        errors.append(
            f"R5: 豁免条目 {fqn} 已不生效（类已入库或引用已移除）⇒ 请从 {ALLOWLIST_REL} 删除"
        )

    notes = [
        f"扫描索引内 .java {len(index.sources)} 个，库内包 {len(index.catalog.packages)} 个，"
        f"命名空间根 {', '.join(sorted(index.catalog.roots))}"
    ]
    if allow:
        notes.append(f"登记豁免 {len(allow)} 条（生效 {len(used)} 条）")
    return errors, notes, len(index.sources)


def main(argv: Optional[Sequence[str]] = None) -> int:
    ap = argparse.ArgumentParser(
        description="Java import 引用完整性（引用方入库、被引用方必须也入库）"
    )
    ap.add_argument("--check", action="store_true", help="CI 模式：只输出违规行")
    ap.add_argument("--root", default=str(REPO_ROOT), help="仓库根（单测注入用）")
    args = ap.parse_args(argv)

    errors, notes, scanned = check(Path(args.root))
    if errors:
        for e in errors:
            print(e)
        hard = [e for e in errors if e.startswith(("R1:", "R2:", "R3:", "R5:"))]
        print(f"FAIL: {len(hard)} 处引用完整性问题（扫描 {scanned} 个文件）")
        return 1
    if not args.check:
        for n in notes:
            print(n)
        print("OK: Java import 引用完整性通过（索引内引用方均指向已入库的类）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
