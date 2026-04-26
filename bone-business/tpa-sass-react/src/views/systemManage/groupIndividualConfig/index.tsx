import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Input, Select, DatePicker, Space, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';

interface GroupIndividualConfig {
  id: string;
  name: string;
  code: string;
  type: string;
  description: string;
  status: number;
  createTime: string;
  updateTime: string;
}

const { Option } = Select;
const { RangePicker } = DatePicker;

const GroupIndividualConfig: React.FC = () => {
  const [configs, setConfigs] = useState<GroupIndividualConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchParams, setSearchParams] = useState({
    name: '',
    code: '',
    type: '',
    status: undefined,
    dateRange: undefined,
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchConfigs = async () => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        const mockData: GroupIndividualConfig[] = [
          {
            id: '1',
            name: '团体意外险配置',
            code: 'GROUP_ACCIDENT',
            type: '团险',
            description: '团体意外险配置',
            status: 1,
            createTime: '2026-04-01 10:00:00',
            updateTime: '2026-04-15 14:30:00',
          },
          {
            id: '2',
            name: '个人重疾险配置',
            code: 'INDIVIDUAL_CRITICAL',
            type: '个险',
            description: '个人重疾险配置',
            status: 1,
            createTime: '2026-04-02 09:00:00',
            updateTime: '2026-04-10 11:20:00',
          },
          {
            id: '3',
            name: '团体医疗险配置',
            code: 'GROUP_MEDICAL',
            type: '团险',
            description: '团体医疗险配置',
            status: 1,
            createTime: '2026-04-03 14:00:00',
            updateTime: '2026-04-12 09:45:00',
          },
          {
            id: '4',
            name: '个人意外险配置',
            code: 'INDIVIDUAL_ACCIDENT',
            type: '个险',
            description: '个人意外险配置',
            status: 0,
            createTime: '2026-03-25 16:00:00',
            updateTime: '2026-03-30 10:15:00',
          },
        ];
        setConfigs(mockData);
        setPagination({ ...pagination, total: mockData.length });
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('获取团单个险配置失败');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfigs();
  }, []);

  const handleSearch = () => {
    // 实现搜索逻辑
    fetchConfigs();
  };

  const handleReset = () => {
    setSearchParams({
      name: '',
      code: '',
      type: '',
      status: undefined,
      dateRange: undefined,
    });
  };

  const handleAdd = () => {
    // 实现添加逻辑
    message.info('添加团单个险配置');
  };

  const handleEdit = (record: GroupIndividualConfig) => {
    // 实现编辑逻辑
    message.info(`编辑团单个险配置: ${record.name}`);
  };

  const handleDelete = (record: GroupIndividualConfig) => {
    // 实现删除逻辑
    message.info(`删除团单个险配置: ${record.name}`);
  };

  const columns: TableProps<GroupIndividualConfig>['columns'] = [
    {
      title: '配置名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '配置编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
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
      render: (_: any, record: GroupIndividualConfig) => (
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
      <Card title="团单个险配置" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>添加</Button>}>
        <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end' }}>
          <Input
            placeholder="配置名称"
            value={searchParams.name}
            onChange={(e) => setSearchParams({ ...searchParams, name: e.target.value })}
            style={{ width: 200 }}
          />
          <Input
            placeholder="配置编码"
            value={searchParams.code}
            onChange={(e) => setSearchParams({ ...searchParams, code: e.target.value })}
            style={{ width: 200 }}
          />
          <Select
            placeholder="类型"
            value={searchParams.type}
            onChange={(value) => setSearchParams({ ...searchParams, type: value })}
            style={{ width: 120 }}
          >
            <Option value="团险">团险</Option>
            <Option value="个险">个险</Option>
          </Select>
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
          dataSource={configs}
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

export default GroupIndividualConfig;