import React, { useState, useEffect } from 'react';
import { Table, Input, DatePicker, message } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import * as api from '../services/api';
import { unwrapPage } from '../utils/pageResult';
import type { AuditLog } from '../types';

const { RangePicker } = DatePicker;

const AuditLogPage: React.FC = () => {
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');


  const fetchAuditLogs = async () => {
    setLoading(true);
    try {
      const response = await api.getAuditLogs({ page, pageSize });
      if (response.code === 200) {
        const { records, total } = unwrapPage(response.data);
        setAuditLogs(records);
        setTotal(total);
      }
    } catch {
      message.error('获取审计日志失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchAuditLogs();
  }, [page, pageSize]);

  const columns = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 90 },
    { title: '操作', dataIndex: 'operation', key: 'operation' },
    { title: '资源类型', dataIndex: 'resourceType', key: 'resourceType' },
    { title: '资源ID', dataIndex: 'resourceId', key: 'resourceId' },
    { title: '结果', dataIndex: 'result', key: 'result', width: 90 },
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
    { title: '操作时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
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
          <RangePicker style={{ width: 300 }} />
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
          onChange: (p, ps) => {
            setPage(p);
            if (ps) setPageSize(ps);
          }
        }}
      />
    </div>
  );
};

export default AuditLogPage;
