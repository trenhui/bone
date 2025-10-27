import React from 'react';
import { Layout, Button, Typography, Spin, Popconfirm, Empty, message, Card, Menu, Tabs } from 'antd';
import { SyncOutlined, PlusOutlined, EditOutlined, DeleteOutlined, CodeOutlined, DatabaseOutlined, LineChartOutlined, BookOutlined } from '@ant-design/icons';
import ApiService from './services/apiService';
import defaultErrorHandler from './utils/errorHandler';
import ResourceManager from './utils/resourceManager';
import extensionConfig from './config/extensionConfig';
import './App.css';

const { TabPane } = Tabs;

const { Header, Content } = Layout;
const { Title } = Typography;

class App extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      loading: false,
      extensions: [],
      extensionPoints: [],
      selectedExtension: null,
      selectedExtPoint: null,
      isModalVisible: false,
      filterKey: '',
      currentPage: 1,
      pageSize: extensionConfig.ui.table.defaultPageSize,
      total: 0,
      activeTab: 'extensions',
      statistics: null
    };
    
    // 使用默认的资源管理器实例
    this.componentResourceManager = ResourceManager;
    // 配置环境检查
    this.isDev = import.meta.env.MODE === 'development';
  }
  
  componentWillUnmount() {
    // 组件卸载时清理所有资源，防止内存泄漏
    if (this.componentResourceManager && this.componentResourceManager.cleanupAll) {
      this.componentResourceManager.cleanupAll();
    }
    
    if (process.env.NODE_ENV === 'development') {
      console.log('App组件已卸载，所有资源已清理');
    }
  }
  
  async componentDidMount() {
    // 使用组件级别的资源管理器加载数据
    this.loadExtensions();
    this.loadExtensionPoints();
    this.loadStatistics();
    
    // 监听扩展事件
    // communicator.on('extension.created', this.handleExtensionCreated);
    // communicator.on('extension.updated', this.handleExtensionUpdated);
    // communicator.on('extension.deleted', this.handleExtensionDeleted);
    // communicator.on('extPoint.updated', this.handleExtPointUpdated);
    
    // 注册组件资源管理器到全局统计（仅开发环境）
    if (process.env.NODE_ENV === 'development') {
      console.log('App组件已挂载，资源管理器已初始化');
    }
  }
  
  // 加载统计数据
  async loadStatistics() {
    try {
      const response = await ApiService.extension.getExtensionStatistics();
      this.setState({ statistics: response });
    } catch (error) {
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'loadStatistics'
      });
    }
  }
  
  // 加载扩展实现列表
  async loadExtensions() {
    try {
      this.setState({ loading: true });
      const { filterKey, currentPage, pageSize } = this.state;
      const params = {
        page: currentPage,
        pageSize: pageSize,
        keyword: filterKey
      };
      
      // 使用组件级别的资源管理器
      const response = await ApiService.extension.getExtensions(params);
      
      this.setState({
        extensions: response.list || [],
        total: response.total || 0
      });
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'loadExtensions',
        params: this.state
      });
      
      // 降级策略：使用模拟数据
      this.setState({
        extensions: this.getMockExtensions(),
        total: 5
      });
    } finally {
      this.setState({ loading: false });
    }
  }
  
  // 标签页切换处理
  handleTabChange = (activeTab) => {
    this.setState({ activeTab });
    
    // 根据切换的标签加载相应数据
    if (activeTab === 'extensions') {
      this.loadExtensions();
    } else if (activeTab === 'extPoints') {
      this.loadExtensionPoints();
    } else if (activeTab === 'stats') {
      this.loadStatistics();
    }
  }
  
  // 加载扩展点列表
  async loadExtensionPoints() {
    try {
      // 使用组件级别的资源管理器
      const response = await ApiService.extPoint.getExtPoints({});
      const { data } = response;
      
      this.setState({
        extensionPoints: Array.isArray(data) ? data : []
      });
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'loadExtensionPoints'
      });
      
      // 降级策略：使用模拟数据
      this.setState({
        extensionPoints: this.getMockExtensionPoints()
      });
    }
  }
  
  // 生成模拟扩展实现数据
  getMockExtensions() {
    return [
      {
        id: '1',
        name: '默认支付扩展',
        description: '系统默认的支付实现',
        extPointId: 'payment',
        implementationClass: 'com.example.extension.payment.DefaultPaymentImpl',
        priority: 100,
        status: 'enabled',
        config: '{}',
        creator: 'admin',
        createTime: '2024-01-01 10:00:00',
        updateTime: '2024-01-01 10:00:00'
      },
      {
        id: '2',
        name: '优惠券处理扩展',
        description: '处理优惠券逻辑的扩展',
        extPointId: 'coupon',
        implementationClass: 'com.example.extension.coupon.DefaultCouponImpl',
        priority: 90,
        status: 'enabled',
        config: '{"maxDiscount": 50}',
        creator: 'admin',
        createTime: '2024-01-02 14:30:00',
        updateTime: '2024-01-02 14:30:00'
      }
    ];
  }

  // 生成模拟扩展点数据
  getMockExtensionPoints() {
    return [
      {
        id: 'payment',
        name: '支付扩展点',
        description: '用于扩展不同的支付方式',
        interfaceClass: 'com.example.extension.payment.PaymentService',
        parameters: [{ name: 'orderId', type: 'String' }, { name: 'amount', type: 'BigDecimal' }],
        returnType: 'PaymentResult'
      },
      {
        id: 'coupon',
        name: '优惠券扩展点',
        description: '用于扩展优惠券处理逻辑',
        interfaceClass: 'com.example.extension.coupon.CouponService',
        parameters: [{ name: 'couponCode', type: 'String' }, { name: 'userId', type: 'String' }],
        returnType: 'CouponResult'
      }
    ];
  }

  // 处理扩展创建事件
  handleExtensionCreated = (data) => {
    this.setState(prevState => ({
      extensions: [...prevState.extensions, data],
      total: prevState.total + 1
    }));
    message.success(`扩展 "${data.name}" 已创建`);
  };

  // 处理扩展更新事件
  handleExtensionUpdated = (data) => {
    this.setState(prevState => ({
      extensions: prevState.extensions.map(item =>
        item.id === data.id ? { ...item, ...data } : item
      )
    }));
    message.success(`扩展 "${data.name}" 已更新`);
  };

  // 处理扩展删除事件
  handleExtensionDeleted = (id) => {
    this.setState(prevState => ({
      extensions: prevState.extensions.filter(item => item.id !== id),
      total: prevState.total - 1
    }));
    message.success('扩展已删除');
  };

  // 处理扩展创建
  async handleCreateExtension(values) {
    try {
      // 构建扩展数据
      const extensionData = {
        name: values.name,
        description: values.description,
        extPointId: values.extPointId,
        implementationClass: values.implementationClass,
        priority: values.priority || 0,
        status: values.status || 'disabled',
        config: values.config || '{}',
        creator: 'admin' // 实际应用中应从登录用户获取
      };
      
      // 使用组件级别的资源管理器
      const response = await ApiService.extension.createExtension(extensionData);
      const { data } = response;
      
      // 更新本地状态
      this.setState(prevState => ({
        extensions: [...prevState.extensions, data],
        total: prevState.total + 1,
        isModalVisible: false
      }));
      
      message.success('扩展创建成功');
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'handleCreateExtension',
        extensionData: values
      });
      
      message.error('创建失败，请稍后重试');
    }
  }

  // 处理更新扩展
  async handleUpdateExtension(values) {
    try {
      // 构建更新数据
      const updateData = {
        name: values.name,
        description: values.description,
        extPointId: values.extPointId,
        implementationClass: values.implementationClass,
        priority: values.priority || 0,
        status: values.status || 'disabled',
        config: values.config || '{}'
      };
      
      // 使用组件级别的资源管理器
      await ApiService.extension.updateExtension(this.state.selectedExtension.id, updateData);
      
      // 更新本地状态
      this.setState(prevState => ({
        extensions: prevState.extensions.map(item =>
          item.id === this.state.selectedExtension.id ? { ...item, ...updateData } : item
        ),
        isModalVisible: false,
        selectedExtension: null
      }));
      
      message.success('扩展更新成功');
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'handleUpdateExtension',
        extensionId: this.state.selectedExtension?.id,
        updateData: values
      });
      
      message.error('更新失败，请稍后重试');
    }
  }

  // 启用/禁用扩展
  async toggleExtensionStatus(record) {
    try {
      const newStatus = record.status === 'enabled' ? 'disabled' : 'enabled';
      
      // 使用组件级别的资源管理器
      await ApiService.extension.enableExtension(record.id, newStatus === 'enabled');
      
      // 更新本地状态
      this.setState(prevState => ({
        extensions: prevState.extensions.map(item =>
          item.id === record.id ? { ...item, status: newStatus } : item
        )
      }));
      
      message.success(`扩展 ${newStatus === 'enabled' ? '启用' : '禁用'} 成功`);
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'toggleExtensionStatus',
        extensionId: record.id,
        newStatus: record.status === 'enabled' ? 'disabled' : 'enabled'
      });
      
      message.error('操作失败，请稍后重试');
    }
  }

  // 删除扩展
  async deleteExtension(record) {
    try {
      // 使用组件级别的资源管理器
      await ApiService.extension.deleteExtension(record.id);
      
      // 更新本地状态
      this.setState(prevState => ({
        extensions: prevState.extensions.filter(item => item.id !== record.id),
        total: prevState.total - 1
      }));
      
      message.success('扩展删除成功');
    } catch (error) {
      // 使用默认错误处理器处理错误
      defaultErrorHandler.handleError(error, {
        component: 'App',
        operation: 'deleteExtension',
        extensionId: record.id,
        extensionInfo: { name: record.name, extPointId: record.extPointId }
      });
      
      message.error('删除失败，请稍后重试');
    }
  }
  
  // 处理搜索
  handleSearch = () => {
    // 取消正在进行的搜索请求
    this.componentResourceManager.abortAllOperations('get_extensions');
    
    this.setState({ currentPage: 1 }, () => {
      this.loadExtensions();
    });
  };
  
  // 处理页面变化
  handlePageChange = (page, pageSize) => {
    // 取消正在进行的分页请求
    this.componentResourceManager.abortAllOperations('get_extensions');
    
    this.setState({ currentPage: page, pageSize }, () => {
      this.loadExtensions();
    });
  };
  
  // 刷新数据
  handleRefresh = () => {
    // 取消所有正在进行的数据请求
    this.componentResourceManager.abortAllOperations('get_extensions');
    this.componentResourceManager.abortAllOperations('get_extension_points');
    
    this.loadExtensions();
    this.loadExtensionPoints();
  };

  // 渲染注解最佳实践面板
  renderAnnotationBestPractices() {
    const { bestPractices } = extensionConfig.bestPractices;
    
    return (
      <div className="best-practices-container">
        <Title level={4}>注解使用最佳实践</Title>
        
        <Card title="扩展点定义最佳实践" style={{ marginBottom: 16 }}>
          <ul>
            {bestPractices.usageRecommendations.extPointDefinition.map((item, index) => (
              <li key={index}>{item}</li>
            ))}
          </ul>
        </Card>
        
        <Card title="扩展实现最佳实践" style={{ marginBottom: 16 }}>
          <ul>
            {bestPractices.usageRecommendations.extensionImplementation.map((item, index) => (
              <li key={index}>{item}</li>
            ))}
          </ul>
        </Card>
        
        <Card title="常见问题解答" style={{ marginBottom: 16 }}>
          {bestPractices.faqs.map((faq, index) => (
            <div key={index} className="faq-item">
              <h5>{faq.question}</h5>
              <p>{faq.answer}</p>
            </div>
          ))}
        </Card>
      </div>
    );
  }
  
  // 统计卡片组件
  StatisticCards = ({ statistics }) => {
    return (
      <div className="statistic-cards">
        <Card className="stat-card">
          <Title level={3}>{statistics?.totalExtensions || 0}</Title>
          <p>总扩展实现数</p>
        </Card>
        <Card className="stat-card">
          <Title level={3}>{statistics?.enabledExtensions || 0}</Title>
          <p>启用的扩展</p>
        </Card>
        <Card className="stat-card">
          <Title level={3}>{statistics?.totalExtensionPoints || 0}</Title>
          <p>扩展点总数</p>
        </Card>
        <Card className="stat-card">
          <Title level={3}>{statistics?.mostUsedExtensionPoint || '暂无'}</Title>
          <p>最活跃扩展点</p>
        </Card>
      </div>
    );
  };

  render() {
    const { loading, extensions, extensionPoints, filterKey, currentPage, pageSize, total, activeTab, statistics } = this.state;
    
    return (
      <Layout className="app-layout">
        <Header className="app-header">
          <div className="header-content">
            <h1>Bone 扩展引擎管理控制台</h1>
            <Button 
              type="primary" 
              icon={<SyncOutlined />} 
              onClick={this.handleRefresh}
              loading={loading}
            >
              刷新数据
            </Button>
          </div>
        </Header>
        <Content className="app-content">
          <Tabs 
            activeKey={activeTab} 
            onChange={this.handleTabChange}
            style={{ marginBottom: 16 }}
            items={[
              {
                key: 'extensions',
                label: '扩展实现',
                icon: <DatabaseOutlined />
              },
              {
                key: 'extPoints',
                label: '扩展点',
                icon: <CodeOutlined />
              },
              {
                key: 'stats',
                label: '统计分析',
                icon: <LineChartOutlined />
              },
              {
                key: 'docs',
                label: '使用文档',
                icon: <BookOutlined />
              }
            ]}
          >
            {activeTab === 'extensions' && (
              <Card className="extension-card">
                <div className="card-header">
                  <Title level={4}>扩展实现管理</Title>
                  <div className="header-actions">
                    <Button 
                      type="primary" 
                      icon={<PlusOutlined />}
                      onClick={() => this.setState({ selectedExtension: null, isModalVisible: true })}
                    >
                      新建扩展
                    </Button>
                  </div>
                </div>
                
                <div className="search-bar">
                  <input
                    type="text"
                    placeholder="输入扩展名称搜索..."
                    value={filterKey}
                    onChange={(e) => this.setState({ filterKey: e.target.value })}
                    onKeyPress={(e) => e.key === 'Enter' && this.handleSearch()}
                  />
                  <Button type="primary" onClick={this.handleSearch}>搜索</Button>
                </div>
                
                {loading ? (
                  <div className="loading-container">
                    <Spin size="large" tip="加载中..." />
                  </div>
                ) : (
                  <div className="extension-list">
                    {extensions.length > 0 ? (
                      <div className="list-content">
                        <div className="table-header">
                          <div className="table-cell">名称</div>
                          <div className="table-cell">描述</div>
                          <div className="table-cell">扩展点</div>
                          <div className="table-cell">实现类</div>
                          <div className="table-cell">优先级</div>
                          <div className="table-cell">状态</div>
                          <div className="table-cell">操作</div>
                        </div>
                        {extensions.map(extension => (
                          <div key={extension.id} className="table-row">
                            <div className="table-cell">{extension.name}</div>
                            <div className="table-cell">{extension.description}</div>
                            <div className="table-cell">
                              {extensionPoints.find(ep => ep.id === extension.extPointId)?.name || extension.extPointId}
                            </div>
                            <div className="table-cell">{extension.implementationClass}</div>
                            <div className="table-cell">{extension.priority}</div>
                            <div className="table-cell">
                              <span className={`status-badge ${extension.status}`}>
                                {extension.status === 'enabled' ? '启用' : '禁用'}
                              </span>
                            </div>
                            <div className="table-cell action-buttons">
                              <Button 
                                type="text" 
                                icon={<EditOutlined />}
                                onClick={() => this.setState({ selectedExtension: extension, isModalVisible: true })}
                              />
                              <Popconfirm
                                title="确定要删除这个扩展吗？"
                                onConfirm={() => this.deleteExtension(extension)}
                                okText="确定"
                                cancelText="取消"
                              >
                                <Button type="text" danger icon={<DeleteOutlined />} />
                              </Popconfirm>
                              <Button
                                type={extension.status === 'enabled' ? 'text' : 'primary'}
                                onClick={() => this.toggleExtensionStatus(extension)}
                              >
                                {extension.status === 'enabled' ? '禁用' : '启用'}
                              </Button>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <Empty description="暂无扩展实现数据" />
                    )}
                    
                    {extensions.length > 0 && (
                      <div className="pagination">
                        <div className="total-info">共 {total} 条数据</div>
                        <div className="pagination-controls">
                          <Button 
                            disabled={currentPage === 1}
                            onClick={() => this.handlePageChange(currentPage - 1, pageSize)}
                          >
                            上一页
                          </Button>
                          <span className="page-info">第 {currentPage} 页</span>
                          <Button 
                            disabled={currentPage * pageSize >= total}
                            onClick={() => this.handlePageChange(currentPage + 1, pageSize)}
                          >
                            下一页
                          </Button>
                        </div>
                      </div>
                    )}
                  </div>
                )}
              </Card>
            )}
            
            {activeTab === 'extPoints' && (
              <Card className="extension-point-card">
                <Title level={4}>扩展点管理</Title>
                {loading ? (
                  <div className="loading-container">
                    <Spin size="large" tip="加载中..." />
                  </div>
                ) : (
                  <div className="extension-point-list">
                    {extensionPoints.length > 0 ? (
                      <div className="list-content">
                        <div className="table-header">
                          <div className="table-cell">ID</div>
                          <div className="table-cell">名称</div>
                          <div className="table-cell">描述</div>
                          <div className="table-cell">接口类</div>
                          <div className="table-cell">参数</div>
                          <div className="table-cell">返回类型</div>
                          <div className="table-cell">使用次数</div>
                        </div>
                        {extensionPoints.map(extPoint => (
                          <div key={extPoint.id} className="table-row">
                            <div className="table-cell">{extPoint.id}</div>
                            <div className="table-cell">{extPoint.name}</div>
                            <div className="table-cell">{extPoint.description}</div>
                            <div className="table-cell">{extPoint.interfaceClass}</div>
                            <div className="table-cell">
                              {Array.isArray(extPoint.parameters) ? 
                                extPoint.parameters.map(p => `${p.name}: ${p.type}`).join(', ') : 
                                extPoint.parameters}
                            </div>
                            <div className="table-cell">{extPoint.returnType}</div>
                            <div className="table-cell">
                              {extensions.filter(e => e.extPointId === extPoint.id).length}
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <Empty description="暂无扩展点数据" />
                    )}
                  </div>
                )}
              </Card>
            )}
            
            {activeTab === 'stats' && (
              <div className="statistics-container">
                {this.StatisticCards({ statistics })}
                
                <Card className="chart-card">
                  <Title level={4}>扩展实现活跃度</Title>
                  <div className="chart-placeholder">
                    <p>图表展示区域</p>
                    <p>扩展实现按业务域分布</p>
                  </div>
                </Card>
              </div>
            )}
            
            {activeTab === 'docs' && (
              <div className="documentation-container">
                {this.renderAnnotationBestPractices()}
              </div>
            )}
          </Tabs>
          
          {/* 资源使用统计（仅开发环境显示） */}
          {process.env.NODE_ENV === 'development' && (
            <Card className="resource-stats-card">
              <Title level={5}>资源使用统计</Title>
              <pre>
                {JSON.stringify(this.componentResourceManager.getResourceStats(), null, 2)}
              </pre>
            </Card>
          )}
        </Content>
      </Layout>
    );
  }
}

export default App;
