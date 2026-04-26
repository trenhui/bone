import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Input, Select, DatePicker, Space, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';

interface Event {
  id: string;
  name: string;
  code: string;
  description: string;
  status: number;
  createTime: string;
  updateTime: string;
}

const { Option } = Select;
const { RangePicker } = DatePicker;

const EventManage: React.FC = () => {
  const [events, setEvents] = useState<Event[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchParams, setSearchParams] = useState({
    name: '',
    code: '',
    status: undefined,
    dateRange: undefined,
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchEvents = async () => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        const mockData: Event[] = [
          {
            id: '1',
            name: '理赔提交事件',
            code: 'CLAIM_SUBMIT',
            description: '理赔提交事件配置',
            status: 1,
            createTime: '2026-04-01 10:00:00',
            updateTime: '2026-04-15 14:30:00',
          },
          {
            id: '2',
            name: '理赔审核事件',
            code: 'CLAIM_AUDIT',
            description: '理赔审核事件配置',
            status: 1,
            createTime: '2026-04-02 09:00:00',
            updateTime: '2026-04-10 11:20:00',
          },
          {
            id: '3',
            name: '理赔处理事件',
            code: 'CLAIM_PROCESS',
            description: '理赔处理事件配置',
            status: 1,
            createTime: '2026-04-03 14:00:00',
            updateTime: '2026-04-12 09:45:00',
          },
          {
            id: '4',
            name: '理赔完成事件',
            code: 'CLAIM_COMPLETE',
            description: '理赔完成事件配置',
            status: 0,
            createTime: '2026-03-25 16:00:00',
            updateTime: '2026-03-30 10:15:00',
          },
        ];
        setEvents(mockData);
        setPagination({ ...pagination, total: mockData.length });
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('获取事件管理失败');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  const handleSearch = () => {
    // 实现搜索逻辑
    fetchEvents();
  };

  const handleReset = () => {
    setSearchParams({
      name: '',
      code: '',
      status: undefined,
      dateRange: undefined,
    });
  };

  const handleAdd = () => {
    // 实现添加逻辑
    message.info('添加事件');
  };

  const handleEdit = (record: Event) => {
    // 实现编辑逻辑
    message.info(`编辑事件: ${record.name}`);
  };

  const handleDelete = (record: Event) => {
    // 实现删除逻辑
    message.info(`删除事件: ${record.name}`);
  };

  const columns: TableProps<Event>['columns'] = [
    {
      title: '事件名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '事件编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status) => (status === 1 ? '启用' : '禁用'),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      key: 'updateTime',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: Event) => (
        <Space size="middle">
          <Button type="primary" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Button danger icon={<DeleteOutlined />} onClick={() => handleDelete(record)}>
            删除
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '20px' }}>
      <Card title="事件管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>添加</Button>}>
        <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end' }}>
          <Input
            placeholder="事件名称"
            value={searchParams.name}
            onChange={(e) => setSearchParams({ ...searchParams, name: e.target.value })}
            style={{ width: 200 }}
          />
          <Input
            placeholder="事件编码"
            value={searchParams.code}
            onChange={(e) => setSearchParams({ ...searchParams, code: e.target.value })}
            style={{ width: 200 }}
          />
          <Select
            placeholder="状态"
            value={searchParams.status}
            onChange={(value) => setSearchParams({ ...searchParams, status: value })}
            style={{ width: 120 }}
          >
            <Option value={1}>启用</Option>
            <Option value={0}>禁用</Option>
          </Select>
          <RangePicker
            style={{ width: 300 }}
            onChange={(dates) => setSearchParams({ ...searchParams, dateRange: dates })}
          />
          <Space>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              搜索
            </Button>
            <Button onClick={handleReset}>
              重置
            </Button>
          </Space>
        </div>
        <Table
          columns={columns}
          dataSource={events}
          rowKey="id"
          loading={loading}
          pagination={{
            ...pagination,
            onChange: (page, pageSize) => setPagination({ ...pagination, current: page, pageSize }),
          }}
        />
      </Card>
    </div>
  );
};

export default EventManage;