import React, { useState, useEffect } from 'react';
import { Table, Input, DatePicker, message, Space } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { apiService } from '../services/api';
import { AuditLog } from '../types';

const { RangePicker } = DatePicker;

const AuditLogPage: React.FC = () => {
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [dateRange, setDateRange] = useState<[string, string] | null>(null);

  const fetchAuditLogs = async () => {
    setLoading(true);
    try {
      const response = await apiService.getAuditLogs(
        page,
        pageSize,
        keyword,
        dateRange?.[0],
        dateRange?.[1]
      );
      if (response.code === 200) {
        setAuditLogs(response.data.data);
        setTotal(response.data.total);
      }
    } catch (error) {
      message.error('获取审计日志失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditLogs();
  }, [page, pageSize, keyword, dateRange]);

  const handleDateChange = (dates: any) => {
    if (dates) {
      setDateRange([
        dates[0].format('YYYY-MM-DD'),
        dates[1].format('YYYY-MM-DD')
      ]);
    } else {
      setDateRange(null);
    }
  };

  const columns = [
    {
      title: '操作人',
      dataIndex: 'username',
      key: 'username',
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
    },
    {
      title: '资源ID',
      dataIndex: 'resourceId',
      key: 'resourceId',
    },
    {
      title: '详情',
      dataIndex: 'details',
      key: 'details',
      ellipsis: true,
    },
    {
      title: 'IP地址',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
    },
    {
      title: '操作时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 16 }}>
        <h2>审计日志</h2>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <Input
            placeholder="搜索操作人或操作"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            style={{ width: 300 }}
          />
          <RangePicker onChange={handleDateChange} style={{ width: 300 }} />
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={auditLogs}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          onChange: (page, pageSize) => {
            setPage(page);
            setPageSize(pageSize);
          }
        }}
      />
    </div>
  );
};

export default AuditLogPage;