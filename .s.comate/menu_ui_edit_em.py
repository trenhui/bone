import io
p = '/Users/renhui.trh/wps/bone/bone-frontend/apps/bone-metadata-app/src/pages/EntityManagement.tsx'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, tag, expect=1):
    c = s.count(old)
    assert c == expect, f'{tag}: found={c} expect={expect}'
    return s.replace(old, new)

# 1) 顶部注释：旧菜单名注释段（组件 doc 注释保留历史脉络，只改用户可见文案；注释中菜单名同步新词）
s = rep("""      {/* 页头：声明本页职责边界（消除「工作台 / 实体 / 运行时数据三个菜单到底管什么」的困惑） */}""",
       """      {/* 页头：声明本页职责边界（消除「模型设计 / 业务对象 / 业务数据三个菜单到底管什么」的困惑） */}""",
       'comment-header')

# 2) 面包屑：建模工作台 → 模型设计；实体 → 业务对象
s = rep("{ title: <a onClick={() => navigate('/apps')}>建模工作台</a> },",
       "{ title: <a onClick={() => navigate('/apps')}>模型设计</a> },",
       'breadcrumb')
s = rep("{ title: '实体' },",
       "{ title: '业务对象' },",
       'breadcrumb-last')

# 3) 页头标题 + 定位 Tag + 统计行：对齐定稿页头模式（标题+Tag+一句定位），统计并入 Tag 行
s = rep("""          <Typography.Title level={5} style={{ margin: 0 }}>实体</Typography.Title>
          <Tag color="blue" style={{ fontSize: 12 }}>模型清单（唯一真源）</Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 {total} 个{isScoped ? '（当前模块）' : ''} · 全局待发布 {draftTotal ?? '…'}
          </Text>""",
       """          <Typography.Title level={5} style={{ margin: 0 }}>业务对象</Typography.Title>
          <Tag color="blue" style={{ fontSize: 12 }}>对象定义清单（唯一真源）</Tag>
          <Text type="secondary" style={{ fontSize: 12 }}>
            共 {total} 个对象{isScoped ? '（当前模块）' : ''} · 待发布 {draftTotal ?? '…'}
          </Text>""",
       'page-title')

# 4) 页头说明段：新词汇 + 职责边界（对象/字段/数据的动线用新词重述）
s = rep("""        <Text type="secondary" style={{ fontSize: 12 }}>
          在这里检索、筛选、批量发布、复制与维护实体。点「字段」进入实体工作区（结构 / 界面 / 数据 / 发布四页签）；
          录入业务数据请用行内「数据」按钮或工作区的「数据」页签，它会锁定当前实体、不会看错数据。
        </Text>""",
       """        <Text type="secondary" style={{ fontSize: 12 }}>
          在这里检索、筛选、批量发布、复制与维护业务对象。点「结构」对象工作区（结构 / 界面 / 数据 / 发布四页签）；
          录入业务数据请用行内「数据」按钮或工作区的「数据」页签，它会锁定当前对象、不会看错数据。
        </Text>""",
       'page-desc')

# 5) 搜索框 + 按钮文案：实体 → 业务对象（业界：列表首控件 = 搜索，且占位语义完整）
s = rep('placeholder="搜索实体名称/编码"',
       'placeholder="搜索对象名称/编码"',
       'search-ph')

# 6) 新建/导入按钮文案：实体 → 业务对象
s = rep('<AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} icon={<DatabaseOutlined />} onClick={() => setTableImportOpen(true)}>从存量表导入</AuthButton>',
       '<AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} icon={<DatabaseOutlined />} onClick={() => setTableImportOpen(true)}>从存量表导入</AuthButton>',
       'import-table-btn')
s = rep('<AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建实体</AuthButton>',
       '<AuthButton code={BonePermissionCodes.METADATA_MODEL_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>新建对象</AuthButton>',
       'create-btn')

# 7) 表格列头「名称」不动；新键列的 alert / 弹窗标题中「实体」→「对象」在后续批次
io.open(p, 'w', encoding='utf-8').write(s)
print('em-batch1-ok')
