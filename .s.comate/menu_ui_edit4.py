# -*- coding: utf-8 -*-
import io, sys
def patch(path, pairs):
    p = path if path.startswith('/') else '/Users/renhui.trh/wps/bone/' + path
    s = io.open(p, encoding='utf-8').read()
    for old, new in pairs:
        assert s.count(old) == 1, (path, old[:40], s.count(old))
        s = s.replace(old, new)
    io.open(p, 'w', encoding='utf-8').write(s)
    print('OK', path)

# 1) EntityManagement：面包屑终点 + 页头说明 + 搜索占位 + 命令面板
EM = 'bone-frontend/apps/bone-metadata-app/src/pages/EntityManagement.tsx'
patch(EM, [
    ("{ title: '实体' },", "{ title: '业务对象' },"),
    ("""在这里检索、筛选、批量发布、复制与维护实体。点「字段」进入实体工作区（结构 / 界面 / 数据 / 发布四页签）；
          录入业务数据请用行内「数据」按钮或工作区的「数据」页签，它会锁定当前实体、不会看错数据。""",
     """在这里检索、筛选、批量发布、复制与维护业务对象。点「结构」进入对象工作区（结构 / 界面 / 数据 / 发布四页签）；
          录入业务数据请用行内「数据」按钮或工作区的「数据」页签，它会锁定当前对象、不会看错数据。"""),
    ('placeholder="搜索实体名称/编码"', 'placeholder="搜索对象名称/编码"'),
    ('<Text strong>跳转实体</Text><Text type="secondary" style={{ fontSize: 12 }}>Ctrl / ⌘ + K · 实体搜索</Text>',
     '<Text strong>跳转对象</Text><Text type="secondary" style={{ fontSize: 12 }}>Ctrl / ⌘ + K · 对象搜索</Text>'),
    ('placeholder="输入实体名称 / 编码 / 表名，回车打开第一个匹配"', 'placeholder="输入对象名称 / 编码 / 表名，回车打开第一个匹配"'),
    ('>无匹配实体</Text>', '>无匹配对象</Text>'),
])

# 2) EntityDetail：字段删除 Popconfirm + 数据按钮 Tooltip
ED = 'bone-frontend/apps/bone-metadata-app/src/pages/EntityDetail.tsx'
patch(ED, [
    ('description="删除字段可能导致运行时数据异常，请谨慎操作。"', 'description="删除字段可能导致业务数据异常，请谨慎操作。"'),
    ("<Tooltip title={isRuntimePublished ? '打开本实体的运行时数据' : '发布后（运行时交付模式）可录入数据'}>",
     "<Tooltip title={isRuntimePublished ? '打开本对象的业务数据' : '发布后（自动生成界面模式）可录入数据'}>"),
])

# 3) InterfaceTab：列投影说明
IT = 'bone-frontend/apps/bone-metadata-app/src/components/InterfaceTab.tsx'
patch(IT, [
    ('以上调整即时生效于「预览」以及发布后的运行时数据表（两者共用同一套列投影）。',
     '以上调整即时生效于「预览」以及发布后的业务数据表（两者共用同一套列投影）。'),
])

# 4) ModelingWorkspace：仅更新文件头 doc-block 注释（术语同步，不影响运行时）
MW = 'bone-frontend/apps/bone-metadata-app/src/pages/ModelingWorkspace.tsx'
patch(MW, [
    ("""/**
 * 建模工作台 —— **元数据域的唯一入口**，任务导向首页（2b F1/UC-W1）。
 *
 * ── 三个菜单的职责边界（本页 / 「实体」/ 「运行时数据」）──────────────
 *「你在这里提问的是『从零开始建一个模型吗』，不是『我有哪些模型』」：
 *   · 本页（`/apps`）      = **入口与待办**：怎么开始建模（3 个 CTA）+ 有多少草稿待发布 + 最近编辑。
 *                不做实体清单 —— 清单真源在「实体」页（有搜索/筛选/批量发布）。
 *   · 实体   （`/entities`）= **实体的唯一权威清单**：搜索、筛选、批量发布、复制、导出、删除。
 *   · 运行时数据（`/runtime`）= **跨实体的数据运营台**：录入/编辑物理表记录。
 *                            单个实体的数据请走「实体详情 → 数据 Tab」（带上下文，不会看错实体）。
 * 三者不是三个并列的「同类列表」，而是 **建模入口 → 模型管理 → 数据运营** 的一条动线。""",
     """/**
 * 模型设计 —— **元数据域的唯一创建入口**，任务导向首页（2b F1/UC-W1；命名 IA 定稿 2026-10-11）。
 *
 * ── 三个菜单的职责边界（本页 / 「业务对象」/ 「业务数据」）────────────
 *「你在这里提问的是『从零开始建一个模型吗』，不是『我有哪些模型』」：
 *   · 本页（`/apps`）      = **入口与待办**：怎么开始建模（3 个 CTA）+ 有多少草稿待发布 + 最近编辑。
 *                不做对象清单 —— 清单真源在「业务对象」页（有搜索/筛选/批量发布）。
 *   · 业务对象（`/entities`）= **对象的唯一权威清单**：搜索、筛选、批量发布、复制、导出、删除。
 *   · 业务数据（`/runtime`）  = **跨对象的数据运营台**：录入/编辑物理表记录。
 *                            单个对象的数据请走「对象详情 → 数据 Tab」（带上下文，不会看错对象）。
 * 三者不是三个并列的「同类列表」，而是 **设计入口 → 对象管理 → 数据运营** 的一条动线。"""),
])

# 5) RuntimeDataManagement：注释同步
RD = 'bone-frontend/apps/bone-metadata-app/src/pages/RuntimeDataManagement.tsx'
patch(RD, [
    ('为空则走独立全局「运行时数据」路由（带实体选择器）',
     '为空则走独立全局「业务数据」路由（带对象选择器）'),
])
print('ALL DONE')
