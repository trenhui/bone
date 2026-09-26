import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Table, Tag, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { referenceApi } from '../services/api';
import type { ReferenceSet, ReferenceValue } from '../types/governance';

/** 参考数据管理（G15 / §2.3）：币种/地区/行业等值域，与主数据分离治理、不走审批流。 */
const ReferenceDataManagement: React.FC = () => {
  const [sets, setSets] = useState<ReferenceSet[]>([]);
  const [selected, setSelected] = useState<ReferenceSet | null>(null);
  const [values, setValues] = useState<ReferenceValue[]>([]);
  const [setOpen, setSetOpen] = useState(false);
  const [valueOpen, setValueOpen] = useState(false);
  const [setForm] = Form.useForm();
  const [valueForm] = Form.useForm();

  const loadSets = useCallback(async () => {
    const res = await referenceApi.sets();
    setSets(res.data ?? []);
  }, []);

  const loadValues = useCallback(async (s: ReferenceSet) => {
    const res = await referenceApi.values(s.id);
    setValues(res.data ?? []);
  }, []);

  useEffect(() => {
    void loadSets();
  }, [loadSets]);

  const setColumns = [
    { title: '值域编码', dataIndex: 'setCode', key: 'c' },
    { title: '值域名称', dataIndex: 'setName', key: 'n' },
    { title: '外部标准', dataIndex: 'externalStandard', key: 'e', render: (v?: string) => v ?? '—' },
    { title: '状态', dataIndex: 'status', key: 's', render: (s: string) => <Tag color={s === 'PUBLISHED' ? 'green' : 'default'}>{s}</Tag> },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: ReferenceSet) => (
        <Button
          size="small"
          type={selected?.id === r.id ? 'primary' : 'default'}
          onClick={() => {
            setSelected(r);
            void loadValues(r);
          }}
        >
          查看值
        </Button>
      )
    }
  ];

  const valueColumns = [
    { title: '值编码', dataIndex: 'valueCode', key: 'c' },
    { title: '值名称', dataIndex: 'valueName', key: 'n' },
    { title: '外部码', dataIndex: 'externalCode', key: 'e', render: (v?: string) => v ?? '—' },
    { title: '排序', dataIndex: 'sortOrder', key: 's' },
    { title: '启用', dataIndex: 'enabled', key: 'en', render: (v: boolean) => (v ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>) },
    {
      title: '操作',
      key: 'act',
      render: (_: unknown, r: ReferenceValue) =>
        r.enabled ? (
          <Button
            size="small"
            danger
            onClick={async () => {
              await referenceApi.disableValue(r.id);
              message.success('已停用');
              if (selected) void loadValues(selected);
            }}
          >
            停用
          </Button>
        ) : null
    }
  ];

  return (
    <Card
      title="参考数据（与主数据分离治理 · 不走审批流）"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setSetOpen(true)}>
          新建值域
        </Button>
      }
    >
      <Table
        rowKey="id"
        size="small"
        columns={setColumns}
        dataSource={sets}
        pagination={false}
        style={{ marginBottom: 16 }}
      />

      {selected && (
        <Card
          size="small"
          type="inner"
          title={`值列表 · ${selected.setName}（${selected.setCode}）`}
          extra={
            <Button size="small" icon={<PlusOutlined />} onClick={() => setValueOpen(true)}>
              新增值
            </Button>
          }
        >
          <Table rowKey="id" size="small" columns={valueColumns} dataSource={values} pagination={false} />
        </Card>
      )}

      <Modal
        title="新建值域"
        open={setOpen}
        onCancel={() => setSetOpen(false)}
        destroyOnClose
        onOk={async () => {
          const v = await setForm.validateFields();
          await referenceApi.createSet(v);
          message.success('值域已创建');
          setSetOpen(false);
          setForm.resetFields();
          void loadSets();
        }}
      >
        <Form form={setForm} layout="vertical">
          <Form.Item name="setCode" label="值域编码" rules={[{ required: true, message: '编码不能为空' }]}>
            <Input placeholder="CURRENCY / COUNTRY / INDUSTRY / UOM" />
          </Form.Item>
          <Form.Item name="setName" label="值域名称" rules={[{ required: true, message: '名称不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="externalStandard" label="外部标准（ISO4217 / GB2260 等）">
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`新增值 · ${selected?.setName ?? ''}`}
        open={valueOpen}
        onCancel={() => setValueOpen(false)}
        destroyOnClose
        onOk={async () => {
          const v = await valueForm.validateFields();
          await referenceApi.createValue(selected!.id, v);
          message.success('值已新增');
          setValueOpen(false);
          valueForm.resetFields();
          if (selected) void loadValues(selected);
        }}
      >
        <Form form={valueForm} layout="vertical">
          <Form.Item name="valueCode" label="值编码" rules={[{ required: true, message: '编码不能为空' }]}>
            <Input placeholder="CNY" />
          </Form.Item>
          <Form.Item name="valueName" label="值名称" rules={[{ required: true, message: '名称不能为空' }]}>
            <Input placeholder="人民币" />
          </Form.Item>
          <Form.Item name="externalCode" label="外部标准码">
            <Input placeholder="156" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default ReferenceDataManagement;
