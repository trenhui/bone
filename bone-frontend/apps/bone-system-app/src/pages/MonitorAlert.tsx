import React, { useState, useEffect } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Table,
  Button,
  Space,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  message,
  Popconfirm,
  Tag,
  Progress,
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { AlertRule, AlertRecord, Metrics, SystemInfo } from '@/types';
import { monitorApi } from '@/services/api';

const { Option } = Select;

const MonitorAlertPage: React.FC = () => {
  const [metrics, setMetrics] = useState<Metrics | null>(null);
  const [systemInfo, setSystemInfo] = useState<SystemInfo | null>(null);
  const [alertRules, setAlertRules] = useState<AlertRule[]>([]);
  const [alertEvents, setAlertEvents] = useState<AlertRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [ruleModalVisible, setRuleModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<AlertRule | null>(null);
  const [form] = Form.useForm();

  const fetchData = async () => {
    setLoading(true);
    try {
      const [metricsRes, healthRes, rulesRes, eventsRes] = await Promise.all([
        monitorApi.getMetrics(),
        monitorApi.getHealth(),
        monitorApi.getAlertRules({ pageNum: 1, pageSize: 100 }),
        monitorApi.getAlertEvents({ pageNum: 1, pageSize: 10 }),
      ]);

      // 后端 /system/metrics 返回 Micrometer 原始键值对，归一化为页面期待的 Metrics 形状
      if (metricsRes.code === 200) {
        const raw = (metricsRes.data ?? {}) as unknown as Record<string, number>;
        const memMax = raw['jvm.memory.max'] ?? -1;
        const memUsed = raw['jvm.memory.used'] ?? 0;
        setMetrics({
          ...raw,
          cpu: raw.cpu ?? 0,
          memory: memMax > 0 ? Math.round((memUsed / memMax) * 1000) / 10 : 0,
          disk: raw.disk ?? 0,
          errorRate: raw.errorRate ?? 0,
          apiResponseTime: raw.apiResponseTime ?? 0,
          qps: raw.qps ?? 0,
        } as Metrics);
      }
      // 后端 /system/health 返回 Actuator 结构（status/components），映射为 SystemInfo
      if (healthRes.code === 200) {
        const h = (healthRes.data ?? {}) as unknown as Record<string, unknown>;
        setSystemInfo({
          ...h,
          healthStatus: (h.healthStatus ?? h.status ?? 'UNKNOWN') as string,
          version: (h.version ?? '-') as string,
          uptime: (h.uptime ?? '-') as string,
          services: (h.services ?? []) as string[],
        } as SystemInfo);
      }
      if (rulesRes.code === 200) setAlertRules(rulesRes.data.list ?? []);
      if (eventsRes.code === 200) setAlertEvents(eventsRes.data.list ?? []);
    } catch (error) {
      message.error('获取数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    const interval = setInterval(fetchData, 30000);
    return () => clearInterval(interval);
  }, []);

  const handleCreateRule = () => {
    setEditingRule(null);
    form.resetFields();
    setRuleModalVisible(true);
  };

  const handleEditRule = (rule: AlertRule) => {
    setEditingRule(rule);
    form.setFieldsValue({
      ...rule,
      notificationChannels: rule.notificationChannels.join(','),
    });
    setRuleModalVisible(true);
  };

  const handleSaveRule = async () => {
    try {
      const values = await form.validateFields();
      const ruleData = {
        ...values,
        notificationChannels: values.notificationChannels.split(',').map((s: string) => s.trim()),
      };

      let response;
      if (editingRule?.id) {
        response = await monitorApi.updateAlertRule({ ...ruleData, id: editingRule.id });
      } else {
        response = await monitorApi.createAlertRule(ruleData);
      }

      if (response.code === 200) {
        message.success('保存告警规则成功');
        setRuleModalVisible(false);
        fetchData();
      }
    } catch (error) {
      message.error('保存告警规则失败');
    }
  };

  const handleDeleteRule = async (id: number) => {
    try {
      const response = await monitorApi.deleteAlertRule(id);
      if (response.code === 200) {
        message.success('删除告警规则成功');
        fetchData();
      }
    } catch (error) {
      message.error('删除告警规则失败');
    }
  };

  const handleToggleRule = async (id: number, enabled: boolean) => {
    try {
      const response = enabled
        ? await monitorApi.enableAlertRule(id)
        : await monitorApi.disableAlertRule(id);
      if (response.code === 200) {
        message.success(enabled ? '启用告警规则成功' : '禁用告警规则成功');
        fetchData();
      }
    } catch (error) {
      message.error('操作失败');
    }
  };

  const getLevelTag = (level: string) => {
    const colorMap: Record<string, string> = {
      CRITICAL: 'red',
      WARNING: 'orange',
      INFO: 'blue',
      TRIGGERED: 'red',
      RESOLVED: 'green',
    };
    const labelMap: Record<string, string> = {
      CRITICAL: '严重',
      WARNING: '警告',
      INFO: '信息',
      TRIGGERED: '已触发',
      RESOLVED: '已解决',
    };
    return <Tag color={colorMap[level]}>{labelMap[level] || level}</Tag>;
  };

  const getHealthStatusLabel = (status: string) => {
    const labelMap: Record<string, string> = {
      HEALTHY: '健康',
      UNHEALTHY: '异常',
      DEGRADED: '降级',
    };
    return labelMap[status] || status;
  };

  const getHealthStatusColor = (status: string) => {
    const colorMap: Record<string, string> = {
      HEALTHY: '#3f8600',
      UNHEALTHY: '#cf1322',
      DEGRADED: '#fa8c16',
    };
    return colorMap[status] || '#000000';
  };

  const ruleColumns = [
    {
      title: '规则名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '监控指标',
      dataIndex: 'metric',
      key: 'metric',
    },
    {
      title: '阈值',
      dataIndex: 'threshold',
      key: 'threshold',
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      render: getLevelTag,
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean, record: AlertRule) => (
        <Switch
          checked={enabled}
          onChange={(checked) => handleToggleRule(record.id!, checked)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: AlertRule) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEditRule(record)}>
            编辑
          </Button>
          <Popconfirm
            title="确定要删除此规则吗？"
            onConfirm={() => handleDeleteRule(record.id!)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const eventColumns = [
    {
      title: '告警消息',
      dataIndex: 'message',
      key: 'message',
    },
    {
      title: '实际值',
      dataIndex: 'value',
      key: 'value',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: getLevelTag,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: '解决时间',
      dataIndex: 'resolveTime',
      key: 'resolveTime',
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>
          刷新数据
        </Button>
      </div>

      {/* 系统健康状态 */}
      {systemInfo && (
        <Card title="系统状态" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic
                title="系统状态"
                value={getHealthStatusLabel(systemInfo.healthStatus)}
                valueStyle={{ color: getHealthStatusColor(systemInfo.healthStatus) }}
                prefix={systemInfo.healthStatus === 'HEALTHY' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
              />
            </Col>
            <Col span={6}>
              <Statistic title="版本" value={systemInfo.version} />
            </Col>
            <Col span={6}>
              <Statistic title="运行时间" value={systemInfo.uptime} />
            </Col>
            <Col span={6}>
              <Statistic title="服务数量" value={systemInfo.services?.length ?? 0} />
            </Col>
          </Row>
        </Card>
      )}

      {/* 监控指标 */}
      {metrics && (
        <Card title="监控指标" style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={6}>
              <Card>
                <Statistic title="CPU 使用率" value={metrics.cpu} suffix="%" />
                <Progress percent={metrics.cpu} status={metrics.cpu > 80 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title="内存使用率" value={metrics.memory} suffix="%" />
                <Progress percent={metrics.memory} status={metrics.memory > 85 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title="磁盘使用率" value={metrics.disk} suffix="%" />
                <Progress percent={metrics.disk} status={metrics.disk > 90 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title="API 错误率" value={metrics.errorRate} suffix="%" />
                <Progress percent={metrics.errorRate} status={metrics.errorRate > 5 ? 'exception' : 'normal'} />
              </Card>
            </Col>
          </Row>
          <Row gutter={16} style={{ marginTop: 16 }}>
            <Col span={8}>
              <Card>
                <Statistic title="API 响应时间" value={metrics.apiResponseTime} suffix="ms" />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic title="QPS" value={metrics.qps} />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic title="数据库连接数" value={metrics.dbConnections} />
              </Card>
            </Col>
          </Row>
        </Card>
      )}

      {/* 告警规则 */}
      <Card
        title="告警规则"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRule}>
            创建规则
          </Button>
        }
        style={{ marginBottom: 16 }}
      >
        <Table
          columns={ruleColumns}
          dataSource={alertRules}
          rowKey="id"
          loading={loading}
        />
      </Card>

      {/* 最近告警事件 */}
      <Card title="最近告警事件">
        <Table
          columns={eventColumns}
          dataSource={alertEvents}
          rowKey="id"
          loading={loading}
          pagination={false}
        />
      </Card>

      <Modal
        title={editingRule ? '编辑告警规则' : '创建告警规则'}
        open={ruleModalVisible}
        onOk={handleSaveRule}
        onCancel={() => setRuleModalVisible(false)}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item label="规则名称" name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label="监控指标" name="metric" rules={[{ required: true }]}>
            <Select>
              <Option value="cpu">CPU 使用率</Option>
              <Option value="memory">内存使用率</Option>
              <Option value="disk">磁盘使用率</Option>
              <Option value="errorRate">API 错误率</Option>
              <Option value="apiResponseTime">API 响应时间</Option>
            </Select>
          </Form.Item>
          <Form.Item label="阈值" name="threshold" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="告警级别" name="level" rules={[{ required: true }]}>
            <Select>
              <Option value="CRITICAL">严重</Option>
              <Option value="WARNING">警告</Option>
              <Option value="INFO">信息</Option>
            </Select>
          </Form.Item>
          <Form.Item
            label="通知渠道（多个用逗号分隔）"
            name="notificationChannels"
            rules={[{ required: true }]}
          >
            <Input placeholder="email, sms, wechat, dingtalk" />
          </Form.Item>
          <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MonitorAlertPage;
