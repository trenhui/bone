import React from 'react';
import { Dropdown, Badge, Avatar } from 'antd';
import { UserOutlined, BellOutlined } from '@ant-design/icons';
import './NavBar.css';

const NavBar: React.FC = () => {
  const userMenu = [
    {
      key: 'profile',
      label: '个人资料',
    },
    {
      key: 'settings',
      label: '设置',
    },
    {
      key: 'logout',
      label: '退出登录',
    },
  ];

  return (
    <div className="navbar">
      <div className="navbar-left">
        {/* 左侧内容 */}
      </div>
      <div className="navbar-right">
        <Badge count={5} className="navbar-badge">
          <BellOutlined className="navbar-icon" />
        </Badge>
        <Dropdown menu={{ items: userMenu }} placement="bottomRight">
          <div className="navbar-user">
            <Avatar icon={<UserOutlined />} />
            <span className="navbar-username">管理员</span>
          </div>
        </Dropdown>
      </div>
    </div>
  );
};

export default NavBar;