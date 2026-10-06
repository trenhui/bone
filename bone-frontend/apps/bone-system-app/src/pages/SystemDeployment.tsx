import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Card,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  InputNumber,
  Switch,
  message,
  Table,
  Tag,
  Row,
  Col,
  Statistic,
  Descriptions,
} from 'antd';
import {
  CloudUploadOutlined,
  ReloadOutlined,
  PlayCircleOutlined,
  StopOutlined,
  HistoryOutlined,
} from '@ant-design/icons';
import { systemApi } from '@/services/api';
import type { SystemInfo } from '@/types';

const { Option } = Select;

interface DeploymentRecord {
  id: string;
  version: string;
  status: 'success' | 'failed' | 'pending' | 'running';
  startTime: string;
  endTime?: string;
  operator: string;
  description?: string;
}

const SystemDeploymentPage: React.FC = () => {
  const { t } = useTranslation();
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [deployModalVisible, setDeployModalVisible] = useState(false);
  const [upgradeModalVisible, setUpgradeModalVisible] = useState(false);
  const [historyModalVisible, setHistoryModalVisible] = useState(false);
  const [deployLoading, setDeployLoading] = useState(false);
  const [upgradeLoading, setUpgradeLoading] = useState(false);
  const [deploymentRecords, setDeploymentRecords] = useState<DeploymentRecord[]>([]);
  const [form] = Form.useForm();

  const fetchSystemInfo = async () => {
    try {
      const response = await systemApi.getInfo();
      if (response.code === 200) {
        // 后端 /system/info 仅返回 name/description/version，缺失 uptime/healthStatus/services
        // 时需兜底，否则渲染期取 .length 会整页崩溃（同 MonitorAlert 的归一化约定）。
        const raw = (response.data ?? {}) as unknown as Record<string, unknown>;
        setSystemInfo({
          ...raw,
          version: (raw.version ?? '-') as string,
          uptime: (raw.uptime ?? '-') as string,
          healthStatus: (raw.healthStatus ?? raw.status ?? 'UNKNOWN') as string,
          services: (raw.services ?? []) as string[],
        } as SystemInfo);
      }
    } catch (error) {
      message.error(t('system.systemDeployment.fetchSystemInfoFailed'));
    }
  };

  const fetchDeploymentRecords = () => {
    // 模拟数据（当前后端未提供部署历史端点，页面先用占位数据保持可交互）
    setDeploymentRecords([
      {
        id: '1',
        version: 'v1.2.3',
        status: 'success',
        startTime: '2024-04-20 10:00:00',
        endTime: '2024-04-20 10:05:30',
        operator: 'admin',
        description: '例行升级',
      },
      {
        id: '2',
        version: 'v1.2.2',
        status: 'failed',
        startTime: '2024-04-19 15:00:00',
        endTime: '2024-04-19 15:10:00',
        operator: 'admin',
        description: '配置错误回滚',
      },
    ]);
  };

  useEffect(() => {
    fetchSystemInfo();
    fetchDeploymentRecords();
  }, []);

  const handleDeploy = async () => {
    try {
      const values = await form.validateFields();
      setDeployLoading(true);
      const response = await systemApi.deploy(values);
      if (response.code === 200) {
        message.success(t('system.systemDeployment.deployedSuccess'));
        setDeployModalVisible(false);
        fetchSystemInfo();
        fetchDeploymentRecords();
      }
    } catch (error) {
      message.error(t('system.systemDeployment.deployFailed'));
    } finally {
      setDeployLoading(false);
    }
  };

  const handleUpgrade = async () => {
    try {
      const values = await form.validateFields();
      setUpgradeLoading(true);
      const response = await systemApi.upgrade(values.version);
      if (response.code === 200) {
        message.success(t('system.systemDeployment.upgradeSuccess'));
        setUpgradeModalVisible(false);
        fetchSystemInfo();
        fetchDeploymentRecords();
      }
    } catch (error) {
      message.error(t('system.systemDeployment.upgradeFailed'));
    } finally {
      setUpgradeLoading(false);
    }
  };

  const handleRestart = async () => {
    try {
      const response = await systemApi.restart();
      if (response.code === 200) {
        message.success(t('system.systemDeployment.restartCommandSent'));
      }
    } catch (error) {
      message.error(t('system.systemDeployment.restartFailed'));
    }
  };

  const handleShutdown = async () => {
    try {
      const response = await systemApi.shutdown();
      if (response.code === 200) {
        message.success(t('system.systemDeployment.shutdownCommandSent'));
      }
    } catch (error) {
      message.error(t('system.systemDeployment.shutdownFailed'));
    }
  };

  const getStatusTag = (status: string) => {
    const colorMap: Record<string, string> = {
      success: 'green',
      failed: 'red',
      pending: 'orange',
      running: 'blue',
    };
    const labelMap: Record<string, string> = {
      success: t('system.systemDeployment.statusSuccess'),
      failed: t('system.systemDeployment.statusFailed'),
      pending: t('system.systemDeployment.statusPending'),
      running: t('system.systemDeployment.statusRunning'),
    };
    return <Tag color={colorMap[status]}>{labelMap[status] || status}</Tag>;
  };

  const recordColumns = [
    {
      title: t('system.systemDeployment.version'),
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: t('system.systemDeployment.status'),
      dataIndex: 'status',
      key: 'status',
      render: getStatusTag,
    },
    {
      title: t('system.systemDeployment.startTime'),
      dataIndex: 'startTime',
      key: 'startTime',
    },
    {
      title: t('system.systemDeployment.endTime'),
      dataIndex: 'endTime',
      key: 'endTime',
    },
    {
      title: t('system.systemDeployment.operator'),
      dataIndex: 'operator',
      key: 'operator',
    },
    {
      title: t('system.systemDeployment.description'),
      dataIndex: 'description',
      key: 'description',
    },
  ];

  return (
    <div>
      {/* 系统概览 */}
      {systemInfo && (
        <Card title={t('system.systemDeployment.overview')} style={{ marginBottom: 16 }}>
          <Descriptions column={2}>
            <Descriptions.Item label={t('system.systemDeployment.currentVersion')}>{systemInfo.version}</Descriptions.Item>
            <Descriptions.Item label={t('system.systemDeployment.uptime')}>{systemInfo.uptime}</Descriptions.Item>
            <Descriptions.Item label={t('system.systemDeployment.healthStatus')}>
              {getStatusTag(systemInfo.healthStatus)}
            </Descriptions.Item>
            <Descriptions.Item label={t('system.systemDeployment.serviceCount')}>{systemInfo.services?.length ?? 0}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 16 }}>
            <Space>
              <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setDeployModalVisible(true)}>
                {t('system.systemDeployment.deploySystem')}
              </Button>
              <Button icon={<CloudUploadOutlined />} onClick={() => setUpgradeModalVisible(true)}>
                {t('system.systemDeployment.upgradeVersion')}
              </Button>
              <Button icon={<PlayCircleOutlined />} onClick={handleRestart}>
                {t('system.systemDeployment.restartSystem')}
              </Button>
              <Button danger icon={<StopOutlined />} onClick={handleShutdown}>
                {t('system.systemDeployment.shutdownSystem')}
              </Button>
              <Button icon={<HistoryOutlined />} onClick={() => setHistoryModalVisible(true)}>
                {t('system.systemDeployment.deployHistory')}
              </Button>
              <Button icon={<ReloadOutlined />} onClick={fetchSystemInfo}>
                {t('system.systemDeployment.refresh')}
              </Button>
            </Space>
          </div>
        </Card>
      )}

      {/* Kubernetes 配置 */}
      <Card title={t('system.systemDeployment.kubernetesConfig')} style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Card>
              <Statistic title={t('system.systemDeployment.podReplicas')} value={3} />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title={t('system.systemDeployment.autoScaling')} value={t('system.systemDeployment.autoScalingEnabled')} />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title={t('system.systemDeployment.rollingUpdate')} value={t('system.systemDeployment.rollingUpdateConfigured')} />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Helm Chart 信息 */}
      <Card title={t('system.systemDeployment.helmChartInfo')}>
        <Descriptions column={2}>
          <Descriptions.Item label={t('system.systemDeployment.chartName')}>bone-platform</Descriptions.Item>
          <Descriptions.Item label={t('system.systemDeployment.chartVersion')}>v1.2.3</Descriptions.Item>
          <Descriptions.Item label={t('system.systemDeployment.appVersion')}>{systemInfo?.version || '-'}</Descriptions.Item>
          <Descriptions.Item label={t('system.systemDeployment.releaseName')}>bone-production</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 部署弹窗 */}
      <Modal
        title={t('system.systemDeployment.deploySystem')}
        open={deployModalVisible}
        onOk={handleDeploy}
        onCancel={() => setDeployModalVisible(false)}
        confirmLoading={deployLoading}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item label={t('system.systemDeployment.environment')} name="environment" rules={[{ required: true }]} initialValue="production">
            <Select>
              <Option value="development">{t('system.systemDeployment.envDevelopment')}</Option>
              <Option value="testing">{t('system.systemDeployment.envTesting')}</Option>
              <Option value="staging">{t('system.systemDeployment.envStaging')}</Option>
              <Option value="production">{t('system.systemDeployment.envProduction')}</Option>
            </Select>
          </Form.Item>
          <Form.Item label={t('system.systemDeployment.version')} name="version" rules={[{ required: true }]}>
            <Input placeholder={t('system.systemDeployment.versionPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('system.systemDeployment.replicas')} name="replicas" rules={[{ required: true }]} initialValue={3}>
            <InputNumber min={1} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={t('system.systemDeployment.enableAutoScaling')} name="autoScaling" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
          <Form.Item label={t('system.systemDeployment.description')} name="description">
            <Input.TextArea rows={3} placeholder={t('system.systemDeployment.deployDescriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 升级弹窗 */}
      <Modal
        title={t('system.systemDeployment.upgradeSystem')}
        open={upgradeModalVisible}
        onOk={handleUpgrade}
        onCancel={() => setUpgradeModalVisible(false)}
        confirmLoading={upgradeLoading}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        width={500}
      >
        <Form form={form} layout="vertical">
          <Form.Item label={t('system.systemDeployment.targetVersion')} name="version" rules={[{ required: true }]}>
            <Input placeholder={t('system.systemDeployment.targetVersionPlaceholder')} />
          </Form.Item>
          <Form.Item label={t('system.systemDeployment.description')} name="description">
            <Input.TextArea rows={3} placeholder={t('system.systemDeployment.upgradeDescriptionPlaceholder')} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 部署历史弹窗 */}
      <Modal
        title={t('system.systemDeployment.deployHistory')}
        open={historyModalVisible}
        onCancel={() => setHistoryModalVisible(false)}
        footer={null}
        width={800}
      >
        <Table
          columns={recordColumns}
          dataSource={deploymentRecords}
          rowKey="id"
          locale={{ emptyText: t('common.empty') }}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => t('system.systemDeployment.totalRecords', { total }),
          }}
        />
      </Modal>
    </div>
  );
};

export default SystemDeploymentPage;
