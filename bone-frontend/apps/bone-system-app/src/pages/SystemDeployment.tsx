import React, { useState, useEffect } from 'react';
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
  id: number;
  version: string;
  status: 'success' | 'failed' | 'pending' | 'running';
  startTime: string;
  endTime?: string;
  operator: string;
  description?: string;
}

const SystemDeploymentPage: React.FC = () => {
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
        setSystemInfo(response.data);
      }
    } catch (error) {
      message.error('获取系统信息失败');
    }
  };

  const fetchDeploymentRecords = () => {
    // 模拟数据
    setDeploymentRecords([
      {
        id: 1,
        version: 'v1.2.3',
        status: 'success',
        startTime: '2024-04-20 10:00:00',
        endTime: '2024-04-20 10:05:30',
        operator: 'admin',
        description: '例行升级',
      },
      {
        id: 2,
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
        message.success('部署成功');
        setDeployModalVisible(false);
        fetchSystemInfo();
        fetchDeploymentRecords();
      }
    } catch (error) {
      message.error('部署失败');
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
        message.success('升级成功');
        setUpgradeModalVisible(false);
        fetchSystemInfo();
        fetchDeploymentRecords();
      }
    } catch (error) {
      message.error('升级失败');
    } finally {
      setUpgradeLoading(false);
    }
  };

  const handleRestart = async () => {
    try {
      const response = await systemApi.restart();
      if (response.code === 200) {
        message.success('重启命令已发送');
      }
    } catch (error) {
      message.error('重启失败');
    }
  };

  const handleShutdown = async () => {
    try {
      const response = await systemApi.shutdown();
      if (response.code === 200) {
        message.success('关闭命令已发送');
      }
    } catch (error) {
      message.error('关闭失败');
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
      success: '成功',
      failed: '失败',
      pending: '待处理',
      running: '进行中',
    };
    return <Tag color={colorMap[status]}>{labelMap[status] || status}</Tag>;
  };

  const recordColumns = [
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: getStatusTag,
    },
    {
      title: '开始时间',
      dataIndex: 'startTime',
      key: 'startTime',
    },
    {
      title: '结束时间',
      dataIndex: 'endTime',
      key: 'endTime',
    },
    {
      title: '操作人',
      dataIndex: 'operator',
      key: 'operator',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
  ];

  return (
    <div>
      {/* 系统概览 */}
      {systemInfo && (
        <Card title="系统概览" style={{ marginBottom: 16 }}>
          <Descriptions column={2}>
            <Descriptions.Item label="当前版本">{systemInfo.version}</Descriptions.Item>
            <Descriptions.Item label="运行时间">{systemInfo.uptime}</Descriptions.Item>
            <Descriptions.Item label="健康状态">
              {getStatusTag(systemInfo.healthStatus)}
            </Descriptions.Item>
            <Descriptions.Item label="服务数量">{systemInfo.services.length}</Descriptions.Item>
          </Descriptions>
          <div style={{ marginTop: 16 }}>
            <Space>
              <Button type="primary" icon={<CloudUploadOutlined />} onClick={() => setDeployModalVisible(true)}>
                部署系统
              </Button>
              <Button icon={<CloudUploadOutlined />} onClick={() => setUpgradeModalVisible(true)}>
                升级版本
              </Button>
              <Button icon={<PlayCircleOutlined />} onClick={handleRestart}>
                重启系统
              </Button>
              <Button danger icon={<StopOutlined />} onClick={handleShutdown}>
                关闭系统
              </Button>
              <Button icon={<HistoryOutlined />} onClick={() => setHistoryModalVisible(true)}>
                部署历史
              </Button>
              <Button icon={<ReloadOutlined />} onClick={fetchSystemInfo}>
                刷新
              </Button>
            </Space>
          </div>
        </Card>
      )}

      {/* Kubernetes 配置 */}
      <Card title="Kubernetes 配置" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={8}>
            <Card>
              <Statistic title="Pod 副本数" value={3} />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title="自动伸缩" value="已启用" />
            </Card>
          </Col>
          <Col span={8}>
            <Card>
              <Statistic title="滚动更新" value="已配置" />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* Helm Chart 信息 */}
      <Card title="Helm Chart 信息">
        <Descriptions column={2}>
          <Descriptions.Item label="Chart 名称">bone-platform</Descriptions.Item>
          <Descriptions.Item label="Chart 版本">v1.2.3</Descriptions.Item>
          <Descriptions.Item label="App 版本">{systemInfo?.version || '-'}</Descriptions.Item>
          <Descriptions.Item label="Release 名称">bone-production</Descriptions.Item>
        </Descriptions>
      </Card>

      {/* 部署弹窗 */}
      <Modal
        title="部署系统"
        open={deployModalVisible}
        onOk={handleDeploy}
        onCancel={() => setDeployModalVisible(false)}
        confirmLoading={deployLoading}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="环境" name="environment" rules={[{ required: true }]} initialValue="production">
            <Select>
              <Option value="development">开发环境</Option>
              <Option value="testing">测试环境</Option>
              <Option value="staging">预发布环境</Option>
              <Option value="production">生产环境</Option>
            </Select>
          </Form.Item>
          <Form.Item label="版本" name="version" rules={[{ required: true }]}>
            <Input placeholder="例如: v1.2.3" />
          </Form.Item>
          <Form.Item label="副本数" name="replicas" rules={[{ required: true }]} initialValue={3}>
            <InputNumber min={1} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="启用自动伸缩" name="autoScaling" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} placeholder="部署说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 升级弹窗 */}
      <Modal
        title="升级系统"
        open={upgradeModalVisible}
        onOk={handleUpgrade}
        onCancel={() => setUpgradeModalVisible(false)}
        confirmLoading={upgradeLoading}
        width={500}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="目标版本" name="version" rules={[{ required: true }]}>
            <Input placeholder="例如: v1.2.4" />
          </Form.Item>
          <Form.Item label="描述" name="description">
            <Input.TextArea rows={3} placeholder="升级说明" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 部署历史弹窗 */}
      <Modal
        title="部署历史"
        open={historyModalVisible}
        onCancel={() => setHistoryModalVisible(false)}
        footer={null}
        width={800}
      >
        <Table
          columns={recordColumns}
          dataSource={deploymentRecords}
          rowKey="id"
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Modal>
    </div>
  );
};

export default SystemDeploymentPage;
