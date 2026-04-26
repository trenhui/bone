import React, { useEffect, useState } from 'react';
import { Card, Button, Table, Input, Select, DatePicker } from 'antd';
import { SearchOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';

const { Option } = Select;
const { RangePicker } = DatePicker;

interface GroupSignItem {
  id: string;
  groupName: string;
  policyNo: string;
  signCount: number;
  totalCount: number;
  status: string;
  createTime: string;
  creator: string;
}

const Page: React.FC = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<GroupSignItem[]>([]);
  const [loading, setLoading] = useState(false);

  const columns = [
    {
      title: '团体名称',
      dataIndex: 'groupName',
      key: 'groupName',
    },
    {
      title: '保单号',
      dataIndex: 'policyNo',
      key: 'policyNo',
    },
    {
      title: '已签名人数',
      dataIndex: 'signCount',
      key: 'signCount',
    },
    {
      title: '总人数',
      dataIndex: 'totalCount',
      key: 'totalCount',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
    },
    {
      title: '创建人',
      dataIndex: 'creator',
      key: 'creator',
    },
    {
      title: '操作',
      key: 'action',
      render: (_, record) => (
        <div>
          <Button type="link" onClick={() => navigate(`/jobManage/groupSign/detail/${record.id}`)}>
            查看详情
          </Button>
        </div>
      ),
    },
  ];

  const handleCreate = () => {
    navigate('/jobManage/groupSign/create');
  };

  return (
    <div className="app-container">
      <Card className="mb-2" shadow={false}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
          <div>
            <span style={{ fontWeight: 'bold', color: '#1890ff' }}>
              团险签收管理
            </span>
          </div>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建团险签收
          </Button>
        </div>

        <div style={{ display: 'flex', gap: '12px', marginBottom: '16px', flexWrap: 'wrap' }}>
          <Input placeholder="团体名称" style={{ width: 200 }} />
          <Input placeholder="保单号" style={{ width: 200 }} />
          <Select placeholder="状态" style={{ width: 120 }}>
            <Option value="all">全部</Option>
            <Option value="pending">待处理</Option>
            <Option value="completed">已完成</Option>
          </Select>
          <RangePicker style={{ width: 300 }} />
          <Button type="primary" icon={<SearchOutlined />}>
            搜索
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={data}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </div>
  );
};

export default Page;
