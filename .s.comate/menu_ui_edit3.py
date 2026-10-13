import io
p = '/Users/renhui.trh/wps/bone/bone-frontend/apps/bone-metadata-app/src/pages/ModelingWorkspace.tsx'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, tag):
    assert s.count(old) == 1, f'{tag}: found={s.count(old)}'
    return s.replace(old, new)

# 1) 我的应用折叠区说明：新词汇 + 边界（应用管理归 IAM）
s = rep('''label: <Space><AppstoreOutlined /><span>我的应用</span><Text type="secondary">应用/模块管理属低频操作，实体主路径见上方</Text></Space>,''',
'''label: <Space><AppstoreOutlined /><span>我的应用</span><Text type="secondary">应用/模块的创建与授权归 IAM「应用管理」；此处仅用于按应用浏览对象，主路径见上方</Text></Space>,''',
    'apps-collapse')

# 2) 空态引导：从「去 IAM 创建」改为「联系管理员 + 直达清单」双出口（消除跨域断点）
s = rep('''<Alert type="info" message="暂无可建模应用，请先在「IAM 管理 → 应用管理」创建" />''',
'''<Alert type="info" showIcon message="还没有可建模的应用" description="应用由组织管理员在「IAM 管理 → 应用管理」统一创建并授权成员；获得权限后回到本页即可开始设计模型。也可先浏览已有<业务对象>熟悉模型结构。" />''',
    'empty-alert')

io.open(p, 'w', encoding='utf-8').write(s)
print('mw-batch3-ok')
