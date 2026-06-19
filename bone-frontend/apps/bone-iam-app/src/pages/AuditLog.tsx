import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Input, DatePicker, Select, Button, Tag, Drawer, Descriptions,
  Space, Tooltip, Typography, Card, Statistic, Row, Col,
} from 'antd';
import { App as AntdApp } from 'antd';
import {
  SearchOutlined, DownloadOutlined, EyeOutlined,
  LoginOutlined, LogoutOutlined, PlusOutlined, EditOutlined,
  DeleteOutlined, ExportOutlined, ImportOutlined,
  ReloadOutlined, FileSearchOutlined,
} from '@ant-design/icons';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import type { AuditLog } from '../types';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;
const { Text } = Typography;

/** 操作类型映射 */
const OPERATION_MAP: Record<string, { label: string; color: string; icon: React.ReactNode }> = {
  CREATE: { label: '创建', color: 'green', icon: <PlusOutlined /> },
  READ: { label: '查看', color: 'blue', icon: <FileSearchOutlined /> },
  UPDATE: { label: '更新', color: 'orange', icon: <EditOutlined /> },
  DELETE: { label: '删除', color: 'red', icon: <DeleteOutlined /> },
  LOGIN: { label: '登录', color: 'cyan', icon: <LoginOutlined /> },
  LOGOUT: { label: '登出', color: 'default', icon: <LogoutOutlined /> },
  EXPORT: { label: '导出', color: 'purple', icon: <ExportOutlined /> },
  IMPORT: { label: '导入', color: 'geekblue', icon: <ImportOutlined /> },
};

/** 资源类型映射 */
const RESOURCE_TYPE_MAP: Record<string, string> = {
  ACCOUNT: '账号',
  ROLE: '角色',
  PERMISSION: '权限',
  TENANT: '租户',
  SYSTEM: '系统',
  AUDIT: '审计',
};

const AuditLogPage: React.FC = () => {
  const { message: messageApi } = AntdApp.useApp();

  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 筛选条件
  const [userId, setUserId] = useState<number | undefined>();
  const [operation, setOperation] = useState<string | undefined>();
  const [resourceType, setResourceType] = useState<string | undefined>();
  const [result, setResult] = useState<string | undefined>();
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);
  const [searchKeyword, setSearchKeyword] = useState('');

  // 详情抽屉
  const [detailVisible, setDetailVisible] = useState(false);
  const [currentLog, setCurrentLog] = useState<AuditLog | null>(null);

  // 导出中
  const [exporting, setExporting] = useState(false);

  const fetchAuditLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Parameters<typeof api.getAuditLogs>[0] = {
        page,
        pageSize,
        userId,
        operation: operation as any,
        resourceType,
        result,
        startTime: dateRange?.[0]?.format('YYYY-MM-DD HH:mm:ss') || undefined,
        endTime: dateRange?.[1]?.format('YYYY-MM-DD HH:mm:ss') || undefined,
      };
      const response = await api.getAuditLogs(params);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        setAuditLogs(records);
        setTotal(newTotal);
      }
    } catch {
      messageApi.error('获取审计日志失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, userId, operation, resourceType, result, dateRange, messageApi]);

  useEffect(() => {
    void fetchAuditLogs();
  }, [fetchAuditLogs]);

  /** 导出 CSV */
  const handleExport = async () => {
    setExporting(true);
    try {
      const params: Parameters<typeof api.getAuditLogs>[0] = {
        page: 1,
        pageSize: 10000,
        userId,
        operation: operation as any,
        resourceType,
        result,
        startTime: dateRange?.[0]?.format('YYYY-MM-DD HH:mm:ss') || undefined,
        endTime: dateRange?.[1]?.format('YYYY-MM-DD HH:mm:ss') || undefined,
      };
      const response = await api.exportAuditLogs(params);
      if (response.code === 200 && response.data) {
        // 前端生成 CSV 下载
        const logs = response.data;
        const header = 'ID,租户ID,用户ID,操作,资源类型,资源ID,IP,User-Agent,结果,耗时(ms),操作时间\n';
        const rows = logs.map((log: AuditLog) =>
          [log.id, log.tenantId, log.userId, log.operation, log.resourceType,
           log.resourceId || '', log.ip || '', `"${(log.userAgent || '').replace(/"/g, '""')}"`,
           log.result, log.duration || '', log.createdAt
          ].join(',')
        ).join('\n');
        const csv = '\uFEFF' + header + rows;
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `audit-logs-${dayjs().format('YYYYMMDDHHmmss')}.csv`;
        a.click();
        URL.revokeObjectURL(url);
        messageApi.success('导出成功');
      }
    } catch {
      messageApi.error('导出失败');
    } finally {
      setExporting(false);
    }
  };

  /** 查看详情 */
  const showDetail = (record: AuditLog) => {
    setCurrentLog(record);
    setDetailVisible(true);
  };

  /** 重置筛选 */
  const handleReset = () => {
    setUserId(undefined);
    setOperation(undefined);
    setResourceType(undefined);
    setResult(undefined);
    setDateRange(null);
    setSearchKeyword('');
    setPage(1);
  };

  /** 统计数据 */
  const successCount = auditLogs.filter(l => l.result === 'SUCCESS').length;
  const failCount = auditLogs.filter(l => l.result === 'FAILED').length;

  const columns = [
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 180,
      render: (val: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作类型',
      dataIndex: 'operation',
      key: 'operation',
      width: 100,
      render: (val: string) => {
        const op = OPERATION_MAP[val];
        return op ? <Tag color={op.color} icon={op.icon}>{op.label}</Tag> : val;
      },
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
      width: 100,
      render: (val: string) => RESOURCE_TYPE_MAP[val] || val || '-',
    },
    {
      title: '资源ID',
      dataIndex: 'resourceId',
      key: 'resourceId',
      width: 160,
      ellipsis: true,
      render: (val: string) => (
        <Tooltip title={val}><Text copyable={{ tooltips: ['复制', '已复制'] }}>{val || '-'}</Text></Tooltip>
      ),
    },
    {
      title: '用户ID',
      dataIndex: 'userId',
      key: 'userId',
      width: 100,
    },
    {
      title: 'IP地址',
      dataIndex: 'ip',
      key: 'ip',
      width: 140,
      render: (val: string) => val || '-',
    },
    {
      title: '结果',
      dataIndex: 'result',
      key: 'result',
      width: 90,
      render: (val: string) => (
        <Tag color={val === 'SUCCESS' ? 'success' : 'error'}>
          {val === 'SUCCESS' ? '成功' : '失败'}
        </Tag>
      ),
    },
    {
      title: '耗时',
      dataIndex: 'duration',
      key: 'duration',
      width: 90,
      render: (val: number) => val != null ? `${val}ms` : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 60,
      render: (_: unknown, record: AuditLog) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => showDetail(record)} />
      ),
    },
  ];

  return (
    <div>
      {/* 统计卡片 */}
      <Row gutter={16} style={{ marginBottom: 16 }}>
        <Col span={6}>
          <Card size="small">
            <Statistic title="总记录数" value={total} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="当前页成功" value={successCount} valueStyle={{ color: '#52c41a' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="当前页失败" value={failCount} valueStyle={{ color: '#ff4d4f' }} />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic title="当前页成功率" value={auditLogs.length ? Math.round(successCount / auditLogs.length * 100) : 0} suffix="%" />
          </Card>
        </Col>
      </Row>

      {/* 筛选栏 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
        <Space wrap>
          <Input
            placeholder="用户ID"
            value={searchKeyword}
            onChange={(e) => {
              setSearchKeyword(e.target.value);
              const num = parseInt(e.target.value, 10);
              setUserId(isNaN(num) ? undefined : num);
            }}
            style={{ width: 120 }}
            prefix={<SearchOutlined />}
            allowClear
          />
          <Select
            placeholder="操作类型"
            value={operation}
            onChange={setOperation}
            style={{ width: 120 }}
            allowClear
          >
            {Object.entries(OPERATION_MAP).map(([key, { label }]) => (
              <Select.Option key={key} value={key}>{label}</Select.Option>
            ))}
          </Select>
          <Select
            placeholder="资源类型"
            value={resourceType}
            onChange={setResourceType}
            style={{ width: 120 }}
            allowClear
          >
            {Object.entries(RESOURCE_TYPE_MAP).map(([key, label]) => (
              <Select.Option key={key} value={key}>{label}</Select.Option>
            ))}
          </Select>
          <Select
            placeholder="结果"
            value={result}
            onChange={setResult}
            style={{ width: 100 }}
            allowClear
          >
            <Select.Option value="SUCCESS">成功</Select.Option>
            <Select.Option value="FAILED">失败</Select.Option>
          </Select>
          <RangePicker
            showTime
            value={dateRange}
            onChange={(dates) => setDateRange(dates)}
            style={{ width: 360 }}
          />
          <Button icon={<ReloadOutlined />} onClick={handleReset}>重置</Button>
        </Space>
        <Button
          type="primary"
          icon={<DownloadOutlined />}
          loading={exporting}
          onClick={handleExport}
        >
          导出CSV
        </Button>
      </div>

      {/* 数据表格 */}
      <Table
        columns={columns}
        dataSource={auditLogs}
        rowKey="id"
        loading={loading}
        size="small"
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showQuickJumper: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          },
        }}
      />

      {/* 详情抽屉 */}
      <Drawer
        title="审计日志详情"
        placement="right"
        width={560}
        open={detailVisible}
        onClose={() => setDetailVisible(false)}
      >
        {currentLog && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="日志ID">{currentLog.id}</Descriptions.Item>
            <Descriptions.Item label="租户ID">{currentLog.tenantId}</Descriptions.Item>
            <Descriptions.Item label="用户ID">{currentLog.userId}</Descriptions.Item>
            <Descriptions.Item label="操作类型">
              {(() => {
                const op = OPERATION_MAP[currentLog.operation];
                return op ? <Tag color={op.color} icon={op.icon}>{op.label} ({currentLog.operation})</Tag> : currentLog.operation;
              })()}
            </Descriptions.Item>
            <Descriptions.Item label="资源类型">
              {RESOURCE_TYPE_MAP[currentLog.resourceType] || currentLog.resourceType || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="资源ID">
              <Text copyable={{ text: currentLog.resourceId || '', tooltips: ['复制', '已复制'] } as any}>
                {currentLog.resourceId || '-'}
              </Text>
            </Descriptions.Item>
            <Descriptions.Item label="IP地址">{currentLog.ip || '-'}</Descriptions.Item>
            <Descriptions.Item label="User-Agent">
              <Text style={{ wordBreak: 'break-all', fontSize: 12 }}>{currentLog.userAgent || '-'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="请求参数">
              <Text style={{ wordBreak: 'break-all', fontSize: 12 }}>{currentLog.parameters || '-'}</Text>
            </Descriptions.Item>
            <Descriptions.Item label="结果">
              <Tag color={currentLog.result === 'SUCCESS' ? 'success' : 'error'}>
                {currentLog.result === 'SUCCESS' ? '成功' : '失败'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="耗时">{currentLog.duration != null ? `${currentLog.duration}ms` : '-'}</Descriptions.Item>
            <Descriptions.Item label="操作时间">
              {currentLog.createdAt ? dayjs(currentLog.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default AuditLogPage;
