import React, { useCallback, useEffect, useState } from 'react';
import { Table, Button, Modal, Typography, Space, DatePicker, Select, Input, message, Card } from 'antd';
import { DownloadOutlined, EyeOutlined } from '@ant-design/icons';
import type { Dayjs } from 'dayjs';
import { codeGenerationApi } from '../services/api';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

type DateRange = [Dayjs | null, Dayjs | null] | null;

interface GenerationHistoryItem {
  id: number;
  projectName: string;
  basePackage: string;
  moduleName: string;
  generateTime: string;
  status: 'SUCCESS' | 'FAILED';
  fileCount: number;
  taskId: string;
}

const GenerationHistory: React.FC = () => {
  const [history, setHistory] = useState<GenerationHistoryItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedHistory, setSelectedHistory] = useState<GenerationHistoryItem | null>(null);
  const [searchParams, setSearchParams] = useState<{
    projectName: string;
    status: string;
    timeRange: DateRange;
  }>({
    projectName: '',
    status: '',
    timeRange: null,
  });

  const loadHistory = useCallback(async () => {
    try {
      setLoading(true);
      const mockHistory: GenerationHistoryItem[] = [
        {
          id: 1,
          projectName: 'demo-project',
          basePackage: 'com.example',
          moduleName: 'demo',
          generateTime: '2024-01-01 10:00:00',
          status: 'SUCCESS',
          fileCount: 10,
          taskId: 'task-1',
        },
        {
          id: 2,
          projectName: 'test-project',
          basePackage: 'com.test',
          moduleName: 'test',
          generateTime: '2024-01-02 11:00:00',
          status: 'FAILED',
          fileCount: 0,
          taskId: 'task-2',
        },
      ];
      setHistory(mockHistory);
    } catch (error) {
      message.error('加载生成历史失败');
      console.error('加载生成历史失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadHistory();
  }, [loadHistory]);

  const handleViewDetail = (item: GenerationHistoryItem): void => {
    setSelectedHistory(item);
    setDetailModalVisible(true);
  };

  // 处理下载代码
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
    } catch (error) {
      message.error('下载代码失败');
      console.error('下载代码失败:', error);
    }
  };

  // 表格列定义
  const columns = [
    {
      title: '项目名称',
      dataIndex: 'projectName',
      key: 'projectName',
    },
    {
      title: '基础包路径',
      dataIndex: 'basePackage',
      key: 'basePackage',
    },
    {
      title: '模块名称',
      dataIndex: 'moduleName',
      key: 'moduleName',
    },
    {
      title: '生成时间',
      dataIndex: 'generateTime',
      key: 'generateTime',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Text style={{ color: status === 'SUCCESS' ? 'green' : 'red' }}>
          {status === 'SUCCESS' ? '成功' : '失败'}
        </Text>
      ),
    },
    {
      title: '生成文件数',
      dataIndex: 'fileCount',
      key: 'fileCount',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: unknown, record: GenerationHistoryItem) => (
        <Space size="middle">
          <Button
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            查看详情
          </Button>
          {record.status === 'SUCCESS' && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => handleDownloadCode(record.taskId)}
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
        <Title level={4}>生成历史</Title>
        
        <div style={{ marginBottom: '16px', display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          <Input
            placeholder="项目名称"
            style={{ width: 200 }}
            value={searchParams.projectName}
            onChange={(e) => setSearchParams({ ...searchParams, projectName: e.target.value })}
          />
          <Select
            placeholder="状态"
            style={{ width: 120 }}
            value={searchParams.status}
            onChange={(value) => setSearchParams({ ...searchParams, status: value })}
          >
            <Select.Option value="">全部</Select.Option>
            <Select.Option value="SUCCESS">成功</Select.Option>
            <Select.Option value="FAILED">失败</Select.Option>
          </Select>
          <RangePicker
            style={{ width: 300 }}
            value={searchParams.timeRange}
            onChange={(dates) => setSearchParams({ ...searchParams, timeRange: dates })}
          />
          <Button type="primary" onClick={loadHistory}>
            搜索
          </Button>
        </div>
        
        <Table
          columns={columns}
          dataSource={history}
          rowKey="id"
          loading={loading}
          pagination={{
            pageSize: 10,
            showSizeChanger: true,
            showQuickJumper: true,
          }}
        />
      </Card>

      {/* 历史详情弹窗 */}
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
              onClick={() => handleDownloadCode(selectedHistory.taskId)}
            >
              下载代码
            </Button>
          ),
        ]}
        width={800}
      >
        {selectedHistory && (
          <div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>项目名称：</Text>
              <Text>{selectedHistory.projectName}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>基础包路径：</Text>
              <Text>{selectedHistory.basePackage}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>模块名称：</Text>
              <Text>{selectedHistory.moduleName}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>生成时间：</Text>
              <Text>{selectedHistory.generateTime}</Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>状态：</Text>
              <Text style={{ color: selectedHistory.status === 'SUCCESS' ? 'green' : 'red' }}>
                {selectedHistory.status === 'SUCCESS' ? '成功' : '失败'}
              </Text>
            </div>
            <div style={{ marginBottom: '16px' }}>
              <Text strong>生成文件数：</Text>
              <Text>{selectedHistory.fileCount}</Text>
            </div>
            {selectedHistory.status === 'FAILED' && (
              <div style={{ marginBottom: '16px' }}>
                <Text strong>错误信息：</Text>
                <Text type="danger">生成过程中出现错误，请检查配置并重试</Text>
              </div>
            )}
            <div style={{ marginBottom: '16px' }}>
              <Text strong>生成的文件列表：</Text>
              <ul style={{ marginTop: '8px' }}>
                {Array.from({ length: selectedHistory.fileCount }).map((_, index) => (
                  <li key={index} style={{ marginBottom: '4px' }}>
                    文件 {index + 1}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default GenerationHistory;