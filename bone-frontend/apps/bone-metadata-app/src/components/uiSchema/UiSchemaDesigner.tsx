/**
 * UI Schema 配置器（Designer）主容器：左侧配置面板 + 右侧实时预览。
 *
 * 为什么是两栏而不是"配完再预览"：
 * 界面配置的正确性无法靠读 JSON 判断。左改右见的反馈回路能把"配错了"的发现成本
 * 从「保存 → 发布 → 打开运行时页面 → 发现不对 → 回来改」压缩到一次点击之内。
 * 这是 Retool / Appsmith / Salesforce Lightning App Builder 的共同范式。
 *
 * 状态纪律：
 * - schema 单一真源 + 不可变更新，编辑历史天然可撤销；
 * - dirty 标记只由「与已保存快照的差异」决定，不做猜测；
 * - 乐观锁（If-Match）冲突不去猜，直接提示用户重新加载。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Dropdown, Empty, message, Popconfirm, Space, Spin, Tabs, Tag, Tooltip, Typography,
} from 'antd';
import {
  CheckCircleOutlined, CloudUploadOutlined, FileTextOutlined, RedoOutlined,
  ReloadOutlined, SaveOutlined, UndoOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { metadataActionDefApi, metadataUiSchemaApi, type ActionDefSpec, type UiSchemaIssue } from '../../services/metadataApi';
import { buildDefaultSchema, type ViewKey } from './schemaOps';
import { enumFieldsOf, lintSchema, type LintIssue, type LintLocate } from './lint';
import { UI_SCHEMA_SAMPLES } from './samples';
import StructurePanel from './StructurePanel';
import RulesPanel from './RulesPanel';
import ActionBarPanel from './ActionBarPanel';
import ListPanel from './ListPanel';
import DictionaryPanel from './DictionaryPanel';
import LintPanel from './LintPanel';
import JsonPanel from './JsonPanel';
import SchemaPreview from './SchemaPreview';

const { Text } = Typography;

interface Props {
  entityId: string;
  entityCode: string;
  fields: MetaField[];
}

type TabKey = 'structure' | 'rules' | 'actions' | 'list' | 'dict' | 'lint' | 'json';

const TAB_LABEL: Record<TabKey, string> = {
  structure: '结构',
  rules: '规则',
  actions: '动作条',
  list: '列表',
  dict: '字典',
  lint: '自检',
  json: 'JSON',
};

export default function UiSchemaDesigner({ entityId, entityCode, fields }: Props): React.ReactElement {
  const [loading, setLoading] = useState(true);
  const [schema, setSchema] = useState<BoneUiSchema | null>(null);
  const [baseline, setBaseline] = useState<string>(''); // 已保存快照，用于判脏
  const [lock, setLock] = useState(0);
  const [saving, setSaving] = useState(false);
  const [validating, setValidating] = useState(false);
  const [serverIssues, setServerIssues] = useState<UiSchemaIssue[] | null>(null);
  const [actionDefs, setActionDefs] = useState<ActionDefSpec[] | null>(null);

  const [tab, setTab] = useState<TabKey>('structure');
  const [view, setView] = useState<ViewKey>('form');
  const [focus, setFocus] = useState<LintLocate | undefined>(undefined);
  const [jsonResetKey, setJsonResetKey] = useState(0);
  const [width, setWidth] = useState(560);
  const dragging = useRef(false);

  // 撤销/重做
  const [past, setPast] = useState<BoneUiSchema[]>([]);
  const [future, setFuture] = useState<BoneUiSchema[]>([]);

  /* ---------- 加载 ---------- */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await metadataUiSchemaApi.get(entityId);
      let next: BoneUiSchema;
      if (res.code === 200 && res.data?.json) {
        try {
          next = JSON.parse(res.data.json) as BoneUiSchema;
        } catch {
          message.error('已保存的 schema 解析失败，已回落为默认结构');
          next = buildDefaultSchema(entityCode, fields);
        }
        setLock(Number(res.data.lockVersion ?? 0));
      } else {
        next = buildDefaultSchema(entityCode, fields);
        setLock(0);
      }
      setSchema(next);
      setBaseline(JSON.stringify(next));
      setPast([]);
      setFuture([]);
      setServerIssues(null);
    } catch {
      message.error('界面 schema 加载失败');
    } finally {
      setLoading(false);
    }
  }, [entityId, entityCode, fields]);

  useEffect(() => { void load(); }, [load]);

  // 动作定义（建模期真源），供动作条下拉
  useEffect(() => {
    metadataActionDefApi.list(entityId)
      .then((r) => setActionDefs(r.code === 200 ? (r.data ?? []) : []))
      .catch(() => setActionDefs([]));
  }, [entityId]);

  /* ---------- 编辑 ---------- */
  const commit = useCallback((next: BoneUiSchema) => {
    setSchema((cur) => {
      if (cur) setPast((p) => [...p.slice(-49), cur]);
      return next;
    });
    setFuture([]);
  }, []);

  const undo = () => {
    if (!past.length) return;
    const prev = past[past.length - 1];
    setPast((p) => p.slice(0, -1));
    setSchema((cur) => { if (cur) setFuture((f) => [cur, ...f].slice(0, 50)); return prev; });
  };
  const redo = () => {
    if (!future.length) return;
    const nxt = future[0];
    setFuture((f) => f.slice(1));
    setSchema((cur) => { if (cur) setPast((p) => [...p, cur]); return nxt; });
  };

  const dirty = useMemo(
    () => !!schema && JSON.stringify(schema) !== baseline,
    [schema, baseline],
  );

  /* ---------- 自检 ---------- */
  const knownFields = useMemo(() => new Set(fields.map((f) => f.code)), [fields]);
  const enumFields = useMemo(() => enumFieldsOf(fields), [fields]);
  const issues = useMemo<LintIssue[]>(
    () => (schema ? lintSchema(schema, knownFields, enumFields) : []),
    [schema, knownFields, enumFields],
  );
  const errCount = issues.filter((i) => i.level === 'error').length;
  const warnCount = issues.filter((i) => i.level === 'warning').length;

  const onLocate = (l: LintLocate | undefined) => {
    if (!l) return;
    setTab(l.tab as TabKey);
    setFocus(l);
  };

  /* ---------- 保存 / 校验 ---------- */
  const save = async () => {
    if (!schema) return;
    setSaving(true);
    try {
      const res = await metadataUiSchemaApi.save(entityId, JSON.stringify(schema), lock);
      if (res.code === 200) {
        message.success('界面配置已保存');
        setBaseline(JSON.stringify(schema));
        setLock(Number(res.data?.lockVersion ?? lock + 1));
        setServerIssues(null);
      } else if (res.code === 409 || res.code === 412) {
        message.warning('配置已被其他人修改（乐观锁冲突），已重新加载最新内容');
        void load();
      } else {
        message.error(res.message ?? '保存失败');
      }
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const revalidate = async () => {
    setValidating(true);
    try {
      const res = await metadataUiSchemaApi.validate(entityId);
      if (res.code === 200) {
        setServerIssues(res.data ?? []);
        message[(res.data ?? []).length ? 'warning' : 'success'](
          (res.data ?? []).length ? `后端校验发现 ${res.data?.length} 个问题` : '后端校验通过',
        );
      } else {
        message.error(res.message ?? '校验失败');
      }
    } catch {
      message.error('校验失败');
    } finally {
      setValidating(false);
    }
  };

  /* ---------- 左栏宽度拖拽 ---------- */
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!dragging.current) return;
      const w = e.clientX - 24;
      setWidth(Math.min(900, Math.max(380, w)));
    };
    const onUp = () => { dragging.current = false; document.body.style.cursor = ''; };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => { window.removeEventListener('mousemove', onMove); window.removeEventListener('mouseup', onUp); };
  }, []);

  if (loading) {
    return <div style={{ padding: 40, textAlign: 'center' }}><Spin tip="加载界面配置…" /></div>;
  }

  if (!schema) {
    return <Empty description="无法加载界面配置" />;
  }

  const isEmptyConfig = (schema.views.form?.sections?.length ?? 0) === 0;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: 680 }}>
      {/* ---------- 工具条 ---------- */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
        padding: '8px 10px', background: '#fff', border: '1px solid #e6eaf0',
        borderRadius: 10, marginBottom: 10,
      }}>
        <Tooltip title={dirty ? '有改动尚未保存' : '与已保存内容一致'}>
          <Tag
            icon={<CheckCircleOutlined />}
            color={dirty ? 'orange' : 'green'}
            style={{ margin: 0, fontWeight: 600 }}
          >
            {dirty ? '未保存改动' : '已保存'}
          </Tag>
        </Tooltip>

        {errCount > 0 && <Tag color="red" style={{ margin: 0 }}>{errCount} 错误</Tag>}
        {warnCount > 0 && <Tag color="orange" style={{ margin: 0 }}>{warnCount} 警告</Tag>}
        {errCount === 0 && warnCount === 0 && <Tag color="blue" style={{ margin: 0 }}>自检通过</Tag>}

        <span style={{ flex: 1 }} />

        <Tooltip title="撤销（Ctrl+Z）">
          <Button size="small" icon={<UndoOutlined />} disabled={!past.length} onClick={undo} />
        </Tooltip>
        <Tooltip title="重做（Ctrl+Shift+Z）">
          <Button size="small" icon={<RedoOutlined />} disabled={!future.length} onClick={redo} />
        </Tooltip>
        <Tooltip title="放弃改动，重新从服务端加载">
          <Button size="small" icon={<ReloadOutlined />} onClick={() => void load()}>重新加载</Button>
        </Tooltip>

        <Dropdown
          menu={{
            items: UI_SCHEMA_SAMPLES.map((s) => ({
              key: s.key,
              label: (
                <div>
                  <div>{s.label}</div>
                  <Text type="secondary" style={{ fontSize: 11 }}>{s.desc}</Text>
                </div>
              ),
              onClick: () => {
                commit({ ...s.schema, entityCode });
                message.info(`已载入「${s.label}」样例，请按实体实际字段核对引用`);
                setTab('structure');
              },
            })),
          }}
        >
          <Button size="small" icon={<FileTextOutlined />}>载入场景样例</Button>
        </Dropdown>

        <Button size="small" loading={validating} onClick={revalidate}>后端校验</Button>
        <Popconfirm
          title="保存界面配置"
          description={errCount > 0 ? `本地自检仍有 ${errCount} 个错误，确定保存？` : '保存后需发布才对运行时生效'}
          onConfirm={save}
          okText="保存" cancelText="取消"
        >
          <Button size="small" type="primary" icon={<SaveOutlined />} loading={saving} disabled={!dirty}>
            保存
          </Button>
        </Popconfirm>
      </div>

      {isEmptyConfig && (
        <Alert
          type="info" showIcon icon={<CloudUploadOutlined />} style={{ marginBottom: 10 }}
          message="这份实体还没有界面设计"
          description="可以从零拖字段搭建，也可以「载入场景样例」后按实体字段微调 —— 样例载入后自检会逐条报出字段引用差异。"
        />
      )}

      {/* ---------- 主体两栏 ---------- */}
      <div style={{ display: 'flex', flex: 1, minHeight: 0, gap: 0 }}>
        {/* 左：配置面板 */}
        <div style={{
          width, flex: '0 0 auto', display: 'flex', flexDirection: 'column',
          background: '#fff', border: '1px solid #e6eaf0', borderRadius: 10, overflow: 'hidden',
        }}>
          <Tabs
            size="small" activeKey={tab}
            onChange={(k) => {
              setTab(k as TabKey);
              // 切到 JSON 时用当前 schema 刷新文本框，避免展示过期内容
              if (k === 'json') setJsonResetKey((n) => n + 1);
            }}
            style={{ padding: '0 8px', flex: '0 0 auto' }}
            items={([
              { key: 'structure', label: TAB_LABEL.structure },
              { key: 'rules', label: TAB_LABEL.rules },
              { key: 'actions', label: TAB_LABEL.actions },
              { key: 'list', label: TAB_LABEL.list },
              { key: 'dict', label: TAB_LABEL.dict },
              {
                key: 'lint',
                label: (
                  <Space size={4}>
                    {TAB_LABEL.lint}
                    {errCount > 0 && <Tag color="red" style={{ fontSize: 10, margin: 0 }}>{errCount}</Tag>}
                  </Space>
                ),
              },
              { key: 'json', label: TAB_LABEL.json },
            ])}
          />
          <div style={{ flex: 1, overflow: 'auto', padding: '10px 12px' }}>
            {tab === 'structure' && (
              <StructurePanel
                schema={schema} onChange={commit} fields={fields}
                view={view} onViewChange={setView}
                focus={focus?.tab === 'structure' ? focus : undefined}
              />
            )}
            {tab === 'rules' && (
              <RulesPanel
                schema={schema} onChange={commit} fields={fields}
                focus={focus?.tab === 'rules' ? focus : undefined}
              />
            )}
            {tab === 'actions' && (
              <ActionBarPanel
                schema={schema} onChange={commit} fields={fields} actionDefs={actionDefs}
                focus={focus?.tab === 'actions' ? focus : undefined}
              />
            )}
            {tab === 'list' && (
              <ListPanel
                schema={schema} onChange={commit} fields={fields}
                focus={focus?.tab === 'list' ? focus : undefined}
              />
            )}
            {tab === 'dict' && (
              <DictionaryPanel
                schema={schema} onChange={commit} fields={fields}
                focus={focus?.tab === 'dict' ? focus : undefined}
              />
            )}
            {tab === 'lint' && (
              <LintPanel
                issues={issues} serverIssues={serverIssues}
                onLocate={onLocate} onRevalidate={revalidate} validating={validating}
              />
            )}
            {tab === 'json' && (
              <JsonPanel
                schema={schema}
                onApply={(next) => { commit(next); message.success('已应用到配置'); }}
                resetKey={jsonResetKey}
              />
            )}
          </div>
        </div>

        {/* 拖拽分隔条 */}
        <div
          onMouseDown={() => { dragging.current = true; document.body.style.cursor = 'col-resize'; }}
          style={{
            width: 8, cursor: 'col-resize', flex: '0 0 auto',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <div style={{ width: 2, height: 40, background: '#e6eaf0', borderRadius: 2 }} />
        </div>

        {/* 右：预览画布 */}
        <div style={{
          flex: 1, minWidth: 0, overflow: 'auto', padding: 16,
          background: '#f5f7fa', border: '1px solid #e6eaf0', borderRadius: 10,
        }}>
          <SchemaPreview schema={schema} fields={fields} entityCode={entityCode} />
        </div>
      </div>
    </div>
  );
}
