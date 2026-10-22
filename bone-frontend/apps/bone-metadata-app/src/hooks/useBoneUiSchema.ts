import { useEffect, useMemo, useState } from 'react';
import { metadataUiSchemaApi } from '../services/metadataApi';
import {
  checkSchemaVersion,
  resolveVariant,
  type BoneUiSchema,
  type SchemaVersionCheck,
  type UiContext,
} from '@bone/ui-schema';

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
  /**
   * 执行期 schema（published 快照优先，从未发布回落草稿）—— 与后端
   * {@code UiSchemaApplicationService.schemaForExecution} 同口径：
   * 线上跑的必须是「发布时冻结的那一份」，建模者改草稿不应在无发布的情况下
   * 让线上行为漂移（绕过发布闸门）。供运行时列表列投影等线上渲染消费。
   */
  executionSchema: BoneUiSchema | null;
  /** executionSchema 的来源（published=发布快照 / draft=未发布回落草稿 / null=无 schema） */
  executionSource: 'published' | 'draft' | null;
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
  const [publishedRaw, setPublishedRaw] = useState<BoneUiSchema | null>(null);
  const [status, setStatus] = useState<UiSchemaStatus>('loading');
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!entityId) {
      setStatus('absent');
      setRaw(null);
      setPublishedRaw(null);
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
          setPublishedRaw(null);
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
        // 发布快照独立解析：坏快照不连坐草稿管线（回落 draft 由下方 useMemo 处理）
        try {
          setPublishedRaw(
            res.data.publishedSchemaJson
              ? (JSON.parse(res.data.publishedSchemaJson) as BoneUiSchema)
              : null,
          );
        } catch {
          setPublishedRaw(null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRaw(null);
          setPublishedRaw(null);
          setStatus('error');
        }
      });
    return () => {
      cancelled = true;
    };
  }, [entityId, tick]);

  const schema = useMemo(() => (raw ? resolveVariant(raw, ctx) : null), [raw, ctx]);
  const versionCheck = useMemo(() => (raw ? checkSchemaVersion(raw) : null), [raw]);
  const executionSchema = useMemo(
    () => {
      const src = publishedRaw ?? raw;
      return src ? resolveVariant(src, ctx) : null;
    },
    [publishedRaw, raw, ctx],
  );
  const executionSource = publishedRaw ? 'published' : raw ? 'draft' : null;

  return { status, schema, executionSchema, executionSource, versionCheck, reload: () => setTick((t) => t + 1) };
}

export default useBoneUiSchema;
