import { useState, useEffect } from 'react'
import { Layout, Menu, Typography, Card, Table, Tag, Space, Button, Input, Select } from 'antd'
import { HomeOutlined, CodeOutlined, SettingOutlined, AlertOutlined, GithubOutlined } from '@ant-design/icons'
import axios from 'axios'

const { Header, Sider, Content } = Layout
const { Title, Text } = Typography
const { Search } = Input

// 模拟扩展点数据
const mockExtPoints = [
  {
    id: '1',
    name: '订单支付扩展点',
    domain: '支付',
    category: '核心服务',
    interfaceName: 'com.bone.engine.extension.example.PaymentExtPoint',
    version: '1.0.0',
    description: '用于处理各种支付场景的扩展点',
    implementationCount: 3,
    status: 'active'
  },
  {
    id: '2',
    name: '用户认证扩展点',
    domain: '用户',
    category: '安全',
    interfaceName: 'com.bone.engine.extension.example.AuthExtPoint',
    version: '1.0.0',
    description: '用于用户认证的扩展点',
    implementationCount: 2,
    status: 'active'
  },
  {
    id: '3',
    name: '商品推荐扩展点',
    domain: '商品',
    category: '营销',
    interfaceName: 'com.bone.engine.extension.example.RecommendExtPoint',
    version: '1.1.0',
    description: '用于商品推荐算法的扩展点',
    implementationCount: 4,
    status: 'active'
  }
]

// 模拟扩展实现数据
const mockExtensions = [
  {
    id: '1',
    extPointId: '1',
    name: '默认支付处理器',
    className: 'com.bone.engine.extension.example.DefaultPaymentProcessor',
    tenantCode: 'DEFAULT',
    bizCode: 'PAYMENT',
    scenario: 'NORMAL',
    priority: 100,
    version: '1.0.0',
    status: 'enabled',
    createTime: '2024-01-01 10:00:00'
  },
  {
    id: '2',
    extPointId: '1',
    name: 'VIP支付处理器',
    className: 'com.bone.engine.extension.example.VipPaymentProcessor',
    tenantCode: 'TENANT_A',
    bizCode: 'PAYMENT',
    scenario: 'VIP',
    priority: 50,
    version: '1.0.0',
    status: 'enabled',
    createTime: '2024-01-02 14:30:00'
  },
  {
    id: '3',
    extPointId: '1',
    name: '企业支付处理器',
    className: 'com.bone.engine.extension.example.EnterprisePaymentProcessor',
    tenantCode: 'TENANT_B',
    bizCode: 'PAYMENT',
    scenario: 'ENTERPRISE',
    priority: 80,
    version: '1.0.0',
    status: 'disabled',
    createTime: '2024-01-03 09:15:00'
  }
]

function App() {
  const [collapsed, setCollapsed] = useState(false)
  const [activeKey, setActiveKey] = useState('1')
  const [extPoints, setExtPoints] = useState(mockExtPoints)
  const [extensions, setExtensions] = useState(mockExtensions)
  const [selectedExtPoint, setSelectedExtPoint] = useState(null)
  const [loading, setLoading] = useState(false)

  // 加载扩展点数据
  useEffect(() => {
    const loadExtPoints = async () => {
      try {
        setLoading(true)
        // 实际环境中这里会调用API
        // const response = await axios.get('/api/ext-points')
        // setExtPoints(response.data)
        // 这里使用模拟数据
        setExtPoints(mockExtPoints)
      } catch (error) {
        console.error('加载扩展点失败:', error)
      } finally {
        setLoading(false)
      }
    }
    loadExtPoints()
  }, [])

  // 加载扩展实现数据
  const loadExtensions = async (extPointId) => {
    try {
      setLoading(true)
      // 实际环境中这里会调用API
      // const response = await axios.get(`/api/ext-points/${extPointId}/extensions`)
      // setExtensions(response.data)
      // 这里使用模拟数据
      const filtered = mockExtensions.filter(ext => ext.extPointId === extPointId)
      setExtensions(filtered)
    } catch (error) {
      console.error('加载扩展实现失败:', error)
    } finally {
      setLoading(false)
    }
  }

  // 菜单选择处理
  const handleMenuClick = (e) => {
    setActiveKey(e.key)
    if (e.key === '1') {
      setSelectedExtPoint(null)
    }
  }

  // 查看扩展点详情
  const handleViewExtPoint = (record) => {
    setSelectedExtPoint(record)
    loadExtensions(record.id)
  }

  // 扩展点表格列定义
  const extPointColumns = [
    {
      title: '扩展点名称',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <a onClick={() => handleViewExtPoint(record)}>{text}</a>
      )
    },
    {
      title: '领域',
      dataIndex: 'domain',
      key: 'domain',
      render: (text) => <Tag color="blue">{text}</Tag>
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: (text) => <Tag color="green">{text}</Tag>
    },
    {
      title: '接口名称',
      dataIndex: 'interfaceName',
      key: 'interfaceName'
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '实现数量',
      dataIndex: 'implementationCount',
      key: 'implementationCount'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (text) => (
        <Tag color={text === 'active' ? 'success' : 'default'}>
          {text === 'active' ? '活跃' : '禁用'}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button type="link" onClick={() => handleViewExtPoint(record)}>详情</Button>
        </Space>
      )
    }
  ]

  // 扩展实现表格列定义
  const extensionColumns = [
    {
      title: '实现名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '实现类',
      dataIndex: 'className',
      key: 'className'
    },
    {
      title: '租户代码',
      dataIndex: 'tenantCode',
      key: 'tenantCode'
    },
    {
      title: '业务域',
      dataIndex: 'bizCode',
      key: 'bizCode'
    },
    {
      title: '场景',
      dataIndex: 'scenario',
      key: 'scenario'
    },
    {
      title: '优先级',
      dataIndex: 'priority',
      key: 'priority'
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (text) => (
        <Tag color={text === 'enabled' ? 'success' : 'default'}>
          {text === 'enabled' ? '启用' : '禁用'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime'
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button type="link">编辑</Button>
          <Button 
            type="link" 
            danger={record.status === 'enabled'}
          >
            {record.status === 'enabled' ? '禁用' : '启用'}
          </Button>
        </Space>
      )
    }
  ]

  return (
    <Layout>
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={value => setCollapsed(value)}
        width={250}
      >
        <div className="bone-logo">
          Bone 扩展引擎
        </div>
        <Menu 
          mode="inline" 
          selectedKeys={[activeKey]}
          onClick={handleMenuClick}
          style={{ height: '100%', borderRight: 0 }}
        >
          <Menu.Item key="1" icon={<HomeOutlined />}>
            扩展点管理
          </Menu.Item>
          <Menu.Item key="2" icon={<CodeOutlined />}>
            扩展实现管理
          </Menu.Item>
          <Menu.Item key="3" icon={<SettingOutlined />}>
            系统配置
          </Menu.Item>
          <Menu.Item key="4" icon={<AlertOutlined />}>
            运行监控
          </Menu.Item>
        </Menu>
      </Sider>
      <Layout className="site-layout">
        <Header className="site-layout-background" style={{ padding: 0, height: 64, lineHeight: '64px', paddingRight: 24, textAlign: 'right' }}>
          <Text type="secondary" style={{ marginRight: 16 }}>Bone Extension Engine v1.0.0</Text>
          <GithubOutlined />
        </Header>
        <Content style={{ margin: '0 16px' }}>
          <div 
            className="site-layout-background" 
            style={{ padding: 24, minHeight: 360 }}
          >
            {!selectedExtPoint ? (
              <>
                <Title level={4}>扩展点列表</Title>
                <Card>
                  <div style={{ marginBottom: 16 }}>
                    <Search
                      placeholder="搜索扩展点"
                      onSearch={value => console.log(value)}
                      style={{ width: 250, marginRight: 16 }}
                    />
                    <Select
                      placeholder="按领域筛选"
                      style={{ width: 150, marginRight: 16 }}
                      options={[
                        { value: 'all', label: '全部' },
                        { value: '支付', label: '支付' },
                        { value: '用户', label: '用户' },
                        { value: '商品', label: '商品' }
                      ]}
                    />
                    <Button type="primary">刷新</Button>
                  </div>
                  <Table 
                    columns={extPointColumns} 
                    dataSource={extPoints} 
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                  />
                </Card>
              </>
            ) : (
              <>
                <Button 
                  type="link" 
                  onClick={() => setSelectedExtPoint(null)}
                  style={{ marginBottom: 16 }}
                >
                  ← 返回列表
                </Button>
                <Title level={4}>{selectedExtPoint.name} - 扩展实现列表</Title>
                <Card>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginBottom: 16 }}>
                      <div>
                        <Text strong>接口名称：</Text>
                        <Text>{selectedExtPoint.interfaceName}</Text>
                      </div>
                      <div>
                        <Text strong>版本：</Text>
                        <Text>{selectedExtPoint.version}</Text>
                      </div>
                      <div>
                        <Text strong>领域：</Text>
                        <Tag color="blue">{selectedExtPoint.domain}</Tag>
                      </div>
                      <div>
                        <Text strong>分类：</Text>
                        <Tag color="green">{selectedExtPoint.category}</Tag>
                      </div>
                    </div>
                    <div style={{ marginBottom: 16 }}>
                      <Text strong>描述：</Text>
                      <Text>{selectedExtPoint.description}</Text>
                    </div>
                    <Search
                      placeholder="搜索扩展实现"
                      onSearch={value => console.log(value)}
                      style={{ width: 250, marginRight: 16 }}
                    />
                    <Select
                      placeholder="按租户筛选"
                      style={{ width: 150, marginRight: 16 }}
                      options={[
                        { value: 'all', label: '全部' },
                        { value: 'DEFAULT', label: '默认' },
                        { value: 'TENANT_A', label: '租户A' },
                        { value: 'TENANT_B', label: '租户B' }
                      ]}
                    />
                    <Button type="primary">刷新</Button>
                  </div>
                  <Table 
                    columns={extensionColumns} 
                    dataSource={extensions} 
                    rowKey="id"
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                  />
                </Card>
              </>
            )}
          </div>
        </Content>
      </Layout>
    </Layout>
  )
}

export default App