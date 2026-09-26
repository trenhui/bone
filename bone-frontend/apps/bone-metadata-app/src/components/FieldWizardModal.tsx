import { useEffect, useState } from 'react';
import { Button, Form, Input, InputNumber, Modal, Select, Space, Steps, Tag, Typography } from 'antd';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';
import type { CreateMetaFieldReq } from '../types';
import { FIELD_TYPE_MAP } from '../types';

const { Text, Title } = Typography;

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

  useEffect(() => {
    if (open) {
      setStep(0);
      setSelectedType(null);
      form.resetFields();
    }
  }, [open, form]);

  const needsLength = (t: string | null) => t === 'STRING' || t === 'TEXT' || t === 'DECIMAL';

  const handleFinish = async () => {
    if (!selectedType) return;
    const values = await form.validateFields();
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
      });
    } finally {
      setSubmitting(false);
    }
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
            <Button type="primary" disabled={!selectedType} icon={<RightOutlined />} iconPosition="end" onClick={() => setStep(1)}>
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
          <Form.Item name="required" label="必填" initialValue={false}>
            <Select options={[{ value: true, label: '是' }, { value: false, label: '否' }]} />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号" initialValue={9999}>
            <InputNumber min={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="字段的业务含义" />
          </Form.Item>
        </Form>
      )}
    </Modal>
  );
};

export default FieldWizardModal;
