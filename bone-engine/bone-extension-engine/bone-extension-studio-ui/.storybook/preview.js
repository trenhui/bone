/**
 * Storybook 预览配置
 */
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import '../src/App.css';
import '../src/index.css';

// 全局参数配置
export const parameters = {
  actions: {
    argTypesRegex: '^on[A-Z].*',
  },
  controls: {
    matchers: {
      color: /(background|color)$/i,
      date: /Date$/,
    },
  },
  // 文档配置
  docs: {
    inlineStories: true,
  },
  // 背景色配置
  backgrounds: {
    default: 'light',
    values: [
      { name: 'light', value: '#ffffff' },
      { name: 'dark', value: '#1a1a1a' },
      { name: 'gray', value: '#f5f5f5' },
    ],
  },
  // 响应式视图配置
  viewport: {
    viewports: {
      mobile: { name: 'Mobile', styles: { width: '375px', height: '667px' } },
      tablet: { name: 'Tablet', styles: { width: '768px', height: '1024px' } },
      desktop: { name: 'Desktop', styles: { width: '1440px', height: '900px' } },
    },
  },
};

// 全局装饰器 - 包装所有组件
export const decorators = [
  // 路由上下文
  (Story) => ({
    wrapper: ({ children }) => (
      <BrowserRouter>
        <ConfigProvider
          theme={{
            token: {
              colorPrimary: '#1890ff',
              fontSizeBase: 14,
              borderRadius: 6,
            },
          }}
        >
          {children}
        </ConfigProvider>
      </BrowserRouter>
    ),
    children: Story,
  }),
];

// 全局加载微前端开发环境（仅在Storybook中使用）
if (typeof window !== 'undefined') {
  // 动态导入微前端开发工具包
  try {
    const { createMicroAppDevKit } = require('../src/utils/microAppDevKit');
    const devKit = createMicroAppDevKit('storybook', { mockMode: true });
    devKit.initialize();
    console.log('Storybook 微前端模拟环境已初始化');
  } catch (error) {
    console.warn('Storybook 微前端环境初始化失败:', error);
  }
}