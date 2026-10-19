/**
 * JSON 源码面板：给熟悉协议的工程师一个逃生舱。
 *
 * 定位：不是主要编辑方式，而是"可视化编辑做不到的事"的兜底（批量改、粘贴别人配好的 schema、
 * 排查可视化编辑器的边界情况）。因此不做双向实时同步（会造成输入被覆盖的诡异体验），
 * 而是显式「应用」——用户始终掌控什么时候把文本写回配置。
 *
 * 应用前门禁（v1.2 补齐，对标 Retool/Appsmith 的 apply 前 schema 校验）：
 * - checkSchemaVersion：大版本≠1 直接拒绝（未知大版本不猜渲染）；minor 偏高只警告不阻断；
 * - lintSchema：error 级问题禁止写入并在 Alert 列出（此前只 JSON.parse，
 *   粘一份 schemaVersion:2 或结构错乱的 schema 能直接落库，全靠底部小字兜底）。
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { CheckOutlined, CopyOutlined, DownloadOutlined, FormatPainterOutlined, UploadOutlined } from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import { checkSchemaVersion } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { enumFieldsOf, lintSchema, type LintIssue } from './lint';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onApply: (next: BoneUiSchema) => void;
  /** 外部 schema 变更时是否重置文本框（切 tab / 保存后） */
  resetKey: number;
  /** 实体字段表（apply 前跑 lintSchema 判悬空引用） */
  fields: MetaField[];
}

export default function JsonPanel({ schema, onApply, resetKey, fields }: Props): React.ReactElement {
  const [text, setText] = useState('');
  const [err, setErr] = useState<string | null>(null);
  /** apply 被门禁拦下的问题清单（版本/协议 error） */
  const [gateIssues, setGateIssues] = useState<LintIssue[]>([]);
  const [minorWarn, setMinorWarn] = useState(false);

  /** N3 文件导出/导入（配置即代码）：剪贴板复制之外，提供落盘备份 / 跨环境迁移 / 版本库归档。 */
  const fileRef = useRef<HTMLInputElement | null>(null);
  const exportFile = () => {
    const blob = new Blob([text], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${schema.entityCode ?? 'ui-schema'}.json`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };
  const importFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      setText(String(reader.result ?? ''));
      setErr(null);
      setGateIssues([]);
      setMinorWarn(false);
    };
    reader.readAsText(file);
    e.target.value = '';
  };

  useEffect(() => {
    setText(JSON.stringify(schema, null, 2));
    setErr(null);
    setGateIssues([]);
    setMinorWarn(false);
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

  /** 应用前门禁：版本协商 + 协议自检。返回是否放行 */
  const gate = (v: BoneUiSchema): boolean => {
    const versionCheck = checkSchemaVersion(v);
    if (versionCheck === 'error-bad-major') {
      setGateIssues([{ level: 'error', message: `schemaVersion=${v.schemaVersion} 不在已知大版本（${1}）内，渲染器不猜测未知大版本——请改回 v1 或用可视化面板编辑` }]);
      setMinorWarn(false);
      return false;
    }
    setMinorWarn(versionCheck === 'warn-unknown-minor');
    const knownFields = new Set(fields.map((f) => f.code));
    const errors = lintSchema(v, knownFields, enumFieldsOf(fields)).filter((i) => i.level === 'error');
    setGateIssues(errors);
    return errors.length === 0;
  };

  const tryApply = () => {
    const v = parse();
    if (v && gate(v)) onApply(v);
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
        <Button size="small" icon={<DownloadOutlined />} onClick={exportFile}>导出文件</Button>
        <Button size="small" icon={<UploadOutlined />} onClick={() => fileRef.current?.click()}>导入文件</Button>
        <input ref={fileRef} type="file" accept=".json,application/json" style={{ display: 'none' }} onChange={importFile} />
        <Button
          size="small" type="primary" icon={<CheckOutlined />}
          onClick={tryApply}
        >
          应用到配置
        </Button>
        {err
          ? <Tag color="red">JSON 非法：{err}</Tag>
          : gateIssues.length > 0
            ? <Tag color="red">协议错误 {gateIssues.length} 处，已阻止写入</Tag>
            : minorWarn
              ? <Tag color="gold">minor 高于已知版本，未知字段将被前向忽略</Tag>
              : <Tag color="green">语法正确</Tag>}
      </Space>

      {err && (
        <Alert type="error" showIcon style={{ marginBottom: 8 }} message="JSON 解析失败，未写入配置" description={err} />
      )}

      {gateIssues.length > 0 && (
        <Alert
          type="error" showIcon style={{ marginBottom: 8 }}
          message={`协议自检发现 ${gateIssues.length} 处错误，已阻止写入（与保存前自检同口径）`}
          description={
            <div>
              {gateIssues.slice(0, 5).map((i, idx) => <div key={idx}>· {i.message}</div>)}
              {gateIssues.length > 5 && <div>…等 {gateIssues.length} 处</div>}
              <div style={{ marginTop: 4, color: '#86909c' }}>修复后可再次点击「应用到配置」；协议根与对象均为 additionalProperties:false。</div>
            </div>
          }
        />
      )}

      {minorWarn && gateIssues.length === 0 && (
        <Alert type="warning" showIcon style={{ marginBottom: 8 }}
          message="minor 版本高于本设计器已知版本" description="未知字段会被前向忽略（不报错），但旧版本运行时可能看不到新能力。" />
      )}

      <textarea
        value={text}
        onChange={(e) => { setText(e.target.value); setErr(null); setGateIssues([]); setMinorWarn(false); }}
        spellCheck={false}
        style={{
          flex: 1, minHeight: 320, width: '100%', resize: 'none',
          fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Consolas, monospace',
          fontSize: 12, lineHeight: 1.6, padding: 12, borderRadius: 8,
          border: `1px solid ${err || gateIssues.length ? '#ff4d4f' : '#d9d9d9'}`, background: '#fafbfd', color: '#262626',
          outline: 'none', tabSize: 2,
        }}
      />

      <Text type="secondary" style={{ fontSize: 11, marginTop: 6 }}>
        修改后需点「应用到配置」才会生效。应用前会跑版本协商与协议自检（error 级问题禁止写入）。
      </Text>
    </div>
  );
}
