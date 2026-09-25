import React, { useEffect, useState } from 'react';
import { Card, Form, InputNumber, Switch, Select, Button, Divider, Typography, Space, Alert } from 'antd';
import { App as AntdApp } from 'antd';
import { SaveOutlined, InfoCircleOutlined } from '@ant-design/icons';
import * as api from '../services/api';
import ModulePage from '../components/ModulePage';

const { Title } = Typography;

const AuditSettingsPage: React.FC = () => {
  const { message: messageApi } = AntdApp.useApp();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  /** 加载审计设置 */
  const fetchSettings = async () => {
    setLoading(true);
    try {
      const response = await api.getAuditSettings();
      if (response.code === 200 && response.data) {
        form.setFieldsValue(response.data);
      }
    } catch {
      messageApi.error('获取审计设置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void fetchSettings();
  }, []);

  /** 保存审计设置 */
  const handleSave = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const response = await api.updateAuditSettings(values);
      if (response.code === 200) {
        messageApi.success('审计设置已保存');
      } else {
        messageApi.error(response.message || '保存失败');
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        messageApi.error(err.message);
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <ModulePage title="审计设置" description="配置审计日志的采集范围与保留策略。" card={false}>

      <Alert
        type="info"
        icon={<InfoCircleOutlined />}
        showIcon
        message="审计日志记录系统中所有敏感操作，包括登录、账号变更、权限修改等。合理配置审计策略有助于安全合规和问题追溯。"
        style={{ marginBottom: 24 }}
      />

      <Card loading={loading}>
        <Form form={form} layout="vertical" initialValues={{
          retentionDays: 90,
          autoArchiveEnabled: false,
          archiveAfterDays: 30,
          storageType: 'DATABASE',
          wormEnabled: false,
        }}>
          {/* 日志保留 */}
          <Title level={5}>日志保留</Title>
          <Form.Item
            name="retentionDays"
            label="保留天数"
            tooltip="超过保留天数的审计日志将被自动清理"
            rules={[{ required: true, message: '请输入保留天数' }]}
          >
            <InputNumber min={1} max={3650} addonAfter="天" style={{ width: 200 }} />
          </Form.Item>

          <Divider />

          {/* 自动归档 */}
          <Title level={5}>自动归档</Title>
          <Form.Item
            name="autoArchiveEnabled"
            label="启用自动归档"
            valuePropName="checked"
            tooltip="开启后，超过指定天数的日志将自动归档到外部存储"
          >
            <Switch checkedChildren="开" unCheckedChildren="关" />
          </Form.Item>
          <Form.Item
            noStyle
            shouldUpdate={(prev, cur) => prev.autoArchiveEnabled !== cur.autoArchiveEnabled}
          >
            {({ getFieldValue }) =>
              getFieldValue('autoArchiveEnabled') ? (
                <Form.Item
                  name="archiveAfterDays"
                  label="归档天数"
                  tooltip="超过此天数的日志将被归档"
                  rules={[{ required: true, message: '请输入归档天数' }]}
                >
                  <InputNumber min={1} max={3650} addonAfter="天" style={{ width: 200 }} />
                </Form.Item>
              ) : null
            }
          </Form.Item>

          <Divider />

          {/* 存储配置 */}
          <Title level={5}>存储配置</Title>
          <Form.Item
            name="storageType"
            label="存储方式"
            tooltip="选择审计日志的存储后端"
            rules={[{ required: true, message: '请选择存储方式' }]}
          >
            <Select style={{ width: 200 }}>
              <Select.Option value="DATABASE">数据库</Select.Option>
              <Select.Option value="MINIO">MinIO 对象存储</Select.Option>
              <Select.Option value="S3">AWS S3</Select.Option>
            </Select>
          </Form.Item>

          <Divider />

          {/* WORM 模式 */}
          <Title level={5}>合规保护</Title>
          <Form.Item
            name="wormEnabled"
            label="WORM 模式（一次写入，多次读取）"
            valuePropName="checked"
            tooltip="开启后审计日志不可修改或删除，满足金融等行业合规要求"
          >
            <Switch checkedChildren="开" unCheckedChildren="关" />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, cur) => prev.wormEnabled !== cur.wormEnabled}>
            {({ getFieldValue }) =>
              getFieldValue('wormEnabled') ? (
                <Alert
                  type="warning"
                  message="WORM 模式已启用，审计日志将不可修改或删除。此操作不可逆，请谨慎开启。"
                  style={{ marginBottom: 16 }}
                />
              ) : null
            }
          </Form.Item>

          <Divider />

          <Form.Item>
            <Space>
              <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={handleSave}>
                保存设置
              </Button>
              <Button onClick={() => form.resetFields()}>重置</Button>
            </Space>
          </Form.Item>
        </Form>
      </Card>
    </ModulePage>
  );
};

export default AuditSettingsPage;
