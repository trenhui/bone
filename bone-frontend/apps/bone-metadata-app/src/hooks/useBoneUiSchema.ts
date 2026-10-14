import { useEffect, useMemo, useState } from 'react';
import { metadataUiSchemaApi } from '../services/metadataApi';
import {
  checkSchemaVersion,
  resolveVariant,
  type BoneUiSchema,
  type SchemaVersionCheck,
  type UiContext,
} from '../utils/uiSchemaEngine';

export type UiSchemaStatus =
  | 'loading'
  | 'absent' // 无 schema → 回落 overlay/默认（非错误）
  | 'ok' // 绿：正常渲染
  | 'warn-unknown-minor' // 黄：未知 minor，告警条 + 未知字段忽略 + 继续渲染
  | 'error'; // 红：取数失败/结构坏 → 回落默认表单 + 上报

export interface UseBoneUiSchema {
  status: UiSchemaStatus;
  /** 变体裁决后的 schema（null=回落渲染） */
  schema: BoneUiSchema | null;
  versionCheck: SchemaVersionCheck | null;
  reload: () => void;
}

/**
 * 运行时 schema 管线（技术方案 §6.7/§6.8）：
 * GET ui-schema → JSON 解析 → checkSchemaVersion（三色旗）→ resolveVariant(ctx) → 渲染。
 * - 绿/黄都出 schema（黄带告警）；红/缺席返回 null，调用方回落 overlay/默认表单。
 * - 缓存/上报由调用方负责（published 快照 localStorage、render_fail 上报见 §6.8/§2.6）。
 */
export function useBoneUiSchema(entityId: string | null, ctx: UiContext = {}): UseBoneUiSchema {
  const [raw, setRaw] = useState<BoneUiSchema | null>(null);
  const [status, setStatus] = useState<UiSchemaStatus>('loading');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!entityId) {
      setStatus('absent');
      setRaw(null);
      return;
    }
    let cancelled = false;
    setStatus('loading');
    metadataUiSchemaApi
      .get(entityId)
      .then((res) => {
        if (cancelled) return;
        if (res.code !== 200 || !res.data) {
          setStatus('absent');
          setRaw(null);
          return;
        }
        try {
          const parsed = JSON.parse(res.data.json) as BoneUiSchema;
          const vc = checkSchemaVersion(parsed);
          setRaw(parsed);
          setStatus(vc === 'ok' ? 'ok' : vc === 'warn-unknown-minor' ? 'warn-unknown-minor' : 'error');
        } catch {
          setRaw(null);
          setStatus('error');
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRaw(null);
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [entityId, tick]);

  const schema = useMemo(() => (raw ? resolveVariant(raw, ctx) : null), [raw, ctx]);
  const versionCheck = useMemo(() => (raw ? checkSchemaVersion(raw) : null), [raw]);

  return { status, schema, versionCheck, reload: () => setTick((t) => t + 1) };
}

export default useBoneUiSchema;
