import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Alert, Button, Card, Col, Descriptions, Form, Input, Radio, Row, Select,
  Space, Spin, Steps, Table, Tag, Typography, message,
} from 'antd';
import { ArrowLeftOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import { appApi, moduleApi, type BoneApplication, type BoneModule } from '../services/appModuleApi';
import { metadataTemplateApi } from '../services/metadataApi';
import type { MetaTemplate, MetaTemplateField } from '../types';
import { DELIVERY_MODE, META_DELIVERY_RUNTIME } from '../types';

const { Title, Text, Paragraph } = Typography;

const CODE_PATTERN = /^[a-zA-Z][a-zA-Z0-9_]*$/;

/**
 * 模板实例化向导（UC-W2 主流程 A / 2b F6）：选模板 → 看字段预览 → 填编码/表名/归属模块 → 实例化 → 直达实体详情。
 *
 * 后端 G3（ADR-0038）已提供 /api/v1/metadata/templates 目录与 instantiate；本页是其唯一前端入口，
 * 实例化成功后实体 scope=TENANT、template_id 追溯，模板字段带「平台模板」语义（后续升级 diff 预留）。
 */
const TemplateWizard: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [form] = Form.useForm();

  const [step, setStep] = useState(0);
  const [apps, setApps] = useState<BoneApplication[]>([]);
  const [modules, setModules] = useState<BoneModule[]>([]);
  const [appId, setAppId] = useState<string | undefined>(searchParams.get('appId') ?? undefined);
  const [moduleId, setModuleId] = useState<string | undefined>(
    searchParams.get('moduleId') ?? undefined,
  );
  const [templates, setTemplates] = useState<MetaTemplate[]>([]);
  const [selectedTemplate, setSelectedTemplate] = useState<MetaTemplate | null>(null);
  const [tplFields, setTplFields] = useState<MetaTemplateField[]>([]);
  const [fieldsLoading, setFieldsLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    appApi.listMine({ page: 1, size: 50 }).then((res) => {
      if (res.code === 200) setApps(res.data.list);
    });
    metadataTemplateApi.list().then((res) => {
      if (res.code === 200) setTemplates(res.data.filter((t) => t.status === 1));
    });
  }, []);

  useEffect(() => {
    if (!appId) { setModules([]); return; }
    moduleApi.listByApp(appId, { pageNum: 1, pageSize: 100 }).then((res) => {
      if (res.code === 200) setModules(res.data.list);
    });
  }, [appId]);

  const chooseTemplate = async (tpl: MetaTemplate | null) => {
    setSelectedTemplate(tpl);
    setTplFields([]);
    if (!tpl) return;
    setFieldsLoading(true);
    try {
      const res = await metadataTemplateApi.fields(tpl.id);
      if (res.code === 200) setTplFields(res.data);
    } finally {
      setFieldsLoading(false);
    }
  };

  const fieldColumns: ColumnsType<MetaTemplateField> = [
    { title: '字段', dataIndex: 'displayName', width: 130, ellipsis: true },
    { title: '编码', dataIndex: 'code', width: 120, ellipsis: true },
    { title: '类型', dataIndex: 'fieldType', width: 90, render: (t: string) => <Tag>{(t || '').toUpperCase()}</Tag> },
    { title: '长度', dataIndex: 'length', width: 70, render: (v: number | null) => v ?? '—' },
    {
      title: '必填', dataIndex: 'required', width: 60,
      render: (v: boolean) => (v ? <Tag color="red">是</Tag> : <Tag>否</Tag>),
    },
  ];

  const goStep = (target: number) => {
    if (target >= 1 && !selectedTemplate) {
      message.warning('请先选择模板');
      return;
    }
    if (target >= 2 && !moduleId) {
      message.warning('请先选择归属模块');
      return;
    }
    setStep(target);
  };

  const handleSubmit = async () => {
    if (!selectedTemplate) return;
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const res = await metadataTemplateApi.instantiate(selectedTemplate.id, {
        name: values.name,
        code: values.code,
        displayName: values.displayName,
        description: values.description,
        tableName: values.tableName,
        moduleId,
        deliveryMode: values.deliveryMode,
      });
      if (res.code === 201 || res.code === 200) {
        message.success('实例化成功，已进入实体详情');
        const newId = res.data;
        navigate(
          appId && moduleId
            ? `/apps/${appId}/modules/${moduleId}/entities/${newId}`
            : `/entities/${newId}`,
        );
      } else {
        message.error(res.message || '实例化失败');
      }
    } catch {
      message.error('实例化失败，请检查编码/表名是否重复');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="page">
      <Space style={{ marginBottom: 12 }}>
        <Button type="link" icon={<ArrowLeftOutlined />} style={{ padding: 0 }} onClick={() => navigate('/apps')}>
          返回工作台
        </Button>
        <Text type="secondary">/</Text>
        <Title level={4} style={{ margin: 0 }}>从平台模板新建实体</Title>
      </Space>

      <Steps
        size="small"
        current={step}
        items={[{ title: '选模板' }, { title: '归属模块' }, { title: '填写并实例化' }]}
        style={{ maxWidth: 640, marginBottom: 20 }}
      />

      {/* Step 0: 选模板 + 字段预览 */}
      {step === 0 && (
        <Row gutter={16}>
          <Col xs={24} md={10}>
            <Card
              size="small"
              title="平台模板（平台预置行业/领域基线，租户只读）"
              extra={<Button size="small" icon={<ReloadOutlined />} onClick={() => metadataTemplateApi.list().then((r) => r.code === 200 && setTemplates(r.data.filter((t) => t.status === 1)))} />}
            >
              <Radio.Group
                style={{ width: '100%' }}
                value={selectedTemplate?.id}
                onChange={(e) => {
                  const tpl = templates.find((t) => t.id === e.target.value) || null;
                  chooseTemplate(tpl);
                }}
              >
                <Space direction="vertical" style={{ width: '100%' }}>
                  {templates.map((t) => (
                    <Radio key={t.id} value={t.id} style={{ display: 'flex', alignItems: 'flex-start' }}>
                      <div>
                        <Space>
                          <Text strong>{t.name}</Text>
                          <Text code style={{ fontSize: 12 }}>{t.code}</Text>
                          {t.currentVersion && <Tag>{t.currentVersion}</Tag>}
                        </Space>
                        <Paragraph type="secondary" style={{ marginBottom: 0, fontSize: 12 }}>
                          {t.description || t.domain || '—'}
                        </Paragraph>
                      </div>
                    </Radio>
                  ))}
                  {templates.length === 0 && <Alert type="info" message="暂无已发布的平台模板" />}
                </Space>
              </Radio.Group>
            </Card>
          </Col>
          <Col xs={24} md={14}>
            <Card size="small" title={selectedTemplate ? `「${selectedTemplate.name}」默认字段（${tplFields.length}）` : '字段预览'}>
              {fieldsLoading ? (
                <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
              ) : (
                <Table
                  rowKey="code"
                  columns={fieldColumns}
                  dataSource={tplFields}
                  pagination={false}
                  size="small"
                  locale={{ emptyText: selectedTemplate ? '该模板无默认字段' : '选择左侧模板查看默认字段' }}
                />
              )}
              <Button type="primary" style={{ marginTop: 12 }} disabled={!selectedTemplate} onClick={() => goStep(1)}>
                下一步：归属模块
              </Button>
            </Card>
          </Col>
        </Row>
      )}

      {/* Step 1: 归属应用与模块 */}
      {step === 1 && (
        <Card size="small" style={{ maxWidth: 640 }}>
          <Form layout="vertical" style={{ maxWidth: 420 }}>
            <Form.Item label="所属应用" required>
              <Select
                placeholder="选择要挂载的应用"
                value={appId}
                options={apps.map((a) => ({ value: a.id, label: `${a.name}（${a.code}）` }))}
                onChange={(v) => { setAppId(v); setModuleId(undefined); }}
              />
            </Form.Item>
            <Form.Item label="所属模块" required tooltip="实体归属到模块；模块归属决定应用建模权限边界">
              <Select
                placeholder="选择模块"
                value={moduleId}
                disabled={!appId}
                options={modules.map((m) => ({ value: String(m.id), label: `${m.name}（${m.code}）` }))}
                onChange={(v) => setModuleId(v)}
              />
            </Form.Item>
            <Space>
              <Button onClick={() => setStep(0)}>上一步</Button>
              <Button type="primary" disabled={!moduleId} onClick={() => goStep(2)}>下一步：填写信息</Button>
            </Space>
          </Form>
        </Card>
      )}

      {/* Step 2: 填写编码/表名并实例化 */}
      {step === 2 && (
        <Card size="small" style={{ maxWidth: 640 }}>
          <Descriptions size="small" column={2} style={{ marginBottom: 12 }}>
            <Descriptions.Item label="模板">{selectedTemplate?.name}</Descriptions.Item>
            <Descriptions.Item label="默认字段">{tplFields.length} 个（实例化时复制）</Descriptions.Item>
          </Descriptions>
          <Form form={form} layout="vertical" style={{ maxWidth: 420 }}>
            <Form.Item name="code" label="实体编码" rules={[
              { required: true, message: '请输入实体编码' },
              { pattern: CODE_PATTERN, message: '以字母开头，仅字母/数字/下划线' },
            ]} tooltip="租户内唯一；实例化后编码归租户">
              <Input placeholder="如：customer" />
            </Form.Item>
            <Form.Item name="tableName" label="数据库表名" rules={[
              { required: true, message: '请输入表名' },
              { pattern: CODE_PATTERN, message: '以字母开头，仅字母/数字/下划线' },
            ]} tooltip="发布（RUNTIME 模式）时将按此表名建表/对齐">
              <Input placeholder="如：t_customer" />
            </Form.Item>
            <Form.Item name="name" label="名称" rules={[{ required: true }]}>
              <Input placeholder="如：customer" />
            </Form.Item>
            <Form.Item name="displayName" label="显示名" rules={[{ required: true }]}>
              <Input placeholder="如：客户" />
            </Form.Item>
            <Form.Item name="deliveryMode" label="交付模式" initialValue={META_DELIVERY_RUNTIME} tooltip="模式A=生成源码入Git；模式B=发布即得运行时 CRUD API">
              <Select
                options={Object.entries(DELIVERY_MODE).map(([value, label]) => ({ value: Number(value), label }))}
              />
            </Form.Item>
            <Form.Item name="description" label="描述">
              <Input.TextArea rows={2} placeholder="实体的业务含义（可选）" />
            </Form.Item>
            <Space>
              <Button onClick={() => setStep(1)}>上一步</Button>
              <Button type="primary" loading={submitting} onClick={handleSubmit}>
                实例化并进入详情
              </Button>
            </Space>
          </Form>
        </Card>
      )}
    </div>
  );
};

export default TemplateWizard;
