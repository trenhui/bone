#!/usr/bin/env python3
"""synapClaim 架构逻辑边界机检（只读，不修改任何文件）。

判据一：每个子域到 7 个上下文的归属完整、互斥，且 ``quota`` 只归 Funds（D4=A）。
       （子域个数不写死在这里：真源是 synapclaim-domain-context-map.json，写死就会出现
        「新增子域后判据仍宣称 20 个」的文档↔代码漂移。）
判据二：Controller 的 ``ApiResponse`` / ``PageResult`` 泛型不得出现 domain **实体/聚合**
        （HC-003）。读模型 Projection、值对象 record 与 application 层输出对象不在判据内。

用法：
    python3 scripts/check-synapclaim-architecture.py                # 主判据
    python3 scripts/check-synapclaim-architecture.py --self-test    # 负向探针自证

--self-test 在临时目录重建最小模块树并逐个注入违规，断言每个负向探针都判红；
判据恒绿（把违规当通过）是这类门禁最常见的失效形态，故必须与正向同批自证，
并由活入口每次提交强制运行（口径同 tools/check_all.py 的双行登记）。
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import sys
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
MAP_FILE = Path(__file__).resolve().parent / "synapclaim-domain-context-map.json"
APP = (
    REPO
    / "bone-apps"
    / "synap-claim"
    / "synap-claim-app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "bone"
    / "claim"
)
MODEL_DIR = APP / "domain" / "model"
CTRL_DIR = APP / "adapter" / "web" / "controller"
ENTITY_BASE = r"(?:TenantAggregateRoot|TenantAbstractEntity|AbstractEntity|AggregateRoot)"


def check_domain_context(
    map_file: Path = MAP_FILE, model_dir: Path = MODEL_DIR
) -> list[str]:
    data = json.loads(map_file.read_text(encoding="utf-8"))
    contexts = data["contexts"]
    errors: list[str] = []
    owner: dict[str, str] = {}
    for ctx, spec in contexts.items():
        for sub in spec["subdomains"]:
            if sub in owner:
                errors.append(f"子域重复归属: {sub} 同时归 {owner[sub]} 与 {ctx}")
            owner[sub] = ctx

    if not model_dir.is_dir():
        return [f"领域模型目录不存在: {model_dir}"]
    actual = sorted(p.name for p in model_dir.iterdir() if p.is_dir())
    mapped = sorted(owner)
    if actual != mapped:
        errors.append(f"子域清单与磁盘不一致: 磁盘={actual} 映射={mapped}")
    if owner.get("quota") != "Funds":
        errors.append(f"quota 归属错误: 期望 Funds，实际 {owner.get('quota')}")
    funds = contexts.get("Funds", {})
    for sub in ("settlement", "reconciliation", "quota"):
        if sub not in funds.get("subdomains", []):
            errors.append(f"Funds 缺少子域 {sub}")
    return errors


def domain_entity_types(model_dir: Path = MODEL_DIR) -> set[str]:
    """扫描 domain/model 下的实体/聚合类名；Projection、值对象、枚举不算。"""
    entities: set[str] = set()
    for path in model_dir.rglob("*.java"):
        text = path.read_text(encoding="utf-8")
        if not re.search(rf"extends\s+{ENTITY_BASE}\b|@Table\s*\(", text):
            continue
        match = re.search(r"\b(?:class|record)\s+(\w+)", text)
        if match:
            entities.add(match.group(1))
    return entities


def check_response_contract(
    ctrl_dir: Path = CTRL_DIR, model_dir: Path = MODEL_DIR
) -> list[str]:
    if not ctrl_dir.is_dir():
        return [f"Controller 目录不存在: {ctrl_dir}"]
    entities = domain_entity_types(model_dir)
    errors: list[str] = []
    for path in sorted(ctrl_dir.glob("*Controller.java")):
        text = path.read_text(encoding="utf-8")
        for match in re.finditer(
            r"(?:ApiResponse|PageResult)\s*<([^;]*?)>\s+\w+\s*\(", text, re.S
        ):
            generic = match.group(1)
            for entity in entities:
                if re.search(rf"\b{re.escape(entity)}\b", generic):
                    errors.append(f"{path.name}: 响应信封直出领域实体 {entity}")
    return errors


def _write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


# ---------------------------------------------------------------- 判据三/四：分页契约
CLAIM_PAGES = REPO / "bone-frontend" / "apps" / "bone-claim-app" / "src" / "pages"
PAIRED_FIND = re.compile(r"\b(\w+Repository)\s*\.\s*(find\w*Page)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")
PAIRED_COUNT = re.compile(r"\b(\w+Repository)\s*\.\s*(count\w*Page)\s*\(([^()]*(?:\([^()]*\)[^()]*)*)\)")
# 分页 size 写死成 ≥2 位数字（20/50/100 这类「固定切片」）；`size: 1` 是单条指标探测，不是分页
FIXED_SIZE = re.compile(r"(?<![\w$])size\s*:\s*(\d{2,})\b")
SIZE_FLOOR = 10
# find*Page 相比 count*Page 允许多出来的「非过滤参数」形态（只允许这些落在尾部）：
# 分页参数 + 排序参数。排序/分页都不改变结果集，count 省略它们是正确做法；
# 而少一个**过滤**参数（kw / status / insurerCode …）就是整表判断。
PAGE_ARG = re.compile(
    r"^(?:[\w$.]*\.)?(?:page|pageNum|size|pageSize|offset|limit"
    r"|sortBy|sortField|sortOrder|sortingFields|orderBy|asc|desc)\(?\)?$"
)


def _split_args(raw: str) -> list[str]:
    """按顶层逗号切实参（跳过嵌套括号内的逗号），再抹掉空白，供前缀比对。"""
    out, depth, buf = [], 0, ""
    for ch in raw:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(buf)
            buf = ""
            continue
        buf += ch
    if buf.strip():
        out.append(buf)
    return [re.sub(r"\s+", "", a) for a in out if a.strip()]


def check_paging_same_source(app_dir: Path = APP) -> list[str]:
    """判据三：分页 total 必须与列表同源——count*Page 的实参必须是 find*Page 实参的**前缀**。

    为什么需要（2026-11-16 变异验证实证）：`total` 若由「整表 count」得出而列表带过滤条件，
    分页器算出的页数比真实结果多，用户翻到空白页；这与「前端固定切片」是同一个数据正确性
    缺陷的两端（一端谎报总数、一端谎报内容）。允许 find 多出分页参数（page/size）落在尾部，
    但不允许 count 少过滤条件——少一个条件就是「整表判断」。
    """
    errors: list[str] = []
    for path in sorted(app_dir.rglob("*ApplicationService.java")):
        text = path.read_text(encoding="utf-8")
        finds: dict[str, list[str]] = {}
        for m in PAIRED_FIND.finditer(text):
            finds.setdefault(m.group(1), _split_args(m.group(3)))
        for m in PAIRED_COUNT.finditer(text):
            repo, count_args = m.group(1), _split_args(m.group(3))
            find_args = finds.get(repo)
            if find_args is None:
                errors.append(
                    f"{path.name}: {repo} 有 count*Page 却无配对的 find*Page"
                    "（total 来源不明，无法判断是否同源）"
                )
                continue
            # 先剥掉 find 的分页/排序实参（它们不改变结果集），再要求 count 与**过滤实参完全相同**。
            # 只比前缀不够：`count(t)` 也是 `find(t, kw, page, size)` 的前缀，但那个 count 明显
            # 少了 kw 过滤条件（整表判断）。完全一致还顺带钉住实参顺序与个数。
            filtered = [a for a in find_args if not PAGE_ARG.match(a)]
            if count_args != filtered:
                dropped = [a for a in filtered if a not in count_args]
                detail = f"（少了过滤实参 {dropped}）" if dropped else ""
                errors.append(
                    f"{path.name}: {repo} 的 count 实参 {count_args} 与 find 的过滤实参 "
                    f"{filtered} 不一致{detail}"
                    "：total 与列表不同源会让页数偏大，用户翻到空白页"
                )
    return errors


def check_no_fixed_page_slice(pages_dir: Path = CLAIM_PAGES) -> list[str]:
    """判据四：**工作台列表页**不得把分页 size 写死成字面量（≥10 视为「固定切片」）。

    实测背景：核赔页曾按 `size: 50` 固定取 50 条、再由 antd 按 `pageSize: 20` 前端切片，
    第 3 页起恒空。`size: 1` 是「取一条算指标」的正当用法，故阈值取 10 而不是 1。

    射程只到 `*WorkbenchPage.tsx`（作业台的待办/案件列表）：配置页里 `{ page: 1, size: 200 }`
    是「一次拉满下拉选项」，不是分页，误拦会逼作者加豁免从而废掉整条判据。
    块注释先剥离——本仓存在「此前固定取 size: 50」这类说明性注释，扫描到就成假红。
    """
    errors: list[str] = []
    if not pages_dir.is_dir():
        return errors
    for path in sorted(pages_dir.glob("*WorkbenchPage.tsx")):
        text = _strip_block_comments(path.read_text(encoding="utf-8"))
        for lineno, line in enumerate(text.splitlines(), 1):
            code = line.split("//", 1)[0]
            for m in FIXED_SIZE.finditer(code):
                if int(m.group(1)) >= SIZE_FLOOR:
                    errors.append(
                        f"{path.name}:{lineno}: 分页 size 写死为 {m.group(1)}"
                        "（固定切片冒充分页，翻页时静默丢行；应传受控的 page/size 状态）"
                    )
    return errors


def _strip_block_comments(src: str) -> str:
    """按行剥离块注释但保留行号（状态机跨行延续），避免注释里的示例代码被当成实现。"""
    out: list[str] = []
    in_block = False
    for line in src.splitlines():
        if in_block:
            end = line.find("*/")
            if end == -1:
                out.append("")
                continue
            line = line[end + 2:]
            in_block = False
        while "/*" in line:
            start = line.find("/*")
            end = line.find("*/", start + 2)
            if end == -1:
                line = line[:start]
                in_block = True
                break
            line = line[:start] + line[end + 2:]
        out.append(line)
    return "\n".join(out)


def self_test() -> int:
    """正例基线 + 4 个负向探针：证明判据在跑，不是恒绿。"""
    failures: list[str] = []
    RED_PROBES = [0]
    GREEN_PROBES = [0]
    with tempfile.TemporaryDirectory(prefix="synapclaim-arch-selftest-") as tmp:
        root = Path(tmp)
        map_file = root / "synapclaim-domain-context-map.json"
        model = root / "domain" / "model"
        ctrl = root / "adapter" / "web" / "controller"
        app_svc = root / "application"
        pages = root / "pages"

        def write_baseline() -> None:
            doc = {
                "contexts": {
                    "Funds": {"subdomains": ["settlement", "reconciliation", "quota"]},
                    "Claim": {"subdomains": ["claim"]},
                    "Config": {"subdomains": ["config"]},
                }
            }
            _write(map_file, json.dumps(doc, ensure_ascii=False))
            for sub in ("settlement", "reconciliation", "quota", "claim", "config"):
                (model / sub).mkdir(parents=True, exist_ok=True)
            ctrl.mkdir(parents=True, exist_ok=True)
            _write(
                ctrl / "FooController.java",
                "public class FooController {\n"
                "  public ApiResponse<FooResp> list() { return null; }\n"
                "}\n",
            )
            # 合规分页：count 实参 = find 实参前缀（find 只多出 page/size）
            _write(
                app_svc / "FooApplicationService.java",
                "public class FooApplicationService {\n"
                "  PageResult<Foo> page(long t, String kw, int page, int size) {\n"
                "    PageBounds b = PageBounds.of(page, size);\n"
                "    var rows = fooRepository.findFooPage(t, kw, b.page(), b.size());\n"
                "    long total = fooRepository.countFooPage(t, kw);\n"
                "    return PageResult.of(rows, total);\n"
                "  }\n"
                "}\n",
            )
            # 合规页面：size 传受控变量；`size: 1` 是单条指标探测，不得误报
            _write(
                pages / "FooWorkbenchPage.tsx",
                "const resp = await api.tasks({ page, size: pageSize });\n"
                "const probe = await api.metrics({ size: 1 });\n"
                "const grid = { pageSize: 20 };\n",
            )

        def expect_red(label: str, errors: list[str]) -> None:
            RED_PROBES[0] += 1
            if errors:
                print(f"  PASS 负向探针 {label}: {errors[0]}")
            else:
                failures.append(f"{label} 未判红（判据可能恒绿）")

        def expect_green(label: str, errors: list[str]) -> None:
            """互补探针：证明判据不是「什么都拦」——恒红与恒绿同样没有判别力。"""
            GREEN_PROBES[0] += 1
            if errors:
                failures.append(f"{label} 应判绿却判红（误报会逼作者关掉门禁）: {errors[0]}")
            else:
                print(f"  PASS 正向互证 {label}（不误报）")

        # go 塞：合规基线必须判绿，否则「什么都拦」与「什么都不拦」同样不可用
        write_baseline()
        baseline = (
            check_domain_context(map_file, model)
            + check_response_contract(ctrl, model)
            + check_paging_same_source(app_svc)
            + check_no_fixed_page_slice(pages)
        )
        if baseline:
            failures.append(f"正向基线意外判红: {baseline}")
        else:
            print("  PASS 正向基线：子域归属 / HC-003 / 分页同源 / 无固定切片 均合规")

        # 探针 1：D4 核心裁决——quota 错挂 Config
        doc = json.loads(map_file.read_text(encoding="utf-8"))
        doc["contexts"]["Funds"]["subdomains"].remove("quota")
        doc["contexts"]["Config"]["subdomains"].append("quota")
        _write(map_file, json.dumps(doc, ensure_ascii=False))
        expect_red("quota 错挂 Config", check_domain_context(map_file, model))
        write_baseline()

        # 探针 2：同一子域重复归属（映射互斥性）
        doc = json.loads(map_file.read_text(encoding="utf-8"))
        doc["contexts"]["Claim"]["subdomains"].append("quota")
        _write(map_file, json.dumps(doc, ensure_ascii=False))
        expect_red("子域重复归属", check_domain_context(map_file, model))
        write_baseline()

        # 探针 3：映射声明与磁盘子域不一致（防止只校验 JSON 自洽）
        shutil.rmtree(model / "claim")
        expect_red("映射与磁盘不一致", check_domain_context(map_file, model))
        write_baseline()

        # 探针 4：HC-003 Controller 响应信封直出领域实体
        _write(
            model / "claim" / "ClaimCase.java",
            "public class ClaimCase extends TenantAbstractEntity {}\n",
        )
        _write(
            ctrl / "FooController.java",
            "public class FooController {\n"
            "  public ApiResponse<ClaimCase> get() { return null; }\n"
            "}\n",
        )
        expect_red("响应信封直出领域实体", check_response_contract(ctrl, model))

        # 探针 5：total 由「整表 count」得出（count 少了列表的过滤条件）
        write_baseline()
        _write(
            app_svc / "FooApplicationService.java",
            "public class FooApplicationService {\n"
            "  PageResult<Foo> page(long t, String kw, int page, int size) {\n"
            "    var rows = fooRepository.findFooPage(t, kw, page, size);\n"
            "    long total = fooRepository.countFooPage(t);\n"
            "    return PageResult.of(rows, total);\n"
            "  }\n"
            "}\n",
        )
        expect_red("total 整表判断（count 少过滤条件）", check_paging_same_source(app_svc))

        # 探针 6：count 找不到配对的 find*Page（total 来源不明）
        write_baseline()
        _write(
            app_svc / "FooApplicationService.java",
            "public class FooApplicationService {\n"
            "  long total = barRepository.countBarPage(1L, \"k\");\n"
            "}\n",
        )
        expect_red("total 来源不明（无配对 find*Page）", check_paging_same_source(app_svc))

        # 探针 7：前端把分页 size 写死（固定切片冒充分页）
        write_baseline()
        _write(
            pages / "FooWorkbenchPage.tsx",
            "const resp = await api.tasks({ status, size: 50 });\n",
        )
        expect_red("分页 size 写死成 50", check_no_fixed_page_slice(pages))

        # 互补探针：size: pageSize / size: 1 / pageSize: 20 都不得误报
        # （恒红与恒绿同样没有判别力；阈值若退化成「见 size 就拦」，这三条会立刻翻红）
        write_baseline()
        expect_green(
            "受控 size 变量与单条探测 size: 1 / 表格 pageSize: 20",
            check_no_fixed_page_slice(pages),
        )

    if failures:
        for f in failures:
            print(f"  FAIL {f}", file=sys.stderr)
        return 1
    print(f"  self-test: {RED_PROBES[0]}/{RED_PROBES[0]} 负向探针判红，{GREEN_PROBES[0]} 个正向互证不误报，基线全绿")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="synapClaim 架构逻辑边界机检")
    parser.add_argument("--self-test", action="store_true", help="负向探针自证")
    args = parser.parse_args()
    if args.self_test:
        return self_test()

    map_errors = check_domain_context()
    resp_errors = check_response_contract()
    paging_errors = check_paging_same_source()
    slice_errors = check_no_fixed_page_slice()
    print(f"[domain-context] 子域归属 + quota 归 Funds: {'PASS' if not map_errors else 'FAIL'}")
    for e in map_errors:
        print(f"  - {e}")
    print(f"[response-contract] Controller 无领域实体直出: {'PASS' if not resp_errors else 'FAIL'}")
    for e in resp_errors:
        print(f"  - {e}")
    print(f"[paging-same-source] total 与列表同源: {'PASS' if not paging_errors else 'FAIL'}")
    for e in paging_errors:
        print(f"  - {e}")
    print(f"[paging-no-fixed-slice] 分页 size 不写死: {'PASS' if not slice_errors else 'FAIL'}")
    for e in slice_errors:
        print(f"  - {e}")
    all_errors = map_errors + resp_errors + paging_errors + slice_errors
    if all_errors:
        print(f"FAILED: {len(all_errors)} 项违规")
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
