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

type ThemeConfig = Settings;

const App: React.FC = () => {
  // 状态管理
  const [collapsed, setCollapsed] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);
  const [settingVisible, setSettingVisible] = useState(false);
  const [notifications, setNotifications] = useState<number>(3);
  const [selectedTopMenuKey, setSelectedTopMenuKey] = useState<string | null>(null);
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  // 用于跟踪用户交互状态，提高菜单响应性能
  const [isUserInteracting, setIsUserInteracting] = useState(false);
  const [themeConfig, setThemeConfig] = useState<ThemeConfig>({
    layout: 'mix',
    primaryColor: 'daybreak',
    navTheme: 'dark',
    contentWidth: 'Fluid',
    fixedHeader: true,
    fixSiderbar: true,
    autoHideHeader: false,
    colorWeak: false,
  });
  const [isDarkMode, setIsDarkMode] = useState<boolean>(false);
  
  // 切换暗黑模式
  const toggleTheme = () => {
    setIsDarkMode(!isDarkMode);
    setThemeConfig(prev => ({
      ...prev,
      navTheme: !isDarkMode ? 'dark' : 'light'
    }));
  };
  
  // 切换全屏
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen();
    } else {
      document.exitFullscreen();
    }
  };
  
  // 切换设置抽屉
  const toggleSettingDrawer = () => {
    setSettingVisible(!settingVisible);
  };
  
  // 处理主题配置变更
  const handleThemeChange = (newSettings: Settings) => {
    setThemeConfig(newSettings);
  };
  
  // 监听全屏状态变化
  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement);
    };
    
    document.addEventListener('fullscreenchange', handleFullscreenChange);
    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange);
    };
  }, []);
  
  // 页面加载时设置默认选中菜单项
  useEffect(() => {
    // 如果没有选中的菜单项，默认选中第一个菜单项
    if (!selectedTopMenuKey && menuData.length > 0) {
      setSelectedTopMenuKey(menuData[0].key);
    }
  }, [selectedTopMenuKey]);
  
  // 处理布局类型变化时重置选中状态
  useEffect(() => {
    if (themeConfig.layout === 'mix' && menuData.length > 0) {
      // 确保在混合布局下有选中的菜单项
      if (!selectedTopMenuKey) {
        setSelectedTopMenuKey(menuData[0].key);
        // 默认展开第一个菜单项的子菜单
        const firstMenuItem = menuData[0];
        if (firstMenuItem.children && firstMenuItem.children.length > 0) {
          setOpenKeys([firstMenuItem.key]);
        }
      }
    }
  }, [themeConfig.layout, selectedTopMenuKey]);
  
  // 当选中的顶部菜单项改变时，自动展开对应的侧边栏子菜单 - 核心修复
  useEffect(() => {
    // 在混合布局下，只要有选中的顶部菜单，立即展开对应的侧边栏子菜单
    if (selectedTopMenuKey && themeConfig.layout === 'mix') {
      // 立即执行，不使用延迟，确保响应迅速
      const menuItem = menuData.find(item => item.key === selectedTopMenuKey);
      if (menuItem && menuItem.children && menuItem.children.length > 0) {
        // 强制设置展开状态，确保侧边栏子菜单显示
        setOpenKeys([selectedTopMenuKey]);
      } else {
        // 无子菜单时收起所有菜单
        setOpenKeys([]);
      }
    }
  }, [selectedTopMenuKey, themeConfig.layout]);
  
  // 监听URL变化，确保菜单状态与当前路由同步
  useEffect(() => {
    const handleLocationChange = () => {
      // 获取当前URL路径
      const currentPath = window.location.pathname;
      
      // 查找匹配的菜单项
      let matchedTopMenuKey: string | null = null;
      
      // 检查是否匹配顶层菜单
      const topMenuMatch = menuData.find(item => currentPath === item.key);
      if (topMenuMatch) {
        matchedTopMenuKey = topMenuMatch.key;
      } else {
        // 检查是否匹配子菜单
        const parentMenuMatch = menuData.find(item => 
          item.children && item.children.some(child => currentPath === child.key)
        );
        if (parentMenuMatch) {
          matchedTopMenuKey = parentMenuMatch.key;
        }
      }
      
      // 如果找到匹配项且与当前选中项不同，则更新选中状态和展开状态
      if (matchedTopMenuKey && matchedTopMenuKey !== selectedTopMenuKey) {
        setSelectedTopMenuKey(matchedTopMenuKey);
        
        // 如果是混合布局且有子菜单，自动展开
        if (themeConfig.layout === 'mix') {
          const menuItem = menuData.find(item => item.key === matchedTopMenuKey);
          if (menuItem && menuItem.children && menuItem.children.length > 0) {
            setOpenKeys([matchedTopMenuKey]);
          }
        }
      }
    };
    
    // 监听popstate事件（浏览器前进/后退按钮）
    window.addEventListener('popstate', handleLocationChange);
    
    // 初始检查
    handleLocationChange();
    
    return () => {
      window.removeEventListener('popstate', handleLocationChange);
    };
  }, [selectedTopMenuKey, themeConfig.layout]);
  
  // 监听侧边栏展开状态变化，优化用户体验
  useEffect(() => {
    // 可以在这里添加额外的交互逻辑，例如记录用户的菜单偏好等
  }, [openKeys]);
  
  // 菜单数据
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
        },
      ],
    },
  ];
  
  // 自定义logo
  const logo = () => (
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
        backgroundColor: '#1890ff', 
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
  
  // 面包屑配置
  const breadcrumbRender = (routers?: MenuDataItem[]) => [
    { path: '/', breadcrumbName: '首页' },
    ...(routers || []),
  ];
  
  // 菜单数据处理函数
  const menuDataRender = () => menuData;
  
  // 处理顶部菜单点击 - 简化逻辑，确保混合布局下子菜单正确展开
  const onMenuHeaderClick = (e: { key: string }) => {
    const clickedKey = e.key;
    
    // 1. 立即更新选中状态
    setSelectedTopMenuKey(clickedKey);
    
    // 2. 在混合布局下，强制展开对应的侧边栏子菜单
    if (themeConfig.layout === 'mix') {
      const menuItem = menuData.find(item => item.key === clickedKey);
      if (menuItem && menuItem.children && menuItem.children.length > 0) {
        // 直接设置展开状态
        setOpenKeys([clickedKey]);
      }
    }
    
    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  
  // 处理菜单项点击 - 简化逻辑，确保状态同步
  const onMenuClick = (e: { key: string }) => {
    const clickedKey = e.key;
    
    // 查找点击项所属的顶层菜单
    let targetTopMenuKey = clickedKey;
    
    // 检查是否是子菜单项
    const parentMenu = menuData.find(item => 
      item.children && item.children.some(child => child.key === clickedKey)
    );
    
    if (parentMenu) {
      // 子菜单点击 - 设置父菜单为顶部选中项
      targetTopMenuKey = parentMenu.key;
    }
    
    // 更新顶部选中状态
    setSelectedTopMenuKey(targetTopMenuKey);
    
    // 滚动到顶部
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };
  
  // 处理侧边栏菜单展开/收起 - 简化逻辑，确保与顶部菜单正确联动
  const onOpenChange = (newOpenKeys: string[]) => {
    // 获取顶层菜单的key
    const topKeys = menuData.map(item => item.key);
    
    // 在混合布局下，确保只有一个顶层菜单处于展开状态
    const topLevelOpenKeys = newOpenKeys.filter(key => topKeys.includes(key));
    
    if (topLevelOpenKeys.length > 0) {
      // 只保留最后一个展开的顶层菜单
      const lastTopLevelOpenKey = topLevelOpenKeys[topLevelOpenKeys.length - 1];
      
      // 同步更新顶部菜单选中状态
      setSelectedTopMenuKey(lastTopLevelOpenKey);
      
      // 仅保留该顶层菜单的展开状态
      setOpenKeys([lastTopLevelOpenKey]);
    } else {
      // 没有展开的顶层菜单
      setOpenKeys([]);
    }
  };
  
  // 右侧工具栏渲染
  const rightContentRender = () => {
    const textColor = themeConfig.navTheme === 'dark' ? '#fff' : '#333';
    const userMenuItems: MenuProps['items'] = [
      {
        key: '1',
        label: '个人中心',
        icon: <UserOutlined />,
      },
      {
        key: '2',
        label: '账户设置',
        icon: <SettingOutlined />,
      },
      {
        type: 'divider',
      },
      {
        key: '3',
        label: '退出登录',
        icon: <LogoutOutlined />,
        danger: true,
      },
    ];

    return (
      <Space size="small" className="ant-pro-global-header-item-right">
        {/* 通知按钮 */}
        <Dropdown menu={{ items: [] }}>
          <Badge count={notifications} showZero>
            <Button type="text" icon={<BellOutlined />} />
          </Badge>
        </Dropdown>
        
        {/* 全屏按钮 */}
        <Button 
          type="text" 
          icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />} 
          onClick={toggleFullscreen} 
        />
        
        {/* 主题切换按钮 */}
        <Button 
          type="text" 
          icon={isDarkMode ? <SunOutlined /> : <MoonOutlined />} 
          onClick={toggleTheme} 
        />
        
        {/* 设置按钮 */}
        <Button 
          type="text" 
          icon={<SettingOutlined />} 
          onClick={toggleSettingDrawer} 
        />
        
        {/* 用户头像和下拉菜单 */}
        <Dropdown 
          menu={{ items: userMenuItems }} 
          placement="bottomRight"
        >
          <Avatar size={24} icon={<UserOutlined />} />
        </Dropdown>
      </Space>
    );
  };
  
  // 全局样式设置 - 基于Ant Design设计规范的混合布局菜单交互优化
  const CustomStyle = () => (
    <style>
      {`
        /* 优化顶部和混合布局的样式 */
        .ant-pro-header {
          padding: 0 16px !important;
          box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
          background: inherit;
        }
        
        /* 修复混合布局下的顶部菜单项样式 - 符合Ant Design企业级应用规范 */
        .ant-pro-menu-top-menu > .ant-menu-item {
          padding: 0 20px !important;
          margin: 0 4px !important;
          height: 52px !important;
          line-height: 52px !important;
          position: relative;
          overflow: hidden;
          border-radius: 4px;
          user-select: none;
        }
        
        /* 修复混合布局下的顶部菜单选中样式 */
        .ant-pro-menu-top-menu > .ant-menu-item.ant-menu-item-selected {
          background-color: rgba(24, 144, 255, 0.1) !important;
          color: #1890ff !important;
          font-weight: 600;
        }
        
        /* 提升顶部菜单项的交互体验 - 添加下划线动画效果 */
        .ant-pro-menu-top-menu > .ant-menu-item {
          transition: all 0.2s ease-in-out;
        }
        
        .ant-pro-menu-top-menu > .ant-menu-item:hover {
          background-color: rgba(24, 144, 255, 0.05) !important;
          transform: translateY(-1px);
        }
        
        .ant-pro-menu-top-menu > .ant-menu-item::after {
          content: '';
          position: absolute;
          left: 0;
          bottom: 0;
          width: 0;
          height: 2px;
          background-color: #1890ff;
          transition: width 0.3s ease;
        }
        
        .ant-pro-menu-top-menu > .ant-menu-item.ant-menu-item-selected::after {
          width: 100%;
        }
        
        /* 优化混合布局下侧边栏菜单的样式 */
        .ant-pro-sider-menu.ant-menu-root.ant-menu-vertical {
          padding: 8px 0;
          background: transparent !important;
        }
        
        /* 侧边栏菜单项样式优化 */
        .ant-pro-sider-menu .ant-menu-item {
          padding: 0 24px !important;
          margin: 0 !important;
          transition: all 0.2s ease;
          border-radius: 4px;
          margin: 2px 8px !important;
          user-select: none;
        }
        
        /* 侧边栏菜单项选中和悬停效果 */
        .ant-pro-sider-menu .ant-menu-item:hover,
        .ant-pro-sider-menu .ant-menu-submenu-title:hover {
          background-color: rgba(24, 144, 255, 0.05) !important;
        }
        
        .ant-pro-sider-menu .ant-menu-item.ant-menu-item-selected {
          background-color: rgba(24, 144, 255, 0.1) !important;
          color: #1890ff !important;
        }
        
        /* 侧边栏子菜单项样式 */
        .ant-pro-sider-menu .ant-menu-sub .ant-menu-item {
          padding-left: 40px !important;
        }
        
        /* 确保子菜单展开/收起时有平滑过渡效果 */
        .ant-menu-vertical .ant-menu-sub {
          background-color: transparent !important;
          transition: all 0.3s ease-in-out;
          padding: 4px 0;
          overflow: hidden;
        }
        
        /* 优化混合布局下的菜单展开/收起图标 */
        .ant-menu-submenu-arrow {
          transition: transform 0.3s ease;
        }
        
        /* 优化侧边栏展开/收起动画 */
        .ant-pro-sider {
          transition: all 0.3s ease;
          will-change: width;
        }
        
        /* 确保内容区域不被固定头部遮挡 */
        .ant-pro-layout-top-menu .ant-pro-layout-content,
        .ant-pro-layout-mix .ant-pro-layout-content {
          padding-top: 72px !important;
          transition: all 0.3s ease;
        }
        
        /* 在混合布局下优化布局结构 */
        .ant-pro-layout-mix .ant-pro-sider.ant-pro-sider-fixed {
          box-shadow: 1px 0 4px rgba(0, 0, 0, 0.05);
          background: inherit;
          z-index: 10;
        }
        
        /* 优化菜单交互反馈 */
        .ant-menu-item:active,
        .ant-menu-submenu-title:active {
          background-color: rgba(24, 144, 255, 0.08) !important;
        }
        
        /* 为可展开的菜单项添加视觉提示 */
        .ant-menu-submenu:hover .ant-menu-submenu-title {
          color: #1890ff !important;
        }
        
        /* 提升菜单动画性能 */
        .ant-menu-item,
        .ant-menu-submenu-title {
          will-change: background-color, transform;
        }
        
        /* 菜单项加载时的过渡效果 */
        @keyframes fadeIn {
          from {
            opacity: 0;
            transform: translateX(-10px);
          }
          to {
            opacity: 1;
            transform: translateX(0);
          }
        }
        
        /* 应用子菜单动画 */
        .ant-menu-submenu-open .ant-menu-sub {
          animation: fadeIn 0.3s ease-out;
        }
      `}
    </style>
  );

  return (
    <>
      <ProLayout
        logo={logo}
        menuDataRender={menuDataRender}
        splitMenus={themeConfig.layout === 'mix'}
        breadcrumbRender={breadcrumbRender}
        rightContentRender={rightContentRender}
        selectedKeys={selectedTopMenuKey ? [selectedTopMenuKey] : ['/']}
        openKeys={openKeys}
        onMenuHeaderClick={onMenuHeaderClick}
        onMenuClick={onMenuClick}
        onOpenChange={onOpenChange}
        layout={themeConfig.layout}
        navTheme={themeConfig.navTheme}
        primaryColor={themeConfig.primaryColor}
        contentWidth={themeConfig.contentWidth}
        fixedHeader={true}
        fixSiderbar={themeConfig.layout === 'mix'}
        autoHideHeader={false}
        colorWeak={themeConfig.colorWeak}
        siderWidth={220}
        collapsedWidth={56}
        collapsed={themeConfig.layout === 'top' ? true : collapsed}
        onCollapse={(value) => {
          if (themeConfig.layout !== 'top') {
            setCollapsed(value);
          }
        }}
        breakpoint="lg"
        headerHeight={64}
        clickToCollapse={themeConfig.layout !== 'mix'}
        autoHideScrollbar={true}
        showBreadcrumb={true}
      >
        {/* 主要内容区域 */}
        <div style={{ 
          padding: 24, 
          minHeight: 360, 
          textAlign: 'center'
        }}>
          <Typography.Title level={3}>Bone Platform</Typography.Title>
          <Typography.Paragraph>欢迎使用Bone Platform，请从左侧菜单选择功能</Typography.Paragraph>
        </div>
      </ProLayout>
      
      {/* 设置抽屉 */}
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
};

export default App;