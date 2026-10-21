/**
 * 字典覆写面板：只允许改显示名 / 隐藏选项，禁止新造选项。
 *
 * 协议 §3.1 明文：枚举/码表的真源在元数据域（meta_field.enumValues），UI Schema 只"引用"。
 * 因此这里不提供"新建选项""删除选项"——那等于在 UI 层造出元数据域不认识的 value，
 * 运行时存进去的数据会破坏参照完整性。
 *
 * 覆写结果写入 schema.dictionaries，随 JSON 导出、可撤销、可发布。
 *
 * 选项源三态（doc/design/modules/11 §6.1）：卡片表头展示来源 Tag（本地枚举/系统字典）+ 真源定位
 * （typeCode）+ 物化元数据（项数/解析时间/触发方）+ 状态徽标（正常/已漂移/解析失败）+ 操作
 * （刷新快照）。覆写（labelOverride/hidden）作用于解析后的选项集合，与来源类型正交（§4.5）。
 *
 * 灰度与迁移（v1.1 §6.5 / §5.3）：挂载即查本租户的 dictBinding 开关，关闭时绑定/迁移入口禁用并说明原因
 * （只在后端拒绝会让用户填完表单才吃 400）；本地枚举卡片提供「升级为平台字典」向导——
 * 先预览 match/merge 计划（哪些一致、哪些真源多出、哪些本地多出、哪些同码不同义），再确认执行。
 */
import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Empty, Input, Modal, Radio, Space, Switch, Tag, Tooltip, Typography, message } from 'antd';
import { ArrowUpOutlined, RollbackOutlined, SyncOutlined } from '@ant-design/icons';
import { parseEnumValues, parseEnumSource } from '@bone/shared-types';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField, EnumSourceDiff, DictMigrationPlan } from '../../types';
import { dictBindingToggleApi, metadataFieldOptionsApi } from '../../services/metadataApi';
import { clearDict, dictOf, setDictHidden, setDictLabel } from './schemaOps';
import { useScrollToFocus } from './useScrollToFocus';

const { Text } = Typography;

interface Props {
  schema: BoneUiSchema;
  onChange: (next: BoneUiSchema) => void;
  fields: MetaField[];
  focus?: { key?: string };
  /**
   * 快照刷新成功后回调（v1.1 联调实证修复）：选项列表与物化时间取自 fields prop，
   * 不通知宿主重取则面板停留在旧快照——漂移徽标消失了，弃用项却仍显示为生效态，
   * 「刷新」只兑现了一半语义。宿主（EntityDetail）注入 loadFields。
   */
  onFieldsRefetch?: () => void;
}

export default function DictionaryPanel({ schema, onChange, fields, focus, onFieldsRefetch }: Props): React.ReactElement {
  // 定位滚动：focus.key 命中哪个字段的字典，滚到那张卡（钩子须无条件调用，置于早返回之前）
  useScrollToFocus(focus?.key != null ? `d${focus.key}` : undefined);
  const enumFields = fields.filter((f) => parseEnumValues(f.enumValues).length > 0);
  /** fieldCode → 快照 vs 真源差异（仅 dict 绑定字段按需拉取；null=未加载/加载中） */
  const [diffs, setDiffs] = useState<Record<string, EnumSourceDiff | null>>({});
  const [refreshing, setRefreshing] = useState<string | null>(null);

  /** 本租户字典绑定灰度（v1.1 §6.5）；null = 未知（查询失败不阻断面板，后端仍会兜底拒绝） */
  const [bindingToggle, setBindingToggle] = useState<{ enabled: boolean; source: string } | null>(null);
  /** 迁移向导（v1.1 §5.3）：migrateField 非空即弹窗打开 */
  const [migrateField, setMigrateField] = useState<MetaField | null>(null);
  const [migrateType, setMigrateType] = useState('');
  const [migratePlan, setMigratePlan] = useState<DictMigrationPlan | null>(null);
  const [planLoading, setPlanLoading] = useState(false);
  const [strategy, setStrategy] = useState<'KEEP_LOCAL_OVERRIDE' | 'MERGE_INTO_DICT'>('KEEP_LOCAL_OVERRIDE');
  const [conflictConfirmed, setConflictConfirmed] = useState(false);
  const [migrating, setMigrating] = useState(false);

  // dict 绑定字段挂载即查一次漂移（doc 11 §6.1 徽标）；端点带 TTL 缓存，重复进入不放大压力
  useEffect(() => {
    const bound = enumFields.filter((f) => parseEnumSource(f.enumSource)?.type === 'dict');
    if (!bound.length) return;
    let cancelled = false;
    (async () => {
      for (const f of bound) {
        try {
          const res = await metadataFieldOptionsApi.diff(f.id);
          if (!cancelled && res.code === 200) {
            setDiffs((prev) => ({ ...prev, [f.code]: res.data }));
          }
        } catch {
          // 读路径 fail-soft：拉不到就不显示徽标，不阻断面板使用（发布预览的 ERROR 项才是硬判据）
        }
      }
    })();
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅挂载时按字段集合查一次
  }, [fields.map((f) => f.id + ':' + (f.enumSource ?? '')).join(',')]);

  // v1.1 §4.3 版本协商：定时比对「真源版本 vs 快照版本」，不一致则自动刷新——
  // 字典变更分钟级自动到达绑定字段，无需人工点「刷新快照」（手动按钮保留）。挂载即查一次，之后每 30s 轮询。
  useEffect(() => {
    const bound = enumFields.filter((f) => parseEnumSource(f.enumSource)?.type === 'dict');
    if (!bound.length) return;
    let cancelled = false;
    const check = async () => {
      for (const f of bound) {
        try {
          const res = await metadataFieldOptionsApi.version(f.id);
          if (cancelled || res.code !== 200) continue;
          const { sourceVersion, snapshotVersion } = res.data;
          if (sourceVersion != null && snapshotVersion != null && sourceVersion !== snapshotVersion) {
            await refreshSnapshot(f);
          }
        } catch {
          // 读路径 fail-soft：版本比对失败不阻断面板（手动刷新仍是兜底）
        }
      }
    };
    const timer = setInterval(check, 30000);
    check();
    return () => {
      cancelled = true;
      clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 绑定集合变化才重启轮询；refreshSnapshot 经闭包捕获最新值
  }, [fields.map((f) => f.id + ':' + (f.enumSource ?? '')).join(',')]);

  // 灰度开关挂载即查：关闭时禁用绑定/迁移入口并说明原因（UI 必须真消费后端约束，不能等提交才 400）
  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const res = await dictBindingToggleApi.state();
        if (!cancelled && res.code === 200) {
          setBindingToggle({ enabled: res.data.enabled, source: res.data.source });
        }
      } catch {
        // 查询失败按「未知」处理：不禁用入口（后端仍会拒绝），避免因监控端点抖动误锁建模
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const openMigration = (f: MetaField) => {
    setMigrateField(f);
    setMigrateType('');
    setMigratePlan(null);
    setStrategy('KEEP_LOCAL_OVERRIDE');
    setConflictConfirmed(false);
  };

  /** 比对：拿 match/merge 计划（不产生变更）。计划由后端给出，前端不自行推算分类。 */
  const loadMigrationPlan = async () => {
    if (!migrateField) return;
    const typeCode = migrateType.trim();
    if (!typeCode) {
      message.warning('请先填写目标值域编码');
      return;
    }
    setPlanLoading(true);
    try {
      const res = await metadataFieldOptionsApi.migrationPreview(migrateField.id, typeCode);
      if (res.code === 200) {
        setMigratePlan(res.data);
        setConflictConfirmed(false);
      } else {
        setMigratePlan(null);
        message.error(`比对失败：${errorText(res)}`);
      }
    } catch {
      setMigratePlan(null);
      message.error('比对失败（字典服务不可达）');
    } finally {
      setPlanLoading(false);
    }
  };

  const submitMigration = async () => {
    if (!migrateField || !migratePlan) return;
    setMigrating(true);
    try {
      const res = await metadataFieldOptionsApi.migrateToDict(migrateField.id, {
        typeCode: migratePlan.typeCode,
        localOnlyStrategy: strategy,
        confirmConflicts: conflictConfirmed,
      });
      if (res.code === 200) {
        const parts = [`快照 ${res.data.snapshotCount} 项`];
        if (res.data.mergedIntoDict > 0) parts.push(`并入字典 ${res.data.mergedIntoDict} 项`);
        if (res.data.keptLocalOverrides > 0) parts.push(`保留本地 ${res.data.keptLocalOverrides} 项`);
        message.success(`已升级为平台字典：${parts.join('，')}`);
        setMigrateField(null);
        setMigratePlan(null);
        onFieldsRefetch?.();
      } else {
        message.error(`升级失败：${errorText(res)}`);
      }
    } catch {
      message.error('升级失败（网络或服务不可达）');
    } finally {
      setMigrating(false);
    }
  };

  const refreshSnapshot = async (f: MetaField) => {
    setRefreshing(f.code);
    try {
      const res = await metadataFieldOptionsApi.refresh(f.id);
      if (res.code === 200) {
        message.success(`已刷新快照：${res.data.beforeCount} → ${res.data.afterCount} 项`);
        const diffRes = await metadataFieldOptionsApi.diff(f.id);
        if (diffRes.code === 200) setDiffs((prev) => ({ ...prev, [f.code]: diffRes.data }));
        // 选项与物化时间来自宿主 fields，必须重取才是「刷新」的完整语义——
        // 仅更新 diffs 会让徽标说「已最新」而列表仍展示旧快照（联调实证）。
        onFieldsRefetch?.();
      } else {
        message.error(`刷新失败（已保留旧快照）：${errorText(res)}`);
      }
    } catch {
      message.error('刷新失败（字典服务不可达，已保留旧快照）');
    } finally {
      setRefreshing(null);
    }
  };

  if (!enumFields.length) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description="该实体没有带枚举值的字段，无需字典覆写"
        style={{ padding: '24px 0' }}
      />
    );
  }

  return (
    <div>
      <Alert
        type="info" showIcon style={{ marginBottom: 12 }}
        message="码表真源在元数据域"
        description="这里只能做协议允许的覆写：改显示名、隐藏选项。本地枚举的新增/删除请到字段的 enumValues 修改；绑定系统字典的字段请到「系统管理 → 字典配置」维护（真源变更会在此处显示漂移）。需要按角色差异化请用「变体」而非在一份 schema 内做覆盖。"
      />

      {bindingToggle && !bindingToggle.enabled && (
        <Alert
          type="warning" showIcon style={{ marginBottom: 12 }}
          message="当前租户未开放「平台字典绑定」（灰度关闭）"
          description="绑定与升级入口已禁用：字段仍按本地枚举渲染与校验，行为与开放前完全一致。需要开放请联系管理员按租户灰度开通。"
        />
      )}

      {enumFields.map((f) => {
        const opts = parseEnumValues(f.enumValues);
        const ov = dictOf(schema, f.code);
        const ovCount = (ov?.hidden?.length ?? 0) + Object.keys(ov?.labelOverride ?? {}).length;
        const hiddenCount = ov?.hidden?.length ?? 0;
        const decl = parseEnumSource(f.enumSource);
        const diff = diffs[f.code];

        return (
          <div
            key={f.code}
            data-locate={focus?.key === f.code ? `d${f.code}` : undefined}
            style={{
              border: `1px solid ${focus?.key === f.code ? '#ffa940' : '#f0f0f0'}`,
              borderRadius: 10, marginBottom: 10, overflow: 'hidden', background: '#fff',
            }}
          >
            <div style={{
              display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
              borderBottom: '1px solid #f0f0f0', background: '#fafbfd', flexWrap: 'wrap',
            }}>
              <Text strong style={{ fontSize: 13 }}>{f.displayName}</Text>
              <Text code style={{ fontSize: 11 }}>{f.code}</Text>
              {/* 来源 Tag（doc 11 §6.1）：inline=本地枚举；dict=系统字典绑定 */}
              {decl?.type === 'dict' ? (
                <Tooltip title={`绑定系统字典值域 ${String(decl.ref?.typeCode ?? '')}（快照模式，运营改字典后重新发布本实体生效）`}>
                  <Tag color="blue" style={{ fontSize: 11 }}>系统字典 · {String(decl.ref?.typeCode ?? '')}</Tag>
                </Tooltip>
              ) : (
                <Tag style={{ fontSize: 11 }}>本地枚举</Tag>
              )}
              {ovCount > 0 && <Tag color="orange" style={{ fontSize: 11 }}>已覆写 {ovCount} 项</Tag>}
              {hiddenCount >= opts.length && <Tag color="red" style={{ fontSize: 11 }}>全部隐藏</Tag>}
              {/* 状态徽标：已漂移（WARNING）/ 真源不可达（ERROR） */}
              {decl?.type === 'dict' && diff && !diff.resolved && (
                <Tooltip title="字典服务不可达：展示的是上次发布快照，请尽快恢复服务后刷新">
                  <Tag color="red" style={{ fontSize: 11 }}>解析失败</Tag>
                </Tooltip>
              )}
              {decl?.type === 'dict' && diff?.resolved && (diff.added.length || diff.removed.length || diff.labelChanged.length || (diff.stateChanged?.length ?? 0)) > 0 && (
                <Tooltip title={`真源已变更：新增 ${diff.added.length} / 移除 ${diff.removed.length} / 改标签 ${diff.labelChanged.length} / 状态变迁 ${diff.stateChanged?.length ?? 0}；removed 的历史数据旧值不受影响，重新发布后生效`}>
                  <Tag color="gold" style={{ fontSize: 11 }}>已漂移</Tag>
                </Tooltip>
              )}
              {decl?.type !== 'dict' && (
                <Tooltip
                  title={
                    bindingToggle?.enabled === false
                      ? '当前租户未开放平台字典绑定（灰度关闭）'
                      : '把本地枚举升级为平台字典：先比对本地与真源的差异，再确认绑定'
                  }
                >
                  <Button
                    size="small" type="text" icon={<ArrowUpOutlined />}
                    disabled={bindingToggle?.enabled === false}
                    aria-label={`升级 ${f.code} 为平台字典`}
                    onClick={() => openMigration(f)}
                  >
                    升级为平台字典
                  </Button>
                </Tooltip>
              )}
              <span style={{ flex: 1 }} />
              <Text type="secondary" style={{ fontSize: 11 }}>
                共 {opts.length} 项
                {decl?.type === 'dict'
                  ? ` · 物化于 ${decl.resolvedAt ? decl.resolvedAt.replace('T', ' ').replace(/\.\d+Z?$/, '') : '—'}（${decl.resolvedBy ?? '—'}）`
                  : ' · 本地枚举（只读）'}
              </Text>
              {decl?.type === 'dict' && (
                <Tooltip title="按字典真源重新物化快照（失败时保留旧快照）">
                  <Button
                    size="small" type="text" icon={<SyncOutlined spin={refreshing === f.code} />}
                    loading={refreshing === f.code}
                    aria-label={`刷新 ${f.code} 的字典快照`}
                    onClick={() => refreshSnapshot(f)}
                  >
                    刷新
                  </Button>
                </Tooltip>
              )}
              {ovCount > 0 && (
                <Tooltip title={`清除「${f.displayName}」的全部覆写`}>
                  <Button size="small" type="text" icon={<RollbackOutlined />}
                    aria-label={`还原 ${f.code} 的字典覆写`}
                    onClick={() => onChange(clearDict(schema, f.code))}>
                    还原
                  </Button>
                </Tooltip>
              )}
            </div>

            <div style={{ padding: '8px 10px' }}>
              {opts.map((o) => {
                const hidden = (ov?.hidden ?? []).includes(o.code);
                const deprecated = o.state === 'DEPRECATED';
                return (
                  <div
                    key={o.code}
                    style={{
                      display: 'flex', alignItems: 'center', gap: 8,
                      padding: '5px 6px', borderRadius: 6, marginBottom: 3,
                      background: hidden ? '#fafafa' : undefined,
                      opacity: hidden ? 0.6 : 1,
                    }}
                  >
                    <Text code style={{ fontSize: 12, width: 120, flex: '0 0 auto' }}>{o.code}</Text>
                    <Text type="secondary" style={{ fontSize: 12, width: 130, flex: '0 0 auto' }}
                      delete={hidden}>
                      {o.label}
                    </Text>
                    {/* 三态生命周期（v1.1 §4.5）：DEPRECATED 置灰 + 「废弃中」标签——
                        让设计者提前感知「这个选项正在下线」，而不是某天突然消失 */}
                    {deprecated && <Tag color="volcano" style={{ fontSize: 11 }}>废弃中</Tag>}
                    <Input
                      size="small" style={{ flex: 1, minWidth: 120 }}
                      placeholder="覆盖显示名（留空沿用原名）"
                      value={ov?.labelOverride?.[o.code] ?? ''}
                      onChange={(e) => onChange(setDictLabel(schema, f.code, o.code, e.target.value))}
                    />
                    <Tooltip title="隐藏后该选项不在下拉中出现；历史数据中的旧值不受影响">
                      <Switch
                        size="small" aria-label={`隐藏选项 ${f.code}.${o.code}`} checked={hidden}
                        onChange={(v) => onChange(setDictHidden(schema, f.code, o.code, v))}
                      />
                    </Tooltip>
                    <Text type="secondary" style={{ fontSize: 11, width: 32 }}>隐藏</Text>
                  </div>
                );
              })}
            </div>
          </div>
        );
      })}

      <Modal
        open={migrateField != null}
        title={`升级为平台字典 · ${migrateField?.displayName ?? ''}`}
        onCancel={() => { setMigrateField(null); setMigratePlan(null); }}
        onOk={submitMigration}
        okText="确认迁移"
        cancelText="取消"
        confirmLoading={migrating}
        okButtonProps={{
          disabled: migratePlan == null || (migratePlan.requiresConfirmation && !conflictConfirmed),
        }}
        destroyOnHidden
        width={640}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <div>
            <Text type="secondary" style={{ fontSize: 12 }}>
              目标值域编码（平台字典）。先「比对」查看计划，确认后才会真正绑定并物化快照。
            </Text>
            <Space.Compact style={{ width: '100%', marginTop: 6 }}>
              <Input
                placeholder="如 SYS_STATUS"
                value={migrateType}
                aria-label="迁移目标值域编码"
                onChange={(e) => { setMigrateType(e.target.value); setMigratePlan(null); }}
                onPressEnter={loadMigrationPlan}
              />
              <Button onClick={loadMigrationPlan} loading={planLoading}>比对</Button>
            </Space.Compact>
          </div>

          {migratePlan && (
            <>
              <div data-testid="migration-plan">
                <Tag color="green">完全一致 {migratePlan.matched.length}</Tag>
                <Tag color="blue">真源多出 {migratePlan.dictOnly.length}</Tag>
                <Tag color="orange">本地多出 {migratePlan.localOnly.length}</Tag>
                <Tag color={migratePlan.labelConflicts.length ? 'gold' : undefined}>
                  同码不同义 {migratePlan.labelConflicts.length}
                </Tag>
                <Tag color={migratePlan.codeConflicts.length ? 'gold' : undefined}>
                  同义不同码 {migratePlan.codeConflicts.length}
                </Tag>
              </div>

              {migratePlan.localOnly.length > 0 && (
                <Text type="secondary" style={{ fontSize: 12 }}>
                  本地多出：{migratePlan.localOnly.join('、')}
                </Text>
              )}

              {migratePlan.labelConflicts.length > 0 && (
                <div>
                  <Text strong style={{ fontSize: 12 }}>同码不同义（告警不阻断，需确认）</Text>
                  {migratePlan.labelConflicts.map((c) => (
                    <div key={c.code} style={{ fontSize: 12 }}>
                      <Text code>{c.code}</Text>：本地「{c.localLabel}」→ 真源「{c.dictLabel}」
                    </div>
                  ))}
                </div>
              )}

              {migratePlan.codeConflicts.length > 0 && (
                <div>
                  <Text strong style={{ fontSize: 12 }}>同义不同码（告警不阻断，需确认编码口径）</Text>
                  {migratePlan.codeConflicts.map((c) => (
                    <div key={c.localCode} style={{ fontSize: 12 }}>
                      「{c.label}」：本地 <Text code>{c.localCode}</Text> ↔ 真源 <Text code>{c.dictCode}</Text>
                    </div>
                  ))}
                </div>
              )}

              {migratePlan.localOnly.length > 0 && (
                <div>
                  <Text strong style={{ fontSize: 12 }}>本地多出项怎么处理</Text>
                  <Radio.Group
                    value={strategy}
                    onChange={(e) => setStrategy(e.target.value)}
                    style={{ display: 'block' }}
                  >
                    <Radio value="KEEP_LOCAL_OVERRIDE">
                      保留为本地追加项（不写共享值域；会持续显示为与真源的差异）
                    </Radio>
                    <Radio value="MERGE_INTO_DICT">
                      并入字典（向真源新增这些项，需要字典写权限）
                    </Radio>
                  </Radio.Group>
                </div>
              )}

              {migratePlan.requiresConfirmation && (
                <Checkbox
                  checked={conflictConfirmed}
                  onChange={(e) => setConflictConfirmed(e.target.checked)}
                >
                  我已确认上述冲突的语义（存量数据按这些 code 存储，改绑后渲染口径会变化）
                </Checkbox>
              )}
            </>
          )}
        </Space>
      </Modal>
    </div>
  );
}

/** 错误响应 → 可读文案（与 api 层 errorMessage 同口径，取码+message） */
function errorText(res: { code?: number; message?: string; data?: unknown }): string {
  const code = (res.data as { errorCode?: string } | null | undefined)?.errorCode;
  return code ? `[${code}] ${res.message ?? ''}` : (res.message ?? '未知错误');
}
