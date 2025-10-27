// 微应用配置文件
// 提供给主应用框架的集成配置
module.exports = {
  name: 'bone-extension-studio-ui',
  entry: '/',
  activeRule: '/extension-studio',
  // 应用信息配置
  appInfo: {
    title: 'Bone扩展引擎管理控制台',
    description: '用于管理和可视化展示扩展点和扩展实现的控制台',
    version: '1.0.0',
    author: 'Bone Engine Team'
  },
  // 路由配置
  routes: [
    {
      path: '/extension-studio',
      name: '扩展管理',
      component: 'ExtensionManagement',
      icon: 'code'
    },
    {
      path: '/extension-studio/ext-points',
      name: '扩展点管理',
      component: 'ExtPointManagement',
      parentPath: '/extension-studio'
    },
    {
      path: '/extension-studio/extensions',
      name: '扩展实现管理',
      component: 'ExtensionManagement',
      parentPath: '/extension-studio'
    },
    {
      path: '/extension-studio/stats',
      name: '统计分析',
      component: 'Statistics',
      parentPath: '/extension-studio'
    },
    {
      path: '/extension-studio/docs',
      name: '使用文档',
      component: 'Documentation',
      parentPath: '/extension-studio'
    }
  ],
  // 权限配置
  permissions: {
    view: ['ROLE_EXTENSION_VIEW'],
    edit: ['ROLE_EXTENSION_EDIT'],
    admin: ['ROLE_EXTENSION_ADMIN']
  },
  // 微应用间通信配置
  communication: {
    events: {
      listen: ['extension.created', 'extension.updated', 'extension.deleted', 'extPoint.updated'],
      emit: ['extension-studio.refresh', 'extension-studio.notify']
    },
    globalState: {
      sync: ['userInfo', 'tenantInfo', 'authToken']
    }
  },
  // 资源预加载配置
  preload: {
    js: [],
    css: [],
    api: ['/api/extensions/stats', '/api/ext-points']
  },
  // 性能优化配置
  performance: {
    lazyLoad: true,
    keepAlive: true,
    preloadComponents: ['ExtensionTable', 'ExtPointTable', 'StatisticsCard']
  },
  // 错误配置
  error: {
    fallbackComponent: 'ErrorFallback',
    retryable: true,
    maxRetries: 3
  },
  // 国际化配置
  i18n: {
    defaultLocale: 'zh-CN',
    locales: ['zh-CN', 'en-US'],
    resources: {
      'zh-CN': {
        name: '扩展引擎管理',
        description: 'Bone扩展引擎可视化管理控制台'
      },
      'en-US': {
        name: 'Extension Engine Management',
        description: 'Bone Extension Engine Visual Management Console'
      }
    }
  },
  // 注解配置集成
  annotationConfig: true
};
