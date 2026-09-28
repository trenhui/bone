import React, { useCallback, useEffect, useState } from 'react';
import {
  Table, Input, DatePicker, Select, Button, Tag, Drawer, Descriptions,
  Space, Tooltip, Typography,
} from 'antd';
import { App as AntdApp } from 'antd';
import {
  SearchOutlined, DownloadOutlined, EyeOutlined,
  LoginOutlined, LogoutOutlined, PlusOutlined, EditOutlined,
  DeleteOutlined, ExportOutlined, ImportOutlined,
  ReloadOutlined, FileSearchOutlined,
} from '@ant-design/icons';
import * as api from '../services/api';
import { isForbiddenError, resolveIamErrorMessage } from '../utils/iamErrorMessages';
import { ListEmptyState, ListErrorState, ListForbiddenState } from '../components/ListStates';
import ModulePage from '../components/ModulePage';
import { StatisticCard } from '@ant-design/pro-components';
import { unwrapPage } from '../utils/pageResult';
import { downloadBlob } from '../utils/download';
import { formatDate } from '@bone/shared-utils';
import type { AuditLog } from '../types';
import dayjs from 'dayjs';
/**
 * 时间窗 → 后端 `LocalDateTime` 可解析的 ISO-8601 本地格式。
 *
 * 后端 `AuditLogListQuery.startedAt/endedAt` 是 `LocalDateTime`，Spring 按 ISO_LOCAL_DATE_TIME 解析；
 * 发 `YYYY-MM-DD HH:mm:ss`（空格分隔）会 400 —— 列表与导出共用同一 query 对象，两者都会挂。
 * 注意**不能**用 `toISOString()`：那会带 `Z`，LocalDateTime 同样解析失败。
 */
const isoLocal = (d: dayjs.Dayjs | null | undefined): string | undefined =>
  d ? d.format('YYYY-MM-DDTHH:mm:ss') : undefined;


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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // 筛选条件
  const [userId, setUserId] = useState<string | undefined>();
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
    setLoadError(null);
    setForbidden(false);
    try {
      const params: Parameters<typeof api.getAuditLogs>[0] = {
        page,
        pageSize,
        userId,
        operation,
        resourceType,
        result,
        startTime: isoLocal(dateRange?.[0]),
        endTime: isoLocal(dateRange?.[1]),
      };
      const response = await api.getAuditLogs(params);
      if (response.code === 200) {
        const { records, total: newTotal } = unwrapPage(response.data);
        setAuditLogs(records);
        setTotal(newTotal);
      }
    } catch (err: unknown) {
      setForbidden(isForbiddenError(err));
      setLoadError(resolveIamErrorMessage(err) ?? '获取审计日志失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize, userId, operation, resourceType, result, dateRange]);

  useEffect(() => {
    void fetchAuditLogs();
  }, [fetchAuditLogs]);

  /** 导出 CSV */
  const handleExport = async () => {
    setExporting(true);
    try {
      // 字节流契约（§5.8）：后端直接返回 CSV 二进制，不包 ApiResponse，故无 code/data 可判。
      // 参数名对齐后端 AuditLogListQuery：导出用 startedAt / endedAt / operation。
      const blob = await api.exportAuditLogs({
        userId,
        operation: operation as string | undefined,
        resourceType,
        result,
        startedAt: isoLocal(dateRange?.[0]),
        endedAt: isoLocal(dateRange?.[1]),
      });
      downloadBlob(
        blob,
        `audit-logs-${formatDate(new Date()).replace(/[-: ]/g, '')}.csv`,
      );
      messageApi.success('导出成功');
    } catch (err: unknown) {
      messageApi.error(resolveIamErrorMessage(err) ?? '导出失败');
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
      // 走 shared-utils/i18n/format：后端按 UTC 下发，裸 dayjs 会按浏览器本地时区解释
      render: (val: string) => formatDate(val),
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
    <ModulePage title="审计日志" description="记录平台关键操作，支持按用户、操作类型、时间范围检索与导出。" card={false}>
      {/* 统计卡片 */}
      <StatisticCard.Group direction="row" style={{ marginBottom: 16 }}>
        <StatisticCard
          statistic={{ title: '总记录数', value: total }}
        />
        <StatisticCard
          statistic={{ title: '当前页成功', value: successCount, valueStyle: { color: '#52c41a' } }}
        />
        <StatisticCard
          statistic={{ title: '当前页失败', value: failCount, valueStyle: { color: '#ff4d4f' } }}
        />
        <StatisticCard
          statistic={{
            title: '当前页成功率',
            value: auditLogs.length ? Math.round(successCount / auditLogs.length * 100) : 0,
            suffix: '%',
          }}
        />
      </StatisticCard.Group>

      {/* 筛选栏 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 8 }}>
        <Space wrap>
          <Input
            placeholder="用户ID"
            value={searchKeyword}
            onChange={(e) => {
              setSearchKeyword(e.target.value);
              const raw = e.target.value.trim();
              setUserId(raw === '' ? undefined : raw);
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
            presets={[
              { label: '今天', value: [dayjs().startOf('day'), dayjs()] },
              { label: '近 7 天', value: [dayjs().subtract(7, 'day').startOf('day'), dayjs()] },
              { label: '近 30 天', value: [dayjs().subtract(30, 'day').startOf('day'), dayjs()] },
            ]}
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
      {loadError ? (
        forbidden ? (
          <ListForbiddenState onRetry={fetchAuditLogs} />
        ) : (
          <ListErrorState error={loadError} onRetry={fetchAuditLogs} />
        )
      ) : auditLogs.length === 0 && !loading ? (
        <ListEmptyState text="暂无审计日志" />
      ) : (
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
      )}

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
              <Text copyable={{ text: currentLog.resourceId || '', tooltips: ['复制', '已复制'] }}>
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
              {formatDate(currentLog.createdAt)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </ModulePage>
  );
};

export default AuditLogPage;
