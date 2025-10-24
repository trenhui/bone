import { useState, useEffect, useCallback, useMemo } from 'react'
import { Layout, Menu, Typography, Card, Table, Tag, Space, Button, Input, Select, Result, Empty, Modal, notification, Row, Col, Popconfirm, Spin, Badge, Popover, Form, InputNumber } from 'antd'
import { HomeOutlined, CodeOutlined, SettingOutlined, AlertOutlined, GithubOutlined, ReloadOutlined, PlusOutlined, EditOutlined, DeleteOutlined, FilterOutlined, SyncOutlined, SearchOutlined } from '@ant-design/icons'
import axios from 'axios'
import './App.css' // 添加自定义样式文件

const { Header, Sider, Content } = Layout
const { Title, Text } = Typography
const { Search } = Input
const { Option } = Select
const { Item } = Form

// 创建axios实例
const api = axios.create({
  baseURL: '/api',
  timeout: 15000, // 增加超时时间
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
  response => {
    return response.data
  },
  error => {
    console.error('API请求错误:', error)
    return Promise.reject(error)
  }
)

// 错误处理函数
export const handleApiError = (error, customMessage = '操作失败') => {
  let message = customMessage
  if (error.response) {
    // 服务器返回错误状态码
    message += `: ${error.response.data.message || '服务器错误'}`
  } else if (error.request) {
    // 请求已发出但没有收到响应
    message += ': 网络异常，请检查网络连接'
  } else {
    // 其他错误
    message += `: ${error.message}`
  }
  notification.error({ message: '操作失败', description: message })
};

// API调用包装函数
export const callApi = async (apiCall, loadingStateSetter, errorHandler = handleApiError) => {
  try {
    loadingStateSetter(true)
    const result = await apiCall()
    return result
  } catch (error) {
    errorHandler(error)
    throw error
  } finally {
    loadingStateSetter(false)
  }
};

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

// ErrorBoundary组件
class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = { hasError: false, error: null }
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error: error.message }
  }

  componentDidCatch(error, errorInfo) {
    console.error('组件错误:', error, errorInfo)
  }

  resetError = () => {
    this.setState({ hasError: false, error: null })
  };

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 24, textAlign: 'center' }}>
          <Result
            status="error"
            title="应用发生错误"
            subTitle={this.state.error}
            extra={[
              <Button type="primary" key="reload" onClick={this.resetError}>
                重新加载
              </Button>
            ]}
          />
        </div>
      )
    }

    return this.props.children
  }
}

// 数据缓存自定义Hook
const useDataCache = (initialData = []) => {
  const [data, setData] = useState(initialData)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // 获取数据
  const fetchData = useCallback(async (fetchFn) => {
    try {
      setLoading(true)
      setError(null)
      const result = await fetchFn()
      setData(result)
      return result
    } catch (err) {
      setError(err.message || '获取数据失败')
      throw err
    } finally {
      setLoading(false)
    }
  }, [])

  // 更新单个数据项
  const updateItem = useCallback((id, updates) => {
    setData(prev => prev.map(item => 
      item.id === id ? { ...item, ...updates } : item
    ))
  }, [])

  // 添加新数据项
  const addItem = useCallback((newItem) => {
    setData(prev => [...prev, newItem])
  }, [])

  // 删除数据项
  const removeItem = useCallback((id) => {
    setData(prev => prev.filter(item => item.id !== id))
  }, [])

  return {
    data,
    loading,
    error,
    fetchData,
    updateItem,
    addItem,
    removeItem,
    setData
  }
}

// 主应用组件
const App = () => {
  // 状态管理
  const [collapsed, setCollapsed] = useState(false)
  const [activeKey, setActiveKey] = useState('1')
  const [selectedExtPoint, setSelectedExtPoint] = useState(null)
  const [searchKeyword, setSearchKeyword] = useState('')
  const [filterDomain, setFilterDomain] = useState('all')
  const [filterTenant, setFilterTenant] = useState('all')
  const [refreshKey, setRefreshKey] = useState(0)
  const [statsVisible, setStatsVisible] = useState(false)
  const [modalVisible, setModalVisible] = useState(false)
  const [modalLoading, setModalLoading] = useState(false)
  const [operationType, setOperationType] = useState(null) // create or edit
  const [currentExtension, setCurrentExtension] = useState(null)
  const [validationErrors, setValidationErrors] = useState({})
  
  // 创建表单实例
  const [form] = Form.useForm()

  // 使用缓存管理扩展点数据
  const { 
    data: extPoints, 
    loading: extPointsLoading, 
    error: extPointsError, 
    setData: setExtPoints 
  } = useDataCache(mockExtPoints)

  // 使用缓存管理扩展实现数据
  const { 
    data: extensions, 
    loading: extensionsLoading, 
    error: extensionsError, 
    setData: setExtensions 
  } = useDataCache(mockExtensions)

  // 统计数据
  const [statsData, setStatsData] = useState({
    totalExtPoints: 0,
    totalExtensions: 0,
    enabledExtensions: 0,
    disabledExtensions: 0
  })

  // 加载统计数据
  const loadStats = useCallback(() => {
    const stats = {
      totalExtPoints: extPoints.length,
      totalExtensions: extensions.length,
      enabledExtensions: extensions.filter(e => e.status === 'enabled').length,
      disabledExtensions: extensions.filter(e => e.status === 'disabled').length
    }
    setStatsData(stats)
    setStatsVisible(true)
  }, [extPoints, extensions])

  // 刷新数据
  const handleRefresh = useCallback(() => {
    setRefreshKey(prev => prev + 1)
    loadStats()
  }, [loadStats])

  // 菜单点击处理
  const handleMenuClick = useCallback((e) => {
    setActiveKey(e.key)
    setSelectedExtPoint(null)
  }, [])

  // 搜索处理
  const handleSearch = useCallback((value) => {
    setSearchKeyword(value)
  }, [])

  // 选择扩展点
  const handleSelectExtPoint = useCallback((extPoint) => {
    setSelectedExtPoint(extPoint)
  }, [])

  // 返回扩展点列表
  const handleBackToList = useCallback(() => {
    setSelectedExtPoint(null)
  }, [])

  // 表单验证函数
  const validateForm = useCallback((values, type) => {
    const errors = {}
    
    if (type === 'extension') {
      // 验证名称
      if (!values.name || values.name.trim() === '') {
        errors.name = '请输入扩展实现名称'
      } else if (values.name.length > 100) {
        errors.name = '名称长度不能超过100个字符'
      } else {
        delete errors.name
      }

      // 验证实现类
      if (!values.className || values.className.trim() === '') {
        errors.className = '请输入实现类'
      } else {
        // 简单的Java类名格式验证
        const classNameRegex = /^[a-zA-Z_$][a-zA-Z0-9_$.]*$/;
        if (!classNameRegex.test(values.className)) {
          errors.className = '实现类名格式不正确'
        } else {
          delete errors.className
        }
      }

      // 验证优先级
      if (values.priority !== undefined && values.priority !== null) {
        if (values.priority < 0 || values.priority > 1000) {
          errors.priority = '优先级必须在0-1000之间'
        } else {
          delete errors.priority
        }
      } else {
        delete errors.priority
      }

      // 验证配置（如果有）
      if (values.config && values.config.trim() !== '') {
        try {
          JSON.parse(values.config)
          delete errors.config
        } catch (e) {
          errors.config = '配置必须是有效的JSON格式'
        }
      } else {
        delete errors.config
      }
    }

    setValidationErrors(errors)
    return Object.keys(errors).length === 0
  }, [])

  // 模态框表单字段
  const formItems = [
    {
      label: '扩展实现名称',
      name: 'name',
      required: true,
      tooltip: '扩展实现的唯一标识符',
      field: (
        <Input 
          placeholder="请输入扩展实现名称" 
          maxLength={100}
          showCount
          status={validationErrors.name ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.name
    },
    {
      label: '实现类',
      name: 'className',
      required: true,
      tooltip: '完整的Java类名',
      field: (
        <Input 
          placeholder="请输入完整的实现类路径" 
          status={validationErrors.className ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.className
    },
    {
      label: '租户代码',
      name: 'tenantCode',
      tooltip: '可选，用于多租户隔离',
      field: (
        <Input placeholder="请输入租户代码" maxLength={50} />
      )
    },
    {
      label: '业务域',
      name: 'bizCode',
      tooltip: '业务域标识',
      field: (
        <Input placeholder="请输入业务域" maxLength={50} />
      )
    },
    {
      label: '场景',
      name: 'scenario',
      tooltip: '使用场景标识',
      field: (
        <Input placeholder="请输入场景" maxLength={50} />
      )
    },
    {
      label: '优先级',
      name: 'priority',
      tooltip: '数值越大，优先级越高，范围0-1000',
      field: (
        <InputNumber 
          min={0} 
          max={1000} 
          placeholder="请输入优先级" 
          style={{ width: '100%' }}
          status={validationErrors.priority ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.priority
    },
    {
      label: '版本',
      name: 'version',
      tooltip: '扩展实现版本号',
      field: (
        <Input 
          placeholder="请输入版本号，如1.0.0" 
          maxLength={20} 
        />
      )
    },
    {
      label: '配置',
      name: 'config',
      tooltip: '扩展实现的配置信息，必须是有效的JSON格式',
      field: (
        <Input.TextArea 
          placeholder="请输入配置（JSON格式）" 
          rows={4} 
          showCount
          maxLength={500}
          status={validationErrors.config ? 'error' : undefined}
        />
      ),
      errorMsg: validationErrors.config
    },
    {
      label: '扩展点',
      name: 'extPointId',
      required: true,
      tooltip: '所属的扩展点',
      field: (
        <Select
          placeholder="请选择扩展点"
          style={{ width: '100%' }}
          options={extPoints.map(point => ({
            label: `${point.name} (${point.interfaceName})`,
            value: point.id
          }))}
        />
      )
    }
  ]

  // 处理表单提交
  const handleModalOk = async () => {
    const values = form.getFieldsValue();
    
    // 执行表单验证
    if (!validateForm(values, 'extension')) {
      notification.warning({
        message: '表单验证失败',
        description: '请检查并修正表单中的错误字段'
      });
      return;
    }
    
    setModalLoading(true);
    try {
      if (operationType === 'create') {
        // 模拟创建扩展实现
        const newExtension = {
          id: String(Date.now()),
          ...values,
          status: 'enabled',
          createTime: new Date().toLocaleString()
        };
        setExtensions(prev => [...prev, newExtension]);
        notification.success({ message: '创建成功', description: '扩展实现已成功创建' });
      } else {
        // 模拟更新扩展实现
        setExtensions(prev => prev.map(item => 
          item.id === currentExtension.id ? { ...item, ...values } : item
        ));
        notification.success({ message: '更新成功', description: '扩展实现已成功更新' });
      }
      handleModalCancel();
    } catch (error) {
      handleApiError(error, operationType === 'create' ? '创建失败' : '更新失败');
    } finally {
      setModalLoading(false);
    }
  };

  // 处理模态框取消
  const handleModalCancel = () => {
    setModalVisible(false);
    setCurrentExtension(null);
    setOperationType(null);
    setValidationErrors({});
    form.resetFields();
  };

  // 监听模态框状态变化，自动填充表单数据
  useEffect(() => {
    if (modalVisible && operationType === 'edit' && currentExtension) {
      form.setFieldsValue(currentExtension);
    } else if (modalVisible && selectedExtPoint) {
      form.setFieldsValue({ extPointId: selectedExtPoint.id });
    }
  }, [modalVisible, operationType, currentExtension, selectedExtPoint, form]);

  // 初始化加载统计数据
  useEffect(() => {
    loadStats();
  }, [loadStats]);

  // 扩展点表格列定义
  const extPointColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      render: (text, record) => (
        <a onClick={() => handleSelectExtPoint(record)}>{text}</a>
      )
    },
    {
      title: '接口名称',
      dataIndex: 'interfaceName',
      key: 'interfaceName',
      ellipsis: true
    },
    {
      title: '领域',
      dataIndex: 'domain',
      key: 'domain',
      render: domain => <Tag color="blue">{domain}</Tag>
    },
    {
      title: '分类',
      dataIndex: 'category',
      key: 'category',
      render: category => <Tag color="green">{category}</Tag>
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version'
    },
    {
      title: '扩展实现数量',
      dataIndex: 'implementationCount',
      key: 'implementationCount',
      sorter: (a, b) => a.implementationCount - b.implementationCount
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <Space size="middle">
          <Button 
            type="link" 
            onClick={() => handleSelectExtPoint(record)}
          >
            查看扩展实现
          </Button>
        </Space>
      )
    }
  ]

  // 扩展实现表格列定义
  const extensionColumns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name'
    },
    {
      title: '实现类',
      dataIndex: 'className',
      key: 'className',
      ellipsis: true
    },
    {
      title: '租户',
      dataIndex: 'tenantCode',
      key: 'tenantCode',
      render: tenantCode => <Tag>{tenantCode}</Tag>
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
      key: 'priority',
      sorter: (a, b) => a.priority - b.priority
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
      render: status => (
        <Tag color={status === 'enabled' ? 'green' : 'red'}>
          {status === 'enabled' ? '启用' : '禁用'}
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
          <Button 
            type="link" 
            onClick={() => {
              setCurrentExtension(record)
              setOperationType('edit')
              setModalVisible(true)
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确定要修改状态吗？"
            onConfirm={() => {
              // 模拟启用/禁用操作
              setExtensions(prev => prev.map(item => 
                item.id === record.id 
                  ? { ...item, status: item.status === 'enabled' ? 'disabled' : 'enabled' }
                  : item
              ))
              notification.success({
                message: '操作成功',
                description: `已成功${record.status === 'enabled' ? '禁用' : '启用'}该扩展实现`
              })
            }}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link">
              {record.status === 'enabled' ? '禁用' : '启用'}
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  // 统计模态框组件
  const StatsModal = ({ visible, onCancel, data }) => (
    <Modal
      title="系统统计"
      open={visible}
      onCancel={onCancel}
      footer={[
        <Button key="close" onClick={onCancel}>关闭</Button>
      ]}
      width={400}
      centered
    >
      <Card>
        <Row gutter={[16, 16]}>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#f0f5ff', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#1890ff' }}>{data.totalExtPoints}</Text>
              <div style={{ marginTop: 8 }}>总扩展点数量</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#e6f7ff', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#00bcd4' }}>{data.totalExtensions}</Text>
              <div style={{ marginTop: 8 }}>总扩展实现数量</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#f6ffed', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#52c41a' }}>{data.enabledExtensions}</Text>
              <div style={{ marginTop: 8 }}>已启用扩展实现</div>
            </div>
          </Col>
          <Col span={12}>
            <div style={{ textAlign: 'center', padding: 16, backgroundColor: '#fff1f0', borderRadius: 8 }}>
              <Text strong style={{ fontSize: 24, color: '#ff4d4f' }}>{data.disabledExtensions}</Text>
              <div style={{ marginTop: 8 }}>已禁用扩展实现</div>
            </div>
          </Col>
        </Row>
      </Card>
    </Modal>
  )

  // 过滤扩展点数据
  const filteredExtPoints = useMemo(() => {
    return extPoints.filter(point => {
      const matchesSearch = point.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          point.description.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          point.interfaceName.toLowerCase().includes(searchKeyword.toLowerCase())
      const matchesDomain = filterDomain === 'all' || point.domain === filterDomain
      return matchesSearch && matchesDomain
    })
  }, [extPoints, searchKeyword, filterDomain])

  // 过滤扩展实现数据
  const filteredExtensions = useMemo(() => {
    let filtered = extensions
    
    if (selectedExtPoint) {
      filtered = filtered.filter(e => e.extPointId === selectedExtPoint.id)
    }
    
    filtered = filtered.filter(e => {
      const matchesSearch = e.name.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          e.className.toLowerCase().includes(searchKeyword.toLowerCase()) ||
                          e.tenantCode.toLowerCase().includes(searchKeyword.toLowerCase())
      const matchesTenant = filterTenant === 'all' || e.tenantCode === filterTenant
      return matchesSearch && matchesTenant
    })
    
    return filtered
  }, [extensions, selectedExtPoint, searchKeyword, filterTenant])

  // 加载状态和错误处理
  const loading = extPointsLoading || extensionsLoading
  const error = extPointsError || extensionsError

  // 返回组件JSX
  return (
    <Layout className="bone-layout">
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={(value) => setCollapsed(value)}
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
                  </Button>,
                  <Button key="stats" onClick={loadStats}>
                    查看统计
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
                      onChange={(e) => setSearchKeyword(e.target.value)}
                      onSearch={handleSearch}
                      enterButton
                      style={{ width: 300 }}
                    />
                    <Select
                      placeholder="按领域筛选"
                      value={filterDomain}
                      onChange={setFilterDomain}
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
                        notification.info({ message: '功能开发中', description: '新建扩展点功能正在开发中' })
                      }}
                    >
                      新建扩展点
                    </Button>
                  </div>
                  {filteredExtPoints.length === 0 && !loading ? (
                    <Empty description="暂无扩展点数据" />
                  ) : (
                    <Table 
                      columns={extPointColumns} 
                      dataSource={filteredExtPoints} 
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
                  onClick={handleBackToList}
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
                        onChange={(e) => setSearchKeyword(e.target.value)}
                        onSearch={handleSearch}
                        enterButton
                        style={{ width: 300 }}
                      />
                      <Select
                        placeholder="按租户筛选"
                        value={filterTenant}
                        onChange={setFilterTenant}
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
                          setCurrentExtension(null)
                          setOperationType('create')
                          setModalVisible(true)
                        }}
                      >
                        新建扩展实现
                      </Button>
                    </div>
                  </div>
                  {filteredExtensions.length === 0 && !loading ? (
                    <Empty description="暂无扩展实现数据" />
                  ) : (
                    <Table 
                      columns={extensionColumns} 
                      dataSource={filteredExtensions} 
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
      <StatsModal
        visible={statsVisible}
        onCancel={() => setStatsVisible(false)}
        data={statsData}
      />
      <Modal
        title={operationType === 'create' ? '创建扩展实现' : '编辑扩展实现'}
        open={modalVisible}
        onOk={handleModalOk}
        onCancel={handleModalCancel}
        okButtonProps={{ loading: modalLoading }}
        cancelButtonProps={{ disabled: modalLoading }}
        width={600}
        destroyOnClose
        centered
      >
        <Form
          form={form}
          layout="vertical"
          onValuesChange={(_, values) => {
            validateForm(values, 'extension');
          }}
        >
          {formItems.map((item) => (
            <Form.Item
              key={item.name}
              label={item.label}
              required={item.required}
              tooltip={item.tooltip}
              validateStatus={item.errorMsg ? 'error' : undefined}
              help={item.errorMsg}
            >
              {item.field}
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </Layout>
  )
}

export default App