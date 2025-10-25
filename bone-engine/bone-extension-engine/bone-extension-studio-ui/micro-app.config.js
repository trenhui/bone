/**
 * Bone Extension Studio UI 微应用配置
 * 这个文件定义了微应用的元数据，用于在 Bone 前端框架中注册和管理
 */

// 微应用配置文件 - 用于在Bone前端框架中注册和管理
module.exports = {
  // 应用标识
  appId: 'bone-extension-studio-ui',
  
  // 应用名称
  appName: '扩展管理平台',
  
  // 应用描述
  description: 'Bone扩展引擎的管理界面，用于配置和管理扩展点及扩展实现',
  
  // 应用入口
  entry: {
    // 开发环境入口
    dev: 'http://localhost:5173',
    // 生产环境入口
    prod: '//localhost:8080/' // 实际部署时需替换为真实地址
  },
  
  // 路由配置
  routes: [
    {
      path: '/extension',
      name: '扩展管理',
      component: 'ExtensionList',
      meta: {
        title: '扩展管理',
        permission: 'extension:view',
        icon: 'code'
      }
    },
    {
      path: '/extension/:id',
      name: '扩展详情',
      component: 'ExtensionDetail',
      meta: {
        title: '扩展详情',
        permission: 'extension:view',
        hideInMenu: true
      }
    }
  ],
  
  // 权限配置
  permissions: [
    {
      code: 'extension:view',
      name: '查看扩展',
      description: '查看扩展点和扩展实现列表'
    },
    {
      code: 'extension:create',
      name: '创建扩展',
      description: '创建新的扩展实现'
    },
    {
      code: 'extension:update',
      name: '修改扩展',
      description: '修改现有扩展实现'
    },
    {
      code: 'extension:delete',
      name: '删除扩展',
      description: '删除扩展实现'
    },
    {
      code: 'extension:toggle',
      name: '启用/禁用扩展',
      description: '启用或禁用扩展实现'
    }
  ],
  
  // 环境变量配置
  env: {
    // 开发环境配置
    development: {
      apiBaseUrl: '/api',
      mock: true
    },
    // 生产环境配置
    production: {
      apiBaseUrl: '/api',
      mock: false
    }
  },
  
  // 应用生命周期钩子
  lifecycle: {
    // 微应用加载前钩子
    beforeLoad: async (appInfo, appConfig) => {
      console.log(`[${appInfo.appId}] 微应用加载前钩子执行`);
      return true;
    },
    // 微应用挂载前钩子
    beforeMount: async (appInfo, appConfig) => {
      console.log(`[${appInfo.appId}] 微应用挂载前钩子执行`);
      return true;
    },
    // 微应用挂载后钩子
    afterMount: async (appInfo, appConfig) => {
      console.log(`[${appInfo.appId}] 微应用挂载后钩子执行`);
    },
    // 微应用卸载前钩子
    beforeUnmount: async (appInfo, appConfig) => {
      console.log(`[${appInfo.appId}] 微应用卸载前钩子执行`);
      return true;
    }
  },
  
  // 共享依赖配置
  shared: {
    // 是否启用自动共享
    autoShare: true,
    // 共享的库
    libraries: [
      'react',
      'react-dom',
      'antd'
    ]
  },
  
  // 安全策略配置
  security: {
    // 是否启用沙箱隔离
    sandbox: true,
    // 允许的外部资源
    allowedExternalResources: [
      // 这里可以配置允许加载的外部资源
    ],
    // 是否允许弹出窗口
    allowPopup: true,
    // 是否允许修改document.title
    allowTitleChange: true
  },
  
  // 数据预加载配置
  preload: {
    // 是否启用数据预加载
    enabled: true,
    // 预加载的API路径
    apis: ['/api/ext-points', '/api/extensions']
  },
  
  // 错误处理配置
  errorHandler: {
    // 是否上报错误
    reportError: true,
    // 错误上报URL
    reportUrl: '/api/error/report'
  },
  
  // 自定义数据
  customData: {
    // 任何自定义配置都可以放在这里
    defaultPageSize: 10,
    maxPageSize: 100
  }
};
