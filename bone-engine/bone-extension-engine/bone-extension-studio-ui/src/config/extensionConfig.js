/**
 * Bone扩展引擎配置
 * 提供完整的扩展点和扩展实现管理配置，支持可视化展示
 */

// API服务配置
export const apiConfig = {
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  },
  // 模拟环境配置
  mockEnabled: import.meta.env.MODE === 'development' && import.meta.env.VITE_USE_MOCK === 'true',
  // API路由配置
  endpoints: {
    // 扩展点相关
    extPoints: '/ext-points',
    extPointById: (id) => `/ext-points/${id}`,
    // 扩展实现相关
    extensions: '/extensions',
    extensionById: (id) => `/extensions/${id}`,
    extensionStatus: (id) => `/extensions/${id}/status`,
    extensionPriority: (id) => `/extensions/${id}/priority`,
    extensionValidate: (id) => `/extensions/${id}/validate`,
    extensionResetStatistics: (id) => `/extensions/${id}/statistics/reset`,
    // 扫描和注册
    scanExtensions: '/extensions/scan',
    // 统计信息
    extensionStatistics: '/extensions/statistics'
  }
};

// UI配置
export const uiConfig = {
  // 表格配置
  table: {
    defaultPageSize: 10,
    pageSizeOptions: [10, 20, 50, 100],
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total, range) => `${range[0]}-${range[1]} 共 ${total} 条`,
    // 扩展点表格列配置
    extPointColumns: [
      { title: '扩展点名称', dataIndex: 'name', key: 'name', ellipsis: true },
      { title: '接口名称', dataIndex: 'interfaceName', key: 'interfaceName', ellipsis: true },
      { title: '领域', dataIndex: 'domain', key: 'domain', filters: true },
      { title: '分类', dataIndex: 'category', key: 'category', filters: true },
      { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
      { title: '版本', dataIndex: 'version', key: 'version' },
      { title: '状态', dataIndex: 'enabled', key: 'enabled', render: (enabled) => (enabled ? '启用' : '禁用') },
      { title: '扩展实现数', dataIndex: 'extensionCount', key: 'extensionCount' },
      { title: '操作', key: 'action', width: 120, fixed: 'right' }
    ],
    // 扩展实现表格列配置
    extensionColumns: [
      { title: '扩展名称', dataIndex: 'name', key: 'name', ellipsis: true },
      { title: '租户代码', dataIndex: 'tenantCode', key: 'tenantCode', filters: true },
      { title: '业务域', dataIndex: 'bizCode', key: 'bizCode', filters: true },
      { title: '场景', dataIndex: 'scenario', key: 'scenario', filters: true },
      { title: '优先级', dataIndex: 'priority', key: 'priority', sorter: true },
      { title: '版本', dataIndex: 'version', key: 'version' },
      { title: '状态', dataIndex: 'enabled', key: 'enabled', render: (enabled) => (enabled ? '启用' : '禁用') },
      { title: '操作', key: 'action', width: 180, fixed: 'right' }
    ]
  },
  // 表单配置
  form: {
    layout: 'vertical',
    labelAlign: 'left'
  },
  // 搜索配置
  search: {
    defaultFilters: [],
    maxFilters: 5
  },
  // 主题配置
  theme: {
    primaryColor: '#1890ff',
    successColor: '#52c41a',
    warningColor: '#faad14',
    errorColor: '#f5222d'
  }
};

// 注解使用规范配置
export const annotationConfig = {
  // ExtPoint注解使用规范
  extPoint: {
    // 必填字段
    requiredFields: ['name', 'description'],
    // 推荐字段
    recommendedFields: ['version'],
    // 默认值
    defaults: {
      version: '1.0.0',
      transactional: false
    },
    // 验证规则
    validationRules: {
      name: { minLength: 2, maxLength: 100 },
      description: { minLength: 10, maxLength: 500 },
      version: /^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/ // 简单的语义化版本号验证
    }
  },
  // Extension注解使用规范
  extension: {
    // 必填字段
    requiredFields: [],
    // 推荐字段
    recommendedFields: ['name', 'description', 'version'],
    // 默认值
    defaults: {
      version: '1.0.0',
      priority: 100,
      enabled: true,
      isDefault: false
    },
    // 验证规则
    validationRules: {
      name: { minLength: 2, maxLength: 100 },
      description: { maxLength: 500 },
      priority: { min: 0, max: 1000 },
      version: /^\d+\.\d+\.\d+(-[a-zA-Z0-9]+)?$/,
      trafficRate: { min: 0, max: 100 }
    }
  },
  // ExtPointDoc注解使用规范
  extPointDoc: {
    // 必填字段
    requiredFields: ['title', 'description', 'domain', 'category'],
    // 推荐字段
    recommendedFields: ['usage', 'bestPractices', 'params', 'returnInfo'],
    // 默认值
    defaults: {},
    // 验证规则
    validationRules: {
      title: { minLength: 5, maxLength: 200 },
      description: { minLength: 20, maxLength: 1000 },
      domain: { minLength: 2, maxLength: 50 },
      category: { minLength: 2, maxLength: 50 }
    }
  },
  // ExtensionDoc注解使用规范
  extensionDoc: {
    // 必填字段
    requiredFields: ['title', 'description'],
    // 推荐字段
    recommendedFields: ['applicableScenarios', 'implementationDetails'],
    // 默认值
    defaults: {},
    // 验证规则
    validationRules: {
      title: { minLength: 5, maxLength: 200 },
      description: { minLength: 20, maxLength: 1000 }
    }
  }
};

// 资源管理配置
export const resourceConfig = {
  // 资源生命周期配置
  resourceLifetime: {
    default: 300000, // 默认5分钟
    apiRequest: 60000, // API请求1分钟
    cachedData: 3600000, // 缓存数据1小时
    uiComponent: 120000 // UI组件2分钟
  },
  // 资源池配置
  resourcePool: {
    maxConcurrentRequests: 10,
    maxCacheSize: 1000
  }
};

// 错误处理配置
export const errorConfig = {
  // 错误分类配置
  errorTypes: {
    apiError: 'api_error',
    networkError: 'network_error',
    validationError: 'validation_error',
    businessError: 'business_error',
    unknownError: 'unknown_error'
  },
  // 错误级别配置
  errorLevels: {
    info: 'info',
    warning: 'warning',
    error: 'error',
    critical: 'critical'
  },
  // 错误处理策略
  errorStrategies: {
    apiError: 'retry',
    networkError: 'offlineMode',
    validationError: 'feedback',
    businessError: 'notification',
    unknownError: 'report'
  },
  // 重试配置
  retryConfig: {
    maxRetries: 3,
    retryDelay: 1000,
    exponentialBackoff: true
  }
};

// 注解最佳实践配置
export const bestPracticesConfig = {
  // 注解使用建议
  usageRecommendations: {
    // 扩展点定义最佳实践
    extPointDefinition: [
      '始终使用@ExtPoint和@ExtPointDoc为扩展点提供完整信息',
      '@ExtPoint用于运行时配置，专注于扩展点注册和行为控制',
      '@ExtPointDoc用于编译时文档生成，提供详细的使用指导',
      '为扩展点定义清晰的领域和分类',
      '合理设置事务属性',
      '避免在扩展点接口中包含业务逻辑实现'
    ],
    // 扩展实现最佳实践
    extensionImplementation: [
      '为每个扩展实现添加@Extension和@ExtensionDoc',
      '@Extension用于定义路由匹配条件',
      '@ExtensionDoc用于描述实现细节和适用场景',
      '设置合理的优先级，确保扩展执行顺序正确',
      '避免在扩展实现中使用硬编码的业务规则',
      '实现幂等性以确保重试安全',
      '考虑性能影响，避免长时间运行的操作'
    ],
    // 路由策略最佳实践
    routingStrategy: [
      '优先使用精确匹配条件（tenantCode、bizCode等）',
      '使用条件表达式进行复杂匹配',
      '合理使用优先级机制',
      '为默认场景提供默认实现',
      '避免重叠的路由规则',
      '使用多租户支持机制隔离不同租户的扩展'
    ],
    // 版本管理最佳实践
    versionManagement: [
      '使用语义化版本号',
      '明确标记废弃的扩展点和实现',
      '提供迁移路径',
      '避免破坏性变更',
      '版本升级时保持向后兼容性'
    ]
  },
  // 常见问题与解决方案
  faqs: [
    {
      question: '如何为扩展点定义多个实现？',
      answer: '为每个实现添加@Extension注解，并设置不同的路由条件（如tenantCode、bizCode等）'
    },
    {
      question: '如何控制扩展的执行顺序？',
      answer: '通过设置priority属性，数值越小优先级越高'
    },
    {
      question: '如何实现默认扩展？',
      answer: '设置isDefault=true，在没有其他匹配的扩展时会使用默认实现'
    },
    {
      question: '如何进行灰度发布？',
      answer: '使用trafficRate属性控制流量比例，如trafficRate=20表示只有20%的请求会使用该扩展'
    },
    {
      question: '如何基于复杂条件选择扩展？',
      answer: '使用condition属性编写Spring EL表达式实现复杂条件匹配'
    }
  ]
};

// 导出默认配置对象
export default {
  api: apiConfig,
  ui: uiConfig,
  annotation: annotationConfig,
  resource: resourceConfig,
  error: errorConfig,
  bestPractices: bestPracticesConfig
};