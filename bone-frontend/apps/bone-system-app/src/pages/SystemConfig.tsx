import React, { useState, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  message,
  Upload,
  Tag,
} from 'antd';
import {
  EditOutlined,
  HistoryOutlined,
  UploadOutlined,
  DownloadOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { SystemConfig, ConfigHistory } from '@/types';
import { systemConfigApi } from '@/services/api';

const { TextArea } = Input;
const { Option } = Select;

const SystemConfigPage: React.FC = () => {
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [editModalVisible, setEditModalVisible] = useState(false);
  const [historyModalVisible, setHistoryModalVisible] = useState(false);
  const [selectedConfig, setSelectedConfig] = useState<SystemConfig | null>(null);
  const [historyList, setHistoryList] = useState<ConfigHistory[]>([]);
  const [form] = Form.useForm();

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      const response = await systemConfigApi.getConfig();
      if (response.code === 200) {
        setConfigs(response.data.list);
      }
    } catch (error) {
      message.error('获取配置失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfigs();
  }, []);

  const handleEdit = (config: SystemConfig) => {
    setSelectedConfig(config);
    form.setFieldsValue(config);
    setEditModalVisible(true);
  };

  const handleEditSubmit = async () => {
    try {
      const values = await form.validateFields();
      const response = await systemConfigApi.updateConfig({
        ...selectedConfig,
        ...values,
      });
      if (response.code === 200) {
        message.success('更新配置成功');
        setEditModalVisible(false);
        fetchConfigs();
      }
    } catch (error) {
      message.error('更新配置失败');
    }
  };

  const handleViewHistory = async (config: SystemConfig) => {
    if (!config.id) return;
    try {
      const response = await systemConfigApi.getConfigHistory(config.id);
      if (response.code === 200) {
        setHistoryList(response.data);
        setSelectedConfig(config);
        setHistoryModalVisible(true);
      }
    } catch (error) {
      message.error('获取配置历史失败');
    }
  };

  const handleExport = async () => {
    try {
      const response = await systemConfigApi.exportConfig();
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'system-config.json');
      document.body.appendChild(link);
      link.click();
      message.success('导出配置成功');
    } catch (error) {
      message.error('导出配置失败');
    }
  };

  const handleImport = async (file: File) => {
    try {
      const response = await systemConfigApi.importConfig(file);
      if (response.code === 200) {
        message.success('导入配置成功');
        fetchConfigs();
      }
    } catch (error) {
      message.error('导入配置失败');
    }
    return false;
  };

  const getTypeTag = (type: string) => {
    const colorMap: Record<string, string> = {
      SYSTEM: 'blue',
      SERVICE: 'green',
      FEATURE: 'orange',
    };
    return <Tag color={colorMap[type]}>{type}</Tag>;
  };

  const columns = [
    {
      title: '配置键',
      dataIndex: 'configKey',
      key: 'configKey',
    },
    {
      title: '配置值',
      dataIndex: 'configValue',
      key: 'configValue',
      ellipsis: true,
    },
    {
      title: '类型',
      dataIndex: 'configType',
      key: 'configType',
      render: getTypeTag,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: SystemConfig) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button type="link" icon={<HistoryOutlined />} onClick={() => handleViewHistory(record)}>
            历史
          </Button>
        </Space>
      ),
    },
  ];

  const historyColumns = [
    {
      title: '旧值',
      dataIndex: 'oldValue',
      key: 'oldValue',
      ellipsis: true,
    },
    {
      title: '新值',
      dataIndex: 'newValue',
      key: 'newValue',
      ellipsis: true,
    },
    {
      title: '操作人',
      dataIndex: 'operator',
      key: 'operator',
    },
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchConfigs}>
            刷新
          </Button>
          <Upload beforeUpload={handleImport} showUploadList={false} accept=".json">
            <Button icon={<UploadOutlined />}>导入配置</Button>
          </Upload>
          <Button icon={<DownloadOutlined />} onClick={handleExport}>
            导出配置
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={configs}
        rowKey="id"
        loading={loading}
      />

      <Modal
        title="编辑配置"
        open={editModalVisible}
        onOk={handleEditSubmit}
        onCancel={() => setEditModalVisible(false)}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="配置键" name="key">
            <Input disabled />
          </Form.Item>
          <Form.Item label="配置值" name="value" rules={[{ required: true }]}>
            <TextArea rows={4} />
          </Form.Item>
          <Form.Item label="类型" name="type" rules={[{ required: true }]}>
            <Select>
              <Option value="SYSTEM">系统级</Option>
              <Option value="SERVICE">服务级</Option>
              <Option value="FEATURE">功能级</Option>
            </Select>
          </Form.Item>
          <Form.Item label="描述" name="description">
            <TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`配置历史 - ${selectedConfig?.key}`}
        open={historyModalVisible}
        onCancel={() => setHistoryModalVisible(false)}
        footer={null}
        width={800}
      >
        <Table
          columns={historyColumns}
          dataSource={historyList}
          rowKey="id"
          pagination={false}
        />
      </Modal>
    </div>
  );
};

export default SystemConfigPage;
