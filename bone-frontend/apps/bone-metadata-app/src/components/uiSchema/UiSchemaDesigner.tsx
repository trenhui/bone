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
import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert, Button, Dropdown, Empty, Input, List, message, Modal, Popconfirm, Popover, Space, Spin, Tabs, Tag, Tooltip, Typography,
} from 'antd';
import {
  CameraOutlined, CheckCircleOutlined, CloudUploadOutlined, ExclamationCircleOutlined, EyeOutlined, FileTextOutlined, FullscreenExitOutlined,
  FullscreenOutlined, HistoryOutlined, MacCommandOutlined, PlusOutlined, QuestionCircleOutlined, RedoOutlined,
  ReloadOutlined, SaveOutlined, ThunderboltOutlined, UndoOutlined, UserOutlined,
} from '@ant-design/icons';
import type { BoneUiSchema } from '../../utils/uiSchemaEngine';
import type { MetaField } from '../../types';
import { normJson } from '../../utils/normJson';
import { previewChanges, type ChangePreview } from '../../utils/uiSchemaChangePreview';
import { TOUR_STEPS, checklistDismissKey, onboardingMilestones, tourStorageKey } from '../../utils/onboarding';
import { metadataActionDefApi, metadataUiSchemaApi, type ActionDefSpec, type UiSchemaIssue } from '../../services/metadataApi';
import { buildDefaultSchema, type ViewKey } from './schemaOps';
import { enumFieldsOf, lintSchema, type LintIssue, type LintLocate } from './lint';
import { UI_SCHEMA_SAMPLES } from './samples';
import { sampleApplies } from './scenarios';
import StructurePanel, { FieldPalette } from './StructurePanel';
import RulesPanel from './RulesPanel';
import ActionBarPanel from './ActionBarPanel';
import ListPanel from './ListPanel';
import DictionaryPanel from './DictionaryPanel';
import I18nPanel from './I18nPanel';
import BasePanel from './BasePanel';
import LintPanel from './LintPanel';
import JsonPanel from './JsonPanel';
import VariantsPanel from './VariantsPanel';
import SchemaPreview from './SchemaPreview';
import ChangePreviewPanel from './ChangePreview';
import DesignerTour from './DesignerTour';
import OnboardingChecklist from './OnboardingChecklist';
import { fmtSnapTime, loadSnaps, removeSnap, saveSnap, type DesignerSnapshot } from './snapshots';

/**
 * 预览画布包裹 memo：左栏宽度拖拽 / 切换 Tab / 工具栏状态变化都不应触发重型预览重渲染
 * （大 schema 下区块与联动引擎开销显著）。预览的内部状态（身份模拟/场景/数据）自洽保留，
 * 仅当 schema / fields / entityCode 真变化时才重渲染——与运行时同源引擎的昂贵推导解耦。
 */
const MemoSchemaPreview = memo(SchemaPreview);

const { Text } = Typography;

interface Props {
  entityId: string;
  entityCode: string;
  fields: MetaField[];
  /**
   * 前往实体详情「发布」页签（由 EntityDetail 提供 setOuterTab('publish')）。
   * 0-1 闭环的关键一跳：设计器内保存后不用手动找发布入口——
   * 没有该回调时按钮不渲染（设计器被其他宿主复用时不假设外部结构）。
   */
  onGoPublish?: () => void;
  /**
   * 前往实体详情「列表显示」页签（由 EntityDetail 提供 setOuterTab('ui')）。
   * 两层闭环的反向一跳：偏好层→设计器早有引导（「去加入」），设计器→偏好层
   * 此前缺失——日常改契约的用户想顺手调个人显隐/密度/命名视图时没有入口。
   * 没有该回调时按钮不渲染（设计器被其他宿主复用时不假设外部结构）。
   */
  onGoPersonal?: () => void;
  /**
   * 实体行策略声明的 rule name 集合（来自 entity.rowPolicies.rules[].name）。
   * 列表视图 dataScope.policyCode 下拉的真源——只引用不新造（协议 §3.2）。
   */
  policyCodes?: string[];
}

type TabKey = 'structure' | 'rules' | 'actions' | 'list' | 'dict' | 'i18n' | 'base' | 'variants' | 'lint' | 'json';

const TAB_LABEL: Record<TabKey, string> = {
  structure: '结构',
  rules: '规则',
  actions: '动作条',
  list: '列表',
  dict: '字典',
  i18n: '多语言',
  base: '模板',
  variants: '变体',
  lint: '自检',
  json: 'JSON',
};

/**
 * 新手引导（第六轮）：静态 3 步 Modal 已升级为 Spotlight 交互式引导 + 0-1 进度卡。
 * - 步骤定义 / 里程碑判定：utils/onboarding.ts（纯函数，UI 与探针同源）；
 * - 交互组件：DesignerTour.tsx（聚光灯）+ OnboardingChecklist.tsx（进度卡）；
 * - 触发纪律：按实体首访自动弹出（localStorage 记忆），且仅当 0-1 尚未走完时——
 *   已完全部发布的实体再弹 0-1 引导是噪音；用户随时可从工具栏「帮助」重开。
 */

export default function UiSchemaDesigner({ entityId, entityCode, fields, onGoPublish, onGoPersonal, policyCodes = [] }: Props): React.ReactElement {
  const [loading, setLoading] = useState(true);
  const [schema, setSchema] = useState<BoneUiSchema | null>(null);
  const [baseline, setBaseline] = useState<string>(''); // 已保存快照，用于判脏
  const [lock, setLock] = useState(0);
  /** 已发布快照真源（后端 published_schema_json/version；null=从未发布）。三态徽标与待发布改动规模的同源基线。 */
  const [published, setPublished] = useState<{ json: string | null; version: number | null }>({ json: null, version: null });
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
  /**
   * schema 的渲染期镜像（与下方 actionsRef 同一模式）。
   * 编辑动作需要在事件处理器里取「当前 schema」压栈；曾经把 setPast/setFuture 嵌套在
   * setSchema 的更新器函数里读 cur——useState 更新器必须纯粹（React 契约），嵌套
   * setState 会转入 render-phase update 重放路径并被双倍应用（实测：undo /
   * jumpToPast 的 future 每次 +2、连续编辑时 past 出现 phantom 条目，历史栈
   * 「共 N 步可回退」与真实步数漂移）。改为更新器全部纯粹、当前值由 ref 提供。
   */
  const schemaRef = useRef<BoneUiSchema | null>(null);
  schemaRef.current = schema;

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
        setPublished({
          json: res.data.publishedSchemaJson ?? null,
          version: res.data.publishedVersion != null ? Number(res.data.publishedVersion) : null,
        });
      } else {
        next = buildDefaultSchema(entityCode, fields);
        setLock(0);
        setPublished({ json: null, version: null });
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
    const cur = schemaRef.current;
    if (cur) setPast((p) => [...p.slice(-49), cur]);
    setSchema(next);
    setFuture([]);
  }, []);

  const undo = () => {
    if (!past.length) return;
    const prev = past[past.length - 1];
    const cur = schemaRef.current;
    setPast((p) => p.slice(0, -1));
    if (cur) setFuture((f) => [cur, ...f].slice(0, 50));
    setSchema(prev);
  };
  const redo = () => {
    if (!future.length) return;
    const nxt = future[0];
    const cur = schemaRef.current;
    setFuture((f) => f.slice(1));
    if (cur) setPast((p) => [...p, cur]);
    setSchema(nxt);
  };

  /**
   * 跳回「最近第 fromMostRecentIndex 步之前」的状态（可视化历史栈的核心）。
   * fromMostRecentIndex=0 等价于一次 undo；=k 等价于连撤 k+1 步。
   * 被跳过的若干中间态全部压入 future，保证后续仍可逐条 redo（与 undo 语义一致）。
   */
  const jumpToPast = (fromMostRecentIndex: number) => {
    const n = past.length;
    const pastIndex = n - 1 - fromMostRecentIndex;
    if (pastIndex < 0 || pastIndex >= n) return;
    const target = past[pastIndex];
    const dropped = past.slice(pastIndex + 1); // target 之后、当前态之前的若干中间态
    const cur = schemaRef.current;
    setPast(past.slice(0, pastIndex));
    if (cur) setFuture((f) => [...dropped, cur, ...f].slice(0, 50));
    setSchema(target);
    setHistOpen(false);
  };

  /* ---------- 全屏（禅模式） ---------- */
  const [zen, setZen] = useState(false);
  /* ---------- Spotlight 新手引导（按实体首访自动，可从工具栏「帮助」重开） ---------- */
  const [tourOpen, setTourOpen] = useState(false);
  const [tourIdx, setTourIdx] = useState(0);
  /* ---------- 0-1 进度卡（按实体可关闭；加载完成 / 切实体时重读 localStorage） ---------- */
  const [checklistHidden, setChecklistHidden] = useState(false);
  /* ---------- 版本快照（P2 · 本地 30 份上限，恢复前协议 Gate） ---------- */
  const [snaps, setSnaps] = useState<DesignerSnapshot[]>([]);
  const [snapModalOpen, setSnapModalOpen] = useState(false);
  /* ---------- 命令面板（Cmd/Ctrl+K） ---------- */
  const [cmdOpen, setCmdOpen] = useState(false);
  const [cmdQuery, setCmdQuery] = useState('');
  /* ---------- 命令面板键盘导航游标（箭头/Enter 可达） ---------- */
  const [cmdActive, setCmdActive] = useState(0);
  /* ---------- 编辑历史栈（可视化 undo 时间线 · 对标 VS Code Timeline / Figma 版本历史） ---------- */
  const [histOpen, setHistOpen] = useState(false);
  /* 命令面板键盘导航：当前高亮项 ref（用于滚入视口） */
  const cmdActiveRef = useRef<HTMLDivElement | null>(null);

  // 命令面板每次打开或筛选变化，键盘游标归零（避免残留旧游标越界）
  useEffect(() => { if (cmdOpen) setCmdActive(0); }, [cmdOpen]);
  useEffect(() => { setCmdActive(0); }, [cmdQuery]);
  // 键盘游标移动时把高亮项滚入视口（长命令列表不丢焦点）
  useEffect(() => { cmdActiveRef.current?.scrollIntoView({ block: 'nearest' }); }, [cmdActive, cmdOpen]);

  const closeTour = () => {
    setTourOpen(false);
    setTourIdx(0);
    try { localStorage.setItem(tourStorageKey(entityId), '1'); } catch { /* 隐私模式：忽略，下次再引导 */ }
  };

  const dismissChecklist = () => {
    setChecklistHidden(true);
    try { localStorage.setItem(checklistDismissKey(entityId), '1'); } catch { /* 隐私模式：忽略 */ }
  };

  /**
   * 保存请求（鼠标与键盘共用同一路径）：有本地自检错误时先确认，
   * 避免「点了保存 → 静默存进一份带错配置」。存草稿本身允许（不阻断），
   * 真正的硬门禁在发布链路（PublishPreviewPanel 调后端 validate）。
   */
  const requestSave = () => {
    if (!schema) return;
    if (errCount > 0) {
      Modal.confirm({
        title: '保存界面配置',
        content: `本地自检仍有 ${errCount} 个错误，确定保存？（发布前必须修复）`,
        okText: '保存', cancelText: '取消',
        onOk: save,
      });
      return;
    }
    void save();
  };

  /**
   * 键盘快捷键（业界编辑器标配；tooltip 早已宣称 Ctrl+Z，此前无监听 = 假承诺，现补成真）：
   * - Ctrl/⌘+Z 撤销，Ctrl/⌘+Shift+Z（或 Ctrl+Y）重做，Ctrl/⌘+S 保存，Ctrl/⌘+K 命令面板
   * - Esc 退出全屏
   * 输入聚焦（Input/TextArea/Select/contentEditable）时编辑类快捷键不劫持——
   * 否则在 JSON 面板 / 条件编辑器里打字会误触撤销（曾预演：ConditionEditor 内 Ctrl+Z 被全局吞）。
   * Ctrl+K 是全局约定（VS Code 等均不排除输入态），故置于输入 guard 之前。
   */
  const actionsRef = useRef({ undo, redo, requestSave });
  actionsRef.current = { undo, redo, requestSave };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const mod = e.metaKey || e.ctrlKey;
      if (mod && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setCmdOpen((v) => !v);
        return;
      }
      const t = e.target as HTMLElement | null;
      if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.tagName === 'SELECT' || t.isContentEditable)) return;
      if (e.key === 'Escape') {
        // 有弹层（Modal/下拉/气泡）时 Esc 归弹层自己，不退出全屏。
        // 必须判「可见」而非「在 DOM 中」：qiankun 沙箱里 antd Modal 关闭后 portal 不卸载
        // （motion 结束事件丢失），隐藏 wrap 残留 DOM——按存在性判断会永久吞掉 Esc。
        // antd 对下拉/气泡/浮层用 display:none 隐藏，对 Modal wrap 同样如此，统一用计算样式判可见。
        const popupOpen = Array.from(document.querySelectorAll(
          '.ant-modal-wrap, .ant-select-dropdown, .ant-dropdown, .ant-popover',
        )).some((el) => {
          const s = getComputedStyle(el);
          return s.display !== 'none' && s.visibility !== 'hidden';
        });
        if (popupOpen) return;
        // 脏态退出全屏同样要守卫（业界基线：Google Docs / Figma / VS Code）——
        // 与工具栏「退出全屏」按钮的 Popconfirm 同口径；键盘路径无锚点故用命令式 Modal。
        if (zenRef.current && dirtyRef.current) {
          Modal.confirm({
            title: '放弃未保存改动？',
            content: '退出全屏不会保存当前改动；可先保存（Ctrl+S）再退出。',
            okText: '放弃并退出', okButtonProps: { danger: true }, cancelText: '取消',
            onOk: () => setZen(false),
          });
          return;
        }
        setZen(false);
        return;
      }
      if (!mod) return;
      const k = e.key.toLowerCase();
      if (k === 'z' && !e.shiftKey) { e.preventDefault(); actionsRef.current.undo(); }
      else if ((k === 'z' && e.shiftKey) || k === 'y') { e.preventDefault(); actionsRef.current.redo(); }
      else if (k === 's') { e.preventDefault(); void actionsRef.current.requestSave(); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, []);

  // 键盘 Esc 退出全屏的脏态守卫需读最新 zen/dirty：键盘 effect 只挂一次，
  // 直接闭包捕获会拿到首次渲染值（恒 false）——ref 镜像，与 schemaRef 同模式。
  const zenRef = useRef(zen);
  zenRef.current = zen;

  /* ---------- 版本快照（P2 · 对标原型：本地 30 份上限，恢复前协议 Gate） ---------- */
  const doSnapshot = () => {
    if (!schema) return;
    const snap: DesignerSnapshot = {
      id: `snap-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      ts: Date.now(),
      entityCode,
      label: entityCode,
      views: Object.keys(schema.views ?? {}).length,
      schema: JSON.parse(JSON.stringify(schema)) as BoneUiSchema,
    };
    const next = saveSnap(entityId, snap);
    setSnaps(next);
    message.success(`已保存快照（${next.length}/30）`);
  };

  const restoreSnap = (s: DesignerSnapshot) => {
    // 协议 Gate：恢复前若快照含 error 级问题，阻断（与发布门禁同口径：坏配置不静默顶上来）
    const errs = lintSchema(s.schema, knownFields, enumFields).filter((i) => i.level === 'error');
    if (errs.length) {
      Modal.warning({
        title: '恢复被拦截 · 协议错误',
        content: `该快照含 ${errs.length} 处协议错误，已阻止恢复：${errs.slice(0, 3).map((e) => e.message).join('；')}${errs.length > 3 ? '…' : ''}`,
      });
      return;
    }
    commit(s.schema);
    setBaseline(JSON.stringify(s.schema));
    setPast([]);
    setFuture([]);
    setSnapModalOpen(false);
    message.success('已恢复到快照');
  };

  const delSnap = (id: string) => setSnaps(removeSnap(entityId, id));

  /**
   * 命令面板命令集（Cmd/Ctrl+K · 业界编辑器标配）。
   * 不用 useMemo 缓存：命令闭包需要每渲染捕获最新的 save/undo 等引用，
   *  memo 化会把「保存」固化成旧闭包（stale schema/lock → 409 冲突）。
   */
  const COMMANDS: { key: string; group: string; label: string; run: () => void; disabled?: boolean }[] = [
    { key: 'view-form', group: '视图', label: '切换：录入表单视图', run: () => setView('form') },
    { key: 'view-detail', group: '视图', label: '切换：详情视图', run: () => setView('detail') },
    { key: 'validate', group: '校验', label: '运行后端校验', run: () => void revalidate() },
    { key: 'save', group: '保存', label: '保存界面配置', run: () => requestSave() },
    { key: 'snapshot', group: '保存', label: '保存当前快照', run: doSnapshot },
    { key: 'undo', group: '编辑', label: '撤销（Ctrl+Z）', run: undo, disabled: !past.length },
    { key: 'redo', group: '编辑', label: '重做（Ctrl+Shift+Z）', run: redo, disabled: !future.length },
    { key: 'zen-on', group: '视图', label: '进入全屏设计器', run: () => setZen(true) },
    { key: 'zen-off', group: '视图', label: '退出全屏', run: () => setZen(false) },
    { key: 'guide', group: '帮助', label: '新手指引（6 步走完 0-1）', run: () => { setTourIdx(0); setTourOpen(true); } },
    { key: 'checklist', group: '帮助', label: '展开 0-1 上手指引进度卡', run: () => { setChecklistHidden(false); setTab('structure'); } },
    { key: 'go-structure', group: '跳转面板', label: '跳到：结构', run: () => setTab('structure') },
    { key: 'go-rules', group: '跳转面板', label: '跳到：规则', run: () => setTab('rules') },
    { key: 'go-actions', group: '跳转面板', label: '跳到：动作条', run: () => setTab('actions') },
    { key: 'go-list', group: '跳转面板', label: '跳到：列表', run: () => setTab('list') },
    { key: 'go-dict', group: '跳转面板', label: '跳到：字典', run: () => setTab('dict') },
    { key: 'go-i18n', group: '跳转面板', label: '跳到：多语言', run: () => setTab('i18n') },
    { key: 'go-base', group: '跳转面板', label: '跳到：模板', run: () => setTab('base') },
    { key: 'go-variants', group: '跳转面板', label: '跳到：变体', run: () => setTab('variants') },
    { key: 'go-lint', group: '跳转面板', label: '跳到：自检', run: () => setTab('lint') },
    { key: 'go-json', group: '跳转面板', label: '跳到：JSON', run: () => setTab('json') },
  ];
  // 不过滤 memo：18 条命令的过滤成本可忽略，而 memo 化会把命令闭包固化（stale save/undo 引用）
  const cmdFiltered = COMMANDS.filter((c) => {
    const q = cmdQuery.trim().toLowerCase();
    return !q || c.label.toLowerCase().includes(q) || c.group.toLowerCase().includes(q);
  });

  const dirty = useMemo(
    () => !!schema && JSON.stringify(schema) !== baseline,
    [schema, baseline],
  );

  // dirty 的 ref 镜像（Esc 退出全屏守卫用；声明晚于键盘 effect 无妨——
  // effect 回调在事件期读 ref，与渲染体赋值顺序无关）
  const dirtyRef = useRef(dirty);
  dirtyRef.current = dirty;

  /**
   * 发布态三态（对标原型 dirty()/pending() 双判定，但真源走后端已发布快照，非前端近似）：
   * - never：从未发布（publishedSchemaJson 为空）
   * - pending：已存草稿(baseline) ≠ 已发布快照 → 有已保存但未发布的改动
   * - published：已存草稿 === 已发布快照（内容等价，键序/空白无关）
   */
  const publishState = useMemo<'never' | 'pending' | 'published'>(() => {
    if (!published || published.json == null) return 'never';
    if (!baseline) return 'pending';
    try {
      return normJson(JSON.parse(baseline)) === normJson(JSON.parse(published.json)) ? 'published' : 'pending';
    } catch {
      return 'pending';
    }
  }, [baseline, published]);

  /**
   * 已发布快照解析 + 发布变更预告（同一 useMemo 内解析一次，双消费）：
   * - publishedSchema：线上对照预览的真源（传给 SchemaPreview「草稿/线上」开关）；
   * - changePreview：baseline（已存草稿） vs 线上快照的人话变更清单——
   *   真源走后端 published_schema_json（util 头注），不用前端时间戳近似。
   *   取代此前的 pendingChange（顶层维度计数）：那是同一信息的第二套实现，
   * 且粒度粗（只报「联动规则+1」不报是哪条），违反单真源纪律，故删除。
   */
  const { publishedSchema, changePreview } = useMemo(() => {
    let pub: BoneUiSchema | null = null;
    try {
      pub = published.json ? (JSON.parse(published.json) as BoneUiSchema) : null;
    } catch {
      pub = null;
    }
    let draft: BoneUiSchema | null = null;
    try {
      draft = baseline ? (JSON.parse(baseline) as BoneUiSchema) : null;
    } catch {
      draft = null;
    }
    return { publishedSchema: pub, changePreview: previewChanges(draft, pub, fields) as ChangePreview };
  }, [baseline, published, fields]);

  /**
   * 未保存改动离开页面拦截（业界基线：Google Docs / Figma / VS Code 均在脏状态时阻止误关/刷新）。
   * 仅拦截浏览器级卸载（关页 / 刷新 / 地址栏跳转）——这是跨框架最稳的防丢基线。
   * 说明：qiankun 微应用内「点 shell 菜单切换路由」由主应用路由主导，react-router 的
   * useBlocker 无法可靠拦截主应用级跳转（且会误伤正常导航），故不在此硬拦；
   * 路由级拦截需主应用（shell）配合暴露「脏状态回调」后再统一确认，列为后续。
   */
  useEffect(() => {
    if (!dirty) return;
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', onBeforeUnload);
    return () => window.removeEventListener('beforeunload', onBeforeUnload);
  }, [dirty]);

  /* ---------- 自检 ---------- */
  const knownFields = useMemo(() => new Set(fields.map((f) => f.code)), [fields]);
  // 样例按实体领域过滤：跨领域样例（如报销单里出现「门诊快赔」）不进入「载入场景样例」下拉
  const visibleSamples = useMemo(() => UI_SCHEMA_SAMPLES.filter((s) => sampleApplies(s, fields)), [fields]);
  const enumFields = useMemo(() => enumFieldsOf(fields), [fields]);
  const issues = useMemo<LintIssue[]>(
    () => (schema ? lintSchema(schema, knownFields, enumFields) : []),
    [schema, knownFields, enumFields],
  );
  const errCount = issues.filter((i) => i.level === 'error').length;
  const warnCount = issues.filter((i) => i.level === 'warning').length;

  /**
   * 0-1 里程碑（进度卡与引导自动弹出共用同一判定：utils/onboarding 纯函数）。
   * fields.length 参与「列表精选」判定（平铺全字段 = 没精选过）。
   */
  const milestones = useMemo(
    () => onboardingMilestones(schema, publishState, errCount, fields.length),
    [schema, publishState, errCount, fields.length],
  );

  /**
   * 引导自动弹出（按实体首访 + 0-1 未完成双条件）：
   * - 每次 load() 完成（loading 翻 false）评估一次——此刻 published/schema 状态已就绪，
   *   不会在加载中对空 schema 误判；
   * - autoOpenRef 保证「每个实体每次挂载只评估一次」：milestones 随每次编辑变化，
   *   若无守卫会把用户进行到一半的引导重置回第 0 步；
   * - 已走完 0-1（五项全亮，典型：历史实体 / 别人配好我只是来看看）不自动弹——
   *   0-1 引导对已完成者是噪音；需要时工具栏「帮助」随时重开；
   * - localStorage 键按实体隔离：同一浏览器换实体设计，各自首次都会引导。
   */
  const autoOpenRef = useRef<string | null>(null);
  useEffect(() => {
    if (loading) return;
    if (autoOpenRef.current === entityId) return;
    autoOpenRef.current = entityId;
    let seen: string | null = null;
    try { seen = localStorage.getItem(tourStorageKey(entityId)); } catch { /* 隐私模式 */ }
    if (!seen && milestones.some((m) => !m.done)) {
      setTourIdx(0);
      setTourOpen(true);
    }
  }, [loading, entityId, milestones]);

  // 进度卡关闭态按实体记忆（切实体 / 重新加载时重读，避免串味）
  useEffect(() => {
    let v: string | null = null;
    try { v = localStorage.getItem(checklistDismissKey(entityId)); } catch { /* 隐私模式 */ }
    setChecklistHidden(v === '1');
  }, [entityId]);

  const onLocate = (l: LintLocate | undefined) => {
    if (!l) return;
    setTab(l.tab as TabKey);
    setFocus(l);
  };

  /* ---------- 保存 / 校验 ---------- */
  /**
   * 保存（返回是否成功）。返回值供「去发布」链路判断：脏态时先保存再跳转，
   * 保存失败（含乐观锁冲突）不跳转——避免用户跳到发布页面对一份旧配置点发布。
   */
  const save = async (): Promise<boolean> => {
    if (!schema) return false;
    setSaving(true);
    try {
      const res = await metadataUiSchemaApi.save(entityId, JSON.stringify(schema), lock);
      if (res.code === 200) {
        message.success('界面配置已保存');
        setBaseline(JSON.stringify(schema));
        setLock(Number(res.data?.lockVersion ?? lock + 1));
        setServerIssues(null);
        return true;
      }
      if (res.code === 409 || res.code === 412) {
        message.warning('配置已被其他人修改（乐观锁冲突），已重新加载最新内容');
        void load();
        return false;
      }
      message.error(res.message ?? '保存失败');
      return false;
    } catch {
      message.error('保存失败');
      return false;
    } finally {
      setSaving(false);
    }
  };

  /**
   * 0-1 闭环的最后一跳（第六轮新增）：设计器内直接前往实体详情「发布」页签。
   * 脏态时先保存——「跳到发布页发现配置还是旧的」是 0-1 流最常见的断点感来源。
   */
  const handleGoPublish = async () => {
    if (dirty && !(await save())) return; // 保存失败：message 已提示，停在设计器
    onGoPublish?.();
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
    return (
      <div style={{ padding: 40, textAlign: 'center' }}>
        <Spin />
        {/* tip 仅 nest/fullscreen 模式生效，独立 Spin 带 tip 会告警；文案作兄弟节点渲染，视觉等价 */}
        <div style={{ marginTop: 8, color: '#00000073', fontSize: 12 }}>加载界面配置…</div>
      </div>
    );
  }

  if (!schema) {
    return <Empty description="无法加载界面配置" />;
  }

  const isEmptyConfig = (schema.views.form?.sections?.length ?? 0) === 0;

  const content = (
    <div style={{ display: 'flex', flexDirection: 'column', minHeight: zen ? '100%' : 680 }}>
      {/* ---------- 工具条 ---------- */}
      <div style={{
        display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap',
        padding: '8px 10px', background: '#fff', border: '1px solid #e6eaf0',
        borderRadius: 10, marginBottom: 10,
      }}>
        <Tooltip title={dirty ? '有改动尚未保存，离开/刷新页面会提示确认' : '与已保存内容一致'}>
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

        <Tooltip
          title={
            publishState === 'published'
              ? '已存草稿与线上发布版一致'
              : publishState === 'pending'
                ? `有已保存但未发布的改动${changePreview?.summary ? `：${changePreview.summary}` : ''}，发布后才对终端生效`
                : '尚未发布过，终端暂按结构投影渲染'
          }
        >
          <Tag
            icon={publishState === 'published' ? <CheckCircleOutlined /> : publishState === 'pending' ? <CloudUploadOutlined /> : <ExclamationCircleOutlined />}
            color={publishState === 'published' ? 'green' : publishState === 'pending' ? 'gold' : 'default'}
            style={{ margin: 0, fontWeight: 600 }}
            data-tour="publish-state"
          >
            {publishState === 'published' ? '已发布' : publishState === 'pending' ? '待发布' : '未发布'}
          </Tag>
        </Tooltip>

        {/* 发布变更预告（第五轮：上线前用业务语言看清改变什么；never 态=首发内容） */}
        {(publishState === 'pending' || publishState === 'never') && changePreview.total > 0 && (
          <Popover
            trigger="click"
            placement="bottomLeft"
            title={publishState === 'never' ? '首次发布内容预告' : '发布变更预告（已存草稿 vs 线上发布版）'}
            content={
              <ChangePreviewPanel preview={changePreview} mode={publishState === 'never' ? 'first' : 'pending'} />
            }
          >
            <Button size="small" icon={<EyeOutlined />}>
              {publishState === 'never' ? '首发内容' : '变更预告'}
            </Button>
          </Popover>
        )}

        <span style={{ flex: 1 }} />

        <Tooltip title="撤销（Ctrl+Z）">
          <Button size="small" aria-label="撤销" icon={<UndoOutlined />} disabled={!past.length} onClick={undo} />
        </Tooltip>
        <Tooltip title="重做（Ctrl+Shift+Z）">
          <Button size="small" aria-label="重做" icon={<RedoOutlined />} disabled={!future.length} onClick={redo} />
        </Tooltip>
        <Popover
          open={histOpen}
          onOpenChange={setHistOpen}
          trigger="click"
          placement="bottom"
          title="编辑历史 · 点击跳回该步"
          content={
            <div style={{ maxHeight: 320, overflow: 'auto', minWidth: 230 }}>
              {past.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无可回退步骤" />
              ) : (
                <List
                  size="small"
                  dataSource={past.map((_s, i) => i).reverse()}
                  renderItem={(i) => (
                    <List.Item
                      style={{ cursor: 'pointer', padding: '6px 8px', borderRadius: 6 }}
                      onClick={() => jumpToPast(past.length - 1 - i)}
                    >
                      <List.Item.Meta
                        avatar={<span style={{ color: '#1677ff', fontWeight: 700 }}>↶</span>}
                        title={`回退 ${past.length - i} 步`}
                        description={`点此跳回该状态（共 ${past.length} 步可回退）`}
                      />
                    </List.Item>
                  )}
                />
              )}
              {future.length > 0 && (
                <div style={{ marginTop: 8, color: '#999', fontSize: 12 }}>
                  可重做 {future.length} 步（Ctrl+Shift+Z）
                </div>
              )}
            </div>
          }
        >
          <Button size="small" aria-label="编辑历史" icon={<HistoryOutlined />} disabled={!past.length}>历史</Button>
        </Popover>
        <Tooltip title="放弃改动，重新从服务端加载">
          {dirty ? (
            /* 第三轮「丢弃未保存改动的三道守卫」之一：重新加载前确认。
             * 曾因合并取用户在途演进版而丢失（dev 只剩 beforeunload 一道），
             * 由 _probe_designer_polish 回归探针负向发现，现补回。 */
            <Popconfirm
              title="放弃未保存改动？"
              description="重新加载会从服务端拉取最新版本，当前未保存的界面配置将丢失。"
              okText="重新加载" okButtonProps={{ danger: true }} cancelText="取消"
              onConfirm={() => void load()}
            >
              <Button size="small" aria-label="重新加载" icon={<ReloadOutlined />}>重新加载</Button>
            </Popconfirm>
          ) : (
            <Button size="small" aria-label="重新加载" icon={<ReloadOutlined />} onClick={() => void load()}>重新加载</Button>
          )}
        </Tooltip>

        <Tooltip title={zen ? '退出全屏（Esc）' : '全屏编辑（Esc 退出）'}>
          {zen && dirty ? (
            <Popconfirm
              title="放弃未保存改动？"
              description="退出全屏不会保存当前改动；可先保存（Ctrl+S）再退出。"
              okText="放弃并退出" okButtonProps={{ danger: true }} cancelText="取消"
              onConfirm={() => setZen(false)}
            >
              <Button size="small" icon={<FullscreenExitOutlined />}>退出全屏</Button>
            </Popconfirm>
          ) : (
            <Button
              size="small"
              icon={zen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
              onClick={() => setZen((v) => !v)}
            >
              {zen ? '退出全屏' : '全屏'}
            </Button>
          )}
        </Tooltip>

        <Tooltip title="新手指引（6 步走完 0-1：字段库 → 分区 → 列表 → 规则 → 发布）">
          <Button size="small" icon={<QuestionCircleOutlined />} onClick={() => { setTourIdx(0); setTourOpen(true); }}>
            帮助
          </Button>
        </Tooltip>

        <Dropdown
          menu={{
            items: visibleSamples.map((s) => ({
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

        <Dropdown
          menu={{
            items: [
              { key: 'save', label: '保存当前快照', icon: <CameraOutlined />, onClick: doSnapshot },
              { key: 'history', label: '版本历史…', icon: <HistoryOutlined />, onClick: () => { setSnaps(loadSnaps(entityId)); setSnapModalOpen(true); } },
            ],
          }}
        >
          <Button size="small" icon={<CameraOutlined />}>快照</Button>
        </Dropdown>

        <Tooltip title="命令面板（Cmd/Ctrl + K）">
          <Button size="small" icon={<MacCommandOutlined />} onClick={() => setCmdOpen(true)}>命令</Button>
        </Tooltip>

        {/* 两层闭环反向入口：契约层 → 个人偏好层（仅自己可见的显隐/密度/命名视图） */}
        {onGoPersonal && (
          <Tooltip title="打开「列表显示」：调整仅自己可见的个人偏好（列显隐/密度/改名/个人视图）">
            <Button size="small" icon={<UserOutlined />} onClick={onGoPersonal}>
              个人偏好
            </Button>
          </Tooltip>
        )}

        {/* 0-1 闭环：设计器内直达发布（脏态先保存）；未提供回调（其他宿主复用）时不渲染 */}
        {onGoPublish && publishState !== 'published' && (
          <Tooltip title={dirty ? '有未保存改动——点击将先保存再前往「发布」' : '前往「发布」页签；发布后才对终端用户生效'}>
            <Button size="small" icon={<ThunderboltOutlined />} loading={saving} onClick={() => void handleGoPublish()}>
              去发布
            </Button>
          </Tooltip>
        )}

        <Tooltip title="保存（Ctrl+S）。保存后需发布才对运行时生效">
          <Button size="small" type="primary" icon={<SaveOutlined />} loading={saving} disabled={!dirty} onClick={requestSave} data-tour="save">
            保存
          </Button>
        </Tooltip>
      </div>

      {isEmptyConfig && (
        <Alert
          type="info" showIcon icon={<CloudUploadOutlined />} style={{ marginBottom: 10 }}
          message="这份实体还没有界面设计"
          description="可以从零拖字段搭建，也可以「载入场景样例」后按实体字段微调 —— 样例载入后自检会逐条报出字段引用差异。"
        />
      )}

      {/* 协议错误阻断式横幅（对标原型 Gate：错误不藏在 Tab 里，顶出来 + 给修复捷径） */}
      {errCount > 0 && (
        <Alert
          type="error" showIcon style={{ marginBottom: 10 }}
          message={`界面协议存在 ${errCount} 处错误，保存前建议修复`}
          description={
            <div>
              <div style={{ marginBottom: 6 }}>
                {issues.filter((i) => i.level === 'error').slice(0, 3).map((i, idx) => (
                  <div key={idx}>· {i.message}</div>
                ))}
                {errCount > 3 && <div>…等 {errCount} 处</div>}
              </div>
              <Button size="small" danger onClick={() => setTab('lint')}>前往「自检」面板修复</Button>
            </div>
          }
        />
      )}

      {/* ---------- 主体两栏 ---------- */}
      <div style={{ display: 'flex', flex: 1, minHeight: 0, gap: 0, position: 'relative' }}>
        {/* 左：配置面板 */}
        <div style={{
          width, flex: '0 0 auto', display: 'flex', flexDirection: 'column',
          background: '#fff', border: '1px solid #e6eaf0', borderRadius: 10, overflow: 'hidden',
          position: 'relative',
        }} data-tour="left-panel">
          {/* 0-1 新手指引进度卡（加载中不渲染，避免对未就绪 schema 虚亮） */}
          {!loading && !checklistHidden && (
            <div style={{ padding: '10px 10px 0' }}>
              <OnboardingChecklist
                milestones={milestones}
                onNavigate={(t) => setTab(t)}
                onGoPublish={onGoPublish ? () => void handleGoPublish() : undefined}
                onDismiss={dismissChecklist}
              />
            </div>
          )}
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
                key: 'i18n',
                label: (
                  <Space size={4}>
                    {TAB_LABEL.i18n}
                    {schema.i18n?.namespace && <Tag color="blue" style={{ fontSize: 10, margin: 0 }}>ns</Tag>}
                  </Space>
                ),
              },
              {
                key: 'base',
                label: (
                  <Space size={4}>
                    {TAB_LABEL.base}
                    {schema.base?.templateCode && <Tag color="purple" style={{ fontSize: 10, margin: 0 }}>跟随</Tag>}
                  </Space>
                ),
              },
              {
                key: 'variants',
                label: (
                  <Space size={4}>
                    {TAB_LABEL.variants}
                    {(schema.variants?.length ?? 0) > 0 && (
                      <Tag color="blue" style={{ fontSize: 10, margin: 0 }}>{schema.variants?.length}</Tag>
                    )}
                  </Space>
                ),
              },
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
                policyCodes={policyCodes} actionDefs={actionDefs}
                focus={focus?.tab === 'list' ? focus : undefined}
              />
            )}
            {tab === 'dict' && (
              <DictionaryPanel
                schema={schema} onChange={commit} fields={fields}
                focus={focus?.tab === 'dict' ? focus : undefined}
              />
            )}
            {tab === 'i18n' && (
              <I18nPanel
                schema={schema} onChange={commit} entityCode={entityCode}
              />
            )}
            {tab === 'base' && (
              <BasePanel
                entityId={entityId} schema={schema} onChange={commit}
                lockVersion={lock} onMerged={() => void load()}
              />
            )}
            {tab === 'variants' && (
              <VariantsPanel schema={schema} onChange={commit} fields={fields} actionDefs={actionDefs} />
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

          {/* FAB 控件库（对标原型：就近调出字段库，不必滚到面板底部） */}
          <Popover
            trigger="click"
            placement="topRight"
            title="添加字段到界面"
            content={<FieldPalette schema={schema} fields={fields} onChange={commit} view={view} />}
          >
            <Button
              shape="circle" size="large" type="primary"
              aria-label="添加字段到界面"
              icon={<PlusOutlined />}
              style={{ position: 'absolute', right: 14, bottom: 14, zIndex: 5, boxShadow: '0 6px 16px rgba(22,119,255,.35)' }}
            />
          </Popover>
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
        }} data-tour="preview">
          <MemoSchemaPreview
            schema={schema} fields={fields} entityCode={entityCode} editView={view}
            publishedSchema={publishedSchema}
            draftDiffers={publishState === 'pending'}
          />
        </div>
      </div>
    </div>
  );

  /* Spotlight 新手引导（portal 到 body，全屏/常态挂载点无关；z-index 高于 Modal/message） */
  const tour = (
    <DesignerTour open={tourOpen} steps={TOUR_STEPS} index={tourIdx} onIndex={setTourIdx} onClose={closeTour} />
  );

  /* 版本历史 Modal（本地快照；恢复前跑协议 Gate） */
  const snapModal = (
    <Modal title="版本历史（本地快照 · 上限 30）" open={snapModalOpen} onCancel={() => setSnapModalOpen(false)} footer={null} width={560}>
      {snaps.length === 0 ? (
        <Empty description="还没有快照，点工具栏「快照 → 保存当前快照」即可存档" />
      ) : (
        <List
          dataSource={snaps}
          renderItem={(s) => (
            <List.Item
              actions={[
                <Button key="r" type="link" onClick={() => restoreSnap(s)}>恢复</Button>,
                <Button key="d" type="link" danger onClick={() => delSnap(s.id)}>删除</Button>,
              ]}
            >
              <List.Item.Meta
                title={`${s.label} · ${s.views} 视图`}
                description={`${fmtSnapTime(s.ts)}（${snaps.indexOf(s) + 1}/30）`}
              />
            </List.Item>
          )}
        />
      )}
    </Modal>
  );

  /* 命令面板 Modal（Cmd/Ctrl+K · 键盘可达：↑/↓ 移动、Enter 执行、Esc 关闭，对标 VS Code/Sublime） */
  const cmdModal = (
    <Modal
      title="命令面板（Cmd/Ctrl + K）" open={cmdOpen} onCancel={() => setCmdOpen(false)} footer={null} width={520}
    >
      <Input
        autoFocus
        placeholder="搜索命令，如：全屏 / 校验 / 撤销 / 跳到 结构（↑↓ 选择，Enter 执行）"
        value={cmdQuery}
        onChange={(e) => setCmdQuery(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'ArrowDown') {
            e.preventDefault();
            setCmdActive((i) => Math.min(cmdFiltered.length - 1, i + 1));
          } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            setCmdActive((i) => Math.max(0, i - 1));
          } else if (e.key === 'Enter') {
            e.preventDefault();
            const c = cmdFiltered[cmdActive];
            if (c && !c.disabled) { c.run(); setCmdOpen(false); }
          }
        }}
        style={{ marginBottom: 10 }}
      />
      <div style={{ maxHeight: '50vh', overflow: 'auto' }}>
        {cmdFiltered.length === 0 ? (
          <Empty description="无匹配命令" />
        ) : (
          cmdFiltered.map((c, idx) => {
            const active = idx === cmdActive;
            return (
              <div
                key={c.key}
                ref={active ? cmdActiveRef : undefined}
                onClick={() => { if (!c.disabled) { c.run(); setCmdOpen(false); } }}
                onMouseEnter={() => setCmdActive(idx)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 8, padding: '8px 10px',
                  borderRadius: 6, cursor: c.disabled ? 'not-allowed' : 'pointer',
                  opacity: c.disabled ? 0.45 : 1,
                  background: active ? '#e6f4ff' : 'transparent',
                }}
              >
                <Tag style={{ margin: 0, fontSize: 10 }}>{c.group}</Tag>
                <span style={{ fontSize: 13 }}>{c.label}</span>
                {active && <span style={{ marginLeft: 'auto', fontSize: 10, color: '#1677ff' }}>↵</span>}
              </div>
            );
          })
        )}
      </div>
    </Modal>
  );

  /* 全屏（禅模式）：fixed 覆盖层吃满视口 + 上下文头（业界 IDE Zen Mode / Figma 演示范式），Esc 退出。
     不做 window.open 新窗——qiankun 子应用脱离 shell 会丢登录态/租户头/菜单上下文。
     头部不做 navigate 回跳：本页同时服务 /apps/:appId/modules/:moduleId/entities/:id 作用域路由，
     硬跳 /entities/:id 会丢 app/module 上下文（退出全屏本身即回到原页面）。 */
  if (zen) {
    return (
      <div style={{ position: 'fixed', inset: 0, zIndex: 1080, background: '#f0f2f5', display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', borderBottom: '1px solid #e6eaf0', background: '#fff' }}>
          <Text strong>界面设计器 · {entityCode}</Text>
          <Tag color="blue" style={{ margin: 0 }}>全屏模式</Tag>
          <span style={{ flex: 1 }} />
          {dirty ? (
            <Popconfirm
              title="放弃未保存改动？"
              description="退出全屏不会保存当前改动；可先保存（Ctrl+S）再退出。"
              okText="放弃并退出" okButtonProps={{ danger: true }} cancelText="取消"
              onConfirm={() => setZen(false)}
            >
              <Button size="small" icon={<FullscreenExitOutlined />}>退出全屏（Esc）</Button>
            </Popconfirm>
          ) : (
            <Button size="small" icon={<FullscreenExitOutlined />} onClick={() => setZen(false)}>退出全屏（Esc）</Button>
          )}
        </div>
        <div style={{ flex: 1, minHeight: 0, overflow: 'auto', padding: 12 }}>
          {content}
          {tour}
          {snapModal}
          {cmdModal}
        </div>
      </div>
    );
  }
  return (
    <>
      {content}
      {tour}
      {snapModal}
      {cmdModal}
    </>
  );
}
