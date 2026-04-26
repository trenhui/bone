import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Input, Select, DatePicker, Space, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';

interface Field {
  id: string;
  name: string;
  code: string;
  type: string;
  modelName: string;
  status: number;
  createTime: string;
  updateTime: string;
}

const { Option } = Select;
const { RangePicker } = DatePicker;

const FieldList: React.FC = () => {
  const [fields, setFields] = useState<Field[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchParams, setSearchParams] = useState({
    name: '',
    code: '',
    modelName: '',
    status: undefined,
    dateRange: undefined,
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchFields = async () => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        const mockData: Field[] = [
          {
            id: '1',
            name: '姓名',
            code: 'NAME',
            type: '文本',
            modelName: '用户信息模型',
            status: 1,
            createTime: '2026-04-01 10:00:00',
            updateTime: '2026-04-15 14:30:00',
          },
          {
            id: '2',
            name: '身份证号',
            code: 'ID_CARD',
            type: '文本',
            modelName: '用户信息模型',
            status: 1,
            createTime: '2026-04-02 09:00:00',
            updateTime: '2026-04-10 11:20:00',
          },
          {
            id: '3',
            name: '理赔金额',
            code: 'CLAIM_AMOUNT',
            type: '数字',
            modelName: '理赔申请表单模型',
            status: 1,
            createTime: '2026-04-03 14:00:00',
            updateTime: '2026-04-12 09:45:00',
          },
          {
            id: '4',
            name: '理赔类型',
            code: 'CLAIM_TYPE',
            type: '下拉选择',
            modelName: '理赔申请表单模型',
            status: 0,
            createTime: '2026-03-25 16:00:00',
            updateTime: '2026-03-30 10:15:00',
          },
        ];
        setFields(mockData);
        setPagination({ ...pagination, total: mockData.length });
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('获取字段管理失败');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchFields();
  }, []);

  const handleSearch = () => {
    // 实现搜索逻辑
    fetchFields();
  };

  const handleReset = () => {
    setSearchParams({
      name: '',
      code: '',
      modelName: '',
      status: undefined,
      dateRange: undefined,
    });
  };

  const handleAdd = () => {
    // 实现添加逻辑
    message.info('添加字段');
  };

  const handleEdit = (record: Field) => {
    // 实现编辑逻辑
    message.info(`编辑字段: ${record.name}`);
  };

  const handleDelete = (record: Field) => {
    // 实现删除逻辑
    message.info(`删除字段: ${record.name}`);
  };

  const columns: TableProps<Field>['columns'] = [
    {
      title: '字段名称',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: '字段编码',
      dataIndex: 'code',
      key: 'code',
    },
    {
      title: '字段类型',
      dataIndex: 'type',
      key: 'type',
    },
    {
      title: '所属模型',
      dataIndex: 'modelName',
      key: 'modelName',
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
      render: (_: any, record: Field) => (
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
      <Card title="字段管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>添加</Button>}>
        <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end' }}>
          <Input
            placeholder="字段名称"
            value={searchParams.name}
            onChange={(e) => setSearchParams({ ...searchParams, name: e.target.value })}
            style={{ width: 200 }}
          />
          <Input
            placeholder="字段编码"
            value={searchParams.code}
            onChange={(e) => setSearchParams({ ...searchParams, code: e.target.value })}
            style={{ width: 200 }}
          />
          <Input
            placeholder="所属模型"
            value={searchParams.modelName}
            onChange={(e) => setSearchParams({ ...searchParams, modelName: e.target.value })}
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
          dataSource={fields}
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

export default FieldList;