#!/usr/bin/env python3
"""菜单 IA 定稿·UI 优化批次2：ModelingWorkspace 待发布卡 + CTA 提示。"""
import sys, pathlib

F = pathlib.Path('bone-frontend/apps/bone-metadata-app/src/pages/ModelingWorkspace.tsx')
s = F.read_text(encoding='utf-8')

def rep(anchor, new, tag):
    global s
    assert s.count(anchor) == 1, f'MISSING({tag}): {anchor[:50]!r} count={s.count(anchor)}'
    s = s.replace(anchor, new)

rep('场景模板 6–9 字段开箱可用；空白建模默认自动生成界面',
    '场景模板开箱即用；空白建模发布后即可直接录入业务数据',
    'cta-hint')
rep('{/* 待发布实体（待办入口，非实体清单 —— 清单真源在左侧菜单「实体」） */}',
    '{/* 待发布业务对象（待办入口，非清单 —— 清单真源在左侧菜单「业务对象」。\n'
    '         命名 IA 定稿 2026-10-11：菜单三件套=模型设计/业务对象/业务数据，\n'
    '         本页=模型设计（创建入口+待办+应用导航），不重复实现清单与设计器。 */}',
    'todo-comment')
rep('<span>待发布实体</span>', '<span>待发布业务对象</span>', 'todo-title')
rep('查看全部实体 <ArrowRightOutlined />', '查看全部业务对象 <ArrowRightOutlined />', 'todo-extra')
rep('没有待发布的草稿；用上方「从场景模板开始 / 空白建模 / 从存量表导入」创建。实体清单与批量操作见左侧菜单「实体」。',
    '没有待发布的草稿；用上方「从场景模板开始 / 空白建模 / 从存量表导入」创建。'
    '对象清单与批量操作见左侧菜单「业务对象」。',
    'todo-empty')
rep('草稿尚未生成物理表，发布后才能录入数据。完整列表（搜索 / 筛选 / 批量发布）见左侧菜单「实体」。',
    '草稿尚未生成物理表，发布后才能录入业务数据。完整列表（搜索 / 筛选 / 批量发布）见左侧菜单「业务对象」。',
    'todo-desc')

F.write_text(s, encoding='utf-8')
print('mw-batch2-ok')
