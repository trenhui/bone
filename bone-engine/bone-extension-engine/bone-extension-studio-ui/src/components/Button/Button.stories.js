import React from 'react';
import Button from './Button';
import { createMicroAppDevKit } from '../../utils/microAppDevKit';

// 初始化微前端开发环境（仅在Storybook中）
let devKit;
if (typeof window !== 'undefined' && !window.__MICRO_DEV_KIT__) {
  devKit = createMicroAppDevKit('button-storybook', { mockMode: true });
  devKit.initialize();
  window.__MICRO_DEV_KIT__ = devKit;
}

// 定义故事的元数据
export default {
  title: 'Components/Button',
  component: Button,
  parameters: {
    // 文档配置
    docs: {
      description: {
        component: '一个支持微前端环境的按钮组件，提供多种类型和尺寸选择。'
      }
    },
    // 控制参数配置
    controls: {
      exclude: ['style', 'className', 'ref'],
      sort: 'requiredFirst'
    }
  },
  // 定义argTypes以自定义控制
  argTypes: {
    type: {
      control: {
        type: 'select',
        options: ['default', 'primary', 'success', 'warning', 'error']
      },
      description: '按钮类型'
    },
    size: {
      control: {
        type: 'select',
        options: ['small', 'medium', 'large']
      },
      description: '按钮尺寸'
    },
    onClick: {
      action: 'clicked',
      description: '点击事件处理函数'
    },
    disabled: {
      description: '禁用状态'
    },
    loading: {
      description: '加载状态'
    }
  }
};

// 创建模板
export const Template = (args) => <Button {...args} />;

// 默认按钮
export const Default = Template.bind({});
Default.args = {
  children: '默认按钮',
  type: 'default',
  size: 'medium'
};

// 主要按钮
export const Primary = Template.bind({});
Primary.args = {
  children: '主要按钮',
  type: 'primary',
  size: 'medium'
};

// 成功按钮
export const Success = Template.bind({});
Success.args = {
  children: '成功按钮',
  type: 'success',
  size: 'medium'
};

// 警告按钮
export const Warning = Template.bind({});
Warning.args = {
  children: '警告按钮',
  type: 'warning',
  size: 'medium'
};

// 错误按钮
export const Error = Template.bind({});
Error.args = {
  children: '错误按钮',
  type: 'error',
  size: 'medium'
};

// 尺寸变体
export const Sizes = (args) => (
  <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
    <Button {...args} size="small" children="小按钮" />
    <Button {...args} size="medium" children="中按钮" />
    <Button {...args} size="large" children="大按钮" />
  </div>
);
Sizes.args = {
  type: 'primary'
};

// 状态变体
export const States = (args) => (
  <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
    <Button {...args} disabled children="禁用按钮" />
    <Button {...args} loading children="加载中" />
  </div>
);
States.args = {
  type: 'primary',
  size: 'medium'
};

// 微前端环境下的按钮
export const MicroFrontendContext = (args) => {
  React.useEffect(() => {
    // 模拟微前端环境
    window.__MICRO_DEV_ENV__ = true;
    
    // 设置一些全局状态
    if (devKit) {
      devKit.setGlobalState('microAppContext', {
        user: { id: 'user123', name: '测试用户' },
        appConfig: { theme: 'light', locale: 'zh-CN' }
      });
      
      // 监听消息
      devKit.onMessage('button:clicked', (data) => {
        console.log('Button clicked in micro frontend context:', data);
      });
    }
    
    return () => {
      window.__MICRO_DEV_ENV__ = false;
    };
  }, []);
  
  return (
    <div style={{ padding: '16px', border: '1px dashed #40a9ff', borderRadius: '8px' }}>
      <p style={{ marginBottom: '12px', color: '#40a9ff' }}>微前端环境模拟中</p>
      <Button {...args} children="微前端按钮" />
    </div>
  );
};
MicroFrontendContext.args = {
  type: 'primary',
  size: 'medium'
};

// 按钮组
export const ButtonGroup = (args) => (
  <div style={{ display: 'flex', gap: '0', borderRadius: '6px', overflow: 'hidden' }}>
    <Button {...args} children="左" style={{ borderRight: 'none', borderRadius: '6px 0 0 6px' }} />
    <Button {...args} children="中" style={{ borderRight: 'none', borderLeft: 'none', borderRadius: '0' }} />
    <Button {...args} children="右" style={{ borderLeft: 'none', borderRadius: '0 6px 6px 0' }} />
  </div>
);
ButtonGroup.args = {
  type: 'primary',
  size: 'medium'
};