import React, { useState, useEffect } from 'react';
import { Card, Table, Button, Input, Select, DatePicker, Space, message, Descriptions } from 'antd';
import { EditOutlined, DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import type { TableProps } from 'antd';

interface BizIdentityConfig {
  id: string;
  name: string;
  code: string;
  description: string;
  status: number;
  createTime: string;
  updateTime: string;
}

interface ConfigDetail {
  field: string;
  value: string;
  description: string;
}

const { Option } = Select;
const { RangePicker } = DatePicker;

const BizIdentityConfigDetail: React.FC = () => {
  const [config, setConfig] = useState<BizIdentityConfig | null>(null);
  const [configDetails, setConfigDetails] = useState<ConfigDetail[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchParams, setSearchParams] = useState({
    field: '',
    value: '',
  });
  const [pagination, setPagination] = useState({
    current: 1,
    pageSize: 10,
    total: 0,
  });

  const fetchConfigDetail = async () => {
    setLoading(true);
    try {
      // 模拟API调用
      setTimeout(() => {
        const mockConfig: BizIdentityConfig = {
          id: '1',
          name: '某企业专属配置',
          code: 'ENTERPRISE_A',
          description: '某企业的专属理赔配置',
          status: 1,
          createTime: '2026-04-01 10:00:00',
          updateTime: '2026-04-15 14:30:00',
        };
        setConfig(mockConfig);

        const mockDetails: ConfigDetail[] = [
          {
            field: '理赔限额',
            value: '100000',
            description: '单次理赔最高限额',
          },
          {
            field: '理赔时效',
            value: '3',
            description: '理赔处理时效（工作日）',
          },
          {
            field: '免赔额',
            value: '1000',
            description: '每次理赔免赔额',
          },
          {
            field: '报销比例',
            value: '80%',
            description: '理赔报销比例',
          },
        ];
        setConfigDetails(mockDetails);
        setPagination({ ...pagination, total: mockDetails.length });
        setLoading(false);
      }, 500);
    } catch (error) {
      message.error('获取主体专属配置详情失败');
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchConfigDetail();
  }, []);

  const handleSearch = () => {
    // 实现搜索逻辑
    fetchConfigDetail();
  };

  const handleReset = () => {
    setSearchParams({
      field: '',
      value: '',
    });
  };

  const handleEdit = (record: ConfigDetail) => {
    // 实现编辑逻辑
    message.info(`编辑配置项: ${record.field}`);
  };

  const handleDelete = (record: ConfigDetail) => {
    // 实现删除逻辑
    message.info(`删除配置项: ${record.field}`);
  };

  const columns: TableProps<ConfigDetail>['columns'] = [
    {
      title: '配置项',
      dataIndex: 'field',
      key: 'field',
    },
    {
      title: '配置值',
      dataIndex: 'value',
      key: 'value',
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
    },
    {
      title: '操作',
      key: 'action',
      render: (_: any, record: ConfigDetail) => (
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
      {config && (
        <>
          <Card title="主体专属配置详情">
            <Descriptions bordered column={2}>
              <Descriptions.Item label="配置名称">{config.name}</Descriptions.Item>
              <Descriptions.Item label="配置编码">{config.code}</Descriptions.Item>
              <Descriptions.Item label="描述">{config.description}</Descriptions.Item>
              <Descriptions.Item label="状态">{config.status === 1 ? '启用' : '禁用'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{config.createTime}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{config.updateTime}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="配置详情" style={{ marginTop: 16 }}>
            <div style={{ marginBottom: 16, display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'end' }}>
              <Input
                placeholder="配置项"
                value={searchParams.field}
                onChange={(e) => setSearchParams({ ...searchParams, field: e.target.value })}
                style={{ width: 200 }}
              />
              <Input
                placeholder="配置值"
                value={searchParams.value}
                onChange={(e) => setSearchParams({ ...searchParams, value: e.target.value })}
                style={{ width: 200 }}
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
              dataSource={configDetails}
              rowKey="field"
              loading={loading}
              pagination={{
                ...pagination,
                onChange: (page, pageSize) => setPagination({ ...pagination, current: page, pageSize }),
              }}
            />
          </Card>
        </>
      )}
    </div>
  );
};

export default BizIdentityConfigDetail;