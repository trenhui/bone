import React, { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { PlusOutlined, RocketOutlined, TagOutlined } from '@ant-design/icons';
import { domainTemplateApi } from '../services/api';

const statusColor = (s: string) =>
  s === 'PUBLISHED' ? 'green' : s === 'DRAFT' ? 'orange' : 'default';

/** 域模板管理（G9 / UC-P1 P2 T1）：平台建模板、发版本；租户实例化。 */
const TemplateManagement: React.FC = () => {
  const [rows, setRows] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [instantiateTpl, setInstantiateTpl] = useState<any | null>(null);
  const [versionTpl, setVersionTpl] = useState<any | null>(null);
  const [createForm] = Form.useForm();
  const [instForm] = Form.useForm();
  const [verForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await domainTemplateApi.page({ pageNum: 1, pageSize: 100 });
      setRows(res.data?.records ?? []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const handleCreate = async () => {
    const v = await createForm.validateFields();
    if (v.fieldSchema) {
      try {
        JSON.parse(v.fieldSchema);
      } catch {
        message.error('field_schema 不是合法 JSON');
        return;
      }
    }
    await domainTemplateApi.create(v);
    message.success('模板已创建');
    setCreateOpen(false);
    createForm.resetFields();
    void load();
  };

  const handlePublishVersion = async () => {
    const v = await verForm.validateFields();
    await domainTemplateApi.publishVersion(versionTpl.id, v);
    message.success('版本已发布');
    setVersionTpl(null);
    verForm.resetFields();
    void load();
  };

  const handleInstantiate = async () => {
    const v = await instForm.validateFields();
    await domainTemplateApi.instantiate({ ...v, templateId: instantiateTpl.id, withFields: true });
    message.success('已从模板实例化主数据模型');
    setInstantiateTpl(null);
    instForm.resetFields();
  };

  const columns = [
    { title: '域编码', dataIndex: 'domainCode', key: 'domainCode' },
    { title: '域名称', dataIndex: 'domainName', key: 'domainName' },
    { title: '当前版本', dataIndex: 'currentVersion', key: 'currentVersion' },
    {
      title: '默认治理等级',
      dataIndex: 'defaultGovernanceTier',
      key: 'tier',
      render: (t: string) => <Tag color="blue">{t}</Tag>
    },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: string) => <Tag color={statusColor(s)}>{s}</Tag> },
    {
      title: '操作',
      key: 'actions',
      render: (_: unknown, r: any) => (
        <Space>
          <Button size="small" icon={<TagOutlined />} onClick={() => setVersionTpl(r)}>
            发布版本
          </Button>
          <Button
            size="small"
            type="primary"
            icon={<RocketOutlined />}
            disabled={r.status !== 'PUBLISHED'}
            onClick={() => setInstantiateTpl(r)}
          >
            实例化
          </Button>
          <Popconfirm title="确认归档该模板？" onConfirm={async () => { await domainTemplateApi.archive(r.id); message.success('已归档'); void load(); }}>
            <Button size="small" danger disabled={r.status !== 'PUBLISHED'}>
              归档
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <Card
      title="主数据域模板"
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>
          新建模板
        </Button>
      }
    >
      <Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={false} />

      <Modal
        title="新建域模板"
        open={createOpen}
        onOk={handleCreate}
        onCancel={() => setCreateOpen(false)}
        width={640}
        destroyOnClose
      >
        <Form form={createForm} layout="vertical">
          <Form.Item name="domainCode" label="域编码" rules={[{ required: true, message: '域编码不能为空' }]}>
            <Input placeholder="CUSTOMER / SUPPLIER / MATERIAL / ..." />
          </Form.Item>
          <Form.Item name="domainName" label="域名称" rules={[{ required: true, message: '域名称不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="defaultGovernanceTier" label="默认治理等级" initialValue="L1">
            <Select
              options={[
                { value: 'L1', label: 'L1 轻量（无审批/无版本）' },
                { value: 'L2', label: 'L2 标准（审批+版本）' },
                { value: 'L3', label: 'L3 严格（审批+版本+生效期）' }
              ]}
            />
          </Form.Item>
          <Form.Item name="fieldSchema" label="默认字段集（JSON 数组）">
            <Input.TextArea rows={4} placeholder='[{"code":"name","name":"名称","type":"STRING","required":true}]' />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`发布版本 · ${versionTpl?.domainName ?? ''}`}
        open={!!versionTpl}
        onOk={handlePublishVersion}
        onCancel={() => setVersionTpl(null)}
        destroyOnClose
      >
        <Form form={verForm} layout="vertical">
          <Form.Item name="versionNumber" label="版本号（semver）" rules={[{ required: true, message: '版本号不能为空' }]}>
            <Input placeholder="1.1.0" />
          </Form.Item>
          <Form.Item name="changeLog" label="变更说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`从模板实例化 · ${instantiateTpl?.domainName ?? ''}`}
        open={!!instantiateTpl}
        onOk={handleInstantiate}
        onCancel={() => setInstantiateTpl(null)}
        destroyOnClose
      >
        <Form form={instForm} layout="vertical">
          <Form.Item name="name" label="模型名称" rules={[{ required: true, message: '模型名称不能为空' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="entityCode" label="模型编码（留空自动生成）">
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="governanceTier" label="治理等级（留空跟随模板）">
            <Select allowClear options={[
              { value: 'L1', label: 'L1 轻量' },
              { value: 'L2', label: 'L2 标准' },
              { value: 'L3', label: 'L3 严格' }
            ]} />
          </Form.Item>
          <Form.Item name="owningAppId" label="责任归口应用ID（留空=平台共享域）">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default TemplateManagement;
