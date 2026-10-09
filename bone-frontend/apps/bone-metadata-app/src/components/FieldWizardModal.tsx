import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Steps, Tag, Typography, message } from 'antd';
import { LeftOutlined, PlusOutlined, RightOutlined, DeleteOutlined } from '@ant-design/icons';
import type { CreateMetaFieldReq } from '../types';
import {
  FIELD_TYPE_MAP,
  DATA_CLASSIFICATIONS,
  SENSITIVITY_LEVELS,
} from '../types';

const { Text, Title } = Typography;

/** ENUM 码表选项（T3：字段向导的枚举值编辑器数据模型） */
interface EnumOptionRow {
  code: string;
  label: string;
}

/**
 * 字段向导（2b F10，对标 Salesforce New Field 类型面板）：
 * 第一步按业务语义分组选类型 → 第二步填属性，替代单 Modal 平铺交互。
 */

interface TypeGroup {
  key: string;
  label: string;
  types: string[];
}

const TYPE_GROUPS: TypeGroup[] = [
  { key: 'text', label: '文本类', types: ['STRING', 'TEXT'] },
  { key: 'number', label: '数字类', types: ['INTEGER', 'LONG', 'DECIMAL'] },
  { key: 'bool', label: '布尔类', types: ['BOOLEAN'] },
  { key: 'date', label: '日期类', types: ['DATE', 'DATETIME'] },
  { key: 'enum', label: '枚举/码表', types: ['ENUM'] },
];

export interface FieldWizardProps {
  open: boolean;
  onCancel: () => void;
  onSubmit: (values: CreateMetaFieldReq) => Promise<void>;
}

const FieldWizardModal: React.FC<FieldWizardProps> = ({ open, onCancel, onSubmit }) => {
  const [form] = Form.useForm();
  const [step, setStep] = useState(0);
  const [selectedType, setSelectedType] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  /** T3：ENUM 字段的选项编辑器（替代手敲 JSON，避免码表格式写错导致运行时控件降级） */
  const [enumRows, setEnumRows] = useState<EnumOptionRow[]>([]);

  useEffect(() => {
    if (open) {
      setStep(0);
      setSelectedType(null);
      setEnumRows([]);
      form.resetFields();
    }
  }, [open, form]);

  const isEnum = selectedType === 'ENUM';

  const updateEnumRow = (idx: number, key: keyof EnumOptionRow, value: string) => {
    setEnumRows((prev) => prev.map((r, i) => (i === idx ? { ...r, [key]: value } : r)));
  };

  const addEnumRow = () => setEnumRows((prev) => [...prev, { code: '', label: '' }]);

  const removeEnumRow = (idx: number) =>
    setEnumRows((prev) => prev.filter((_, i) => i !== idx));

  /**
   * 选项行 → enumValues JSON 文本。
   * 校验：code 必填且唯一；label 缺省回落 code。任一行非法 → 返回 undefined 并阻断提交。
   */
  const serializeEnumValues = (): string | undefined => {
    if (!isEnum) return form.getFieldValue('enumValues') || undefined;
    if (enumRows.length === 0) return undefined;
    const seen = new Set<string>();
    for (const r of enumRows) {
      const code = (r.code || '').trim();
      if (!code) {
        message.error('枚举选项的「选项值」不能为空');
        return undefined;
      }
      if (seen.has(code)) {
        message.error(`枚举选项值重复：${code}`);
        return undefined;
      }
      seen.add(code);
    }
    return JSON.stringify(
      enumRows.map((r) => ({ code: (r.code || '').trim(), label: (r.label || '').trim() || (r.code || '').trim() })),
    );
  };

  const needsLength = (t: string | null) => t === 'STRING' || t === 'TEXT' || t === 'DECIMAL';

  const handleFinish = async () => {
    if (!selectedType) return;
    const values = await form.validateFields();
    // ENUM 走结构化选项编辑器；码表非法时 serializeEnumValues 已提示并阻断
    const enumValues = serializeEnumValues();
    if (isEnum && enumValues === undefined && enumRows.length > 0) return;
    setSubmitting(true);
    try {
      await onSubmit({
        name: values.name,
        code: values.code,
        displayName: values.displayName,
        type: selectedType,
        length: values.length,
        required: values.required ?? false,
        comment: values.description,
        sortOrder: values.sortOrder ?? 9999,
        // ===== 业界元数据 / 数据治理属性 =====
        dataClassification: values.dataClassification || undefined,
        pii: values.pii ?? false,
        sensitivityLevel: values.sensitivityLevel || undefined,
        dataSteward: values.dataSteward || undefined,
        businessTerm: values.businessTerm || undefined,
        sourceSystem: values.sourceSystem || undefined,
        enumValues,
        validationRules: values.validationRules || undefined,
      });
    } finally {
      setSubmitting(false);
    }
  };

  /** 「下一步」守卫：ENUM 至少一项，避免建出无码表的空枚举（运行时会渲染成不可用的空 Select） */
  const goStep1 = () => {
    if (!selectedType) return;
    if (selectedType === 'ENUM' && enumRows.length === 0) {
      setEnumRows([{ code: '', label: '' }]);
    }
    setStep(1);
  };

  return (
    <Modal
      title="新建字段向导"
      open={open}
      onCancel={onCancel}
      width={620}
      destroyOnHidden
      footer={
        step === 0 ? (
          <Space>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" disabled={!selectedType} icon={<RightOutlined />} iconPosition="end" onClick={goStep1}>
              下一步
            </Button>
          </Space>
        ) : (
          <Space>
            <Button icon={<LeftOutlined />} onClick={() => setStep(0)}>上一步</Button>
            <Button onClick={onCancel}>取消</Button>
            <Button type="primary" loading={submitting} onClick={handleFinish}>创建字段</Button>
          </Space>
        )
      }
    >
      <Steps
        size="small"
        current={step}
        style={{ marginBottom: 16 }}
        items={[{ title: '选择类型' }, { title: '填写属性' }]}
      />
      {step === 0 && (
        <div>
          {TYPE_GROUPS.map((g) => (
            <div key={g.key} style={{ marginBottom: 12 }}>
              <Text type="secondary" style={{ fontSize: 12 }}>{g.label}</Text>
              <div style={{ display: 'flex', gap: 8, marginTop: 4, flexWrap: 'wrap' }}>
                {g.types.map((t) => {
                  const meta = FIELD_TYPE_MAP[t];
                  const active = selectedType === t;
                  return (
                    <div
                      key={t}
                      role="button"
                      tabIndex={0}
                      onClick={() => setSelectedType(t)}
                      onKeyDown={(e) => e.key === 'Enter' && setSelectedType(t)}
                      style={{
                        flex: '1 1 120px',
                        minWidth: 120,
                        padding: '10px 12px',
                        border: active ? '2px solid #1668dc' : '1px solid #d9d9d9',
                        borderRadius: 8,
                        background: active ? '#f0f7ff' : '#fff',
                        cursor: 'pointer',
                      }}
                    >
                      <Space>
                        <Tag color={meta?.color}>{meta?.icon ?? t}</Tag>
                        <span style={{ fontWeight: 500 }}>{t}</span>
                      </Space>
                      <div>
                        <Text type="secondary" style={{ fontSize: 12 }}>{meta?.desc ?? ''}</Text>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
      )}
      {step === 1 && (
        <Form form={form} layout="vertical">
          <Title level={5} style={{ marginTop: 0 }}>
            类型：<Tag color={FIELD_TYPE_MAP[selectedType ?? '']?.color}>{selectedType}</Tag>
          </Title>
          <Form.Item name="displayName" label="显示名" rules={[{ required: true }]} tooltip="界面展示名，如：姓名">
            <Input placeholder="如：姓名" />
          </Form.Item>
          <Form.Item name="name" label="名称（英文）" rules={[{ required: true }, { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '以字母开头，仅字母/数字/下划线' }]} tooltip="如 firstName">
            <Input placeholder="如：firstName" />
          </Form.Item>
          <Form.Item name="code" label="编码" rules={[{ required: true }, { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '以字母开头，仅字母/数字/下划线' }]} tooltip="实体内唯一，落库物理列名，创建后不可改">
            <Input placeholder="如：first_name" />
          </Form.Item>
          {needsLength(selectedType) && (
            <Form.Item name="length" label="长度" tooltip={selectedType === 'DECIMAL' ? '精度（小数位总长）' : '字符数上限'}>
              <InputNumber min={1} max={selectedType === 'DECIMAL' ? 38 : 65535} style={{ width: '100%' }} placeholder={selectedType === 'DECIMAL' ? '如 10' : '如 64'} />
            </Form.Item>
          )}
          {isEnum && (
            <div style={{ marginBottom: 12 }}>
              <Title level={5} style={{ marginTop: 8 }}>枚举选项</Title>
              <Text type="secondary" style={{ fontSize: 12 }}>
                落库存为标准码表；数据 Tab 会渲染为下拉选择，列表 Tab 渲染为徽标。
              </Text>
              {enumRows.length === 0 ? (
                <Button type="dashed" block icon={<PlusOutlined />} style={{ marginTop: 8 }} onClick={addEnumRow}>
                  添加第一个选项
                </Button>
              ) : (
                <>
                  {enumRows.map((row, idx) => (
                    <Space key={idx} style={{ display: 'flex', marginTop: 8 }} align="baseline">
                      <Input
                        style={{ width: 160 }}
                        placeholder="选项值，如 OPEN"
                        value={row.code}
                        onChange={(e) => updateEnumRow(idx, 'code', e.target.value)}
                      />
                      <Input
                        style={{ width: 160 }}
                        placeholder="显示名，如 待处理"
                        value={row.label}
                        onChange={(e) => updateEnumRow(idx, 'label', e.target.value)}
                      />
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => removeEnumRow(idx)}
                      />
                    </Space>
                  ))}
                  <Button type="dashed" block icon={<PlusOutlined />} style={{ marginTop: 8 }} onClick={addEnumRow}>
                    添加选项
                  </Button>
                </>
              )}
            </div>
          )}
          <Form.Item name="required" label="必填" initialValue={false}>
            <Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号" initialValue={9999}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="字段的业务含义" />
          </Form.Item>

          <Title level={5} style={{ marginTop: 8 }}>
            数据治理属性
          </Title>
          <Form.Item name="dataClassification" label="数据分级">
            <Select
              allowClear
              placeholder="请选择数据分级"
              options={DATA_CLASSIFICATIONS.map((c) => ({ value: c.value, label: `${c.label}（${c.value}）` }))}
            />
          </Form.Item>
          <Form.Item name="pii" label="个人敏感信息(PII)" initialValue={false}>
            <Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />
          </Form.Item>
          <Form.Item name="sensitivityLevel" label="敏感级别">
            <Select
              allowClear
              placeholder="请选择敏感级别"
              options={SENSITIVITY_LEVELS.map((s) => ({ value: s.value, label: s.label }))}
            />
          </Form.Item>
          <Form.Item name="dataSteward" label="数据管家/责任人" tooltip="PII 或高密级字段建议补全责任人">
            <Input placeholder="如：数据治理组-张三" />
          </Form.Item>
          <Form.Item name="businessTerm" label="业务术语/数据标准">
            <Input placeholder="如：客户唯一标识" />
          </Form.Item>
          <Form.Item name="sourceSystem" label="来源系统（血缘）">
            <Input placeholder="如：交易系统" />
          </Form.Item>
          {!isEnum && (
            <Form.Item name="enumValues" label="枚举值/标准码表（JSON）" tooltip="标准码表，如 [{\&quot;code\&quot;:\&quot;M\&quot;,\&quot;label\&quot;:\&quot;男\&quot;}]">
              <Input.TextArea rows={2} placeholder='可选，如 [{"code":"M","label":"男"}]' />
            </Form.Item>
          )}
          <Form.Item name="validationRules" label="校验规则/质量规则（JSON）" tooltip="字段级质量规则，如 {&quot;min&quot;:0,&quot;max&quot;:120}">
            <Input.TextArea rows={2} placeholder='可选，如 {"min":0,"max":120}' />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
};

export default FieldWizardModal;
