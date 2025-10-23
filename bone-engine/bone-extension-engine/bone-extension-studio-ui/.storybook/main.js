/**
 * Storybook 配置文件
 */
module.exports = {
  stories: ['../src/**/*.stories.mdx', '../src/**/*.stories.@(js|jsx|ts|tsx)'],
  addons: [
    '@storybook/addon-links',
    '@storybook/addon-essentials',
    '@storybook/addon-interactions',
    '@storybook/preset-create-react-app',
    '@storybook/addon-a11y',
    '@storybook/addon-styling',
    {
      name: '@storybook/addon-styling',
      options: {
        postCss: true,
      },
    },
  ],
  framework: {
    name: '@storybook/react-vite',
    options: {},
  },
  docs: {
    autodocs: 'tag',
  },
  staticDirs: ['../public'],
  // 配置Vite
  viteFinal: async (config, { configType }) => {
    // 根据环境添加不同配置
    if (configType === 'DEVELOPMENT') {
      // 开发环境配置
      config.server = {
        ...config.server,
        port: 6006,
      };
    }
    
    // 确保React插件正确配置
    const reactPlugin = config.plugins.find(p => p.name === 'vite:react');
    if (reactPlugin) {
      reactPlugin.options = {
        ...reactPlugin.options,
        jsxRuntime: 'automatic',
      };
    }
    
    return config;
  },
};