import React, { useState, useEffect } from 'react';
import { ProLayout, SettingDrawer } from '@ant-design/pro-components';
import { UserOutlined, 
  HomeOutlined, 
  SettingOutlined, 
  DatabaseOutlined, 
  LayoutOutlined,
  CodeOutlined,
  MenuUnfoldOutlined,
  MenuFoldOutlined,
  LogoutOutlined,
  BellOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ThunderboltOutlined,
  MoonOutlined,
  SunOutlined,
  ToolOutlined,
  DownOutlined,
  EyeOutlined,
  ShoppingCartOutlined,
  TransactionOutlined,
  ArrowUpOutlined,
  ArrowLeftOutlined,
  ArrowRightOutlined,
  LineChartOutlined,
  PieChartOutlined,
  LockOutlined,
  FileTextOutlined,
  FileOutlined,
  AppstoreOutlined,
  SolutionOutlined,
  DeploymentUnitOutlined
} from '@ant-design/icons';
import { Avatar, Dropdown, Space, Button, Switch, Typography, Row, Col, Badge, Tooltip, message } from 'antd';
import type { MenuProps } from 'antd';
import type { MenuDataItem, Settings } from '@ant-design/pro-components';
import './App.css';

const { Title, Paragraph } = Typography;

// 使用ProComponents内置的Settings类型，确保配置与官方示例一致
type ThemeConfig = Settings;

const App: React.FC = () => {
  // 状态管理
  const [collapsed, setCollapsed] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [settingVisible, setSettingVisible] = useState(false);
  const [notifications, setNotifications] = useState<number>(3);
  
  // 主题配置 - 使用Ant Design Pro标准配置
  const [themeConfig, setThemeConfig] = useState<ThemeConfig>({
    layout: 'mix', // 默认布局为混合模式
    primaryColor: 'daybreak', // 使用预设主题名而非直接颜色值
    navTheme: 'dark',
    contentWidth: 'Fluid',
    fixedHeader: true,
    fixSiderbar: true,
    autoHideHeader: false,
    colorWeak: false,
  });
  
  // 暗黑模式状态
  const [isDarkMode, setIsDarkMode] = useState<boolean>(false);

  // 从localStorage加载主题配置
  useEffect(() => {
    try {
      const savedConfig = localStorage.getItem('bone-theme-config');
      if (savedConfig) {
        setThemeConfig(JSON.parse(savedConfig));
      }
    } catch (error) {
      console.error('加载主题配置失败:', error);
    }
  }, []);
  
  // 监听主题配置变化，更新页面样式
  useEffect(() => {
    // 这里可以根据配置动态调整页面样式
  }, [themeConfig]);
  
  // 监听全屏状态变化
  useEffect(() => {
    const handleFullscreenChange = () => {
      const fullscreenElement = 
        document.fullscreenElement || 
        (document as any).webkitFullscreenElement || 
        (document as any).mozFullScreenElement || 
        (document as any).msFullscreenElement;
      setIsFullscreen(!!fullscreenElement);
    };
    
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
    document.addEventListener('mozfullscreenchange', handleFullscreenChange);
    document.addEventListener('MSFullscreenChange', handleFullscreenChange);
    
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
      document.removeEventListener('mozfullscreenchange', handleFullscreenChange);
      document.removeEventListener('MSFullscreenChange', handleFullscreenChange);
    };
  }, []);
  
  // 当布局类型改变时，重置折叠状态
  useEffect(() => {
    // 顶部布局下不应该折叠
    if (themeConfig.layout === 'top') {
      setCollapsed(false);
    }
  }, [themeConfig.layout]);

  // 菜单配置 - 采用 Ant Design Pro 标准 MenuDataItem 格式，按照 Bone 工程模块结构组织
  const menuData: MenuDataItem[] = [
    {
      path: '/',
      name: '仪表盘',
      icon: <HomeOutlined />,
      key: '/',
      type: 'menu',
      locale: false,
    },
    {
      path: '/platform',
      name: '平台核心',
      icon: <AppstoreOutlined />,
      key: '/platform',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/platform/iam',
          name: '身份认证管理',
          icon: <UserOutlined />,
          key: '/platform/iam',
          locale: false,
        },
        {
          path: '/platform/masterdata',
          name: '主数据管理',
          icon: <DatabaseOutlined />,
          key: '/platform/masterdata',
          locale: false,
        },
        {
          path: '/platform/notification',
          name: '通知中心',
          icon: <BellOutlined />,
          key: '/platform/notification',
          locale: false,
        },
        {
          path: '/platform/file',
          name: '文件管理',
          icon: <FileOutlined />,
          key: '/platform/file',
          locale: false,
        },
      ],
    },
    {
      path: '/engine',
      name: '业务引擎',
      icon: <ThunderboltOutlined />,
      key: '/engine',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/engine/extension',
          name: '扩展引擎',
          icon: <CodeOutlined />,
          key: '/engine/extension',
          locale: false,
          children: [
            {
              path: '/engine/extension/studio',
              name: '扩展工作室',
              icon: <LayoutOutlined />,
              key: '/engine/extension/studio',
              locale: false,
            },
            {
              path: '/engine/extension/sdk',
              name: '扩展SDK',
              icon: <CodeOutlined />,
              key: '/engine/extension/sdk',
              locale: false,
            },
          ],
        },
        {
          path: '/engine/metadata',
          name: '元数据管理',
          icon: <DatabaseOutlined />,
          key: '/engine/metadata',
          locale: false,
        },
        {
          path: '/engine/smartmeta',
          name: '智能元数据',
          icon: <DatabaseOutlined />,
          key: '/engine/smartmeta',
          locale: false,
        },
        {
          path: '/engine/procurement',
          name: '采购引擎',
          icon: <ShoppingCartOutlined />,
          key: '/engine/procurement',
          locale: false,
        },
        {
          path: '/engine/workflow',
          name: '工作流引擎',
          icon: <ArrowUpOutlined />,
          key: '/engine/workflow',
          locale: false,
        },
      ],
    },
    {
      path: '/business',
      name: '业务模块',
      icon: <TransactionOutlined />,
      key: '/business',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/business/admin',
          name: '后台管理',
          icon: <SettingOutlined />,
          key: '/business/admin',
          locale: false,
        },
        {
          path: '/business/trade',
          name: '交易业务',
          icon: <TransactionOutlined />,
          key: '/business/trade',
          locale: false,
        },
      ],
    },
    {
      path: '/framework',
      name: '框架组件',
      icon: <LayoutOutlined />,
      key: '/framework',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/framework/core',
          name: '核心框架',
          icon: <AppstoreOutlined />,
          key: '/framework/core',
          locale: false,
        },
        {
          path: '/framework/security',
          name: '安全框架',
          icon: <LockOutlined />,
          key: '/framework/security',
          locale: false,
        },
        {
          path: '/framework/datasource',
          name: '数据源',
          icon: <DatabaseOutlined />,
          key: '/framework/datasource',
          locale: false,
        },
        {
          path: '/framework/utils',
          name: '工具类',
          icon: <ToolOutlined />,
          key: '/framework/utils',
          locale: false,
        },
      ],
    },
    {
      path: '/tool',
      name: '开发工具',
      icon: <ToolOutlined />,
      key: '/tool',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/tool/codegen',
          name: '代码生成器',
          icon: <CodeOutlined />,
          key: '/tool/codegen',
          locale: false,
        },
        {
          path: '/tool/scaffold',
          name: '脚手架',
          icon: <LayoutOutlined />,
          key: '/tool/scaffold',
          locale: false,
        },
      ],
    },
    {
      path: '/sdk',
      name: 'SDK接口',
      icon: <CodeOutlined />,
      key: '/sdk',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/sdk/client',
          name: '客户端SDK',
          icon: <AppstoreOutlined />,
          key: '/sdk/client',
          locale: false,
        },
        {
          path: '/sdk/openapi',
          name: 'OpenAPI',
          icon: <AppstoreOutlined />,
          key: '/sdk/openapi',
          locale: false,
        },
      ],
    },
    {
      path: '/dashboard',
      name: '数据分析',
      icon: <LineChartOutlined />,
      key: '/dashboard',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/dashboard/analysis',
          name: '分析页',
          icon: <PieChartOutlined />,
          key: '/dashboard/analysis',
          locale: false,
        },
        {
          path: '/dashboard/monitor',
          name: '系统监控',
          icon: <EyeOutlined />,
          key: '/dashboard/monitor',
          locale: false,
        },
      ],
    },
    {
      path: '/system',
      name: '系统管理',
      icon: <SettingOutlined />,
      key: '/system',
      type: 'menu',
      locale: false,
      children: [
        {
          path: '/system/log',
          name: '日志管理',
          icon: <FileTextOutlined />,
          key: '/system/log',
          locale: false,
        },
        {
          path: '/system/config',
          name: '系统配置',
          icon: <SettingOutlined />,
          key: '/system/config',
          locale: false,
        },
      ],
    },
  ];

  // 自定义右侧用户菜单
  const userMenu: MenuProps['items'] = [
    {
      key: '1',
      label: '个人中心',
      icon: <UserOutlined />,
    },
    {
      key: '2',
      label: '系统设置',
      icon: <SettingOutlined />,
    },
    {
      key: '3',
      label: '退出登录',
      icon: <LogoutOutlined />,
      danger: true,
    },
  ];

  // 自定义通知菜单
  const notificationMenu: MenuProps['items'] = [
    {
      key: '1',
      label: (
        <div style={{ padding: 8, lineHeight: 1.4 }}>
          <div style={{ fontWeight: 500 }}>系统更新通知</div>
          <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>系统已更新至最新版本 v1.2.0</div>
          <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>2小时前</div>
        </div>
      ),
    },
    {
      key: '2',
      label: (
        <div style={{ padding: 8, lineHeight: 1.4 }}>
          <div style={{ fontWeight: 500 }}>任务完成提醒</div>
          <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>数据同步任务已成功完成</div>
          <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>4小时前</div>
        </div>
      ),
    },
    {
      key: '3',
      label: (
        <div style={{ padding: 8, lineHeight: 1.4 }}>
          <div style={{ fontWeight: 500 }}>新消息提醒</div>
          <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>您有3条未读消息</div>
          <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>昨天</div>
        </div>
      ),
    },
    {
      key: '4',
      label: '查看全部通知',
      style: { textAlign: 'center', borderTop: '1px solid #f0f0f0', marginTop: 8 },
    },
  ];

  // 自定义logo
  const logo = () => {
    return (
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        paddingLeft: themeConfig.navTheme === 'dark' ? 10 : 0,
        height: '100%'
      }}>
        <div style={{ 
          width: 32, 
          height: 32, 
          borderRadius: 4, 
          backgroundColor: themeConfig.primaryColor, 
          marginRight: 10,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontWeight: 'bold',
          fontSize: 16
        }}>
          B
        </div>
        {!collapsed && (
          <span style={{ 
            fontSize: 18, 
            fontWeight: 'bold', 
            color: themeConfig.navTheme === 'dark' ? '#fff' : '#000',
            transition: 'all 0.3s'
          }}>
            Bone Platform
          </span>
        )}
      </div>
    );
  };

  // 处理主题配置变更
  const handleThemeChange = (newConfig: Settings) => {
    setThemeConfig(newConfig);
    localStorage.setItem('bone-theme-config', JSON.stringify(newConfig));
  };

  // 切换设置抽屉可见性
  const toggleSettingDrawer = () => {
    setSettingVisible(!settingVisible);
  };

  // 切换全屏
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(err => {
        message.error(`全屏切换失败: ${err.message}`);
      });
    } else {
      if (document.exitFullscreen) {
        document.exitFullscreen();
      }
    }
  };

  // 清除通知
  const clearNotifications = () => {
    setNotifications(0);
    message.success('通知已清空');
  };

  // 自定义右侧内容 - 符合Ant Design Pro的标准布局
  const rightContentRender = () => (
    <Space size="small" className="ant-pro-global-header-item-right">
      {/* 系统更新日志 */}
      <Tooltip title="系统更新日志">
        <Button type="text" icon={<DeploymentUnitOutlined />} />
      </Tooltip>
      
      {/* 暗黑模式切换 */}
      <Tooltip title={isDarkMode ? '切换到亮色模式' : '切换到暗黑模式'}>
        <Button 
          type="text" 
          icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />} 
          onClick={() => setIsDarkMode(!isDarkMode)} 
        />
      </Tooltip>
      
      {/* 通知中心 */}
      <Dropdown menu={{ items: notificationMenu }}>
        <Badge dot={notifications > 0} offset={[0, 0]}>
          <Button type="text" icon={<BellOutlined />} />
        </Badge>
      </Dropdown>
      
      {/* 全屏切换 */}
      <Tooltip title={isFullscreen ? '退出全屏' : '进入全屏'}>
        <Button 
          type="text" 
          icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} 
          onClick={toggleFullscreen} 
        />
      </Tooltip>
      
      {/* 整体风格设置按钮 - 企业级应用标准位置 */}
      <Tooltip title="设置">
        <Button 
          type="text" 
          icon={<SettingOutlined />} 
          onClick={toggleSettingDrawer} 
        />
      </Tooltip>
      
      {/* 用户信息 */}
      <Dropdown 
        menu={{
          items: [
            {
              key: 'user',
              label: '个人中心',
              icon: <UserOutlined />,
            },
            {
              key: 'setting',
              label: '系统设置',
              icon: <SettingOutlined />,
            },
            {
              type: 'divider',
            },
            {
              key: 'logout',
              label: '退出登录',
              icon: <LogoutOutlined />,
              danger: true,
            },
          ],
        }}
      >
        <div className="ant-pro-global-header-item">
          <Avatar size={24} icon={<UserOutlined />} />
        </div>
      </Dropdown>
    </Space>
  );

  // 自定义头部渲染 - 确保顶部导航元素在所有布局模式下都正确显示
  const headerRender = (props) => {
    const { collapsed, onCollapse } = props;
    
    return (
      <div 
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          width: '100%',
          height: '100%',
          padding: '0 20px',
        }}
      >
        {/* 左侧标题区域 */}
        <div style={{ display: 'flex', alignItems: 'center' }}>
          {/* 在侧边布局下显示Logo和折叠按钮 */}
          {themeConfig.layout === 'side' && (
            <>
              {logo()}
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => onCollapse && onCollapse(!collapsed)}
                style={{ 
                  marginLeft: 16, 
                  color: themeConfig.navTheme === 'dark' ? '#fff' : '#000'
                }}
              />
            </>
          )}
          
          {/* 在顶部和混合布局下只显示Logo */}
          {(themeConfig.layout === 'top' || themeConfig.layout === 'mix') && logo()}
        </div>
        
        {/* 右侧内容区域 */}
        <div style={{ display: 'flex', alignItems: 'center' }}>
          {rightContentRender()}
        </div>
      </div>
    );
  };

  // 自定义折叠按钮
  const collapsedButtonRender = () => {
    // 根据布局类型和状态显示不同的折叠按钮
    if (themeConfig.layout === 'top') {
      return null; // 顶部布局不显示折叠按钮
    }
    
    return (
      <Tooltip title={collapsed ? '展开菜单' : '收起菜单'}>
        <div 
          style={{ 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            width: '100%',
            height: 48,
            cursor: 'pointer',
            transition: 'all 0.3s',
            backgroundColor: themeConfig.navTheme === 'dark' ? '#1f1f1f' : '#fafafa',
            borderTop: `1px solid ${themeConfig.navTheme === 'dark' ? 'rgba(255,255,255,0.1)' : '#f0f0f0'}`,
            // 在混合布局下固定在底部
            position: themeConfig.layout === 'mix' ? 'absolute' : 'relative',
            bottom: 0,
            left: 0,
            right: 0,
          }}
          onClick={() => setCollapsed(!collapsed)}
          onMouseEnter={(e) => {
            e.currentTarget.style.backgroundColor = themeConfig.navTheme === 'dark' ? '#2f2f2f' : '#f0f0f0';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.backgroundColor = themeConfig.navTheme === 'dark' ? '#1f1f1f' : '#fafafa';
          }}
        >
          {collapsed ? <ArrowRightOutlined /> : <ArrowLeftOutlined />}
        </div>
      </Tooltip>
    );
  };
  
  // 自定义菜单项渲染已移至ProLayout组件中直接定义
  
  // 自定义菜单栏渲染（顶部） - 这是顶部和混合布局下显示顶部菜单的关键
  const menuBarRender = (props) => {
    if (!props) {
      return null;
    }
    
    const { menuHeaderRender } = props;
    
    // 混合布局的特殊处理 - 只添加平台名称
    if (themeConfig.layout === 'mix') {
      return (
        <div className="ant-pro-layout-header">
          <div style={{ display: 'flex', alignItems: 'center', height: '100%' }}>
            <span style={{ fontSize: 16, fontWeight: 500, marginRight: 24, marginLeft: 8, color: themeConfig.navTheme === 'dark' ? '#fff' : '#000' }}>
              Bone Platform
            </span>
            {/* 重要：直接使用ProLayout提供的menuHeaderRender，不做任何额外修改 */}
            {menuHeaderRender(props)}
          </div>
        </div>
      );
    }
    
    // 顶部布局和其他布局 - 完全使用ProLayout的默认实现
    // 这是解决子菜单展开问题的最佳方式，让ProLayout处理所有菜单交互逻辑
    return menuHeaderRender(props);
  };

  // 自定义面包屑配置
  const breadcrumbRender = (routers?: MenuDataItem[]) => [
    { path: '/', breadcrumbName: '首页' },
    ...(routers || []),
  ];

  // 全局样式设置
  const CustomStyle = () => (
      <style>
        {
          `
          /* 符合Ant Design Pro的标准样式 */
          .ant-pro-global-header-item {
            display: flex;
            align-items: center;
            cursor: pointer;
            padding: 0 8px;
            transition: all 0.3s;
          }
          
          .ant-pro-global-header-item:hover {
            background-color: rgba(255, 255, 255, 0.06);
          }
          
          .ant-pro-global-header-item-right {
            margin-left: auto;
          }
          
          /* 修复侧边栏折叠动画 */
          .ant-pro-sider-children {
            height: 100%;
            overflow-y: auto;
          }
          `
        }
      </style>
    );

  return (
    <>
      <ProLayout
        logo={logo}
        // 使用mock数据确保菜单正常显示
        menuDataRender={() => menuData}
        // 配置分割菜单，仅在混合布局下使用
        splitMenus={themeConfig.layout === 'mix'}
        // 配置面包屑
        breadcrumbRender={breadcrumbRender}
        // 配置右侧内容渲染
        rightContentRender={rightContentRender}
        // 配置菜单栏渲染 - 这是顶部和混合布局下显示顶部菜单的关键
        menuBarRender={menuBarRender}
        // 配置主题和布局
        layout={themeConfig.layout}
        navTheme={themeConfig.navTheme}
        primaryColor={themeConfig.primaryColor}
        contentWidth={themeConfig.contentWidth}
        fixedHeader={themeConfig.fixedHeader}
        fixSiderbar={themeConfig.fixSiderbar}
        autoHideHeader={themeConfig.autoHideHeader}
        colorWeak={themeConfig.colorWeak}
        // 侧边栏配置
        siderWidth={220}
        collapsedWidth={56}
        collapsed={themeConfig.layout !== 'top' ? collapsed : false}
        onCollapse={(value) => {
          if (themeConfig.layout !== 'top') {
            setCollapsed(value);
          }
        }}
        // 其他布局配置
        breakpoint="lg"
        headerHeight={64}
        clickToCollapse={true}
        autoHideScrollbar={true}
        showBreadcrumb={true}
      >
        {/* 主要内容区域 - 使用ProComponents的标准空白页样式 */}
        <div style={{ padding: 24, minHeight: 360, textAlign: 'center' }}>
          <Typography.Title level={3}>Bone Platform</Typography.Title>
          <Typography.Paragraph>欢迎使用Bone Platform，请从左侧菜单选择功能</Typography.Paragraph>
        </div>
      </ProLayout>
      
      {/* 使用ProComponents提供的SettingDrawer组件替代自定义模态框 */}
      <SettingDrawer
        settings={themeConfig}
        onSettingChange={handleThemeChange}
        visible={settingVisible}
        onClose={toggleSettingDrawer}
        hideCopyButton
      />
      
      {/* 自定义样式 */}
      <CustomStyle />
    </>
  );
}

export default App;