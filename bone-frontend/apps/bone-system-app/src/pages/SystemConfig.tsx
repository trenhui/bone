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
import { resolveErrorMessage } from '@bone/shared-utils';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      const response = await systemConfigApi.getConfig();
      if (response.code === 200) {
        setConfigs(response.data.list);
      }
    } catch (error) {
      message.error(resolveErrorMessage(error, '获取配置失败'));
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
        message.success(t('system.systemConfig.updateSuccess'));
        setEditModalVisible(false);
        fetchConfigs();
      }
    } catch (error) {
      message.error(resolveErrorMessage(error, '更新配置失败'));
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
      message.error(resolveErrorMessage(error, '获取配置历史失败'));
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
      message.success(t('system.systemConfig.exportSuccess'));
    } catch (error) {
      message.error(resolveErrorMessage(error, '导出配置失败'));
    }
  };

  const handleImport = async (file: File) => {
    try {
      const response = await systemConfigApi.importConfig(file);
      if (response.code === 200) {
        message.success(t('system.systemConfig.importSuccess'));
        fetchConfigs();
      }
    } catch (error) {
      message.error(resolveErrorMessage(error, '导入配置失败'));
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
      title: t('system.systemConfig.configKeyLabel'),
      dataIndex: 'configKey',
      key: 'configKey',
    },
    {
      title: t('system.systemConfig.configValueLabel'),
      dataIndex: 'configValue',
      key: 'configValue',
      ellipsis: true,
    },
    {
      title: t('system.systemConfig.configTypeLabel'),
      dataIndex: 'configType',
      key: 'configType',
      render: getTypeTag,
    },
    {
      title: t('system.systemConfig.descriptionLabel'),
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: t('system.systemConfig.updatedAtLabel'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
    },
    {
      title: t('system.systemConfig.actionLabel'),
      key: 'action',
      render: (_: unknown, record: SystemConfig) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            {t('system.systemConfig.editButton')}
          </Button>
          <Button type="link" icon={<HistoryOutlined />} onClick={() => handleViewHistory(record)}>
            {t('system.systemConfig.historyButton')}
          </Button>
        </Space>
      ),
    },
  ];

  const historyColumns = [
    {
      title: t('system.systemConfig.oldValueLabel'),
      dataIndex: 'oldValue',
      key: 'oldValue',
      ellipsis: true,
    },
    {
      title: t('system.systemConfig.newValueLabel'),
      dataIndex: 'newValue',
      key: 'newValue',
      ellipsis: true,
    },
    {
      title: t('system.systemConfig.operatorLabel'),
      dataIndex: 'operator',
      key: 'operator',
    },
    {
      title: t('system.systemConfig.operationTimeLabel'),
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchConfigs}>
            {t('system.systemConfig.refreshButton')}
          </Button>
          <Upload beforeUpload={handleImport} showUploadList={false} accept=".json">
            <Button icon={<UploadOutlined />}>{t('system.systemConfig.importButton')}</Button>
          </Upload>
          <Button icon={<DownloadOutlined />} onClick={handleExport}>
            {t('system.systemConfig.exportButton')}
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
        title={t('system.systemConfig.editModalTitle')}
        open={editModalVisible}
        onOk={handleEditSubmit}
        onCancel={() => setEditModalVisible(false)}
      >
        <Form form={form} layout="vertical">
          <Form.Item label={t('system.systemConfig.configKeyLabel')} name="configKey">
            <Input disabled />
          </Form.Item>
          <Form.Item label={t('system.systemConfig.configValueLabel')} name="configValue" rules={[{ required: true }]}>
            <TextArea rows={4} />
          </Form.Item>
          <Form.Item label={t('system.systemConfig.configTypeLabel')} name="configType">
            <Select disabled>
              <Option value="SYSTEM">{t('system.systemConfig.typeSystem')}</Option>
              <Option value="SERVICE">{t('system.systemConfig.typeService')}</Option>
              <Option value="FEATURE">{t('system.systemConfig.typeFeature')}</Option>
            </Select>
          </Form.Item>
          <Form.Item label={t('system.systemConfig.descriptionLabel')} name="description">
            <TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t('system.systemConfig.historyModalTitle')}
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
