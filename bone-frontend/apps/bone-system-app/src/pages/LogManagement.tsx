import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Table,
  Button,
  Space,
  Form,
  Input,
  Select,
  DatePicker,
  Tag,
  message,
  Modal,
  Card,
  Row,
  Col,
  Statistic,
} from 'antd';
import {
  SearchOutlined,
  DownloadOutlined,
  ReloadOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { SystemLog } from '@/types';
import { logApi } from '@/services/api';
import { normalizeTotal } from '@bone/shared-utils';
import dayjs, { Dayjs } from 'dayjs';

const { RangePicker } = DatePicker;
const { Option } = Select;

interface LogSearchFormValues {
  level?: string;
  service?: string;
  keyword?: string;
  dateRange?: [Dayjs, Dayjs];
}

interface LogAnalyzeResult {
  totalCount?: number;
  errorCount?: number;
  warnCount?: number;
  errorRate?: number;
  hotServices?: Array<{ service: string; count: number }>;
  errorDistribution?: Array<{ type: string; count: number }>;
}

const LogManagementPage: React.FC = () => {
  const [logs, setLogs] = useState<SystemLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<SystemLog | null>(null);
  const [analyzeModalVisible, setAnalyzeModalVisible] = useState(false);
  const [analyzeResult, setAnalyzeResult] = useState<LogAnalyzeResult | null>(null);
  const [form] = Form.useForm();
  const { t } = useTranslation();

  const fetchLogs = async (page = 1, pageSize = 20, filters: Record<string, unknown> = {}): Promise<void> => {
    setLoading(true);
    try {
      const params = {
        page: page,
        size: pageSize,
        ...filters,
      };
      const response = await logApi.getLogs(params);
      if (response.code === 200) {
        // 权威字段是 records；list 是后端PageResult 的 @Deprecated 兼容getter，
        // 将在 @JsonIgnore 收敛后消失（Bone-API-规范 §5.3）。此处刻意只读 records。
        setLogs(response.data.records);
        setPagination({
          current: page,
          pageSize,
          total: normalizeTotal(response.data.total),
        });
      }
    } catch {
      message.error(t('system.logManagement.fetchLogsFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
    // 仅在组件挂载时执行一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const buildFilters = (values: LogSearchFormValues): Record<string, unknown> => {
    const filters: Record<string, unknown> = {
      level: values.level,
      service: values.service,
      keyword: values.keyword,
    };
    if (values.dateRange) {
      filters.startTime = values.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
      filters.endTime = values.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
    }
    return filters;
  };

  const handleSearch = async (): Promise<void> => {
    const values = await form.validateFields();
    fetchLogs(1, pagination.pageSize, buildFilters(values));
  };

  const handleReset = (): void => {
    form.resetFields();
    fetchLogs();
  };

  const handleViewDetail = (log: SystemLog): void => {
    setSelectedLog(log);
    setDetailModalVisible(true);
  };

  const handleExport = async (): Promise<void> => {
    try {
      const values = form.getFieldsValue();
      const response = await logApi.exportLogs(buildFilters(values));
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `system-logs-${dayjs().format('YYYYMMDDHHmmss')}.csv`);
      document.body.appendChild(link);
      link.click();
      message.success(t('system.logManagement.exportLogSuccess'));
    } catch {
      message.error(t('system.logManagement.exportLogFailed'));
    }
  };

  const handleAnalyze = async (): Promise<void> => {
    try {
      const values = form.getFieldsValue();
      const response = await logApi.analyzeLogs(buildFilters(values));
      if (response.code === 200) {
        setAnalyzeResult(response.data as LogAnalyzeResult);
        setAnalyzeModalVisible(true);
      }
    } catch {
      message.error(t('system.logManagement.analyzeLogFailed'));
    }
  };

  const getLevelTag = (level: string) => {
    const colorMap: Record<string, string> = {
      ERROR: 'red',
      WARN: 'orange',
      INFO: 'blue',
      DEBUG: 'green',
      TRACE: 'gray',
    };
    return <Tag color={colorMap[level]}>{level}</Tag>;
  };

  const columns = [
    {
      title: t('system.logManagement.colTime'),
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
    },
    {
      title: t('system.logManagement.colLevel'),
      dataIndex: 'level',
      key: 'level',
      width: 100,
      render: getLevelTag,
    },
    {
      title: t('system.logManagement.colService'),
      dataIndex: 'service',
      key: 'service',
      width: 150,
    },
    {
      title: t('system.logManagement.colContent'),
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
    },
    {
      title: t('system.logManagement.colTraceId'),
      dataIndex: 'traceId',
      key: 'traceId',
      width: 200,
    },
    {
      title: t('system.logManagement.colAction'),
      key: 'action',
      width: 100,
      render: (_: unknown, record: SystemLog) => (
        <Button type="link" onClick={() => handleViewDetail(record)}>
          {t('system.logManagement.detail')}
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 搜索表单 */}
      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="dateRange" label={t('system.logManagement.labelTimeRange')}>
            <RangePicker showTime style={{ width: 400 }} />
          </Form.Item>
          <Form.Item name="level" label={t('system.logManagement.labelLogLevel')}>
            <Select placeholder={t('system.logManagement.placeholderAll')} style={{ width: 120 }} allowClear>
              <Option value="ERROR">ERROR</Option>
              <Option value="WARN">WARN</Option>
              <Option value="INFO">INFO</Option>
              <Option value="DEBUG">DEBUG</Option>
              <Option value="TRACE">TRACE</Option>
            </Select>
          </Form.Item>
          <Form.Item name="service" label={t('system.logManagement.colService')}>
            <Input placeholder={t('system.logManagement.placeholderServiceName')} style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="keyword" label={t('system.logManagement.labelKeyword')}>
            <Input placeholder={t('system.logManagement.placeholderSearchKeyword')} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                {t('system.logManagement.search')}
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                {t('system.logManagement.reset')}
              </Button>
            </Space>
          </Form.Item>
        </Form>
        <div style={{ marginTop: 16 }}>
          <Space>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              {t('system.logManagement.exportLog')}
            </Button>
            <Button icon={<BarChartOutlined />} onClick={handleAnalyze}>
              {t('system.logManagement.analyzeLog')}
            </Button>
          </Space>
        </div>
      </Card>

      {/* 日志表格 */}
      <Card>
        <Table
          columns={columns}
          dataSource={logs}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => t('system.logManagement.totalItems', { total }),
            onChange: (page, pageSize) => {
              const values = form.getFieldsValue();
              fetchLogs(page, pageSize, buildFilters(values));
            },
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* 日志详情弹窗 */}
      <Modal
        title={t('system.logManagement.logDetail')}
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={800}
      >
        {selectedLog && (
          <div>
            <p><strong>{t('system.logManagement.fieldTime')}</strong>{selectedLog.createdAt}</p>
            <p><strong>{t('system.logManagement.fieldLevel')}</strong>{getLevelTag(selectedLog.level)}</p>
            <p><strong>{t('system.logManagement.fieldService')}</strong>{selectedLog.service}</p>
            <p><strong>{t('system.logManagement.fieldTraceId')}</strong>{selectedLog.traceId || '-'}</p>
            <p><strong>{t('system.logManagement.fieldContent')}</strong></p>
            <pre style={{
              background: '#f5f5f5',
              padding: 16,
              borderRadius: 4,
              overflow: 'auto',
              maxHeight: 400,
            }}>
              {selectedLog.content}
            </pre>
          </div>
        )}
      </Modal>

      {/* 分析结果弹窗 */}
      <Modal
        title={t('system.logManagement.analyzeResult')}
        open={analyzeModalVisible}
        onCancel={() => setAnalyzeModalVisible(false)}
        footer={null}
        width={800}
      >
        {analyzeResult && (
          <div>
            <Row gutter={16} style={{ marginBottom: 24 }}>
              <Col span={6}>
                <Card>
                  <Statistic title={t('system.logManagement.statTotalLogs')} value={analyzeResult.totalCount || 0} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title={t('system.logManagement.statErrorCount')} value={analyzeResult.errorCount || 0} valueStyle={{ color: '#cf1322' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title={t('system.logManagement.statWarnCount')} value={analyzeResult.warnCount || 0} valueStyle={{ color: '#fa8c16' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title={t('system.logManagement.statErrorRate')} value={`${analyzeResult.errorRate || 0}%`} />
                </Card>
              </Col>
            </Row>
            {analyzeResult.hotServices && (
              <Card title={t('system.logManagement.hotServices')} style={{ marginBottom: 16 }}>
                <ul>
                  {analyzeResult.hotServices.map((item, index) => (
                    <li key={index}>{t('system.logManagement.hotServiceItem', { service: item.service, count: item.count })}</li>
                  ))}
                </ul>
              </Card>
            )}
            {analyzeResult.errorDistribution && (
              <Card title={t('system.logManagement.errorDistribution')}>
                <ul>
                  {analyzeResult.errorDistribution.map((item, index) => (
                    <li key={index}>{t('system.logManagement.errorDistributionItem', { type: item.type, count: item.count })}</li>
                  ))}
                </ul>
              </Card>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default LogManagementPage;
