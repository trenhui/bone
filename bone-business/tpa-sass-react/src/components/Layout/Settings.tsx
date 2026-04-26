import React, { useState } from 'react';
import { Drawer, Switch, Select, Radio, Divider } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import './Settings.css';

const { Option } = Select;

const Settings: React.FC = () => {
  const [open, setOpen] = useState(false);
  const [settings, setSettings] = useState({
    fixedHeader: true,
    tagsView: true,
    layout: 'left',
    theme: 'light',
  });

  const showDrawer = () => {
    setOpen(true);
  };

  const onClose = () => {
    setOpen(false);
  };

  const handleSettingChange = (key: string, value: any) => {
    setSettings({
      ...settings,
      [key]: value,
    });
  };

  return (
    <>
      <div className="settings-trigger" onClick={showDrawer}>
        <SettingOutlined />
      </div>
      <Drawer
        title="系统设置"
        placement="right"
        onClose={onClose}
        open={open}
        width={300}
      >
        <div className="settings-item">
          <span>固定头部</span>
          <Switch
            checked={settings.fixedHeader}
            onChange={(value) => handleSettingChange('fixedHeader', value)}
          />
        </div>
        <div className="settings-item">
          <span>标签页视图</span>
          <Switch
            checked={settings.tagsView}
            onChange={(value) => handleSettingChange('tagsView', value)}
          />
        </div>
        <Divider />
        <div className="settings-item">
          <span>布局模式</span>
          <Select
            value={settings.layout}
            onChange={(value) => handleSettingChange('layout', value)}
            style={{ width: 120 }}
          >
            <Option value="left">左侧菜单</Option>
            <Option value="top">顶部菜单</Option>
            <Option value="mix">混合菜单</Option>
          </Select>
        </div>
        <Divider />
        <div className="settings-item">
          <span>主题模式</span>
          <Radio.Group
            value={settings.theme}
            onChange={(e) => handleSettingChange('theme', e.target.value)}
          >
            <Radio.Button value="light">亮色</Radio.Button>
            <Radio.Button value="dark">暗色</Radio.Button>
          </Radio.Group>
        </div>
      </Drawer>
    </>
  );
};

export default Settings;