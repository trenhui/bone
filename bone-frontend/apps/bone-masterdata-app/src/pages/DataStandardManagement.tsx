import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import { dataStandardApi } from '../services/api';
import type { DataStandard } from '../types/governance';
import { useMessage } from '../App';

/** 规则类型枚举（后端 StandardRuleType：1=ENCODING 编码规则 / 2=REFERENCE 引用值域）。 */
const RULE_TYPE_OPTIONS = [
  { value: 1, label: '编码规则（正则）' },
  { value: 2, label: '引用值域' }
];

const ruleTypeLabel = (t: number) => (t === 1 ? '编码规则' : t === 2 ? '引用值域' : String(t));

/** 数据标准管理：字段级编码规则 / 值域引用契约，写操作门禁 masterdata:standards:write。 */
const DataStandardManagement: React.FC = () => {
  const message = useMessage();
  const [rows, setRows] = useState<DataStandard[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [filterEntityCode, setFilterEntityCode] = useState('');
  const [filterKeyword, setFilterKeyword] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<DataStandard | null>(null);
  const [form] = Form.useForm();
  const ruleType = Form.useWatch<number | undefined>('ruleType', form);

  const load = useCallback(
    async (p: number, s: number, entityCode?: string, keyword?: string) => {
      setLoading(true);
      try {
        const res = await dataStandardApi.page({
          page: p,
          size: s,
          entityCode: entityCode || undefined,
          keyword: keyword || undefined
        });
        setRows(res.data?.records ?? []);
        setTotal(Number(res.data?.total ?? 0));
      } catch {
        message.error('加载数据标准失败');
      } finally {
        setLoading(false);
      }
    },
    [message]
  );

  useEffect(() => {
    void load(page, size);
    // 仅在挂载时加载；筛选/翻页由显式动作触发，避免每次渲染重拉
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEdit = (row: DataStandard) => {
    setEditing(row);
    form.setFieldsValue({
      ruleType: row.ruleType,
      pattern: row.pattern,
      refCode: row.refCode,
      description: row.description
    });
    setModalOpen(true);
  };

  const submit = async () => {
    let v: Record<string, unknown>;
    try {
      v = await form.validateFields();
    } catch {
      return; // 字段级校验错误由 Form.Item 就地展示
    }
    try {
      if (editing) {
        await dataStandardApi.update(editing.id, {
          ruleType: v.ruleType as number,
          pattern: v.pattern as string | undefined,
          refCode: v.refCode as string | undefined,
          description: v.description as string | undefined
        });
        message.success('已保存');
      } else {
        await dataStandardApi.create({
          entityCode: v.entityCode as string,
          fieldCode: v.fieldCode as string,
          ruleType: v.ruleType as number,
          pattern: v.pattern as string | undefined,
          refCode: v.refCode as string | undefined,
          description: v.description as string | undefined
        });
        message.success('已创建');
      }
      setModalOpen(false);
      void load(page, size, filterEntityCode, filterKeyword);
    } catch {
      message.error(editing ? '保存数据标准失败' : '创建数据标准失败');
    }
  };

  const remove = async (row: DataStandard) => {
    try {
      await dataStandardApi.delete(row.id);
      message.success('已删除');
      void load(page, size, filterEntityCode, filterKeyword);
    } catch {
      message.error('删除数据标准失败');
    }
  };

  const columns = [
    { title: '实体编码', dataIndex: 'entityCode', key: 'ec' },
    { title: '字段编码', dataIndex: 'fieldCode', key: 'fc' },
    {
      title: '规则类型',
      dataIndex: 'ruleType',
      key: 'rt',
      render: (t: number) => <Tag color={t === 1 ? 'blue' : 'purple'}>{ruleTypeLabel(t)}</Tag>
    },
    {
      title: '约束',
      key: 'constraint',
      render: (_: unknown, r: DataStandard) =>
        r.ruleType === 2 ? (
          <span>
            值域：<Tag>{r.refCode ?? '—'}</Tag>
          </span>
        ) : (
          <span>
            正则：<Tag>{r.pattern ?? '—'}</Tag>
          </span>
        )
    },
    { title: '描述', dataIndex: 'description', key: 'd', ellipsis: true },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'u' },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: DataStandard) => (
        <Space>
          <AuthButton code={BonePermissionCodes.MASTERDATA_STANDARDS_WRITE} size="small" onClick={() => openEdit(r)}>
            编辑
          </AuthButton>
          <AuthButton code={BonePermissionCodes.MASTERDATA_STANDARDS_WRITE} size="small" danger>
            <Popconfirm title="确认删除该数据标准？" onConfirm={() => void remove(r)}>
              <span>删除</span>
            </Popconfirm>
          </AuthButton>
        </Space>
      )
    }
  ];

  return (
    <Card
      title="数据标准"
      extra={
        <AuthButton code={BonePermissionCodes.MASTERDATA_STANDARDS_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
          新建标准
        </AuthButton>
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          placeholder="实体编码（可选）"
          style={{ width: 200 }}
          value={filterEntityCode}
          onChange={(e) => setFilterEntityCode(e.target.value)}
        />
        <Input
          allowClear
          placeholder="字段编码 / 描述关键字"
          style={{ width: 220 }}
          value={filterKeyword}
          onChange={(e) => setFilterKeyword(e.target.value)}
        />
        <Button
          type="primary"
          onClick={() => {
            setPage(1);
            void load(1, size, filterEntityCode, filterKeyword);
          }}
        >
          查询
        </Button>
        <Button
          onClick={() => {
            setFilterEntityCode('');
            setFilterKeyword('');
            setPage(1);
            void load(1, size);
          }}
        >
          重置
        </Button>
      </Space>

      <Table<DataStandard>
        rowKey="id"
        loading={loading}
        size="small"
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, s) => {
            setPage(p);
            setSize(s);
            void load(p, s, filterEntityCode, filterKeyword);
          }
        }}
      />

      <Modal
        title={editing ? `编辑数据标准 · ${editing.entityCode}.${editing.fieldCode}` : '新建数据标准'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        destroyOnHidden
        onOk={() => void submit()}
      >
        <Form form={form} layout="vertical">
          {editing ? (
            /* 实体/字段编码创建后不可改（后端 UpdateDataStandardReq 不接受），编辑态只读展示 */
            <Form.Item label="实体编码">
              <Input value={`${editing.entityCode}.${editing.fieldCode}`} disabled />
            </Form.Item>
          ) : (
            <>
              <Form.Item name="entityCode" label="实体编码" rules={[{ required: true, message: '实体编码不能为空' }]}>
                <Input placeholder="如 MATERIAL / CUSTOMER" />
              </Form.Item>
              <Form.Item name="fieldCode" label="字段编码" rules={[{ required: true, message: '字段编码不能为空' }]}>
                <Input placeholder="如 code / name" />
              </Form.Item>
            </>
          )}
          <Form.Item name="ruleType" label="规则类型" rules={[{ required: true, message: '请选择规则类型' }]}>
            <Select options={RULE_TYPE_OPTIONS} placeholder="请选择规则类型" />
          </Form.Item>
          {ruleType === 1 && (
            <Form.Item
              name="pattern"
              label="正则表达式"
              rules={[{ required: true, message: '编码规则必须填写正则表达式' }]}
            >
              <Input placeholder="如 ^[A-Z]{2}\d{6}$" />
            </Form.Item>
          )}
          {ruleType === 2 && (
            <Form.Item name="refCode" label="引用值域编码" rules={[{ required: true, message: '引用值域必须填写值域编码' }]}>
              <Input placeholder="参考数据值域编码，如 CURRENCY / COUNTRY" />
            </Form.Item>
          )}
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default DataStandardManagement;
