import React, { useCallback, useEffect, useState } from 'react';
import { Card, Table, Button, Modal, message, Tabs, Descriptions, Tag, Badge } from 'antd';
import { ReloadOutlined, EyeOutlined } from '@ant-design/icons';
import { monitorApi } from '../services/api';
import type { IntegrationLog, FlowStatistics } from '../types';

const { TabPane } = Tabs;

export const FlowMonitor: React.FC = () => {
  const [executions, setExecutions] = useState<IntegrationLog[]>([]);
  const [statistics, setStatistics] = useState<FlowStatistics[]>([]);
  const [loading, setLoading] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [currentExecution, setCurrentExecution] = useState<IntegrationLog | null>(null);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [total, setTotal] = useState(0);

  const fetchExecutions = useCallback(async () => {
    setLoading(true);
    try {
      const response = await monitorApi.getExecutions({ pageNum: page, pageSize });
      setExecutions(response.data.list);
      setTotal(response.data.total);
    } catch {
      message.error('获取执行记录失败');
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  const fetchStatistics = useCallback(async () => {
    try {
      const response = await monitorApi.getStatistics();
      setStatistics(response.data);
    } catch {
      message.error('获取统计数据失败');
    }
  }, []);

  useEffect(() => {
    void fetchExecutions();
    void fetchStatistics();
  }, [fetchExecutions, fetchStatistics]);

  const handleRetry = async (id: number): Promise<void> => {
    try {
      await monitorApi.retryExecution(id);
      message.success('重试成功');
      void fetchExecutions();
    } catch {
      message.error('重试失败');
    }
  };

  const handleViewDetail = (execution: IntegrationLog) => {
    setCurrentExecution(execution);
    setDetailModalVisible(true);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
    case 'SUCCESS':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'RUNNING':
      return 'blue';
    default:
      return 'gray';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
    case 'SUCCESS':
      return '成功';
    case 'FAILED':
      return '失败';
    case 'RUNNING':
      return '运行中';
    default:
      return status;
    }
  };

  const executionColumns = [
    {
      title: '流程名称',
      dataIndex: 'flowName',
      key: 'flowName',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Badge color={getStatusColor(status)} text={getStatusText(status)} />
      ),
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
      title: '操作',
      key: 'action',
      render: (_: unknown, record: IntegrationLog) => (
        <div>
          <Button
            type="link"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
            style={{ marginRight: 8 }}
          >
            查看
          </Button>
          {record.status === 'FAILED' && (
            <Button
              type="link"
              icon={<ReloadOutlined />}
              onClick={() => handleRetry(record.id)}
            >
              重试
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div>
      <Tabs defaultActiveKey="executions">
        <TabPane tab="执行记录" key="executions">
          <Card>
            <Table
              columns={executionColumns}
              dataSource={executions}
              rowKey="id"
              loading={loading}
              pagination={{
                current: page,
                pageSize,
                total,
                onChange: (page) => setPage(page),
                onShowSizeChange: (_, size) => setPageSize(size),
              }}
            />
          </Card>
        </TabPane>
        <TabPane tab="执行统计" key="statistics">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 24 }}>
            {statistics.map(stat => (
              <Card key={stat.flowId} title={stat.flowName}>
                <Descriptions column={2}>
                  <Descriptions.Item label="总执行次数">{stat.executionCount}</Descriptions.Item>
                  <Descriptions.Item label="成功次数">
                    <Tag color="green">{stat.successCount}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="失败次数">
                    <Tag color="red">{stat.failureCount}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="平均执行时间">{stat.avgExecutionTime}ms</Descriptions.Item>
                </Descriptions>
              </Card>
            ))}
          </div>
        </TabPane>
      </Tabs>

      <Modal
        title="执行详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={[
          <Button key="close" onClick={() => setDetailModalVisible(false)}>
            关闭
          </Button>,
        ]}
        width={800}
      >
        {currentExecution && (
          <div>
            <Descriptions column={2} bordered>
              <Descriptions.Item label="流程名称">{currentExecution.flowName}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Badge color={getStatusColor(currentExecution.status)} text={getStatusText(currentExecution.status)} />
              </Descriptions.Item>
              <Descriptions.Item label="开始时间">{currentExecution.startTime}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{currentExecution.endTime}</Descriptions.Item>
            </Descriptions>
            
            <div style={{ marginTop: 24 }}>
              <h4>输入数据</h4>
              <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 4, overflow: 'auto' }}>
                {currentExecution.inputData}
              </pre>
            </div>
            
            <div style={{ marginTop: 24 }}>
              <h4>输出数据</h4>
              <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 4, overflow: 'auto' }}>
                {currentExecution.outputData}
              </pre>
            </div>
            
            {currentExecution.errorMessage && (
              <div style={{ marginTop: 24 }}>
                <h4 style={{ color: '#ff4d4f' }}>错误信息</h4>
                <pre style={{ background: '#fff1f0', padding: 16, borderRadius: 4, overflow: 'auto', border: '1px solid #ffccc7' }}>
                  {currentExecution.errorMessage}
                </pre>
              </div>
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};