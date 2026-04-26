import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Input, Select, DatePicker, Space, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';

interface PolicyRule {
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

const PolicyRuleConfig: React.FC = () => {
  const [policyRules, setPolicyRules] = useState<PolicyRule[]>([]);
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

  const fetchPolicyRules = async () => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        const mockData: PolicyRule[] = [
          {
            id: '1',
            name: '重疾险保单规则',
            code: 'CRITICAL_ILLNESS',
            description: '重疾险保单规则配置',
            status: 1,
            createTime: '2026-04-01 10:00:00',
            updateTime: '2026-04-15 14:30:00',
          },
          {
            id: '2',
            name: '医疗险保单规则',
            code: 'MEDICAL_INSURANCE',
            description: '医疗险保单规则配置',
            status: 1,
            createTime: '2026-04-02 09:00:00',
            updateTime: '2026-04-10 11:20:00',
          },
          {
            id: '3',
            name: '意外险保单规则',
            code: 'ACCIDENT_INSURANCE',
            description: '意外险保单规则配置',
            status: 0,
            createTime: '2026-03-25 16:00:00',
            updateTime: '2026-03-30 10:15:00',
          },
        ];
        setPolicyRules(mockData);
        setPagination({ ...pagination, total: mockData.length });
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('获取保单规则配置失败');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPolicyRules();
  }, []);

  const handleSearch = () => {
    // 实现搜索逻辑
    fetchPolicyRules();
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
    message.info('添加保单规则配置');
  };

  const handleEdit = (record: PolicyRule) => {
    // 实现编辑逻辑
    message.info(`编辑保单规则配置: ${record.name}`);
  };

  const handleDelete = (record: PolicyRule) => {
    // 实现删除逻辑
    message.info(`删除保单规则配置: ${record.name}`);
  };

  const columns: TableProps<PolicyRule>['columns'] = [
    {
      title: '规则名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '规则编码',
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
      render: (_: any, record: PolicyRule) => (
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
      <Card title="保单规则配置" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>添加</Button>}>
        <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end' }}>
          <Input
            placeholder="规则名称"
            value={searchParams.name}
            onChange={(e) => setSearchParams({ ...searchParams, name: e.target.value })}
            style={{ width: 200 }}
          />
          <Input
            placeholder="规则编码"
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
          dataSource={policyRules}
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

export default PolicyRuleConfig;