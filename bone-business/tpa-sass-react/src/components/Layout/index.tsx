import React, { useState, useEffect } from 'react';
import { Layout, Button, Drawer, FloatButton } from 'antd';
import { MenuFoldOutlined, MenuUnfoldOutlined, VerticalAlignTopOutlined } from '@ant-design/icons';
import Sidebar from './Sidebar';
import NavBar from './NavBar';
import TagsView from './TagsView';
import AppMain from './AppMain';
import Settings from './Settings';
import './Layout.css';
import { useAppStore, useSettingsStore } from '@/store';
import { DeviceEnum } from '@/enums/DeviceEnum';
import { LayoutEnum } from '@/enums/LayoutEnum';

const { Header, Sider, Content } = Layout;
const WIDTH_DESKTOP = 992;

const LayoutComponent: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const appStore = useAppStore();
  const settingsStore = useSettingsStore();
  const [isMobile, setIsMobile] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const handleResize = () => {
    const mobile = window.innerWidth < WIDTH_DESKTOP;
    setIsMobile(mobile);
    appStore.toggleDevice(mobile ? DeviceEnum.MOBILE : DeviceEnum.DESKTOP);
    if (mobile) {
      appStore.closeSideBar();
    } else {
      appStore.openSideBar();
    }
  };

  useEffect(() => {
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  const toggleSidebar = () => {
    appStore.toggleSidebar();
  };

  const toggleMobileSidebar = () => {
    setSidebarOpen(!sidebarOpen);
  };

  const handleOutsideClick = () => {
    appStore.closeSideBar();
    setSidebarOpen(false);
  };

  const showSettings = true;
  const layout = settingsStore.layout;
  const fixedHeader = settingsStore.fixedHeader;
  const showTagsView = settingsStore.tagsView;
  const sidebarOpened = appStore.sidebar.opened;

  const classObj = [
    'app-layout',
    !sidebarOpened ? 'hideSidebar' : '',
    sidebarOpened ? 'openSidebar' : '',
    appStore.device === DeviceEnum.MOBILE ? 'mobile' : '',
    `layout-${layout}`
  ].filter(Boolean).join(' ');

  return (
    <div className={classObj} style={{ width: '100%', height: '100%' }}>
      {/* 遮罩层 */}
      {isMobile && sidebarOpen && (
        <div
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            width: '100%',
            height: '100%',
            backgroundColor: 'rgba(0,0,0,0.3)',
            zIndex: 999
          }}
          onClick={handleOutsideClick}
        />
      )}

      {/* 公用侧边栏 */}
      {/* 简单化处理，先不实现qiankun相关功能 */}
      {/* 混合布局 */}
      {layout === LayoutEnum.MIX ? (
        <div className="mix-container" style={{ display: 'flex', height: '100%', paddingTop: '50px' }}>
          <div className="mix-container__left" style={{ position: 'relative', width: sidebarOpened ? 256 : 64, height: '100%' }}>
            {/* 简化处理，先不实现SidebarMenu和sidebar-toggle */}
            <Sidebar collapsed={!sidebarOpened} />
          </div>

          <div className={`main-container ${showTagsView ? 'hasTagsView' : ''}`} style={{ flex: 1, minWidth: 0, marginLeft: 0, height: '100%', overflowY: 'auto' }}>
            <div className={fixedHeader ? 'fixed-header' : ''} style={{ position: fixedHeader ? 'sticky' : 'relative', top: 0, zIndex: 9 }}>
              {showTagsView && <TagsView />}
            </div>
            <AppMain>{children}</AppMain>
            {showSettings && <Settings />}
            <FloatButton.BackTop target={() => (document.querySelector('.main-container') || document.body) as HTMLElement} icon={<VerticalAlignTopOutlined />} />
          </div>
        </div>
      ) : (
        <>
          {/* 移动端侧边栏 */}
          <Drawer
            placement="left"
            open={isMobile && sidebarOpen}
            onClose={() => setSidebarOpen(false)}
            width={256}
            className="mobile-sidebar"
          >
            <Sidebar collapsed={false} />
          </Drawer>

          {/* 桌面端侧边栏 */}
          {!isMobile && layout === LayoutEnum.LEFT && (
            <Sider
              width={256}
              collapsible
              collapsed={!sidebarOpened}
              onCollapse={(collapsed) => {
                if (collapsed) {
                  appStore.closeSideBar();
                } else {
                  appStore.openSideBar();
                }
              }}
              className="sidebar sidebar-container"
            >
              <Sidebar collapsed={!sidebarOpened} />
            </Sider>
          )}

          <div className={`main-layout ${layout === LayoutEnum.TOP ? 'layout-top' : ''}`} style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden', marginLeft: layout === LayoutEnum.LEFT ? 0 : 0 }}>
            <Header className="header" style={{ display: 'flex', alignItems: 'center', padding: 0 }}>
              {layout === LayoutEnum.LEFT && (
                <Button
                  type="text"
                  icon={!sidebarOpened ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                  onClick={isMobile ? toggleMobileSidebar : toggleSidebar}
                  className="toggle-button"
                />
              )}
              {layout === LayoutEnum.LEFT && <NavBar />}
            </Header>

            {showTagsView && <TagsView />}

            <Content className="content" style={{ flex: 1, overflow: 'auto', margin: 0, padding: 0 }}>
              <AppMain>{children}</AppMain>
            </Content>
          </div>

          {showSettings && <Settings />}
          <FloatButton.BackTop target={() => (document.querySelector('.main-layout') || document.body) as HTMLElement} icon={<VerticalAlignTopOutlined />} />
        </>
      )}
    </div>
  );
};

export default LayoutComponent;
