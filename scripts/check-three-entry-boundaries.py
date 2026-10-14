#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""G37：业务建模三入口职责边界静态门禁（2026-10-13）。

背景（doc/design/lowcode/cases/业务建模三入口页最佳方案综述.md 的职责定稿）：
  模型设计（ModelingWorkspace） = 建模入口 / 待办工作台：统计 + 最近草稿 + 应用模块导航，
                                **不承载实体清单**；
  业务对象（EntityManagement） = 模型目录 / 生命周期治理：实体清单的唯一权威真源；
  业务数据（RuntimeDataManagement）= 运行实例数据面：物理表动态 CRUD，经「显式选择」进入。

「一份数据只允许一个列表真源」：工作台若再渲染一份实体清单，用户点两处得两个结果
（且工作台那份无搜索/分页/批量，是残缺的第二真源）。业务数据页若在全局路由静默选中
首个实体，用户以为在看自己选的实体、实际看到列表首项数据（静默串实体）。

规则（均按**净效果**判定，剥离注释行后匹配，防「注释里提到也算违规」的假阳性）：
  规则1：ModelingWorkspace.tsx 不得引用 metadataEntityApi（实体清单 CRUD 门面）——
         工作台数据唯一来源是聚合端点 metadataWorkspaceApi.workspace()；
  规则2：RuntimeDataManagement.tsx 不得出现 setEntityCode(...[0]...) 索引默认选中形状——
         全局路由保持空态由用户显式选择（W0-1 / PRD D12）。

变异验证（2026-10-13 已做）：
  ① ModelingWorkspace 植入 import metadataEntityApi ⇒ 规则1 红（exit 1）；
  ② RuntimeDataManagement 植入 setEntityCode(runtimePublished[0].code) ⇒ 规则2 红；
  ③ 两处撤掉 ⇒ 绿。
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORKSPACE = ROOT / "bone-frontend/apps/bone-metadata-app/src/pages/ModelingWorkspace.tsx"
RUNTIME_DATA = ROOT / "bone-frontend/apps/bone-metadata-app/src/pages/RuntimeDataManagement.tsx"

# 剥离 // 行注释与 /* */ 块注释后逐行扫描（门禁判净效果，注释宣称不算数）
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
LINE_COMMENT = re.compile(r"//[^\n]*")


def strip_comments(text: str) -> str:
    return LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", text))


def check_no_entity_list_in_workspace() -> list[str]:
    """规则1：建模工作台不得引用实体清单 CRUD 门面。"""
    problems = []
    if not WORKSPACE.exists():
        return [f"缺少文件: {WORKSPACE}"]
    code = strip_comments(WORKSPACE.read_text(encoding="utf-8"))
    for i, line in enumerate(code.splitlines(), 1):
        if "metadataEntityApi" in line:
            problems.append(
                f"{WORKSPACE.relative_to(ROOT)}:{i}: 建模工作台引用 metadataEntityApi"
                "（实体清单唯一权威在业务对象页；工作台只经 metadataWorkspaceApi.workspace() 取数）"
            )
    return problems


def check_no_default_entity_selection() -> list[str]:
    """规则2：业务数据全局路由不得默认选中首个实体。"""
    problems = []
    if not RUNTIME_DATA.exists():
        return [f"缺少文件: {RUNTIME_DATA}"]
    code = strip_comments(RUNTIME_DATA.read_text(encoding="utf-8"))
    # setEntityCode(xxx[0]...) / setEntityCode(xxx.at(0)) 索引默认选中形状
    pattern = re.compile(r"setEntityCode\([^)\n]*(\[\s*0\s*\]|\.at\(\s*0\s*\))")
    for i, line in enumerate(code.splitlines(), 1):
        if pattern.search(line):
            problems.append(
                f"{RUNTIME_DATA.relative_to(ROOT)}:{i}: 全局路由默认选中首个实体"
                "（用户未显式选择即展示列表首项数据 = 静默串实体；应保持空态，W0-1 / PRD D12）"
            )
    return problems


def main() -> int:
    problems = check_no_entity_list_in_workspace() + check_no_default_entity_selection()
    if problems:
        print("❌ 业务建模三入口职责边界违规（G37）：")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("✅ 三入口职责边界合规（G37）：工作台无实体清单、业务数据无默认选中")
    return 0


if __name__ == "__main__":
    sys.exit(main())
