import { useState, useEffect, useCallback } from 'react'
import { Layout, Menu, Typography, Card, Table, Tag, Space, Button, Input, Select, Result, Empty, Modal, notification } from 'antd'
import { HomeOutlined, CodeOutlined, SettingOutlined, AlertOutlined, GithubOutlined, ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons'
import axios from 'axios'
import './App.css' // 添加自定义样式文件

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
  // 状态管理
  const [collapsed, setCollapsed] = useState(false)
  const [activeKey, setActiveKey] = useState('1')
  const [extPoints, setExtPoints] = useState([])
  const [extensions, setExtensions] = useState([])
  const [selectedExtPoint, setSelectedExtPoint] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [filterDomain, setFilterDomain] = useState('all')
  const [filterTenant, setFilterTenant] = useState('all')
  const [confirmLoading, setConfirmLoading] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [currentExtension, setCurrentExtension] = useState(null)
  const [operationType, setOperationType] = useState('') // 'create', 'edit', 'toggleStatus'
  const [refreshKey, setRefreshKey] = useState(0) // 用于强制刷新表格

  // 配置axios实例
  const api = axios.create({
    baseURL: '/api',
    timeout: 10000,
    headers: {
      'Content-Type': 'application/json'
    }
  })

  // 请求拦截器
  api.interceptors.request.use(
    config => {
      // 可以在这里添加token等认证信息
      return config
    },
    error => {
      return Promise.reject(error)
    }
  )

  // 响应拦截器
  api.interceptors.response.use(
    response => response,
    error => {
      const message = error.response?.data?.message || '请求失败，请稍后重试'
      notification.error({
        message: '操作失败',
        description: message
      })
      return Promise.reject(error)
    }
  )

  // 加载扩展点数据
  const loadExtPoints = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      
      // 实际环境中这里会调用API
      // const response = await api.get('/ext-points', {
      //   params: {
      //     keyword: searchKeyword,
      //     domain: filterDomain === 'all' ? undefined : filterDomain
      //   }
      // })
      // setExtPoints(response.data)
      
      // 这里使用模拟数据并应用筛选
      let filteredData = [...mockExtPoints]
      if (searchKeyword) {
        filteredData = filteredData.filter(item => 
          item.name.includes(searchKeyword) || 
          item.description.includes(searchKeyword) ||
          item.interfaceName.includes(searchKeyword)
        )
      }
      if (filterDomain !== 'all') {
        filteredData = filteredData.filter(item => item.domain === filterDomain)
      }
      setExtPoints(filteredData)
    } catch (error) {
      console.error('加载扩展点失败:', error)
      setError('加载扩展点数据失败，请稍后重试')
      notification.error({
        message: '加载失败',
        description: '无法加载扩展点列表，请检查网络连接或稍后重试'
      })
    } finally {
      setLoading(false)
    }
  }, [searchKeyword, filterDomain])

  // 初始化加载数据
  useEffect(() => {
    loadExtPoints()
  }, [loadExtPoints])

  // 加载扩展实现数据
  const loadExtensions = useCallback(async (extPointId) => {
    try {
      setLoading(true)
      setError(null)
      
      // 实际环境中这里会调用API
      // const response = await api.get(`/ext-points/${extPointId}/extensions`, {
      //   params: {
      //     keyword: searchKeyword,
      //     tenantCode: filterTenant === 'all' ? undefined : filterTenant
      //   }
      // })
      // setExtensions(response.data)
      
      // 这里使用模拟数据并应用筛选
      let filtered = mockExtensions.filter(ext => ext.extPointId === extPointId)
      if (searchKeyword) {
        filtered = filtered.filter(item => 
          item.name.includes(searchKeyword) || 
          item.className.includes(searchKeyword) ||
          item.tenantCode.includes(searchKeyword)
        )
      }
      if (filterTenant !== 'all') {
        filtered = filtered.filter(item => item.tenantCode === filterTenant)
      }
      setExtensions(filtered)
    } catch (error) {
      console.error('加载扩展实现失败:', error)
      setError('加载扩展实现数据失败，请稍后重试')
      notification.error({
        message: '加载失败',
        description: '无法加载扩展实现列表，请检查网络连接或稍后重试'
      })
    } finally {
      setLoading(false)
    }
  }, [searchKeyword, filterTenant])

  // 菜单选择处理
  const handleMenuClick = (e) => {
    setActiveKey(e.key)
    if (e.key === '1') {
      setSelectedExtPoint(null)
    } else if (e.key === '2') {
      setSelectedExtPoint(null)
      // 可以在这里加载所有扩展实现
    }
  }

  // 处理扩展实现操作
  const handleExtensionOperation = (record, type) => {
    setCurrentExtension(record)
    setOperationType(type)
    
    if (type === 'toggleStatus') {
      // 直接执行状态切换
      handleToggleStatus(record)
    } else {
      // 打开编辑或创建模态框
      setModalVisible(true)
    }
  }

  // 切换扩展实现状态
  const handleToggleStatus = async (record) => {
    try {
      setConfirmLoading(true)
      const newStatus = record.status === 'enabled' ? 'disabled' : 'enabled'
      
      // 实际环境中调用API
      // await api.put(`/extensions/${record.id}/status`, { enabled: newStatus === 'enabled' })
      
      // 模拟更新本地数据
      const updatedExtensions = extensions.map(item => 
        item.id === record.id ? { ...item, status: newStatus } : item
      )
      setExtensions(updatedExtensions)
      
      notification.success({
        message: '操作成功',
        description: `扩展实现已${newStatus === 'enabled' ? '启用' : '禁用'}`
      })
    } catch (error) {
      console.error('切换状态失败:', error)
      notification.error({
        message: '操作失败',
        description: '无法切换扩展实现状态，请稍后重试'
      })
    } finally {
      setConfirmLoading(false)
    }
  }

  // 处理搜索和筛选
  const handleSearch = () => {
    if (selectedExtPoint) {
      loadExtensions(selectedExtPoint.id)
    } else {
      loadExtPoints()
    }
  }

  // 刷新数据
  const handleRefresh = () => {
    setRefreshKey(prev => prev + 1)
    if (selectedExtPoint) {
      loadExtensions(selectedExtPoint.id)
    } else {
      loadExtPoints()
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
      ellipsis: true,
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
      key: 'interfaceName',
      ellipsis: true
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
      fixed: 'right',
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
      key: 'name',
      ellipsis: true
    },
    {
      title: '实现类',
      dataIndex: 'className',
      key: 'className',
      ellipsis: true
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
      fixed: 'right',
      render: (_, record) => (
        <Space size="middle">
          <Button 
            type="link" 
            icon={<EditOutlined />}
            onClick={() => handleExtensionOperation(record, 'edit')}
          >
            编辑
          </Button>
          <Button 
            type="link" 
            danger={record.status === 'enabled'}
            icon={<DeleteOutlined />}
            loading={confirmLoading}
            onClick={() => handleExtensionOperation(record, 'toggleStatus')}
          >
            {record.status === 'enabled' ? '禁用' : '启用'}
          </Button>
        </Space>
      )
    }
  ]

  return (
    <Layout className="bone-layout">
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={value => setCollapsed(value)}
        width={250}
        breakpoint="lg"
        collapsedWidth={80}
        theme="dark"
      >
        <div className="bone-logo">
          <Typography.Title level={5} style={{ color: 'white', margin: 0, padding: '16px', textAlign: collapsed ? 'center' : 'left' }}>
            {collapsed ? 'Bone' : 'Bone 扩展引擎'}
          </Typography.Title>
        </div>
        <Menu 
          mode="inline" 
          selectedKeys={[activeKey]}
          onClick={handleMenuClick}
          style={{ height: '100%', borderRight: 0 }}
          theme="dark"
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
        <Header className="site-layout-background" style={{ padding: 0, height: 64, lineHeight: '64px', paddingRight: 24, textAlign: 'right', boxShadow: '0 2px 8px rgba(0, 0, 0, 0.1)' }}>
          <Button 
            type="text" 
            icon={<ReloadOutlined />}
            onClick={handleRefresh}
            style={{ marginRight: 16 }}
          >
            刷新
          </Button>
          <Text type="secondary" style={{ marginRight: 16 }}>Bone Extension Engine v1.0.0</Text>
          <GithubOutlined />
        </Header>
        <Content style={{ margin: '24px 16px 0', overflow: 'auto' }}>
          <div 
            className="site-layout-background" 
            style={{ padding: 24, minHeight: 'calc(100vh - 120px)', borderRadius: 8 }}
          >
            {error ? (
              <Result
                status="error"
                title="加载失败"
                subTitle={error}
                extra={[
                  <Button type="primary" key="reload" onClick={handleRefresh}>
                    重新加载
                  </Button>
                ]}
              />
            ) : !selectedExtPoint ? (
              <>
                <Title level={4}>扩展点列表</Title>
                <Card>
                  <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
                    <Search
                      placeholder="搜索扩展点（名称/描述/接口名）"
                      value={searchKeyword}
                      onChange={e => setSearchKeyword(e.target.value)}
                      onSearch={handleSearch}
                      enterButton
                      style={{ width: 300 }}
                    />
                    <Select
                      placeholder="按领域筛选"
                      value={filterDomain}
                      onChange={value => setFilterDomain(value)}
                      style={{ width: 150 }}
                      options={[
                        { value: 'all', label: '全部' },
                        { value: '支付', label: '支付' },
                        { value: '用户', label: '用户' },
                        { value: '商品', label: '商品' }
                      ]}
                    />
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      onClick={() => {
                        setOperationType('create');
                        setModalVisible(true);
                      }}
                    >
                      新建扩展点
                    </Button>
                  </div>
                  {extPoints.length === 0 && !loading ? (
                    <Empty description="暂无扩展点数据" />
                  ) : (
                    <Table 
                      columns={extPointColumns} 
                      dataSource={extPoints} 
                      rowKey="id"
                      loading={loading}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: 'max-content' }}
                      key={refreshKey}
                    />
                  )}
                </Card>
              </>
            ) : (
              <>
                <Button 
                  type="link" 
                  onClick={() => setSelectedExtPoint(null)}
                  style={{ marginBottom: 16 }}
                >
                  ← 返回扩展点列表
                </Button>
                <Title level={4}>{selectedExtPoint.name} - 扩展实现列表</Title>
                <Card>
                  <div style={{ marginBottom: 16 }}>
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, marginBottom: 16 }}>
                      <div>
                        <Text strong>接口名称：</Text>
                        <Text copyable>{selectedExtPoint.interfaceName}</Text>
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
                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'center' }}>
                      <Search
                        placeholder="搜索扩展实现（名称/类名/租户）"
                        value={searchKeyword}
                        onChange={e => setSearchKeyword(e.target.value)}
                        onSearch={handleSearch}
                        enterButton
                        style={{ width: 300 }}
                      />
                      <Select
                        placeholder="按租户筛选"
                        value={filterTenant}
                        onChange={value => setFilterTenant(value)}
                        style={{ width: 150 }}
                        options={[
                          { value: 'all', label: '全部' },
                          { value: 'DEFAULT', label: '默认' },
                          { value: 'TENANT_A', label: '租户A' },
                          { value: 'TENANT_B', label: '租户B' }
                        ]}
                      />
                      <Button 
                        type="primary" 
                        icon={<PlusOutlined />}
                        onClick={() => {
                          setCurrentExtension({ extPointId: selectedExtPoint.id });
                          setOperationType('create');
                          setModalVisible(true);
                        }}
                      >
                        新建扩展实现
                      </Button>
                    </div>
                  </div>
                  {extensions.length === 0 && !loading ? (
                    <Empty description="暂无扩展实现数据" />
                  ) : (
                    <Table 
                      columns={extensionColumns} 
                      dataSource={extensions} 
                      rowKey="id"
                      loading={loading}
                      pagination={{ pageSize: 10 }}
                      scroll={{ x: 'max-content' }}
                      key={refreshKey}
                    />
                  )}
                </Card>
              </>
            )}
          </div>
        </Content>
        <footer style={{ textAlign: 'center', padding: '16px', color: 'rgba(0, 0, 0, 0.45)', borderTop: '1px solid #f0f0f0' }}>
          Bone Extension Engine ©{new Date().getFullYear()} Created by Bone Team
        </footer>
      </Layout>
    </Layout>
  )
}

// 扩展实现编辑/创建模态框
const ExtensionModal = ({ visible, onCancel, record, operationType }) => {
  // 这里可以添加表单逻辑
  return (
    <Modal
      title={operationType === 'create' ? '创建扩展实现' : '编辑扩展实现'}
      open={visible}
      onCancel={onCancel}
      footer={[
        <Button key="cancel" onClick={onCancel}>
          取消
        </Button>,
        <Button key="submit" type="primary">
          {operationType === 'create' ? '创建' : '保存'}
        </Button>
      ]}
    >
      {/* 表单内容将在这里实现 */}
      <p>扩展实现编辑表单将在这里实现</p>
    </Modal>
  )
}

export default App