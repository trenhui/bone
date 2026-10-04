import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      if (rulesRes.code === 200) setAlertRules(rulesRes.data.records ?? []);
      if (eventsRes.code === 200) setAlertEvents(eventsRes.data.records ?? []);
    } catch (error) {
      message.error(t('system.monitorAlert.fetchDataFailed'));
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
        message.success(t('system.monitorAlert.saveRuleSuccess'));
        setRuleModalVisible(false);
        fetchData();
      }
    } catch (error) {
      message.error(t('system.monitorAlert.saveRuleFailed'));
    }
  };

  const handleDeleteRule = async (id: string | string) => {
    try {
      const response = await monitorApi.deleteAlertRule(id);
      if (response.code === 200) {
        message.success(t('system.monitorAlert.deleteRuleSuccess'));
        fetchData();
      }
    } catch (error) {
      message.error(t('system.monitorAlert.deleteRuleFailed'));
    }
  };

  const handleToggleRule = async (id: string | string, enabled: boolean) => {
    try {
      const response = enabled
        ? await monitorApi.enableAlertRule(id)
        : await monitorApi.disableAlertRule(id);
      if (response.code === 200) {
        message.success(
          enabled
            ? t('system.monitorAlert.enableRuleSuccess')
            : t('system.monitorAlert.disableRuleSuccess'),
        );
        fetchData();
      }
    } catch (error) {
      message.error(t('system.monitorAlert.operationFailed'));
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
      CRITICAL: t('system.monitorAlert.levelCritical'),
      WARNING: t('system.monitorAlert.levelWarning'),
      INFO: t('system.monitorAlert.levelInfo'),
      TRIGGERED: t('system.monitorAlert.statusTriggered'),
      RESOLVED: t('system.monitorAlert.statusResolved'),
    };
    return <Tag color={colorMap[level]}>{labelMap[level] || level}</Tag>;
  };

  const getHealthStatusLabel = (status: string) => {
    const labelMap: Record<string, string> = {
      HEALTHY: t('system.monitorAlert.healthHealthy'),
      UNHEALTHY: t('system.monitorAlert.healthUnhealthy'),
      DEGRADED: t('system.monitorAlert.healthDegraded'),
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
      title: t('system.monitorAlert.ruleName'),
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: t('system.monitorAlert.metricName'),
      dataIndex: 'metricName',
      key: 'metricName',
    },
    {
      title: t('system.monitorAlert.threshold'),
      dataIndex: 'threshold',
      key: 'threshold',
    },
    {
      title: t('system.monitorAlert.level'),
      dataIndex: 'alertLevel',
      key: 'alertLevel',
      render: getLevelTag,
    },
    {
      title: t('system.monitorAlert.status'),
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
      title: t('system.monitorAlert.action'),
      key: 'action',
      render: (_: unknown, record: AlertRule) => (
        <Space>
          <Button type="link" icon={<EditOutlined />} onClick={() => handleEditRule(record)}>
            {t('system.monitorAlert.edit')}
          </Button>
          <Popconfirm
            title={t('system.monitorAlert.confirmDeleteRule')}
            onConfirm={() => handleDeleteRule(record.id!)}
          >
            <Button type="link" danger icon={<DeleteOutlined />}>
              {t('system.monitorAlert.delete')}
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const eventColumns = [
    {
      title: t('system.monitorAlert.alertMessage'),
      dataIndex: 'message',
      key: 'message',
    },
    {
      title: t('system.monitorAlert.actualValue'),
      dataIndex: 'value',
      key: 'value',
    },
    {
      title: t('system.monitorAlert.status'),
      dataIndex: 'status',
      key: 'status',
      render: getLevelTag,
    },
    {
      title: t('system.monitorAlert.createdAt'),
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
    {
      title: t('system.monitorAlert.resolvedAt'),
      dataIndex: 'resolveTime',
      key: 'resolveTime',
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Button icon={<ReloadOutlined />} onClick={fetchData}>
          {t('system.monitorAlert.refreshData')}
        </Button>
      </div>

      {/* 系统健康状态 */}
      {systemInfo && (
        <Card title={t('system.monitorAlert.systemStatus')} style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={6}>
              <Statistic
                title={t('system.monitorAlert.systemStatus')}
                value={getHealthStatusLabel(systemInfo.healthStatus)}
                valueStyle={{ color: getHealthStatusColor(systemInfo.healthStatus) }}
                prefix={systemInfo.healthStatus === 'HEALTHY' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
              />
            </Col>
            <Col span={6}>
              <Statistic title={t('system.monitorAlert.version')} value={systemInfo.version} />
            </Col>
            <Col span={6}>
              <Statistic title={t('system.monitorAlert.uptime')} value={systemInfo.uptime} />
            </Col>
            <Col span={6}>
              <Statistic
                title={t('system.monitorAlert.serviceCount')}
                value={systemInfo.services?.length ?? 0}
              />
            </Col>
          </Row>
        </Card>
      )}

      {/* 监控指标 */}
      {metrics && (
        <Card title={t('system.monitorAlert.metricName')} style={{ marginBottom: 16 }}>
          <Row gutter={16}>
            <Col span={6}>
              <Card>
                <Statistic title={t('system.monitorAlert.cpuUsage')} value={metrics.cpu} suffix="%" />
                <Progress percent={metrics.cpu} status={metrics.cpu > 80 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title={t('system.monitorAlert.memoryUsage')} value={metrics.memory} suffix="%" />
                <Progress percent={metrics.memory} status={metrics.memory > 85 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title={t('system.monitorAlert.diskUsage')} value={metrics.disk} suffix="%" />
                <Progress percent={metrics.disk} status={metrics.disk > 90 ? 'exception' : 'normal'} />
              </Card>
            </Col>
            <Col span={6}>
              <Card>
                <Statistic title={t('system.monitorAlert.apiErrorRate')} value={metrics.errorRate} suffix="%" />
                <Progress percent={metrics.errorRate} status={metrics.errorRate > 5 ? 'exception' : 'normal'} />
              </Card>
            </Col>
          </Row>
          <Row gutter={16} style={{ marginTop: 16 }}>
            <Col span={8}>
              <Card>
                <Statistic title={t('system.monitorAlert.apiResponseTime')} value={metrics.apiResponseTime} suffix="ms" />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic title={t('system.monitorAlert.qps')} value={metrics.qps} />
              </Card>
            </Col>
            <Col span={8}>
              <Card>
                <Statistic title={t('system.monitorAlert.dbConnections')} value={metrics.dbConnections} />
              </Card>
            </Col>
          </Row>
        </Card>
      )}

      {/* 告警规则 */}
      <Card
        title={t('system.monitorAlert.alertRules')}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreateRule}>
            {t('system.monitorAlert.createRule')}
          </Button>
        }
        style={{ marginBottom: 16 }}
      >
        <Table
          columns={ruleColumns}
          dataSource={alertRules}
          rowKey="id"
          loading={loading}
          locale={{ emptyText: t('common.empty') }}
        />
      </Card>

      {/* 最近告警事件 */}
      <Card title={t('system.monitorAlert.recentEvents')}>
        <Table
          columns={eventColumns}
          dataSource={alertEvents}
          rowKey="id"
          loading={loading}
          pagination={false}
          locale={{ emptyText: t('common.empty') }}
        />
      </Card>

      <Modal
        title={editingRule ? t('system.monitorAlert.editAlertRule') : t('system.monitorAlert.createAlertRule')}
        open={ruleModalVisible}
        onOk={handleSaveRule}
        onCancel={() => setRuleModalVisible(false)}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        width={600}
      >
        <Form form={form} layout="vertical">
          <Form.Item label={t('system.monitorAlert.ruleName')} name="name" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item label={t('system.monitorAlert.metricName')} name="metricName" rules={[{ required: true }]}>
            <Select>
              <Option value="cpu">{t('system.monitorAlert.cpuUsage')}</Option>
              <Option value="memory">{t('system.monitorAlert.memoryUsage')}</Option>
              <Option value="disk">{t('system.monitorAlert.diskUsage')}</Option>
              <Option value="errorRate">{t('system.monitorAlert.apiErrorRate')}</Option>
              <Option value="apiResponseTime">{t('system.monitorAlert.apiResponseTime')}</Option>
            </Select>
          </Form.Item>
          <Form.Item label={t('system.monitorAlert.threshold')} name="threshold" rules={[{ required: true }]}>
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label={t('system.monitorAlert.alertLevel')} name="alertLevel" rules={[{ required: true }]}>
            <Select>
              <Option value="CRITICAL">{t('system.monitorAlert.levelCritical')}</Option>
              <Option value="WARNING">{t('system.monitorAlert.levelWarning')}</Option>
              <Option value="INFO">{t('system.monitorAlert.levelInfo')}</Option>
            </Select>
          </Form.Item>
          <Form.Item
            label={t('system.monitorAlert.notificationChannels')}
            name="notificationChannels"
            rules={[{ required: true }]}
          >
            <Input placeholder="email, sms, wechat, dingtalk" />
          </Form.Item>
          <Form.Item label={t('system.monitorAlert.enabledLabel')} name="enabled" valuePropName="checked" initialValue={true}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default MonitorAlertPage;
