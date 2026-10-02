import React from 'react';
import { Layout } from 'antd';
import { Outlet } from 'react-router-dom';

const { Content } = Layout;

/**
 * 集成态（qiankun 微前端）下，左侧主导航由父容器 Shell 的「代码生成」分组统一提供
 * （数据源管理 / 代码生成 / 模板管理 / 生成历史）。
 *
 * 历史遗留：本组件曾在微前端内再渲染一层本地 Sider（Studio Generator + 四个子菜单），
 * 与 Shell 主导航完全重复，已按 2026-10-01 裁定整体删除——与其他微应用
 * （bone-metadata-app 等，无内部布局导航）保持一致，导航职责全部归 Shell。
 *
 * 独立运行（本地 dev 直连 3009 端口）时通过 hash 路由直达各页面：
 * #/datasources / #/generate / #/templates / #/history
 */
const GeneratorLayout: React.FC = () => (
  <Layout style={{ minHeight: '100%' }}>
    <Content className="page-container">
      <Outlet />
    </Content>
  </Layout>
);

export default GeneratorLayout;
