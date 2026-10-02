import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Table,
  Button,
  Modal,
  Typography,
  Space,
  Select,
  Input,
  message,
  Card,
  Tag,
  Descriptions,
} from 'antd';
import { DownloadOutlined, EyeOutlined, ReloadOutlined } from '@ant-design/icons';
import { codeGenerationApi, historyApi } from '../services/api';
import type { GenerationHistoryItem } from '../services/types';

const { Text } = Typography;

const STATUS_META: Record<
  string,
  { label: string; color: string }
> = {
  SUCCESS: { label: '成功', color: 'green' },
  FAILED: { label: '失败', color: 'red' },
  PROCESSING: { label: '生成中', color: 'blue' },
  PENDING: { label: '排队中', color: 'orange' },
};

const formatTime = (value: string | null): string => {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 19);
};

const GenerationHistory: React.FC = () => {
  const [history, setHistory] = useState<GenerationHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedHistory, setSelectedHistory] = useState<GenerationHistoryItem | null>(null);
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('');

  const loadHistory = useCallback(async () => {
    try {
      setLoading(true);
      const response = await historyApi.getList();
      setHistory(response.data ?? []);
    } catch (error) {
      message.error('加载生成历史失败');
      console.error('加载生成历史失败:', error);
      setHistory([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  // 后端 /history 返回 findRecent 全量列表，名称 / 状态过滤在前端完成
  const filteredHistory = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return history.filter((item) => {
      if (statusFilter && item.status !== statusFilter) return false;
      if (!kw) return true;
      return (
        (item.generationName ?? '').toLowerCase().includes(kw) ||
        (item.moduleName ?? '').toLowerCase().includes(kw) ||
        (item.basePackage ?? '').toLowerCase().includes(kw)
      );
    });
  }, [history, keyword, statusFilter]);

  const handleViewDetail = (item: GenerationHistoryItem): void => {
    setSelectedHistory(item);
    setDetailModalVisible(true);
  };

  // 下载代码（真实 taskId，仅成功任务可下载）
  const handleDownloadCode = async (taskId: string) => {
    try {
      const response = await codeGenerationApi.downloadCode(taskId);
      const url = window.URL.createObjectURL(new Blob([response as unknown as BlobPart]));
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `generated-code-${taskId}.zip`);
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      window.URL.revokeObjectURL(url);
    } catch (error) {
      message.error('下载代码失败');
      console.error('下载代码失败:', error);
    }
  };

  const columns = [
    { title: '项目名称', dataIndex: 'generationName', key: 'generationName' },
    { title: '模板', dataIndex: 'templateName', key: 'templateName', ellipsis: true },
    { title: '基础包路径', dataIndex: 'basePackage', key: 'basePackage', ellipsis: true },
    { title: '模块名称', dataIndex: 'moduleName', key: 'moduleName' },
    {
      title: '生成时间',
      key: 'createdAt',
      render: (_: unknown, record: GenerationHistoryItem) => formatTime(record.createdAt),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const meta = STATUS_META[status] ?? { label: status, color: 'default' };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '生成文件数',
      dataIndex: 'fileCount',
      key: 'fileCount',
      render: (value: number | null) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: GenerationHistoryItem) => (
        <Space size="middle">
          <Button icon={<EyeOutlined />} onClick={() => handleViewDetail(record)}>
            查看详情
          </Button>
          {record.status === 'SUCCESS' && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => void handleDownloadCode(record.taskId)}
            >
              下载代码
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card>
        <Typography.Title level={4}>生成历史</Typography.Title>

        <div
          style={{
            marginBottom: '16px',
            display: 'flex',
            gap: '16px',
            flexWrap: 'wrap',
            alignItems: 'center',
          }}
        >
          <Input
            placeholder="项目 / 模块 / 包路径"
            allowClear
            style={{ width: 220 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
          <Select
            placeholder="状态"
            allowClear
            style={{ width: 140 }}
            value={statusFilter || undefined}
            onChange={(value) => setStatusFilter(value ?? '')}
            options={[
              { value: 'SUCCESS', label: '成功' },
              { value: 'FAILED', label: '失败' },
              { value: 'PROCESSING', label: '生成中' },
              { value: 'PENDING', label: '排队中' },
            ]}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void loadHistory()}>
            刷新
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={filteredHistory}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total) => `共 ${total} 条`,
          }}
        />
      </Card>

      {/* 历史详情弹窗：展示真实生成记录字段 */}
      <Modal
        title="生成详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>,
          selectedHistory?.status === 'SUCCESS' && (
            <Button
              key="download"
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => selectedHistory && void handleDownloadCode(selectedHistory.taskId)}
            >
              下载代码
            </Button>
          ),
        ]}
        width={800}
      >
        {selectedHistory && (
          <div>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="项目名称" span={2}>
                {selectedHistory.generationName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="模板">{selectedHistory.templateName || '-'}</Descriptions.Item>
              <Descriptions.Item label="模块名称">{selectedHistory.moduleName || '-'}</Descriptions.Item>
              <Descriptions.Item label="基础包路径" span={2}>
                {selectedHistory.basePackage || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={(STATUS_META[selectedHistory.status] ?? { color: 'default' }).color}>
                  {(STATUS_META[selectedHistory.status] ?? { label: selectedHistory.status }).label}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="生成文件数">
                {selectedHistory.fileCount ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="耗时">
                {selectedHistory.executionTime != null
                  ? `${(selectedHistory.executionTime / 1000).toFixed(2)} s`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="生成时间">
                {formatTime(selectedHistory.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="开始时间">
                {formatTime(selectedHistory.startedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="完成时间">
                {formatTime(selectedHistory.completedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="输出路径" span={2}>
                {selectedHistory.outputPath || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="生成表" span={2}>
                {selectedHistory.tableNames?.length
                  ? selectedHistory.tableNames.join('、')
                  : '-'}
              </Descriptions.Item>
              {selectedHistory.status === 'FAILED' && (
                <Descriptions.Item label="错误信息" span={2}>
                  <Text type="danger">{selectedHistory.errorMessage || '生成过程中出现错误'}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default GenerationHistory;
