import React, { useState, useEffect } from 'react';
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
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Option } = Select;

const LogManagementPage: React.FC = () => {
  const [logs, setLogs] = useState<SystemLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 });
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<SystemLog | null>(null);
  const [analyzeModalVisible, setAnalyzeModalVisible] = useState(false);
  const [analyzeResult, setAnalyzeResult] = useState<any>(null);
  const [form] = Form.useForm();

  const fetchLogs = async (page = 1, pageSize = 20, filters: any = {}) => {
    setLoading(true);
    try {
      const params = {
        pageNum: page,
        pageSize,
        ...filters,
      };
      const response = await logApi.getLogs(params);
      if (response.code === 200) {
        setLogs(response.data.list);
        setPagination({
          current: page,
          pageSize,
          total: response.data.total,
        });
      }
    } catch (error) {
      message.error('获取日志失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, []);

  const handleSearch = async () => {
    const values = await form.validateFields();
    const filters: any = {
      level: values.level,
      service: values.service,
      keyword: values.keyword,
    };
    if (values.dateRange) {
      filters.startTime = values.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
      filters.endTime = values.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
    }
    fetchLogs(1, pagination.pageSize, filters);
  };

  const handleReset = () => {
    form.resetFields();
    fetchLogs();
  };

  const handleViewDetail = (log: SystemLog) => {
    setSelectedLog(log);
    setDetailModalVisible(true);
  };

  const handleExport = async () => {
    try {
      const values = form.getFieldsValue();
      const filters: any = {
        level: values.level,
        service: values.service,
        keyword: values.keyword,
      };
      if (values.dateRange) {
        filters.startTime = values.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
        filters.endTime = values.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const response = await logApi.exportLogs(filters);
      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `system-logs-${dayjs().format('YYYYMMDDHHmmss')}.csv`);
      document.body.appendChild(link);
      link.click();
      message.success('导出日志成功');
    } catch (error) {
      message.error('导出日志失败');
    }
  };

  const handleAnalyze = async () => {
    try {
      const values = form.getFieldsValue();
      const filters: any = {
        level: values.level,
        service: values.service,
        keyword: values.keyword,
      };
      if (values.dateRange) {
        filters.startTime = values.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
        filters.endTime = values.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
      }
      const response = await logApi.analyzeLogs(filters);
      if (response.code === 200) {
        setAnalyzeResult(response.data);
        setAnalyzeModalVisible(true);
      }
    } catch (error) {
      message.error('分析日志失败');
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
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
    },
    {
      title: '级别',
      dataIndex: 'level',
      key: 'level',
      width: 100,
      render: getLevelTag,
    },
    {
      title: '服务',
      dataIndex: 'service',
      key: 'service',
      width: 150,
    },
    {
      title: '内容',
      dataIndex: 'content',
      key: 'content',
      ellipsis: true,
    },
    {
      title: '追踪ID',
      dataIndex: 'traceId',
      key: 'traceId',
      width: 200,
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_: any, record: SystemLog) => (
        <Button type="link" onClick={() => handleViewDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  return (
    <div>
      {/* 搜索表单 */}
      <Card style={{ marginBottom: 16 }}>
        <Form form={form} layout="inline">
          <Form.Item name="dateRange" label="时间范围">
            <RangePicker showTime style={{ width: 400 }} />
          </Form.Item>
          <Form.Item name="level" label="日志级别">
            <Select placeholder="全部" style={{ width: 120 }} allowClear>
              <Option value="ERROR">ERROR</Option>
              <Option value="WARN">WARN</Option>
              <Option value="INFO">INFO</Option>
              <Option value="DEBUG">DEBUG</Option>
              <Option value="TRACE">TRACE</Option>
            </Select>
          </Form.Item>
          <Form.Item name="service" label="服务">
            <Input placeholder="服务名称" style={{ width: 150 }} />
          </Form.Item>
          <Form.Item name="keyword" label="关键词">
            <Input placeholder="搜索关键词" style={{ width: 200 }} />
          </Form.Item>
          <Form.Item>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                搜索
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
            </Space>
          </Form.Item>
        </Form>
        <div style={{ marginTop: 16 }}>
          <Space>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              导出日志
            </Button>
            <Button icon={<BarChartOutlined />} onClick={handleAnalyze}>
              分析日志
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
            showTotal: (total) => `共 ${total} 条`,
            onChange: (page, pageSize) => {
              const values = form.getFieldsValue();
              const filters: any = {
                level: values.level,
                service: values.service,
                keyword: values.keyword,
              };
              if (values.dateRange) {
                filters.startTime = values.dateRange[0].format('YYYY-MM-DD HH:mm:ss');
                filters.endTime = values.dateRange[1].format('YYYY-MM-DD HH:mm:ss');
              }
              fetchLogs(page, pageSize, filters);
            },
          }}
          scroll={{ x: 1200 }}
        />
      </Card>

      {/* 日志详情弹窗 */}
      <Modal
        title="日志详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={800}
      >
        {selectedLog && (
          <div>
            <p><strong>时间：</strong>{selectedLog.createTime}</p>
            <p><strong>级别：</strong>{getLevelTag(selectedLog.level)}</p>
            <p><strong>服务：</strong>{selectedLog.service}</p>
            <p><strong>追踪ID：</strong>{selectedLog.traceId || '-'}</p>
            <p><strong>内容：</strong></p>
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
        title="日志分析结果"
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
                  <Statistic title="总日志数" value={analyzeResult.totalCount || 0} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title="错误数" value={analyzeResult.errorCount || 0} valueStyle={{ color: '#cf1322' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title="警告数" value={analyzeResult.warnCount || 0} valueStyle={{ color: '#fa8c16' }} />
                </Card>
              </Col>
              <Col span={6}>
                <Card>
                  <Statistic title="错误率" value={`${analyzeResult.errorRate || 0}%`} />
                </Card>
              </Col>
            </Row>
            {analyzeResult.hotServices && (
              <Card title="热门服务" style={{ marginBottom: 16 }}>
                <ul>
                  {analyzeResult.hotServices.map((item: any, index: number) => (
                    <li key={index}>{item.service}: {item.count} 条</li>
                  ))}
                </ul>
              </Card>
            )}
            {analyzeResult.errorDistribution && (
              <Card title="错误分布">
                <ul>
                  {analyzeResult.errorDistribution.map((item: any, index: number) => (
                    <li key={index}>{item.type}: {item.count} 次</li>
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
