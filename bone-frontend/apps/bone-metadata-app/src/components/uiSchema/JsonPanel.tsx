/**
 * JSON 源码面板：给熟悉协议的工程师一个逃生舱。
 *
 * 定位：不是主要编辑方式，而是"可视化编辑做不到的事"的兜底（批量改、粘贴别人配好的 schema、
 * 排查可视化编辑器的边界情况）。因此不做双向实时同步（会造成输入被覆盖的诡异体验），
 * 而是显式「应用」——用户始终掌控什么时候把文本写回配置。
 */
import { useEffect, useState } from 'react';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { CheckOutlined, CopyOutlined, FormatPainterOutlined } from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onApply: (next: BoneUiSchema) => void;
  /** 外部 schema 变更时是否重置文本框（切 tab / 保存后） */
  resetKey: number;
}

export default function JsonPanel({ schema, onApply, resetKey }: Props): React.ReactElement {
  const [text, setText] = useState('');
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    setText(JSON.stringify(schema, null, 2));
    setErr(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resetKey]);

  const parse = (): BoneUiSchema | null => {
    try {
      const v = JSON.parse(text) as BoneUiSchema;
      if (!v || typeof v !== 'object') throw new Error('顶层必须是对象');
      setErr(null);
      return v;
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
      return null;
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Space style={{ marginBottom: 8 }} wrap>
        <Button size="small" icon={<FormatPainterOutlined />} onClick={() => {
          const v = parse();
          if (v) setText(JSON.stringify(v, null, 2));
        }}>
          格式化
        </Button>
        <Button size="small" icon={<CopyOutlined />} onClick={() => { void navigator.clipboard?.writeText(text); }}>
          复制
        </Button>
        <Button
          size="small" type="primary" icon={<CheckOutlined />}
          onClick={() => { const v = parse(); if (v) onApply(v); }}
        >
          应用到配置
        </Button>
        {err
          ? <Tag color="red">JSON 非法：{err}</Tag>
          : <Tag color="green">语法正确</Tag>}
      </Space>

      {err && (
        <Alert type="error" showIcon style={{ marginBottom: 8 }} message="JSON 解析失败，未写入配置" description={err} />
      )}

      <textarea
        value={text}
        onChange={(e) => { setText(e.target.value); setErr(null); }}
        spellCheck={false}
        style={{
          flex: 1, minHeight: 320, width: '100%', resize: 'none',
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
          fontSize: 12, lineHeight: 1.6, padding: 12, borderRadius: 8,
          border: `1px solid ${err ? '#ff4d4f' : '#d9d9d9'}`, background: '#fafbfd', color: '#262626',
          outline: 'none', tabSize: 2,
        }}
      />

      <Text type="secondary" style={{ fontSize: 11, marginTop: 6 }}>
        修改后需点「应用到配置」才会生效。协议根与对象均为 additionalProperties:false，未知字段会在发布时被拒绝。
      </Text>
    </div>
  );
}
