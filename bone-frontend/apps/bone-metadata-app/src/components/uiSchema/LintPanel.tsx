/**
 * 自检面板：前端协议一致性 + 后端校验结果。
 *
 * 两类问题分开呈现，因为它们的**处置方式不同**：
 * - 前端本地问题（引用悬空、配置缺失）→ 改配置即可，点击直接跳到对应面板位置；
 * - 后端校验问题（UI-REF-xx）→ 涉及跨真源一致性（字段是否真存在、动作码是否注册），
 *   是发布闸门的判据，必须显式展示，不能与本地问题混在一起糊过去。
 */
import { Alert, Badge, Button, Space, Tag, Typography } from 'antd';
import {
  CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined, InfoCircleOutlined, ReloadOutlined,
} from '@ant-design/icons';
import type { UiSchemaIssue } from '../../services/metadataApi';
import type { LintIssue, LintLevel } from './lint';

const { Text } = Typography;

const LEVEL_META: Record<LintLevel, { color: string; label: string; icon: React.ReactNode }> = {
  error: { color: 'red', label: '错误', icon: <CloseCircleOutlined /> },
  warning: { color: 'orange', label: '警告', icon: <ExclamationCircleOutlined /> },
  info: { color: 'blue', label: '提示', icon: <InfoCircleOutlined /> },
};

interface Props {
  issues: LintIssue[];
  /** 后端校验结果（null=未校验） */
  serverIssues: UiSchemaIssue[] | null;
  onLocate: (l: LintIssue['locate']) => void;
  onRevalidate: () => void;
  validating: boolean;
}

export default function LintPanel({ issues, serverIssues, onLocate, onRevalidate, validating }: Props): React.ReactElement {
  const counts = {
    error: issues.filter((i) => i.level === 'error').length,
    warning: issues.filter((i) => i.level === 'warning').length,
    info: issues.filter((i) => i.level === 'info').length,
  };

  return (
    <div>
      <Space style={{ marginBottom: 10 }} wrap>
        <Badge count={counts.error} color="#ff4d4f" text="错误" />
        <Badge count={counts.warning} color="#faad14" text="警告" />
        <Badge count={counts.info} color="#1677ff" text="提示" />
        <Button size="small" icon={<ReloadOutlined />} loading={validating} onClick={onRevalidate}>
          后端校验
        </Button>
      </Space>

      {issues.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '20px 0', color: '#52c41a' }}>
          <CheckCircleOutlined style={{ fontSize: 22 }} />
          <div style={{ marginTop: 6, fontSize: 13 }}>本地自检未发现问题</div>
        </div>
      ) : (
        <div>
          {['error', 'warning', 'info'].map((lv) => {
            const list = issues.filter((i) => i.level === lv);
            if (!list.length) return null;
            const meta = LEVEL_META[lv as LintLevel];
            return (
              <div key={lv} style={{ marginBottom: 10 }}>
                <Text strong style={{ fontSize: 12, color: meta.color }}>
                  {meta.icon} {meta.label}（{list.length}）
                </Text>
                {list.map((it, i) => (
                  <div
                    key={i}
                    onClick={() => it.locate && onLocate(it.locate)}
                    style={{
                      display: 'flex', gap: 6, alignItems: 'flex-start', padding: '6px 8px',
                      marginTop: 4, borderRadius: 6, background: '#fafbfd',
                      border: '1px solid #f0f0f0', fontSize: 12, lineHeight: 1.6,
                      cursor: it.locate ? 'pointer' : 'default',
                      transition: 'background .15s',
                    }}
                    onMouseEnter={(e) => { if (it.locate) e.currentTarget.style.background = '#f0f7ff'; }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = '#fafbfd'; }}
                  >
                    <Tag color={meta.color} style={{ fontSize: 10, margin: 0, flex: '0 0 auto' }}>{meta.label}</Tag>
                    <span style={{ flex: 1 }}>{it.message}</span>
                    {it.locate && <Text type="secondary" style={{ fontSize: 11, flex: '0 0 auto' }}>定位 →</Text>}
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}

      <div style={{ marginTop: 14 }}>
        <Text strong style={{ fontSize: 12 }}>后端校验（发布闸门）</Text>
        {serverIssues === null ? (
          <div style={{ marginTop: 6 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              未校验。后端校验会核对字段/动作码是否真实存在，是发布前的最后一道闸。
            </Text>
          </div>
        ) : serverIssues.length === 0 ? (
          <Alert type="success" showIcon style={{ marginTop: 6 }} message="后端校验通过，可以发布" />
        ) : (
          <div style={{ marginTop: 6 }}>
            {serverIssues.map((it, i) => (
              <div key={i} style={{
                display: 'flex', gap: 6, alignItems: 'flex-start', padding: '6px 8px',
                marginBottom: 4, borderRadius: 6, background: '#fff7e6',
                border: '1px solid #ffe7ba', fontSize: 12, lineHeight: 1.6,
              }}>
                <Tag color="orange" style={{ fontSize: 10, margin: 0 }}>{it.code}</Tag>
                <span style={{ flex: 1 }}>{it.message}</span>
                {it.fieldCode && <Text code style={{ fontSize: 11 }}>{it.fieldCode}</Text>}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
