import React from 'react';
import { Menu } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import './Sidebar.css';

const menuItems = [
  {
    key: '/dashboard',
    label: <Link to="/dashboard">仪表盘</Link>,
    icon: null,
  },
  {
    key: 'myJob',
    label: '我的作业',
    icon: null,
    children: [
      {
        key: '/myJob/myPrecheck',
        label: <Link to="/myJob/myPrecheck">我的初审</Link>,
      },
      {
        key: '/myJob/myEntry',
        label: <Link to="/myJob/myEntry">我的录入</Link>,
      },
      {
        key: '/myJob/myQualityCheck',
        label: <Link to="/myJob/myQualityCheck">我的质检</Link>,
      },
      {
        key: '/myJob/myAudit',
        label: <Link to="/myJob/myAudit">我的审核</Link>,
      },
      {
        key: '/myJob/myReview',
        label: <Link to="/myJob/myReview">我的复核</Link>,
      },
    ],
  },
  {
    key: 'jobManage',
    label: '作业管理',
    icon: null,
    children: [
      {
        key: '/jobManage/groupInsuranceSignList',
        label: <Link to="/jobManage/groupInsuranceSignList">团险签收</Link>,
      },
      {
        key: '/jobManage/claimHandOver',
        label: <Link to="/jobManage/claimHandOver">转交赔案</Link>,
      },
      {
        key: '/jobManage/claimDistribute',
        label: <Link to="/jobManage/claimDistribute">分配赔案</Link>,
      },
      {
        key: '/jobManage/uploadRecord',
        label: <Link to="/jobManage/uploadRecord">导入记录</Link>,
      },
    ],
  },
  {
    key: 'claimManage',
    label: '赔案管理',
    icon: null,
    children: [
      {
        key: '/claimManage/pushFail',
        label: <Link to="/claimManage/pushFail">推送失败</Link>,
      },
      {
        key: '/claimManage/pushFailHandleRecord',
        label: <Link to="/claimManage/pushFailHandleRecord">推送失败处理记录</Link>,
      },
      {
        key: '/claimManage/copyClaim',
        label: <Link to="/claimManage/copyClaim">复制赔案</Link>,
      },
    ],
  },
  {
    key: 'jobConfig',
    label: '作业配置',
    icon: null,
    children: [
      {
        key: '/jobConfig/jobBaseConfig',
        label: <Link to="/jobConfig/jobBaseConfig">标准作业配置</Link>,
      },
      {
        key: '/jobConfig/bizIdentityList',
        label: <Link to="/jobConfig/bizIdentityList">主体专属列表</Link>,
      },
    ],
  },
  {
    key: 'policyConfig',
    label: '团单个险管理',
    icon: null,
    children: [
      {
        key: '/policyConfig/groupPolicyList',
        label: <Link to="/policyConfig/groupPolicyList">团险保单管理</Link>,
      },
    ],
  },
  {
    key: 'systemManage',
    label: '系统管理',
    icon: null,
    children: [
      {
        key: '/systemManage/modelManage',
        label: <Link to="/systemManage/modelManage">数据模型管理</Link>,
      },
      {
        key: '/systemManage/optionConfig',
        label: <Link to="/systemManage/optionConfig">系统选项配置</Link>,
      },
      {
        key: '/systemManage/eventManage',
        label: <Link to="/systemManage/eventManage">事件管理</Link>,
      },
      {
        key: '/systemManage/groupIndividualConfig',
        label: <Link to="/systemManage/groupIndividualConfig">团单个险配置</Link>,
      },
    ],
  },
  {
    key: 'codeTools',
    label: '代码基础设施',
    icon: null,
    children: [
      {
        key: '/codeTools/codeGeneration',
        label: <Link to="/codeTools/codeGeneration">代码生成</Link>,
      },
      {
        key: '/codeTools/dataSourceConfiguration',
        label: <Link to="/codeTools/dataSourceConfiguration">数据源配置</Link>,
      },
    ],
  },
];

const Sidebar: React.FC<{ collapsed: boolean }> = ({ collapsed }) => {
  const location = useLocation();
  const currentPath = location.pathname;

  const findSelectedKey = (items: any[], path: string): string => {
    for (const item of items) {
      if (item.children) {
        const childKey: string = findSelectedKey(item.children, path);
        if (childKey) return childKey;
      } else if (item.key === path) {
        return item.key;
      }
    }
    return '';
  };

  const selectedKey = findSelectedKey(menuItems, currentPath);

  return (
    <div className="sidebar">
      <div className="sidebar-logo">
        <h1>{collapsed ? 'TPA' : 'TPA SaaS'}</h1>
      </div>
      <Menu
        mode="inline"
        selectedKeys={[selectedKey]}
        items={menuItems}
        className="sidebar-menu"
      />
    </div>
  );
};

export default Sidebar;