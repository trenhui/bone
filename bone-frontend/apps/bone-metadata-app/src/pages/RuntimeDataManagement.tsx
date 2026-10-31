import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Dropdown,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Steps,
  Switch,
  Table,
  Tag,
  Timeline,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined, DownOutlined, EyeOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { formatDate, formatNumber } from '@bone/shared-utils';
import {
  errorMessage,
  metadataEntityApi,
  metadataFieldApi,
  runtimeApprovalApi,
  runtimeRecordApi,
} from '../services/metadataApi';
import type { ApprovalDescribe } from '../services/metadataApi';
import type {
  MetaEntity,
  MetaField,
  RuntimeRecord,
  RuntimeActionDef,
  RuntimeActionView,
  RuntimeActionExecution,
  RuntimeReferenceOption,
  RuntimeChildRow,
} from '../types';
import {
  DELIVERY_MODE,
  ENTITY_STATUS,
  META_DELIVERY_RUNTIME,
  META_ENTITY_PUBLISHED,
  parseEnumValues,
  RUNTIME_READONLY_FIELDS,
} from '../types';
import { Auth, AuthButton, usePermission } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import {
  enumOptionsOf,
  fieldsOutsideContract,
  isEnumField,
  isRenderSupported,
  projectRuntimeColumns,
  tableSizeOf,
} from '../utils/columnProjection';
import { listRecent } from '../utils/recent';
import CommandPalette from '../components/CommandPalette';
import { BoneDynamicForm, type DisplayMode } from '../components/BoneDynamicForm';
import { RuntimeDetailView, detailValueOf } from '../components/RuntimeDetailView';
import ApprovalProgress from '../components/ApprovalProgress';
import PersonalViewBar from '../components/PersonalViewBar';
import ViewerModeSwitch from '../components/ViewerModeSwitch';
import {
  hiddenTechnicalFields,
  isLabelLessEnum,
  recordIdentityOf,
  visibleFieldsFor,
  VIEWER_MODE_KEY,
  type ViewerMode,
} from '../utils/persona';
import { enumToneOf } from '../utils/enumTone';
import ContractScopeCard from '../components/ContractScopeCard';
import { useBoneUiSchema } from '../hooks/useBoneUiSchema';
import type { UiContext } from '@bone/ui-schema';
import { useOverlayBundle } from '../hooks/useOverlayBundle';

const { Text } = Typography;

/**
 * 筛选运算符选项（P1-3）——token 与后端 {@code RuntimePageQuery.FilterOp} 一一对应。
 * eq 保留为默认：存量 q 形状（field:value）与状态步骤下钻零变更。
 * 每加一个运算符 = 后端一条 SQL 分支 + 一组单测 + 前端一个选项，收敛在高价值七元组。
 */
const FILTER_OP_OPTIONS: { value: string; label: string }[] = [
  { value: 'eq', label: '等于' },
  { value: 'ne', label: '不等于' },
  { value: 'gt', label: '大于' },
  { value: 'gte', label: '不小于' },
  { value: 'lt', label: '小于' },
  { value: 'lte', label: '不大于' },
  { value: 'contains', label: '包含' },
  { value: 'in', label: '属于' },
];

/**
 * 数据生命周期步骤条 —— **由实体自身的状态机定义推导，不含任何硬编码业务状态**。
 *
 * 此前此处写死了报销场景的 7 步（模板起盘→发布→清单出现→录入→提交→审批→打款）与
 * `EXPENSE_STATES=['DRAFT','SUBMITTED','APPROVED','PAID']`。本页是**通用** Runtime 数据页，
 * 任何 RUNTIME 已发布实体都会进来 —— 一个「订单」实体也会被告知要「审批」「打款」，
 * 且 `flowDoneOf` 对非报销状态返回 3，流程条恒停在「录入」，是纯装饰甚至误导。
 *
 * 现改为从 `GET /actions`（actionDefs：fromStatus/toStatus/label/sortOrder/escape）推导：
 *   · 建模侧固定 2 步（模型已定义 / 已发布出物理表）—— 这是进入本页的**前置条件**，
 *     由 selectedEntity.status 实测判定，不假设；
 *   · 数据侧 = 状态机真实状态链；无状态机（escape-only 或无动作）时**不渲染**状态链，
 *     只留建模侧 2 步，避免给无流程实体编造流程。
 *
 * 点击数据侧某状态步骤 → 把列表筛选切到该状态（q=status:<code>，后端为精确匹配，
 * 见 RuntimeQuerySupport#buildFilterClause），步骤从「指示器」变成真正的下钻入口。
 */
const MODEL_STEPS = [
  { key: 'model', label: '模型已定义' },
  { key: 'publish', label: '已发布出物理表' },
] as const;

/**
 * 推导状态链：从无入边的初始态出发，沿动作定义顺序跟进。
 * 分叉时取 sortOrder 最小的出边（与行内「主推进动作」同一约定：后端先声明者为主线）。
 * 带环 / 不可达时截断，保证渲染必然终止。
 */
function deriveStateChain(defs: RuntimeActionDef[]): string[] {
  // escape 动作（打款等副作用动作）不进状态链：它们是某一态上的逃逸，不是流程的下一站
  const edges = defs
    .filter((d) => !d.escape && d.fromStatus && d.toStatus)
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0));
  if (edges.length === 0) return [];
  const hasIncoming = new Set(edges.map((e) => e.toStatus!));
  const start =
    edges.find((e) => !hasIncoming.has(e.fromStatus!))?.fromStatus ?? edges[0].fromStatus!;
  const chain: string[] = [];
  let cursor: string | undefined = start;
  while (cursor && !chain.includes(cursor)) {
    chain.push(cursor);
    cursor = edges.find((e) => e.fromStatus === cursor)?.toStatus ?? undefined;
  }
  return chain;
}

/** 状态码 → 中文标签：优先取 status 字段的 ENUM 码表，回落到状态码本身。 */
function makeStatusLabelOf(fields: MetaField[]): (code?: string | null) => string {
  const opts = parseEnumValues(fields.find((f) => f.code === 'status')?.enumValues);
  return (code) => {
    if (!code) return '—';
    return opts.find((o) => o.code === code)?.label ?? code;
  };
}

function recordId(row: RuntimeRecord): string {
  const id = row.id;
  return id != null ? String(id) : '';
}

function formatCell(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** ENUM 列表 Tag 色板（§5.5：MetaList ENUM 列 → Tag 徽标，可选色板）——
 *  仅用于「无语义可判」的普通枚举（差旅/餐饮/甲/乙）做稳定区分；
 *  状态类枚举走 `enumToneOf` 的语义色，不在此轮转。 */
const ENUM_TAG_COLORS = ['blue', 'green', 'orange', 'purple', 'cyan', 'magenta', 'gold', 'geekblue', 'volcano', 'lime'];

/** ENUM 单元格：码值 → Tag 徽标。
 *
 *  取色两段式：① 状态类语义色（同一状态跨实体同色，见 `enumToneOf`）；
 *  ② 无语义可判时按定义序轮转（稳定不闪烁）。未命中码值兜底灰 Tag。 */
function renderEnumCell(value: unknown, field: MetaField): ReactNode {
  if (value == null || value === '') return '—';
  const opts = enumOptionsOf(field);
  const idx = opts.findIndex((o) => o.code === String(value));
  const opt = idx >= 0 ? opts[idx] : null;
  if (!opt) return <Tag>{String(value)}</Tag>;
  const tone = enumToneOf(opt);
  return <Tag color={tone ?? ENUM_TAG_COLORS[idx % ENUM_TAG_COLORS.length]}>{opt.label}</Tag>;
}

export interface RuntimeDataManagementProps {
  /** 嵌入实体详情「数据」Tab 时传入实体 id；为空则走独立全局「业务数据」路由（带对象选择器） */
  embeddedEntityId?: string | null;
}

/** 412 并发冲突的字段级差异行（供「查看差异」展示：只含真正不同的字段） */
interface ConflictDiffRow {
  code: string;
  name: string;
  mine: unknown;
  theirs: unknown;
}

const RuntimeDataManagement: React.FC<RuntimeDataManagementProps> = ({ embeddedEntityId = null }) => {
  const { id: pathId } = useParams<{ id?: string }>();
  const resolvedId = embeddedEntityId ?? pathId ?? null;
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const location = useLocation();
  /** 权限快照（与 AuthButton 同源）：用于「更多」下拉里逃逸动作的置灰判定 */
  const { has, roles } = usePermission();
  /** 数据 Tab 空态 CTA：由 /…/data 回溯到实体详情并定位「结构」Tab（B1） */
  const structurePath = `${location.pathname.replace(/\/data\/?$/, '')}?tab=structure`;
  /** 「交付口径对照」的去团队契约入口：定位「界面设计器」Tab（契约列的唯一编辑器） */
  const designerPath = `${location.pathname.replace(/\/data\/?$/, '')}?tab=uischema`;

  const [form] = Form.useForm();
  const [actionForm] = Form.useForm();
  const [entities, setEntities] = useState<MetaEntity[]>([]);
  const [entityCode, setEntityCode] = useState<string | null>(null);
  const [detailEntity, setDetailEntity] = useState<MetaEntity | null>(null);
  const [fields, setFields] = useState<MetaField[]>([]);
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState<RuntimeRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<RuntimeRecord | null>(null);
  const [editingEtag, setEditingEtag] = useState<string | undefined>(undefined);
  const [conflict, setConflict] = useState(false);
  /** 412 冲突时线上值 vs 本次提交的差异（W0-4「查看差异」，避免只说「被改了」却不说是哪几项） */
  const [conflictDiff, setConflictDiff] = useState<ConflictDiffRow[]>([]);
  const [diffOpen, setDiffOpen] = useState(false);
  /**
   * P2-⑤：多条件过滤（AND）。q 契约 = {@code field:value}（等值）或 {@code field:op:value}（运算符），
   * 多条件用 {@code ;} 分隔（后端 RuntimePageQuery.parseFilters；单条件无分隔符时与旧 field:value 完全兼容）。
   *
   * <p>双状态：{@link #conditions} 是编辑中的草稿（输入框直绑，不发请求）；
   * {@link #appliedConditions} 是生效值，「查询」按钮才把前者提交为后者。
   * 为什么不防抖直发生效：多行条件表单的业界标准形态是显式 Apply（Ant Design Pro
   * 筛选表单 / GitHub 高级搜索）——边打边查会让「填到一半的条件」产生一次错误口径的
   * 结果集，用户以为「没有匹配数据」实则条件还没填完。
   *
   * <p>P1-3 运算符：eq 走存量形状（{@code field:value}，步骤条下钻与旧请求零变更）；
   * 其余运算符拼 {@code field:op:value}（后端 {@code RuntimePageQuery.FilterOp}）。 含冒号的值（URL/备注）零歧义——后端按「第二段是否为已知运算符」回退等值。
   */
  const [conditions, setConditions] = useState<{ field?: string; op: string; value: string }[]>(
    [],
  );
  const [appliedConditions, setAppliedConditions] = useState<
    { field?: string; op: string; value: string }[]
  >([]);
  /** 生效筛选的 q 串（空数组 ⇒ undefined，不带 q 参数） */
  const appliedQ = useMemo(() => {
    const q = appliedConditions
      .filter((c) => c.field && c.value.trim())
      .map((c) => (c.op === 'eq' ? `${c.field}:${c.value.trim()}` : `${c.field}:${c.op}:${c.value.trim()}`))
      .join(';');
    return q || undefined;
  }, [appliedConditions]);
  /** sort 契约：逗号分隔列名，`-` 前缀降序（RuntimePageQuery.parseSort） */
  const [sortField, setSortField] = useState<string | undefined>(undefined);
  const [sortDesc, setSortDesc] = useState(false);
  /**
   * 流程步骤下钻产生的筛选说明（非空时在工具栏上方显示「已按 X 筛选 · 清除」）。
   * 后端 page 接口不回显生效筛选，前端若不留痕，用户点了步骤条却不知道列表被改了口径 ——
   * 表现为「我明明没筛选，怎么只有 3 条」。
   */
  const [selectedFilterNote, setSelectedFilterNote] = useState<string | null>(null);

  /** 运行时状态机动作定义（按实体加载一次，按行 status 渲染可用按钮） */
  const [actionDefs, setActionDefs] = useState<RuntimeActionDef[]>([]);
  /**
   * 记录级状态链（元数据驱动，见 deriveStateChain）+ 中文标签解析。
   * 列表高亮、抽屉步骤条、动作回显文案三处共用同一份真源，避免各算一遍导致漂移。
   * ⚠ 声明必须在 actionDefs/fields 之后 —— useMemo 回调在渲染期求值，
   *   早于声明引用会命中 TDZ（TS2448）。
   */
  const stateChain = useMemo(() => deriveStateChain(actionDefs), [actionDefs]);
  const statusLabelOf = useMemo(() => makeStatusLabelOf(fields), [fields]);
  /** 抽屉内记录的状态所处链位（-1 = 该态不在链上，如终态或 escape 后的态） */
  const [editingStateIdx, setEditingStateIdx] = useState(-1);
  /**
   * page 级动作发现（Runtime §4.3.1 唯一形态）：`meta.allowedActionsByStatus[status]`。
   * 优先消费它；后端未返回 meta（旧版本 / 无动作实体）时降级到 listActions 全量拉取。
   */
  const [actionsByStatus, setActionsByStatus] = useState<
    Record<string, RuntimeActionView[]> | null
  >(null);
  /** 回收站视图：true 时只展示已归档（deleted=1）记录，并提供「恢复」 */
  const [trashMode, setTrashMode] = useState(false);
  /** 动作执行弹窗：当前动作 + 触发记录 */
  const [actionModal, setActionModal] = useState<{
    open: boolean;
    def: RuntimeActionView | null;
    record: RuntimeRecord | null;
  }>({ open: false, def: null, record: null });

  /**
   * 记录详情抽屉（P2-② 收口）：全字段只读视图 + 动作执行时间线。
   * 时间线数据源 meta_runtime_action_execution（幂等去重与审计同表），
   * P0-2 半事务时序保证任何一次执行（含 PENDING/孤儿）都留有痕迹。
   */
  const [detailDrawer, setDetailDrawer] = useState<{
    open: boolean;
    record: RuntimeRecord | null;
  }>({ open: false, record: null });
  const [executions, setExecutions] = useState<RuntimeActionExecution[]>([]);
  const [executionsLoading, setExecutionsLoading] = useState(false);
  /**
   * 审批进度（引擎真源）：`GET /approval/records/{entity}/{record}` 的实例 + 待办 + 轨迹投影。
   * 只在详情抽屉按需拉取（列表逐行调用即成 N+1）；绝大多数记录 started=false，组件对空实例不占位。
   */
  const [approvalInfo, setApprovalInfo] = useState<ApprovalDescribe | null>(null);
  const [approvalLoading, setApprovalLoading] = useState(false);

  /** 子表（CHILD_TABLE）编辑弹窗状态：当前主记录 + 声明字段 */
  const [childModal, setChildModal] = useState<{
    open: boolean;
    record: RuntimeRecord | null;
    field: MetaField | null;
  }>({ open: false, record: null, field: null });
  /** 明细编辑中的行（键驱动渲染，不依赖子实体 schema） */
  const [childRows, setChildRows] = useState<RuntimeChildRow[]>([]);
  /**
   * 新增明细行的行键序列：antd 5 已废弃 `rowKey` 的第二个 index 参数（控制台告警
   * "index parameter of rowKey function is deprecated"），而新增行尚无后端 id，
   * 故用自增序列给每行一个稳定的临时行键（保存前剔除，不进后端载荷）。
   */
  const childRowSeq = useRef(0);
  /** 明细行编辑小 Form 状态（-1 = 新增行） */
  const [childRowEditor, setChildRowEditor] = useState<{
    open: boolean;
    idx: number;
    row: RuntimeChildRow | null;
  }>({ open: false, idx: -1, row: null });
  /** REFERENCE 下拉选项缓存（field → options，首次打开/搜索时刷新） */
  const [referenceCache, setReferenceCache] = useState<
    Record<string, RuntimeReferenceOption[]>
  >({});

  /** 当前选中实体：嵌入模式来自详情接口，独立模式来自列表选择 */
  const selectedEntity = useMemo(
    () => detailEntity ?? entities.find((e) => e.code === entityCode) ?? null,
    [detailEntity, entities, entityCode],
  );

  /**
   * 协议表单开关（P0-1 · 技术方案 §6.1/§6.8）：
   * 实体配了团队 schema 且版本校验过（绿 ok/黄 warn-unknown-minor）→ 录入弹窗走 BoneDynamicForm；
   * 无 schema/红 error → 老表单零变化。
   *
   * ctx（P0-4 运行时身份）：roles 取 Shell 下发的 `GET /iam/me/roles`（usePermission 订阅
   * permissions:change，切角色/租户即时刷新）——此前恒空对象 ⇒ resolveVariant 永不命中
   * 带角色条件的变体（「核赔工作台」线上不生效），身份模拟只在预览里看得见。
   * useMemo 稳定引用：ctx 每渲染换新会让 useBoneUiSchema 的 useMemo 链条全失效。
   */
  const uiCtx = useMemo<UiContext>(() => ({ roles }), [roles]);
  const uiSchemaPipe = useBoneUiSchema(selectedEntity?.id != null ? String(selectedEntity.id) : null, uiCtx);
  const useSchemaForm =
    (uiSchemaPipe.status === 'ok' || uiSchemaPipe.status === 'warn-unknown-minor') &&
    !!uiSchemaPipe.schema?.views.form?.sections?.length;

  /**
   * displayModes 接线（Tier3 · 协议 §3.1 顶层三态门控运行时表单弹窗）：
   * - 缺 'create' ⇒ 不渲染「新建记录」入口——协议不支持录入，不给点了才报错的死按钮；
   * - 缺 'edit'   ⇒ 编辑弹窗回落只读（displayMode='view'，BoneDynamicForm 只读态已存在），
   *   行级「编辑」入口保留（点了进只读弹窗，语义是"可看不可改"而非消失）；
   * - 'view'      ⇒ 表单弹窗的只读态本身；详情抽屉是独立视图面（views.detail 驱动，P0-3），不在此门控。
   * 缺省 ['create','edit','view']（schema.json default）⇒ 存量 schema 无此字段时全放开，行为零变化。
   * 口径与弹窗渲染同源（uiSchemaPipe.schema）：门控必须与被渲染的表单是同一份 schema。
   */
  const schemaDisplayModes = useMemo<string[]>(() => {
    const m = uiSchemaPipe.schema?.displayModes;
    return Array.isArray(m) && m.length > 0 ? m : ['create', 'edit', 'view'];
  }, [uiSchemaPipe.schema]);
  const canCreate = schemaDisplayModes.includes('create');
  const canEdit = schemaDisplayModes.includes('edit');
  /** 弹窗表单有效显示模式：被门掉的模式回落 'view'（只读），与标题「· 只读」后缀同源 */
  const formMode: DisplayMode = editing ? (canEdit ? 'edit' : 'view') : canCreate ? 'create' : 'view';

  /**
   * 详情视图（P0-3）：发布快照 executionSchema 的 views.detail，已随 ctx 做变体裁决
   * （reviewer 看到的是变体里的详情布局）。配了 → 抽屉按协议投影（页头条 + 分区）；
   * 没配（存量实体绝大多数）→ 回落全字段平铺 Descriptions，行为零变化。
   */
  const execDetail = uiSchemaPipe.executionSchema?.views.detail;

  /**
   * 最近操作实体（跨实体运营台快捷入口）：复用 localStorage 的「最近编辑」记录，
   * 仅展示当前 RUNTIME 已发布候选中命中的实体，避免点开无物理表的实体。
   * 让高频跨实体巡检的用户免重复在数百个候选里搜索（对标飞书/Notion「最近打开」）。
   */
  const recentForRuntime = useMemo(() => {
    if (resolvedId) return [];
    const codes = new Set(entities.map((e) => e.code));
    return listRecent()
      .filter((r) => codes.has(r.code))
      .slice(0, 5);
  }, [entities, resolvedId]);

  const writableFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

  /**
   * 观看者模式（消费面 ⇄ 实施面）—— 本机偏好，默认消费面。
   *
   * <p>为什么默认是消费面：本页第一受众是"用它干活的人"（报销填单/审批），实施者是少数
   * 且清楚自己要点什么；默认展示全部会让多数人替少数人的排障需求买单。偏好粒度刻意取
   * **全局**而非按实体——模式是"我现在来干活还是来排查"的身份，不是某实体的属性
   * （Salesforce Setup / Odoo 开发者模式同理，切一次全局生效）。
   */
  const [viewerMode, setViewerMode] = useState<ViewerMode>(() => {
    try {
      const raw = localStorage.getItem(VIEWER_MODE_KEY);
      if (raw === 'consume' || raw === 'implement') return raw;
    } catch {
      /* localStorage 不可用（隐私模式）按默认消费面处理 */
    }
    return 'consume';
  });
  const implMode = viewerMode === 'implement';
  const changeViewerMode = useCallback((mode: ViewerMode) => {
    setViewerMode(mode);
    try {
      localStorage.setItem(VIEWER_MODE_KEY, mode);
    } catch {
      /* 写不进 localStorage 不影响本次会话内的切换 */
    }
  }, []);

  /**
   * 消费面可读字段：剔除**内部代理键**（如 `approver_id` 这类裸内部外键）。
   *
   * <p>依据是 OWASP 的"信息最小化"与 DAMA 的"代理键不是业务键"——把 Surrogate key
   * 暴露给业务用户，既不增加他的理解、又让界面承担"这是什么数字"的解释成本。
   * 实施模式下全量可见（排障需要），且永不隐藏必填项（避免把显示分层变成写缺列事故）。
   */
  const readFields = useMemo(
    () => visibleFieldsFor(writableFields, viewerMode),
    [writableFields, viewerMode],
  );

  /** 实施模式自查用：被消费面隐藏的代理键字段（点名，而不是只报个数） */
  const hiddenKeyFields = useMemo(
    () => hiddenTechnicalFields(writableFields, 'consume'),
    [writableFields],
  );

  /** 实施模式自查用：只有 code 没有中文标签的枚举字段（裸码的根因） */
  const labelLessEnums = useMemo(() => readFields.filter(isLabelLessEnum), [readFields]);

  /**
   * 表单可编辑字段 = 可写字段排除 `status`。
   * status 由状态机（动作 SPI）驱动，禁止在新建/编辑表单里直接编辑，
   * 否则可绕过状态机边（DRAFT→SUBMITTED→…）直达任一状态，是 G14 漏洞的 UI 回归点。
   * 表格展示列仍使用含 status 的 writableFields（见 projectedFields），保证用户可见当前状态。
   * CHILD_TABLE 声明字段不进主表单（由「明细」弹窗整组替换，G10）——
   * 否则 Form.Item 会把 items 当普通字段提交，产生无物理列的写路径错误。
   */
  const formFields = useMemo(
    () =>
      readFields.filter(
        (f) => f.code !== 'status' && (f.type || '').toUpperCase() !== 'CHILD_TABLE',
      ),
    [readFields],
  );

  /**
   * 运行时列表列投影（P1 · 真源收敛）：
   * 契约（执行期 published ui-schema views.list，个人 overlay 叠加显隐/密度/改名）。
   *
   * <p>为什么从 {@code projectColumns(fields, overlay)} 升级：此前设计器里配的
   * 列表列（列宽/固定列/列序/列标题）**无任何运行时消费方**——写入了 published
   * 快照却没人读，运行时表格仍按 overlay 投影，"配了不生效"。此处把执行期 schema
   * 的 views.list 接为列真源；未配契约列的实体整体回落旧行为（零回归）。
   *
   * <p>个人覆盖的唯一编辑器就在本页工具栏（PersonalViewBar → 列设置 / 我的视图）：
   * 覆盖是运行期状态，编辑入口跟着生命周期走；建模台「界面设计器」只核对契约口径
   * 同一投影（双轨收敛，两个 surface 共用 useOverlayBundle 一份状态实现）。
   */
  const contractListView = uiSchemaPipe.executionSchema?.views.list ?? null;
  const overlay = useOverlayBundle(entityCode, writableFields, contractListView);
  const projectedColumns = useMemo(
    // 注：列集合按「当前模式可读字段」投影（`readFields`）而非全量可写字段——消费面不
    // 因内部代理键多出一列空或满屏数字的列；契约/个人覆盖两层间的判定语义不变。
    () => projectRuntimeColumns(readFields, overlay.activeOv, contractListView),
    [readFields, overlay.activeOv, contractListView],
  );
  /** 字段视图（列查询 fieldsParam / 筛选选项 / 列渲染共用）——由投影列去契约元数据得到 */
  const projectedFields = useMemo(() => projectedColumns.map((c) => c.field), [projectedColumns]);

  /**
   * 视图 → 页面筛选/排序态的单向同步（业界 saved view 语义：Airtable/Notion/Salesforce
   * 的视图 = 列 + 筛选 + 排序 的完整快照）。
   *
   * <p><b>为什么必须同步而不是各管各的</b>：筛选/排序原先只活在页面 useState 里，
   * 切个人视图时被静默清空——用户配好「我的待审：只看待我审批 + 按金额倒序」，
   * 切去别的视图再切回来条件消失，视图就不可复用了。
   *
   * <p><b>单向的依据</b>：只做「视图变了 ⇒ 页面跟随」。
   * 反向（用户改筛选 ⇒ 写回当前视图）刻意不做——那会让每次筛选都产生一次 blob 写入，
   * 且与"筛选是临时的探索、视图是沉淀的资产"这一业界分工相悖。沉淀动作交由
   * 工具栏的「保存到视图」显式触发（见下方 saveCurrentToView）。
   *
   * <p>依赖 activeView.id（视图身份）而非整个 view 对象：同一视图内用户自己调筛选时，
   * 不该被回滚覆盖——否则刚点一下筛选就跳回去。
   */
  const activeViewId = overlay.activeView?.id;
  /** 首次挂载跳过视图回灌（ref 而非 state：渲染期同步的旗标，不引额外渲染） */
  const firstViewSyncRef = useRef(false);
  useEffect(() => {
    const v = overlay.activeView;
    if (!v) return;
    // ① 跳过首次挂载：首屏的用户筛选不该被"默认视图（无 filters）"清空——
    //    实测症状：新建视图后施加筛选，行数变 0 但 appliedConditions 被抹掉，保存存成空。
    //    首次挂载时页面本就是干净口径，无需回灌。
    if (!firstViewSyncRef.current) {
      firstViewSyncRef.current = true;
      return;
    }
    setAppliedConditions(
      (v.filters ?? []).map((f) => ({ field: f.field, op: f.op, value: f.value })),
    );
    setSortField(v.sortField);
    setSortDesc(!!v.sortDesc);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeViewId]);

  /**
   * 「保存当前筛选/排序到视图」——把临时的探索动作沉淀为可复用视图资产（业界惯例）。
   * 只写筛选/排序，不碰列显隐/密度（那些由列设置直接编辑生效）。
   */
  const saveCurrentFilterToView = useCallback(() => {
    // 必须走**一次性**写口：分别调 setViewFilters + setViewSort 是两次读-改-写，
    // 第二次可能基于旧 bundle ⇒ 后写覆盖先写，filters 静默丢失（实测踩过）。
    if (overlay.setViewQuery) {
      overlay.setViewQuery({ filters: appliedConditions, sortField, sortDesc });
    } else {
      overlay.setViewFilters(appliedConditions);
      overlay.setViewSort(sortField, sortDesc);
    }
  }, [overlay, appliedConditions, sortField, sortDesc]);

  /**
   * 筛选字段下拉真源 = 全部可写字段（排除 CHILD_TABLE）。
   *
   * <p>为什么不复用 projectedFields（当前可见列）：列可见性是**展示**维度
   * （契约/个人双层显隐），筛选是**查询**维度——后端 q 的接受域本就是
   * 「全部已建模字段」（{@code RuntimeQuerySupport.allowedDataColumns}，仅排除
   * 无物理列的 CHILD_TABLE，实测 {@code q=items:eq:x} 精确 400）。用可见列当真源
   * 会让「隐藏一列 ⇒ 失去对它的筛选能力」：企业场景按内部字段/已隐藏列过滤直接失能，
   * 且用户看不到因果（下拉里就是没有该项）。
   */
  const filterableFields = useMemo(
    () => writableFields.filter((f) => (f.type || '').toUpperCase() !== 'CHILD_TABLE'),
    [writableFields],
  );

  /**
   * strict 契约集合下「已建模但未列入契约」的字段——运行时列表不显示它们。
   * 同源提示（fieldsOutsideContract）在本页「交付口径对照」卡片，运行时视角必须可观测：
   * 交付工程师最常见困惑「我建的字段呢」就发生在这里（建了字段、发布了，列表里没有）。
   */
  const outsideContractFields = useMemo(
    () => (contractListView ? fieldsOutsideContract(writableFields, contractListView) : []),
    [writableFields, contractListView],
  );

  /**
   * 独立全局路由（/runtime、无 id）：加载 RUNTIME 已发布实体供选择器（W0-2：size 50→500，避免第 51 个后不可选）
   *
   * ⚠ 依赖必须是**原始字符串**而非 `searchParams` 对象：
   *   useSearchParams() 每次渲染都返回新对象引用 ⇒ 把它放进依赖会让本 effect
   *   在每次 setEntities 触发的重渲染后再次执行 ⇒ 请求 → setState → 重渲染 的无限循环。
   *   实测症状：/api/v1/metadata/entities 被反复请求，且页面永远无法稳定，
   *   下游依赖 selectedEntity 的 UI（生命周期步骤条）拿不到稳定值而不渲染。
   */
  const entityFromQuery = searchParams.get('entity');
  useEffect(() => {
    if (resolvedId) return;
    metadataEntityApi
      .page({ page: 1, size: 500, deliveryMode: META_DELIVERY_RUNTIME, status: META_ENTITY_PUBLISHED })
      .then((res) => {
        if (res.code !== 200) {
          message.error(errorMessage(res));
          return;
        }
        const runtimePublished = normalizeRuntimePublished(res.data.records);
        setEntities(runtimePublished);
        // W0-1 / PRD D12：无 :id 且无 ?entity= 时**不替用户决策**——保持空态由用户显式选择。
        // 旧实现会 setEntityCode(runtimePublished[0].code)，即「/runtime 静默选中第一个实体」：
        // 用户以为在看自己选的实体，实际看到的是列表首项的数据（静默串实体）。
        if (entityFromQuery && runtimePublished.some((e) => e.code === entityFromQuery)) {
          setEntityCode(entityFromQuery);
        }
      });
    // entityCode 仅用于首次为空时初始化，不应作为依赖
  }, [entityFromQuery, resolvedId]);

  // 嵌入模式：按实体 id 拉详情得到 code（W0-1：/…/:id/data 绑定 :id，不再静默看错实体）
  useEffect(() => {
    if (!resolvedId) return;
    setDetailEntity(null);
    metadataEntityApi.detail(resolvedId).then((res) => {
      if (res.code === 200) {
        setDetailEntity(res.data);
        setEntityCode(res.data.code);
      } else {
        message.error(errorMessage(res));
      }
    });
  }, [resolvedId]);

  // 字段随选中实体变化加载
  useEffect(() => {
    const eid = selectedEntity?.id;
    if (!eid) {
      setFields([]);
      return;
    }
    metadataFieldApi.page(eid, { page: 1, size: 500 }).then((res) => {
      if (res.code === 200) setFields(normalizePageRecords(res.data));
    });
  }, [selectedEntity?.id]);

  // 状态机动作定义随选中实体加载一次（前端按行 status 过滤渲染按钮）
  useEffect(() => {
    if (!entityCode) {
      setActionDefs([]);
      return;
    }
    runtimeRecordApi.listActions(entityCode).then((res) => {
      if (res.code === 200) setActionDefs(res.data ?? []);
    });
  }, [entityCode]);

  /**
   * 请求参数由列投影产出（W0a2 + §0.6.3）：
   * `fields` = 当前可见列的 code ⇒ 界面 Tab 关掉的列不会出现在响应里，
   * 与「预览列 ≡ 数据列」同源，杜绝两边各算一遍导致的漂移。
   *
   * <p>CHILD_TABLE 列除外：该类型无物理列（明细子表独立存储），code 进 fields 会让
   * 后端 SELECT 一个不存在的列 ⇒ 500（实测 t_expense_reimbursement_e2e_* 报销单）。
   * 明细的交互是行内「明细」入口（按行 listChildren 拉子行），不占数据列。
   */
  const load = useCallback(async () => {
    if (!entityCode) return;
    setLoading(true);
    try {
      const listableFields = projectedFields.filter(
        (f) => (f.type || '').toUpperCase() !== 'CHILD_TABLE',
      );
      const fieldsParam = listableFields.length
        ? listableFields.map((f) => f.code).join(',')
        : undefined;
      const query = {
        page,
        size: pageSize,
        fields: fieldsParam,
        // q 契约：field:value 或 field:op:value，多条件 AND（后端 RuntimePageQuery.parseFilters）
        q: appliedQ,
        // sort 契约：`-` 前缀降序（后端 RuntimePageQuery.parseSort）
        sort: sortField ? `${sortDesc ? '-' : ''}${sortField}` : undefined,
      };
      const res = trashMode
        ? await runtimeRecordApi.trash(entityCode, query)
        : await runtimeRecordApi.page(entityCode, query);
      if (res.code === 200) {
        const pageData = normalizePage(res.data);
        setData(pageData.records);
        setTotal(pageData.total);
        // 动作发现：随列表一次返回，按 status 分组（天然防 N+1；详情页复用同一份）
        setActionsByStatus(res.data.meta?.allowedActionsByStatus ?? null);
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('加载业务数据失败，请确认实体已发布且物理表存在');
    } finally {
      setLoading(false);
    }
  }, [
    entityCode,
    page,
    pageSize,
    projectedFields,
    appliedQ,
    sortField,
    sortDesc,
    trashMode,
  ]);

  useEffect(() => {
    load();
  }, [load]);

  /**
   * 切换实体时清空全部筛选/排序/分页/视图态。
   *
   * 不重置的后果是真实可复现的脏读：按「单号」筛完 A 实体，切到 B 实体后
   * q 仍是 A 的字段与值，而 B 里没有同名列 —— 后端直接 400「q 过滤字段未建模」，
   * 页面只剩一条红色报错；即便同名字段能查，列表也是空的，用户会以为 B 没数据。
   * 业界通例（同 Ant Design Pro 切换数据源）：换源即换查询条件。
   */
  useEffect(() => {
    setConditions([]);
    setAppliedConditions([]);
    setSortField(undefined);
    setSortDesc(false);
    setTrashMode(false);
    setPage(1);
    setPageSize(20);
    setData([]);
    setTotal(0);
    setSelectedFilterNote(null);
    // 首挂载时这几个值本就是初始值，setState 为同值 no-op，不产生额外请求
  }, [entityCode]);

  const openCreate = () => {
    setEditing(null);
    setEditingEtag(undefined);
    setConflict(false);
    setConflictDiff([]);
    form.resetFields();
    // 新建即初始态（链上第 0 站，若该实体有状态机）
    setEditingStateIdx(0);
    setChildRows([]);
    // G10：预取全部 REFERENCE 字段选项（下拉回显/首选项）
    writableFields.forEach((f) => {
      if ((f.type || '').toUpperCase() === 'REFERENCE') loadReferenceOptions(f);
    });
    setModalOpen(true);
  };

  const openEdit = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    setConflict(false);
    setConflictDiff([]);
    const rid = recordId(record);
    if (!rid) {
      message.error('记录缺少主键 id');
      return;
    }
    // 拉取最新记录以捕获 ETag（W0-4：乐观并发，编辑提交带 If-Match）
    try {
      const res = await runtimeRecordApi.get(entityCode, rid);
      if (res.code !== 200) {
        message.error(errorMessage(res));
        return;
      }
      const latest = res.data;
      setEditing(latest);
      setEditingEtag(res.etag);
      const values: Record<string, unknown> = {};
      formFields.forEach((f) => {
        const v = latest[f.code];
        // 后端字段类型统一小写（date/datetime）；比较必须忽略大小写，否则 date 字段会原样
        // 以字符串塞进表单，antd DatePicker 收到非 dayjs 值即抛 `isValid is not a function` 崩溃。
        const ftype = (f.type || '').toUpperCase();
        if ((ftype === 'DATE' || ftype === 'DATETIME') && v != null) {
          const d = dayjs(String(v));
          values[f.code] = d.isValid() ? d : undefined;
        } else {
          values[f.code] = v;
        }
      });
      form.setFieldsValue(values);
      // G10：预取全部 REFERENCE 字段选项（含当前值回显映射 label）
      formFields.forEach((f) => {
        if ((f.type || '').toUpperCase() === 'REFERENCE') loadReferenceOptions(f);
      });
      // 打开已有记录：状态步骤条随其状态在**真实状态链**上的位置推进
      setEditingStateIdx(stateChain.indexOf(String((latest as Record<string, unknown>).status ?? '')));
      setModalOpen(true);
    } catch {
      message.error('加载记录失败');
    }
  };

  /**
   * 归档（软删）。
   *
   * 文案必须是「归档」而非「删除」：物理表无 deleted 列时后端会降级为**物理 DELETE**，
   * 且通用通道没有「彻底删除」语义。叫「删除」会让用户以为这是可逆的常规操作。
   */
  const handleDelete = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    const id = recordId(record);
    if (!id) {
      message.error('记录缺少主键 id');
      return;
    }
    try {
      // 先取最新 ETag，避免基于陈旧快照归档（判据 R13：If-Match 可选但生效）
      let etag: string | undefined;
      try {
        const latest = await runtimeRecordApi.get(entityCode, id);
        etag = latest.etag;
      } catch {
        /* 取不到 ETag 时按无并发保护处理 */
      }
      const res = await runtimeRecordApi.delete(entityCode, id, etag);
      if (res.code === 200) {
        message.success('已归档，可在「回收站」中恢复');
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('归档失败');
    }
  };

  /** 恢复归档记录（回收站 → 在用）。 */
  const handleRestore = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    const id = recordId(record);
    if (!id) {
      message.error('记录缺少主键 id');
      return;
    }
    try {
      const res = await runtimeRecordApi.restore(entityCode, id);
      if (res.code === 200) {
        message.success('已恢复');
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('恢复失败');
    }
  };

  /** 把表单值按字段类型规整（DATE/DATETIME → 本地时区字符串；dayjs 跨实例识别失败时仍按对象能力归一化） */
  const buildBody = (values: Record<string, unknown>): RuntimeRecord => {
    const body: RuntimeRecord = {};
    formFields.forEach((f) => {
      const v = values[f.code];
      const type = (f.type || 'STRING').toUpperCase();
      if (type === 'ATTACHMENT') {
        // 附件清单：Form 值（数组对象）→ JSON 数组串（物理列 TEXT）
        if (v != null && Array.isArray(v)) {
          const cleaned = v
            .filter((a) => a && (String(a.name ?? '').trim() || String(a.url ?? '').trim()))
            .map((a) => ({ name: String(a.name ?? ''), url: String(a.url ?? ''), size: a.size ?? null }));
          body[f.code] = cleaned.length ? JSON.stringify(cleaned) : undefined;
        }
        return;
      }
      if (type === 'DATE' || type === 'DATETIME') {
        if (v == null || v === '') return; // 空值不写，交由后端必填校验
        const fmt = type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss';
        const anyV = v as unknown as { format?: (t: string) => string; $d?: Date };
        let s: string;
        if (anyV && typeof anyV.format === 'function') {
          // dayjs 对象（任意实例，含 antd 内部实例）：直接按其本地时区格式化
          s = anyV.format(fmt);
        } else if (v instanceof Date) {
          s = dayjs(v).format(fmt);
        } else if (typeof v === 'string') {
          // 偶发被序列化为 UTC ISO（dayjs 跨实例 isDayjs 误判）→ 按本地时区还原，避免跨时区差一天
          s = /^\d{4}-\d{2}-\d{2}T/.test(v) ? dayjs(v).format(fmt) : v;
        } else {
          s = String(v);
        }
        body[f.code] = s;
      } else {
        // 空值不写，交由后端必填/类型校验（与 DATE 分支同口径）。
        // ★ 为什么不能直接下发 ''：表单初始化把所有非 BOOLEAN 字段置为 ''
        //   （BoneDynamicForm: init[f.code] = record?.[f.code] ?? ''），
        //   而 COMPUTED/STAT 等展示型字段没有专属分支、会落到这里 ⇒ '' 原样进 body。
        //   后端 RuntimeWriteValidator#validateType 对 DECIMAL/INTEGER 列见到 '' 即报
        //   「应为数值类型」——一个「没填」被报成「填错」，用户穷举也过不了；
        //   且 BOOLEAN 的合法值 false 是布尔而非 ''，不受此守卫影响，0 亦不受影响。
        if (v == null || v === '') return;
        body[f.code] = v as unknown;
      }
    });
    return body;
  };

  /**
   * REFERENCE 下拉选项：按需加载（field 首次渲染 / 搜索时）。
   * 返回后写入缓存；编辑回显时选项含全部候选，选中 id 能直接映射到 label。
   */
  const loadReferenceOptions = useCallback(async (field: MetaField, q?: string) => {
    if (!entityCode || (field.type || '').toUpperCase() !== 'REFERENCE') return;
    try {
      const res = await runtimeRecordApi.lookupOptions(entityCode, field.code, q);
      if (res.code === 200) {
        setReferenceCache((prev) => ({ ...prev, [field.code]: res.data ?? [] }));
      }
    } catch {
      /* 下拉加载失败不阻塞主流程，Select 保持空态可手动重试 */
    }
  }, [entityCode]);

  /** 打开明细（CHILD_TABLE）编辑弹窗：拉现有明细行（键驱动渲染，不依赖子实体 schema 端点） */
  const openChildren = async (record: RuntimeRecord, field: MetaField) => {
    const rid = recordId(record);
    if (!rid) return;
    const spec = childSpecOf(field);
    if (!spec) {
      message.error('明细声明缺少 childEntityCode');
      return;
    }
    setChildModal({ open: true, record, field });
    try {
      const res = await runtimeRecordApi.listChildren(entityCode!, rid, spec.childEntityCode);
      if (res.code === 200) {
        // REFERENCE 行内翻译值（code__label）仅供回显，保存前需剔除（后端 filterChildColumns 也会过滤）
        setChildRows((res.data ?? []).map((r) => stripLabelKeys(r)));
      } else {
        message.error(errorMessage(res));
        setChildRows([]);
      }
    } catch {
      message.error('加载明细失败');
      setChildRows([]);
    }
  };

  /** 明细合计（实时联动 C3）：对 amount 列（或唯一数值列）求和，供弹窗底栏展示 */
  const childSumOf = (rows: RuntimeChildRow[]): number =>
    rows.reduce((sum, r) => {
      const raw = r.amount ?? (Object.entries(r).find(([, v]) => typeof v === 'number')?.[1] as number | undefined);
      const n = typeof raw === 'number' ? raw : Number(raw);
      return Number.isFinite(n) ? sum + n : sum;
    }, 0);

  /**
 * C3 交互细节：保存明细后，头部金额（amount）由后端 ReimbursementChildAmountAggregator 事务内回填，
 * 前端提示已联动 —— 用户不必再手改头表金额（业界标准：头表=明细合计，服务端计算为准）。
 */
  /** 保存明细：整组替换（先删后插同一事务），空数组即清空 */
  const handleChildrenSave = async () => {
    const { record, field } = childModal;
    if (!record || !field || !entityCode) return;
    const rid = recordId(record);
    if (!rid) return;
    const spec = childSpecOf(field);
    if (!spec) return;
    try {
      // 剔除新增行的前端临时行键 __rowKey：它是 UI 记账（antd 行键），不是子实体业务列，
      // 进载荷会被当成未知字段（后端 filterChildColumns 虽会过滤，但前端先清更干净）。
      const payload = childRows.map(stripChildRowKey);
      const res = await runtimeRecordApi.replaceChildren(
        entityCode,
        rid,
        spec.childEntityCode,
        payload,
      );
      if (res.code === 200) {
        message.success(`明细已保存（${res.data?.count ?? childRows.length} 行），头表金额已按合计自动回填`);
        setChildModal({ open: false, record: null, field: null });
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('明细保存失败');
    }
  };

  /** 打开明细行编辑小 Form（idx=-1 为新增） */
  const openChildRowEditor = (idx: number, row: RuntimeChildRow | null) => {
    setChildRowEditor({ open: true, idx, row });
  };

  /** 打开动作执行弹窗（按动作声明的 params 动态渲染表单） */
  const openAction = (record: RuntimeRecord, def: RuntimeActionView) => {
    setActionModal({ open: true, def, record });
    actionForm.resetFields();
  };

  /**
   * 打开记录详情抽屉：全字段只读 + 动作执行时间线（P2-②）。
   * 时间线单独拉取（不在 page 里带）：多数用户不进详情，避免每次列表请求都付全量审计行的带宽。
   *
   * 全字段按需拉取：列表行数据是列投影（只含契约 columns），直接用会让「未进列表投影的字段」
   * 在详情里恒显示「—」——界面设计师没配列的字段（如 异常描述/巡检工时）就再也看不到值。
   * 与 openEdit 同源：先按 id 拉全量记录，失败才兜底用行数据（降级不空白）。
   */
  const openDetail = async (record: RuntimeRecord) => {
    if (!entityCode) return;
    const rid = recordId(record);
    let full: RuntimeRecord = record;
    if (rid) {
      try {
        const res = await runtimeRecordApi.get(entityCode, rid);
        if (res.code === 200 && res.data) full = res.data;
      } catch { /* 拉取失败用行数据兜底，不阻断详情打开 */ }
    }
    setDetailDrawer({ open: true, record: full });
    // 审批进度与动作时间线并行拉取：两者互不依赖，串行会让抽屉第一屏多等一个 RTT。
    setApprovalLoading(true);
    setApprovalInfo(null);
    void (async () => {
      try {
        const res = await runtimeApprovalApi.describe(entityCode, String(record.id));
        if (res.code === 200 && res.data) setApprovalInfo(res.data);
      } catch {
        // 未进审批流 / 无实体读权限：不展示审批段，也不打断详情（降级不阻断）
      } finally {
        setApprovalLoading(false);
      }
    })();
    setExecutionsLoading(true);
    try {
      const res = await runtimeRecordApi.actionExecutions(entityCode, String(record.id));
      if (res.code === 200) {
        setExecutions(res.data ?? []);
      } else {
        setExecutions([]);
        message.error(errorMessage(res));
      }
    } catch {
      setExecutions([]);
      message.error('加载动作执行历史失败');
    } finally {
      setExecutionsLoading(false);
    }
  };

  /**
   * 某行当前可用的动作。
   *
   * 真源优先级：① page 级 `meta.allowedActionsByStatus`（后端已按权限裁剪）——
   * 拿到即用，零额外请求；② 降级到 `GET /actions` 全量 + 前端按 fromStatus 过滤。
   */
  /**
   * 动作按钮的鉴权码 —— **必须与后端 RuntimeAccessGuard#requireAction 同口径**，否则前端比后端严。
   *
   * 后端口径（§4.6）：
   *  · 非逃逸动作（状态流转）：实体级 write / 通配 write / 动作细码 **任一** 即可放行；
   *  · 逃逸动作（有资金/外部副作用，如确认打款）：**必须**命中动作级细码 runtime:{entity}:{action}，
   *    实体级与通配 write 均不覆盖。
   *
   * 若前端对所有动作都强求细码，模板实例化产出的新实体（细码尚未在 IAM 授予）会
   * **一个流转按钮都看不到**，而后端实际是放行状态流转的 —— 真实表现为「按钮凭空消失」。
   * 故：非逃逸回落实体级维护码，逃逸仍强制细码（UI 侧保持 fail-closed）。
   */
  const authCodeOf = (d: RuntimeActionView): string =>
    d.kind === 'escape'
      ? d.permission ?? BonePermissionCodes.METADATA_RUNTIME_WRITE
      : BonePermissionCodes.METADATA_RUNTIME_WRITE;

  const actionsOf = (row: RuntimeRecord): RuntimeActionView[] => {
    if (actionsByStatus) {
      const key = String(row.status ?? '');
      return actionsByStatus[key] ?? actionsByStatus['*'] ?? [];
    }
    return actionDefs
      .filter((d) => d.fromStatus == null || d.fromStatus === String(row.status ?? ''))
      .map(toActionView);
  };

  /** 提交动作：资金类 escape 动作自动生成 Idempotency-Key 防重放 */
  const handleActionSubmit = async () => {
    const { def, record } = actionModal;
    if (!def || !record || !entityCode) return;
    const rid = recordId(record);
    if (!rid) {
      message.error('记录缺少主键 id');
      return;
    }
    let params: Record<string, unknown> = {};
    if (def.params && def.params.length) {
      try {
        params = await actionForm.validateFields();
      } catch {
        return; // 必填校验未过，停留弹窗
      }
    }
    const idempotencyKey = def.requiresIdempotencyKey
      ? (globalThis.crypto?.randomUUID?.() ?? `idem-${Date.now()}-${Math.random()}`)
      : undefined;
    try {
      const res = await runtimeRecordApi.invokeAction(
        entityCode,
        rid,
        def.code,
        params,
        idempotencyKey,
      );
      if (res.code === 200) {
        const idem = res.data?.idempotent ? '（幂等命中，未重复执行）' : '';
        // 状态用中文标签回显：后端回的是 fromStatus/toStatus 码（如 DRAFT→SUBMITTED），
        // 直出会让用户看到内部枚举码（与页面上其他位置的中文状态不一致）。
        const flow = res.data?.fromStatus
          ? `${statusLabelOf(res.data.fromStatus)} → ${statusLabelOf(res.data.toStatus)}`
          : '';
        message.success(`动作「${def.label}」执行成功${flow ? `（${flow}）` : ''}${idem}`);
        setActionModal({ open: false, def: null, record: null });
        load();
      } else {
        message.error(errorMessage(res));
      }
    } catch {
      message.error('动作执行失败');
    }
  };

  const handleSubmit = async () => {
    if (!entityCode) return;
    // 校验未通过时 antd 会在字段下方展示错误并 reject；必须捕获，否则未处理的
    // rejection 会被 qiankun 当作「微应用加载异常」上报并中断整个运行时页面，
    // 真实用户漏填必填项提交时同样会崩。捕获后直接返回即可（错误已内联展示）。
    let values: Record<string, unknown>;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    await saveBody(buildBody(values));
  };

  /** 协议表单提交入口（BoneDynamicForm 已做 linkage/submit 前端校验，直接落 runtime 通道） */
  const handleSchemaSubmit = async (data: Record<string, unknown>) => {
    await saveBody(buildBody(data));
  };

  const saveBody = async (body: RuntimeRecord) => {
    if (!entityCode) return;
    try {
      if (editing) {
        const id = recordId(editing);
        const res = await runtimeRecordApi.update(entityCode, id, body, editingEtag);
        if (res.code === 200) {
          message.success('更新成功');
          setModalOpen(false);
          setEditing(null);
          setEditingEtag(undefined);
          load();
        } else {
          message.error(errorMessage(res));
        }
      } else {
        const res = await runtimeRecordApi.create(entityCode, body);
        if (res.code === 200) {
          message.success('创建成功');
          setModalOpen(false);
          load();
        } else {
          message.error(errorMessage(res));
        }
      }
    } catch (err) {
      // W0-4：412 并发冲突 —— 提示重新加载，而非静默失败
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 412) {
        setConflict(true);
        message.warning('数据已被他人修改（412 并发冲突），请重新加载后再编辑');
        if (editing) {
          const rid = recordId(editing);
          const res = await runtimeRecordApi.get(entityCode, rid);
          if (res.code === 200) {
            // 「查看差异」：把线上最新值与本次提交逐字段比对，只保留真正不同的字段。
            // 只提示「被他人修改」而不指出改了哪几项，用户只能全表重填。
            const server = (res.data ?? {}) as Record<string, unknown>;
            setConflictDiff(
              formFields
                .map((f) => ({
                  code: f.code,
                  name: f.displayName || f.code,
                  mine: body[f.code],
                  theirs: server[f.code],
                }))
                .filter((d) => String(d.mine ?? '') !== String(d.theirs ?? '')),
            );
            setEditing(res.data);
            setEditingEtag(res.etag);
          }
        }
      } else {
        message.error(editing ? '更新失败' : '创建失败');
      }
    }
  };

  const tableColumns: ColumnsType<RuntimeRecord> = useMemo(() => {
    const cols: ColumnsType<RuntimeRecord> = projectedColumns.map(({ field: f, width, pinned }) => {
      const ftype = (f.type || '').toUpperCase();
      return {
        title: f.displayName || f.code,
        dataIndex: f.code,
        key: f.code,
        ellipsis: true,
        // CHILD_TABLE 无物理列（与 DDL 建表跳列同款判据）⇒ 行数据里没有它、
        // 后端对 sort=<child> 精确 400。关闭表头排序入口，把「点了就报错」
        // 变成「不可点」（业界通例：不可排序的列不渲染排序箭头）。
        sorter: ftype !== 'CHILD_TABLE',
        // 契约列宽/固定列（P1）：设计器 views.list 配的 width/pinned 在此真正生效
        // （pinned 为协议 enum：left=钉左侧主键侧，right=钉右侧操作列侧；历史 boolean true 兼容为 left）
        width,
        fixed: pinned === 'right' ? ('right' as const) : pinned ? ('left' as const) : undefined,
        sortOrder: sortField === f.code ? (sortDesc ? 'descend' : 'ascend') : null,
        render: (_: unknown, row) => {
          if (ftype === 'CHILD_TABLE') {
            /* 明细列（复杂企业场景：头行/明细 1—N）。契约把 CHILD_TABLE 配成列表列时，
               渲染为行内「查看明细」入口（与「操作」列明细下拉同源 openChildren）——
               而不是空列（无物理列可取）或表头排序 400。业界对标：Airtable 的
               linked-record 字段渲染为可点开的链接、Salesforce 用 related list 承载 1—N。 */
            const spec = childSpecOf(f);
            if (!spec) {
              return (
                <Tooltip title="明细声明缺少 childEntityCode/foreignKey（字段 validationRules.childTable）">
                  <Tag color="orange">明细声明不完整</Tag>
                </Tooltip>
              );
            }
            return (
              <Button type="link" size="small" onClick={() => openChildren(row, f)}>
                查看明细
              </Button>
            );
          }
          if (isEnumField(f)) return renderEnumCell(row[f.code], f);
          if (ftype === 'REFERENCE') {
            // G10：主行查询已 enrich code__label（JdbcRuntimeRecordService），label 兜底 id
            const label = row[`${f.code}__label`];
            const v = row[f.code];
            if (v == null) return '—';
            return label != null && label !== '' ? `${String(label)}` : String(v);
          }
          if (ftype === 'ATTACHMENT') {
            // 附件列：JSON 数组串 → 数量徽标
            const v = row[f.code];
            if (v == null || v === '') return '—';
            try {
              const arr = typeof v === 'string' ? JSON.parse(v) : v;
              if (Array.isArray(arr) && arr.length) {
                return <Tag color="cyan">{arr.length} 个附件</Tag>;
              }
            } catch {
              /* 非 JSON（历史脏数据）按原样展示 */
            }
            return formatCell(v);
          }
          /* §0.3 G20：DECIMAL 物理存储硬编码 DECIMAL(20,6)（如 128.500000），
             设计口径「界面按 scale=2 渲染」。此处按 2 位标度格式化，避免金额列
             显示成 128.500000 这类对业务用户无意义的长尾零（FLOAT/DOUBLE 同处理）。 */
          if (ftype === 'DECIMAL' || ftype === 'FLOAT' || ftype === 'DOUBLE') {
            const v = row[f.code];
            if (v == null || v === '') return '—';
            const n = typeof v === 'string' ? Number(v) : (v as number);
            if (!Number.isFinite(n)) return formatCell(v);
            return (
              <span style={{ fontFamily: 'ui-monospace, monospace' }}>
                {formatNumber(n, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </span>
            );
          }
          return formatCell(row[f.code]);
        },
      };
    });
    if (cols.length === 0) {
      cols.push({
        title: 'id',
        dataIndex: 'id',
        key: 'id',
        render: (_: unknown, row) => formatCell(row.id),
      });
    }
    cols.push({
      title: '操作',
      key: 'actions',
      fixed: 'right',
      // 收敛为主推进动作 + 更多 + 明细 + 编辑 + 归档（原按状态机动作数平铺需 200+）
      width: 170,
      sorter: false,
      sortOrder: null,
      render: (_: unknown, row) =>
        trashMode ? (
          <Auth code={BonePermissionCodes.METADATA_RUNTIME_WRITE}>
            <Popconfirm
              title="恢复该记录？"
              description="恢复后记录重新出现在数据列表中。"
              onConfirm={() => handleRestore(row)}
            >
              <Button type="link" size="small">
                恢复
              </Button>
            </Popconfirm>
          </Auth>
        ) : (
          <Space>
            {/* 动作按钮真源 = meta.allowedActionsByStatus（后端已按权限裁剪）；
                降级态用 listActions 全量 + 前端按 fromStatus 过滤。前端禁止硬编码按钮列表。 */}
            {(() => {
              /* 状态流转「下一步」引导（对标终稿屏⑤/⑥）：
                 一条记录在某个状态下通常只有 1 个**主推进动作**（如 待审批→审批 / 已审批→确认打款）。
                 原实现把该状态的**全部**动作平铺 inline，操作列随状态机配置宽度漂移、用户也分不清
                 「哪个是主线」。这里只把主推进动作 inline 展示，其余（回退/终止等低频或破坏性动作）
                 收进「更多」下拉——主线一眼可见，次要动作仍可达。
                 判定规则：后端未提供显式 primary 标记时，取**动作定义顺序里的第一个**作为主动作
                 （后端约定主推进动作先声明）；一个动作都没有时不渲染任何引导。 */
              const act = actionsOf(row);
              if (act.length === 0) return null;
              const primary = act.find((d) => (d as unknown as { primary?: boolean }).primary) ?? act[0];
              const rest = act.filter((d) => d.code !== primary.code);
              return (
                <>
                  {/* 逃逸动作（资金/外部副作用）无权限时「置灰+解释」而非隐藏：
                      后端 fail-closed 不变（RuntimeAccessGuard 仍是唯一门禁），
                      前端「看不见」与「不让点」是两回事——隐藏会让用户无法区分
                      「没有这个功能」和「我没权限」，直接转化为支持工单；
                      置灰并给出确切权限码（对齐设计系统 disable 模式语义：
                      「为什么我不能开票」类场景必须可见+可解释）。 */}
                  <AuthButton
                    code={authCodeOf(primary)}
                    type="link"
                    size="small"
                    mode={primary.kind === 'escape' ? 'disable' : 'hide'}
                    deniedHint={
                      primary.kind === 'escape'
                        ? `需授权：${authCodeOf(primary)}（资金类动作须动作级授权，联系管理员）`
                        : undefined
                    }
                    onClick={() => openAction(row, primary)}
                  >
                    {primary.label}
                  </AuthButton>
                  {rest.length > 0 && (
                    <Dropdown menu={{ items: rest.map((d) => {
                      // 逃逸动作进「更多」同样 fail-closed：无细码时置灰并提示缺哪个码，
                      // 避免用户点进去才被后端 403 拒绝（先于错误发生的能力提示）。
                      const fineRequired = d.kind === 'escape';
                      const allowed = !fineRequired || has(authCodeOf(d));
                      return {
                        key: d.code,
                        label: d.label,
                        disabled: !allowed,
                        title: allowed ? undefined : `需授权：${authCodeOf(d)}`,
                        onClick: () => openAction(row, d),
                      };
                    }) }} trigger={['click']}>
                      <Button type="link" size="small">更多 <DownOutlined /></Button>
                    </Dropdown>
                  )}
                </>
              );
            })()}
            {/* G10：CHILD_TABLE 声明字段 → 「明细」入口（整组替换编辑器）。
                低频数据维护动作，收进「更多」与状态流转动作并列，主线不被稀释。 */}
            {(() => {
              const childFields = writableFields.filter(
                (f) => (f.type || '').toUpperCase() === 'CHILD_TABLE',
              );
              if (childFields.length === 0) return null;
              return (
                <Dropdown
                  menu={{
                    items: childFields.map((f) => ({
                      key: `child-${f.code}`,
                      label: `${f.displayName || f.code}明细`,
                      onClick: () => openChildren(row, f),
                    })),
                  }}
                  trigger={['click']}
                >
                  <Button type="link" size="small">明细 <DownOutlined /></Button>
                </Dropdown>
              );
            })()}
            {/* 详情：全字段只读 + 动作执行时间线（P2-②）。
                读权限与列表一致（列表可见即可见），不额外设码——
                审计时间线的可发现性本身就是合规诉求（等保/SOX）。 */}
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => openDetail(row)}
            >
              详情
            </Button>
            <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </AuthButton>
            <Auth code={BonePermissionCodes.METADATA_RUNTIME_WRITE}>
              {/* 文案必须是「归档」+「不可恢复」提示：物理表无 deleted 列时后端会降级为物理 DELETE，
                  叫「删除」会让用户以为这是可逆的常规操作（Runtime §0.3 / 判据 R13）。 */}
              <Popconfirm
                title="归档该记录？"
                description="归档后不再出现在数据列表；若当前物理表不支持软删，归档即永久删除、不可恢复。"
                okText="确认归档"
                cancelText="取消"
                onConfirm={() => handleDelete(row)}
              >
                <Button type="link" size="small" danger>
                  归档
                </Button>
              </Popconfirm>
            </Auth>
          </Space>
        ),
    });
    return cols;
    // handleDelete/handleRestore/openEdit/openAction/actionsOf 为稳定的页面级函数引用，加入会导致循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectedFields, entityCode, sortField, sortDesc, actionDefs, actionsByStatus, trashMode]);

  const renderFieldInput = (field: MetaField) => {
    const type = (field.type || 'STRING').toUpperCase();
    // W0-5 / PRD §5.5：未支持类型必须显式降级提示 + 字段名，禁止静默渲染成裸 Input
    if (!isRenderSupported(field)) {
      return (
        <Alert
          key={field.id}
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`字段「${field.displayName || field.code}」的类型 ${field.type} 暂不支持录入控件`}
          description="该类型在运行时 API 中可读写，但建模界面暂无对应控件；如需界面录入请改用受支持类型。"
        />
      );
    }
    if (type === 'BOOLEAN') {
      return (
        <Form.Item name={field.code} label={field.displayName} valuePropName="checked">
          <Switch />
        </Form.Item>
      );
    }
    if (type === 'INTEGER' || type === 'LONG') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} />
        </Form.Item>
      );
    }
    if (type === 'DECIMAL') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <InputNumber style={{ width: '100%' }} step={0.01} />
        </Form.Item>
      );
    }
    if (type === 'DATE' || type === 'DATETIME') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <DatePicker
            showTime={type === 'DATETIME'}
            format={type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'}
            style={{ width: '100%' }}
          />
        </Form.Item>
      );
    }
    if (type === 'REFERENCE') {
      // G10：远程下拉（lookupOptions，服务端按 reference 声明过滤）。
      // 选项由 openCreate/openEdit 预取（referenceCache）；value=目标记录 id，列表列显示 __label。
      const options = referenceCache[field.code] ?? [];
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true, message: `请选择${field.displayName}` }] : []}
        >
          <Select
            allowClear
            showSearch
            placeholder="请选择"
            notFoundContent={options.length ? '无匹配项' : '加载中…'}
            options={options.map((o) => ({ value: o.id, label: String(o.label ?? o.id) }))}
            onSearch={(q) => {
              if (!q) return;
              const f = fields.find((x) => x.code === field.code);
              if (f) loadReferenceOptions(f, q);
            }}
          />
        </Form.Item>
      );
    }
    if (type === 'ATTACHMENT') {
      // G10：附件清单编辑器（name/url/size）。物理列 TEXT 存 JSON 数组串，无真实文件存储通道
      // （上传通道属 v1.1 路线）——前端维护元数据清单，正文留白由后端 TEXT 列承接。
      return (
        <Form.Item name={field.code} label={field.displayName}>
          <AttachmentEditor />
        </Form.Item>
      );
    }
    if (type === 'ENUM') {
      const options = parseEnumValues(field.enumValues);
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <Select
            allowClear
            placeholder={options.length ? '请选择' : '该枚举未配置码表'}
            options={options.map((o) => ({ value: o.code, label: `${o.label}（${o.code}）` }))}
          />
        </Form.Item>
      );
    }
    if (type === 'TEXT') {
      return (
        <Form.Item
          name={field.code}
          label={field.displayName}
          rules={field.required ? [{ required: true }] : []}
        >
          <Input.TextArea rows={3} />
        </Form.Item>
      );
    }
    return (
      <Form.Item
        name={field.code}
        label={field.displayName}
        rules={field.required ? [{ required: true, message: `请填写${field.displayName}` }] : []}
      >
        <Input />
      </Form.Item>
    );
  };

  // 嵌入模式且实体非 RUNTIME / 未发布：给出明确指引，而非静默错误
  const embeddedBlocked = resolvedId != null && selectedEntity != null
    && (selectedEntity.deliveryMode !== META_DELIVERY_RUNTIME || selectedEntity.status !== META_ENTITY_PUBLISHED);

  return (
    <div className="page">
      <CommandPalette />
      {/* 页头：声明本页职责边界。
          「业务数据」与实体列表的「数据」按钮、实体工作区的「数据」页签是**同一份数据**，
          区别只在**上下文来源**：本页是跨实体运营台（需自己选实体），后两者已锁定实体。
          此前页面只说「模式 B 是什么」，没说「我什么时候该来这里」，用户因此把它
          当成第三个数据入口，在三个入口间反复试错。 */}
      {!resolvedId && (
        <Space direction="vertical" size={0} style={{ marginBottom: 16, width: '100%' }}>
          <Space size={8} wrap>
            <Typography.Title level={5} style={{ margin: 0 }}>业务数据</Typography.Title>
            <Tag color="purple" style={{ fontSize: 12 }}>跨实体数据运营台</Tag>
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            跨实体的数据运营台：当你不确定数据在哪个模型、或要跨多个模型巡检记录时使用。
            已知目标实体时，从「业务对象」列表点行内「数据」按钮进入，会自动锁定当前实体、不会看错。
          </Text>
        </Space>
      )}
      {!resolvedId && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="仅「自动生成界面 + 已发布」的实体有物理表"
          description={
            <>
              经 <Text code>/api/v1/runtime/entities/&#123;code&#125;/records</Text>{' '}
              直接读写物理表，无需代码生成。手写界面实体请到「代码生成」查看。
            </>
          }
        />
      )}

      {/* 独立全局路由：实体选择器；嵌入模式不显示 */}
      {!resolvedId && (
        <Space style={{ marginBottom: 16 }} wrap>
          <span>实体：</span>
          <Select
            style={{ minWidth: 280 }}
            placeholder="选择 RUNTIME 已发布实体"
            value={entityCode ?? undefined}
            onChange={(code) => {
              setEntityCode(code);
              setPage(1);
            }}
            options={entities.map((e) => ({
              value: e.code,
              label: `${e.displayName} (${e.code}) → ${e.tableName}`,
            }))}
            notFoundContent="暂无符合条件的模型，请先在模型管理中创建并发布 RUNTIME 模型"
            /* W0-2：size 50→500 后候选可达数百个，选择器必须可搜索——
               否则「去截断」只是把不可达项从「第 51 个之后」推迟到「第 501 个之后」。 */
            showSearch
            optionFilterProp="label"
            filterOption={(input, option) =>
              String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />
          <Button icon={<ReloadOutlined />} onClick={load} disabled={!entityCode}>
            刷新
          </Button>
          {/* displayModes 门控：schema 未声明 'create' ⇒ 不提供录入入口（见 schemaDisplayModes 注释） */}
          {canCreate && (
            <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!entityCode}>
              新建记录
            </AuthButton>
          )}
          {/* 个人显示组：视图切换 / 列设置 / 自伤遮蔽自救（个人覆盖的唯一编辑器）。
              契约列（列集合/列序/列宽）在建模台「界面设计器」配，发布后全员生效。 */}
          <PersonalViewBar overlay={overlay} fields={readFields} listView={contractListView} onSaveFilterToView={saveCurrentFilterToView} />
          {/* 观看者模式开关：同一份数据两套信息密度（Odoo 开发者模式 / Salesforce Setup 同源思路），
              只有具备建模维护权限的人看得到入口——不可达的能力不占视觉位。 */}
          <ViewerModeSwitch value={viewerMode} onChange={changeViewerMode} />
        </Space>
      )}

      {/* 交付口径对照：**实施模式的诊断面板**，不是业务用户的日常信息。
          此前无条件常驻首屏——「契约 / 列表列 / 个人覆盖」都是**建模产物**的内部构造，报销填单人既不关心也无权处理；常态展示还会把真正的异常（自伤遮蔽）
          淹没在噪音里（Nielsen：异常提示必须与日常信息分开，常态化的警告等于没有警告）。
          现在：实施模式看全量卡片；消费面**仅在有可行动异常**时给一行可自救的提示。 */}
      {!embeddedBlocked && selectedEntity && implMode && (
        <ContractScopeCard
          overlay={overlay}
          fields={writableFields}
          listView={contractListView}
          onOpenDesigner={() => navigate(designerPath)}
        />
      )}
      {/* 消费面的异常通道：个人偏好盖住团队契约（被自伤遮蔽）是本页唯一"用户自己能修"的
          实施向异常，保留一行 + 一键恢复；无异常时消费面干净，不做任何说明。 */}
      {!embeddedBlocked && selectedEntity && !implMode && overlay.shadowedColumns.length > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 12 }}
          message={`有 ${overlay.shadowedColumns.length} 列被你的个人显示设置隐藏了`}
          description={
            <Space size={8} wrap>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {overlay.shadowedColumns.map((f) => f.displayName || f.code).join('、')}
              </Text>
              <Button
                size="small"
                onClick={() =>
                  overlay.shadowedColumns.forEach((f) => overlay.toggleVisible(f.code, true))
                }
              >
                恢复显示
              </Button>
            </Space>
          }
        />
      )}

      {/* 数据生命周期步骤条：**建模侧 2 步只在实施模式出现**，状态机真实状态链两面都给。
          建模侧（模型已定义 / 已发布）是进入本页的**前置条件**——条件已满足却继续占据
          业务用户首屏第一行，等于天天告诉报销人"恭喜你，这张表已经建好了"（中性信息噪音）；
          真正有 news 价值的只有「未发布 / 待建模」这类**实施者才能解决**的异常态。
          状态链可点击下钻 —— 点某状态即把列表筛到该态（q=status:<code>，后端精确匹配）。 */}
      {!embeddedBlocked && selectedEntity && (implMode || stateChain.length > 0) && (
        <Card size="small" style={{ marginBottom: 16 }} styles={{ body: { padding: '14px 16px 16px' } }}>
          <Steps
            size="small"
            current={-1}
            items={[
              ...(implMode
                ? [
                  {
                    title: (
                      <a onClick={() => navigate(structurePath)}>{MODEL_STEPS[0].label}</a>
                    ),
                    status: 'finish' as const,
                  },
                  {
                    title: selectedEntity.status === META_ENTITY_PUBLISHED ? (
                      <Text strong style={{ fontSize: 13 }}>{MODEL_STEPS[1].label}</Text>
                    ) : (
                      <a onClick={() => navigate(structurePath)}>待发布</a>
                    ),
                    status: (selectedEntity.status === META_ENTITY_PUBLISHED ? 'finish' : 'error') as 'finish' | 'error',
                  },
                ]
                : []),
              ...(stateChain.length > 0
                ? stateChain.map((code) => {
                  // 下钻态判定：生效条件恰为「单条 status=code」（用户可能另有多条件，
                  // 那时步骤条不算激活——避免「看着高亮但其实还叠着别的筛选」的误判）。
                  const active =
                      appliedConditions.length === 1 &&
                      appliedConditions[0].field === 'status' &&
                      appliedConditions[0].value === code;
                  return {
                    title: (
                      <a
                        onClick={() => {
                          // 再次点击已选中的状态 = 取消筛选（幂等下钻，符合 Ant Design Pro 筛选标签行为）
                          const nextActive = !active;
                          const next: { field?: string; op: string; value: string }[] =
                              nextActive
                                ? [{ field: 'status', value: code, op: 'eq' }]
                                : [];
                          setConditions(next);
                          setAppliedConditions(next);
                          setSelectedFilterNote(
                            nextActive ? `已按状态「${statusLabelOf(code)}」筛选` : null,
                          );
                          setPage(1);
                        }}
                        style={active ? { fontWeight: 600 } : undefined}
                      >
                        {statusLabelOf(code)}
                      </a>
                    ),
                    status: active ? ('process' as const) : ('wait' as const),
                    description: active ? '已筛选' : undefined,                    };
                })
                : []),
            ]}
          />
          {stateChain.length === 0 && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              该对象未配置状态机，所有记录处于同一状态，无需流转操作。
            </Text>
          )}
        </Card>
      )}

      {/* 最近操作实体：跨实体高频巡检的一键入口（仅显示 RUNTIME 已发布候选） */}
      {!resolvedId && recentForRuntime.length > 0 && (
        <div style={{ marginBottom: 12 }}>
          <Space size={4} wrap>
            <Text type="secondary" style={{ fontSize: 12 }}>最近操作：</Text>
            {recentForRuntime.map((r) => (
              <Tag
                key={r.code}
                style={{ cursor: 'pointer', marginInlineEnd: 4 }}
                color={entityCode === r.code ? 'processing' : undefined}
                onClick={() => { setEntityCode(r.code); setPage(1); }}
              >
                {r.displayName}
              </Tag>
            ))}
          </Space>
        </div>
      )}

      {/* 物理表 / 交付模式 / 发布状态 —— **实施模式专属**。
          物理表名是 schema 的内部实现：对业务用户零信息量，却给误操作与外部探测提供了
          有效线索（信息最小化原则）；一旦展示还等于承诺"这个表名不会变"——后续重命名
          或分表都会变成一次用户可见的破坏性变更。实施模式下保留，排障需要它。 */}
      {selectedEntity && implMode && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          物理表：<Text code>{selectedEntity.tableName}</Text> · 交付模式：
          {DELIVERY_MODE[selectedEntity.deliveryMode ?? 0]} · 状态：
          {ENTITY_STATUS[selectedEntity.status]}
        </Text>
      )}
      {/* 实施模式自查：告诉实施者"消费面为此隐藏了什么 / 哪处还需建模"，
          对标 Odoo 开发者模式的「技术字段已在常规视图隐藏」提示——**信息没有丢，只是分层**。
          两点纪律：① **点名**（只报"隐藏了 1 个字段"等于让人自己去找）；
          ② 给**修复入口**（发现问题却要手工找路，提示就会被当噪音关掉）。 */}
      {selectedEntity && implMode && (hiddenKeyFields.length > 0 || labelLessEnums.length > 0) && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12 }}>
          <Space size={8} wrap>
            {hiddenKeyFields.length > 0 && (
              <Tooltip
                title={`为可读性临时隐藏：${hiddenKeyFields.map((f) => `${f.displayName || f.code}（${f.code}）`).join('、')}。根治办法是建模侧把它们声明为 REFERENCE 并按 label 回显（如审批人显示人名），而不是靠展示层过滤。`}
              >
                <Tag color="orange">
                  业务视图已隐藏 {hiddenKeyFields.length} 个代理键字段：
                  {hiddenKeyFields.map((f) => f.displayName || f.code).join('、')}
                </Tag>
              </Tooltip>
            )}
            {labelLessEnums.map((f) => (
              <Tooltip
                key={f.code}
                title={`枚举「${f.displayName || f.code}」的选项只有 code 没有中文标签，运行时只能显示裸码；改法：结构 Tab 的字段选项里补 {code,label} 对象数组（或绑定系统字典）`}
              >
                <Tag color="gold">枚举缺中文标签：{f.displayName || f.code}</Tag>
              </Tooltip>
            ))}
            <a onClick={() => navigate(structurePath)}>去建模台修复</a>
          </Space>
        </Text>
      )}

      {/* strict 契约集合的可观测面：已建模但未列入团队契约的字段不出现在数据列表。
          本页「交付口径对照」给出同源提示——交付工程师「我建的字段呢」
          类困惑就发生在此（字段建了、发布了，但列表里没有，且无任何迹象）。
          低噪音单行提示（不占 Alert 横幅）：列名前 5 个进 Tooltip，全文去「界面设计器」看。
          实施模式专属：这是"列没配好"，业务用户既看不懂也改不了。 */}
      {implMode && contractListView && outsideContractFields.length > 0 && (
        <Tooltip
          title={`${outsideContractFields.map((f) => f.displayName || f.code).join('、')}——已建模但未列入团队列契约，不出现在数据列表；在「界面设计器 → 列表」加入并发布后生效`}
        >
          <Text type="secondary" style={{ display: 'block', marginBottom: 12, fontSize: 12, cursor: 'help' }}>
            有 {outsideContractFields.length} 个字段未列入团队契约（不在列表显示，悬停查看）
          </Text>
        </Tooltip>
      )}

      {embeddedBlocked && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 16 }}
          message="该实体暂不支持业务数据管理"
          description={
            selectedEntity?.deliveryMode !== META_DELIVERY_RUNTIME
              ? '仅「自动生成界面」交付模式的实体发布后才提供动态 CRUD 数据管理，请改用手写界面实体的业务应用录入数据。'
              : '请先在工作区「发布」Tab 完成发布，物理表建表后即可在此管理数据。'
          }
        />
      )}

      {!embeddedBlocked && selectedEntity && (
        <>
          {/* B1：数据 Tab 空态 —— 无字段时给「补齐字段并发布」结构建议，
              而不是渲染一张零列的空表格（用户会以为「没数据」而非「没字段」） */}
          {formFields.length === 0 ? (
            <Empty
              style={{ margin: '32px 0' }}
              description={
                <Space direction="vertical" size={2}>
                  <Text>该实体还没有可录入的字段</Text>
                  <Text type="secondary">
                    请先在「结构」页签补齐字段并发布，物理表建表后即可在此录入数据
                  </Text>
                </Space>
              }
            >
              <Button type="primary" onClick={() => navigate(structurePath)}>
                去结构补齐字段
              </Button>
            </Empty>
          ) : (
            <>
              {/* 工具栏必须在「实体详情嵌入」与「独立 /runtime 路由」两种形态下都完整可用。
                  历史实现把整条工具栏包在 `resolvedId &&` 里 ⇒ 独立路由没有回收站入口，
                  用户点了「归档」后再也看不到、也恢复不了这条记录 —— 语义上等同永久删除，
                  正是 Runtime §0.3 / 判据 R13 要防的「软删不可观测」。
                  因此 Segmented 与过滤控件移出门控；「新建/刷新」保留在嵌入模式，
                  独立模式下对照组在上方实体选择器工具栏（已含新建记录 + 刷新）。 */}
              <Space style={{ marginBottom: 16 }} wrap>
                {resolvedId && (
                  <>
                    {!trashMode && canCreate && (
                      <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建记录
                      </AuthButton>
                    )}
                    <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
                  </>
                )}
                {/* 个人显示组（独立/嵌入两种形态都可用）：个人覆盖编辑器 + 自伤遮蔽自救 */}
                <PersonalViewBar overlay={overlay} fields={readFields} listView={contractListView} onSaveFilterToView={saveCurrentFilterToView} />
                {/* 观看者模式开关：同一份数据两套信息密度（Odoo 开发者模式 / Salesforce Setup 同源思路），
                    只有具备建模维护权限的人看得到入口——不可达的能力不占视觉位。 */}
                <ViewerModeSwitch value={viewerMode} onChange={changeViewerMode} />
                {/* 回收站：软删必须可观测、可恢复。没有这个入口，「归档」就等同于「永久删除」。 */}
                <Segmented
                  value={trashMode ? 'trash' : 'active'}
                  onChange={(v) => {
                    setTrashMode(v === 'trash');
                    setPage(1);
                  }}
                  options={[
                    { label: '在用', value: 'active' },
                    { label: '回收站', value: 'trash' },
                  ]}
                />
                {/* P2-⑤/P1-3：多条件过滤（AND），运算符扩展集。q 契约 = field:value;field:op:value。
                    显式「查询」提交（不防抖直发）：填到一半的条件不该产出错误口径的结果集。 */}
                <Space.Compact>
                  {conditions.map((c, idx) => (
                    <Space key={idx} size={4} align="start" wrap>
                      <Select
                        allowClear
                        style={{ minWidth: 130 }}
                        placeholder="筛选字段"
                        value={c.field}
                        onChange={(v) => {
                          setConditions((prev) =>
                            prev.map((p, i) => (i === idx ? { ...p, field: v } : p)),
                          );
                          // 手工改条件即视为用户接管筛选，撤掉步骤条留下的说明
                          setSelectedFilterNote(null);
                        }}
                        options={filterableFields.map((f) => ({
                          value: f.code,
                          label: f.displayName || f.code,
                        }))}
                      />
                      <Select
                        style={{ minWidth: 96 }}
                        value={c.op}
                        onChange={(v) => {
                          setConditions((prev) =>
                            prev.map((p, i) => (i === idx ? { ...p, op: v } : p)),
                          );
                          setSelectedFilterNote(null);
                        }}
                        options={FILTER_OP_OPTIONS}
                      />
                      <Input
                        allowClear
                        style={{ width: 200 }}
                        placeholder={
                          c.op === 'in'
                            ? '多值用逗号分隔（属于）'
                            : c.op === 'eq'
                              ? '筛选值（精确匹配）'
                              : `筛选值（${FILTER_OP_OPTIONS.find((o) => o.value === c.op)?.label ?? c.op}）`
                        }
                        value={c.value}
                        onChange={(e) => {
                          setConditions((prev) =>
                            prev.map((p, i) => (i === idx ? { ...p, value: e.target.value } : p)),
                          );
                          setSelectedFilterNote(null);
                        }}
                        onPressEnter={() => {
                          setAppliedConditions(conditions);
                          setPage(1);
                        }}
                      />
                      <Button
                        icon={<DeleteOutlined />}
                        onClick={() => {
                          setConditions((prev) => prev.filter((_, i) => i !== idx));
                        }}
                      />
                    </Space>
                  ))}
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => setConditions((prev) => [...prev, { value: '', op: 'eq' }])}
                  >
                    添加条件
                  </Button>
                  <Button
                    type="primary"
                    disabled={conditions.every((c) => !c.field || !c.value.trim())}
                    onClick={() => {
                      setAppliedConditions(conditions);
                      setPage(1);
                    }}
                  >
                    查询
                  </Button>
                  {appliedConditions.length > 0 && (
                    <Button
                      onClick={() => {
                        setConditions([]);
                        setAppliedConditions([]);
                        setSelectedFilterNote(null);
                        setPage(1);
                      }}
                    >
                      重置
                    </Button>
                  )}
                </Space.Compact>
              </Space>

              {/* 筛选口径留痕：步骤条下钻/多条件查询改变了列表口径但 URL/工具栏都不体现，
                  不提示的话用户会误判「数据变少了」（详见 selectedFilterNote 注释）。 */}
              {selectedFilterNote && (
                <Alert
                  type="info"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={selectedFilterNote}
                  action={
                    <Button
                      size="small"
                      onClick={() => {
                        setConditions([]);
                        setAppliedConditions([]);
                        setSelectedFilterNote(null);
                        setPage(1);
                      }}
                    >
                      清除筛选
                    </Button>
                  }
                />
              )}

              <Table<RuntimeRecord>
                rowKey={(row) => recordId(row) || JSON.stringify(row)}
                loading={loading}
                columns={tableColumns}
                dataSource={data}
                size={tableSizeOf(overlay.activeOv.density)}
                scroll={{ x: true }}
                /* 排序交给后端：列排序变化 → 受控状态 → sort 契约（- 前缀降序） */
                onChange={(_pagination, _filters, sorter) => {
                  const s = Array.isArray(sorter) ? sorter[0] : sorter;
                  const field = s?.field ?? s?.columnKey;
                  if (s?.order && field) {
                    setSortField(String(field));
                    setSortDesc(s.order === 'descend');
                  } else {
                    setSortField(undefined);
                    setSortDesc(false);
                  }
                }}
                pagination={{
                  current: page,
                  pageSize,
                  total,
                  showSizeChanger: true,
                  onChange: (p, ps) => {
                    setPage(p);
                    setPageSize(ps);
                  },
                }}
                /* 空态必须区分「没有数据」与「筛选后没匹配」：前者要给录入 CTA，
                   后者要给清除筛选 CTA —— 同一个 antd 默认 Empty 会让用户在
                   「我明明录了 50 条」和「筛选条件写错了」之间反复猜。 */
                locale={{
                  emptyText: (
                    <Empty description={null}>
                      {appliedConditions.some((c) => c.field && c.value.trim()) ? (
                        <Space direction="vertical" size={4}>
                          <Text>
                            没有匹配「
                            {appliedConditions
                              .filter((c) => c.field && c.value.trim())
                              .map((c) => {
                                const f = filterableFields.find((pf) => pf.code === c.field);
                                return `${f?.displayName || c.field} = ${c.value.trim()}`;
                              })
                              .join(' 且 ')}
                            」的记录
                          </Text>
                          <Button
                            onClick={() => {
                              setConditions([]);
                              setAppliedConditions([]);
                              setSelectedFilterNote(null);
                              setPage(1);
                            }}
                          >
                            清除筛选条件
                          </Button>
                        </Space>
                      ) : trashMode ? (
                        <Space direction="vertical" size={4}>
                          <Text>回收站是空的</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            归档的记录会出现在这里，可随时恢复
                          </Text>
                        </Space>
                      ) : (
                        <Space direction="vertical" size={4}>
                          <Text>还没有任何记录</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            点击「新建记录」录入第一条
                          </Text>
                        </Space>
                      )}
                    </Empty>
                  ),
                }}
              />
            </>
          )}
        </>
      )}

      <Modal
        title={`${editing ? '编辑记录' : '新建记录'}${useSchemaForm ? ' · 协议表单' : ''}${useSchemaForm && formMode === 'view' ? ' · 只读' : ''}`}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={useSchemaForm ? undefined : handleSubmit}
        footer={useSchemaForm ? null : undefined}
        width={useSchemaForm ? 880 : 560}
        destroyOnHidden
        /* 长表单防溢出：协议表单字段多（如 8 字段两分区），不限高时弹窗底部
           「提交」按钮被挤出视口且 fixed 弹窗不随页面滚动 ⇒ 真实用户点不到。
           body 限高 + 内部滚动是 antd 长表单 Modal 的标准做法。 */
        styles={{ body: { maxHeight: 'calc(100vh - 280px)', overflowY: 'auto' } }}
      >
        {useSchemaForm && uiSchemaPipe.schema && entityCode ? (
          <>
            {uiSchemaPipe.status === 'warn-unknown-minor' && (
              <Alert
                type="warning"
                showIcon
                style={{ marginBottom: 12 }}
                message="界面协议有新版本字段，当前页面可能未展示全部内容"
              />
            )}
            <BoneDynamicForm
              schema={uiSchemaPipe.schema}
              fields={fields}
              record={(editing ?? undefined) as Record<string, unknown> | undefined}
              recordId={editing ? recordId(editing) : undefined}
              entityCode={entityCode}
              displayMode={formMode}
              ctx={uiCtx}
              onSubmit={handleSchemaSubmit}
              quiet
            />
          </>
        ) : (
          <>
            {/* 抽屉内状态进度：只在该实体**确有状态机**且记录状态在链上时显示。
            标签取自 status 字段的 ENUM 码表（makeStatusLabelOf），不硬编码业务状态。 */}
            {editing && stateChain.length > 0 && editingStateIdx >= 0 && (
              <Steps
                size="small"
                current={editingStateIdx}
                items={stateChain.map((code) => ({ title: statusLabelOf(code) }))}
                style={{ marginBottom: 16 }}
              />
            )}
            <Form form={form} layout="vertical">
              {conflict && (
                <Alert
                  type="warning"
                  showIcon
                  style={{ marginBottom: 12 }}
                  message={
                    conflictDiff.length > 0
                      ? `记录已被他人修改（${conflictDiff.length} 个字段与你的提交不同）`
                      : '记录已被他人修改'
                  }
                  description="下方表单已重新加载最新数据，请确认后再次提交。"
                  action={
                    conflictDiff.length > 0 ? (
                      <Button size="small" onClick={() => setDiffOpen(true)}>
                    查看差异
                      </Button>
                    ) : undefined
                  }
                />
              )}
              {formFields.length === 0 ? (
                <Alert
                  type="warning"
                  message="该模型尚未配置建模字段，请先在模型详情的「结构」页签中维护"
                />
              ) : (
                formFields.map((f) => <div key={f.id}>{renderFieldInput(f)}</div>)
              )}
            </Form>
          </>
        )}
      </Modal>

      {/* 412 冲突「查看差异」：逐字段展示「你的提交」vs「当前线上值」 */}
      <Modal
        title="与线上数据的差异"
        open={diffOpen}
        onCancel={() => setDiffOpen(false)}
        footer={<Button onClick={() => setDiffOpen(false)}>关闭</Button>}
        width={560}
        destroyOnHidden
      >
        <Table<ConflictDiffRow>
          rowKey="code"
          size="small"
          pagination={false}
          dataSource={conflictDiff}
          columns={[
            { title: '字段', dataIndex: 'name', width: 140, ellipsis: true },
            {
              title: '你的提交',
              dataIndex: 'mine',
              render: (v: unknown) =>
                v == null || v === '' ? <Text type="secondary">（空）</Text> : String(v),
            },
            {
              title: '当前线上值',
              dataIndex: 'theirs',
              render: (v: unknown) =>
                v == null || v === '' ? <Text type="secondary">（空）</Text> : String(v),
            },
          ]}
        />
        <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 12 }}>
          表单已载入线上最新值；确认后再提交，提交会自动带上新的 ETag。
        </Text>
      </Modal>

      {/* 状态机动作执行弹窗：按动作定义的 params_schema 动态渲染入参；无参动作直接确认执行 */}
      <Modal
        title={actionModal.def ? `动作：${actionModal.def.label}` : '执行动作'}
        open={actionModal.open}
        onCancel={() => setActionModal({ open: false, def: null, record: null })}
        onOk={handleActionSubmit}
        width={520}
        destroyOnHidden
      >
        {actionModal.def?.requiresIdempotencyKey && (
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 12 }}
            message="资金类动作已自动携带 Idempotency-Key，重复提交不会二次打款"
          />
        )}
        <Form form={actionForm} layout="vertical">
          {/* 入参按 Describe 声明的 params[] 动态渲染 —— 前端禁止硬编码参数名。
              控件按声明 type 选择（§4.8.2：string/number/integer/boolean/enum），
              使金额/数量类入参成为真正的数值、布尔类成为开关，而非一律文本输入框。 */}
          {actionModal.def?.params?.map((p) => {
            const ptype = (p.type || 'STRING').toUpperCase();
            const isEnum = ptype === 'ENUM' && p.enumValues && p.enumValues.length > 0;
            const isNumber = ptype === 'NUMBER' || ptype === 'INTEGER' || ptype === 'LONG';
            const isBool = ptype === 'BOOLEAN';
            const control = isEnum ? (
              <Select
                placeholder="请选择"
                options={(p.enumValues ?? []).map((v) => ({ value: v, label: v }))}
              />
            ) : isNumber ? (
              <InputNumber style={{ width: '100%' }} />
            ) : isBool ? (
              <Switch />
            ) : (
              <Input placeholder={p.label || p.code} />
            );
            return (
              <Form.Item
                key={p.code}
                name={p.code}
                label={p.label || p.code}
                valuePropName={isBool ? 'checked' : 'value'}
                rules={p.required && !isBool ? [{ required: true, message: `请填写${p.label || p.code}` }] : []}
              >
                {control}
              </Form.Item>
            );
          })}
          {actionModal.def && (!actionModal.def.params || actionModal.def.params.length === 0) && (
            <Alert type="info" showIcon message="该动作无需额外参数，确认即执行" />
          )}
        </Form>
      </Modal>

      {/* 记录详情抽屉（P2-②）：全字段只读 + 动作执行时间线。
          时间线 = meta_runtime_action_execution 按记录回放（P0-2 保证半事务也有行），
          状态色：成功=绿 / 失败=红 / 执行中=蓝 / 孤儿=橙（禁自动重放，须对账）。 */}
      <Drawer
        title={
          /* 标题同样按模式给标识：业务视图 →「报销单号：BX-…」，实施模式 → 带主记录 ID。
             标题是用户判别"我在看哪一条"的唯一锚点，用代理键做锚点等于没有锚点。 */
          detailDrawer.record
            ? `记录详情 · ${recordIdentityOf(detailDrawer.record, writableFields, viewerMode)}`
            : '记录详情'
        }
        open={detailDrawer.open}
        onClose={() => setDetailDrawer({ open: false, record: null })}
        width={560}
        destroyOnHidden
      >
        {detailDrawer.record && (
          <Space direction="vertical" size={16} style={{ width: '100%' }}>
            {execDetail ? (
              /* 协议详情视图（views.detail 投影）：页头关键信息条 + 分区逐控件。
                 * ctx 必须透传：字段级角色可见性（props.roles）在此生效——否则表单里
                 * 按角色隐藏的字段会在详情抽屉全量泄露（本组件曾不接 ctx，是唯一绕过
                 * fieldVisible 的渲染面）。 */
              <RuntimeDetailView detail={execDetail} fields={fields} record={detailDrawer.record} ctx={uiCtx} />
            ) : (
              /* 回落：全字段平铺（未配置 views.detail 的存量实体） */
              <Descriptions
                size="small"
                column={1}
                bordered
                styles={{ label: { width: 132 } }}
                items={visibleFieldsFor(fields, viewerMode)
                  .filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code))
                  .map((f) => ({
                    key: f.code,
                    label: f.displayName || f.code,
                    // ENUM 回显 label 而非裸 code（cnc → 数控机床）：与协议投影同口径
                    children: detailValueOf(f, detailDrawer.record?.[f.code]),
                  }))}
              />
            )}
            {/* 审批进度（引擎真源）：单据提交后「卡在谁那里 / 为什么退回」的唯一可查入口。
                放在动作时间线之上——用户先关心流程到哪一步，再看技术性执行留痕。 */}
            <ApprovalProgress data={approvalInfo} loading={approvalLoading} />
            <div>
              <Typography.Title level={5} style={{ marginTop: 0 }}>
                动作执行时间线
              </Typography.Title>
              {executionsLoading ? (
                <div style={{ padding: 24, textAlign: 'center' }}>
                  <Timeline pending="加载中…" />
                </div>
              ) : executions.length === 0 ? (
                <Empty description="该记录还没有执行过动作" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Timeline
                  items={executions.map((ex) => {
                    const meta =
                      ex.status === 1
                        ? { color: 'green', label: '成功' }
                        : ex.status === 2
                          ? { color: 'red', label: '失败' }
                          : ex.status === 3
                            ? { color: 'blue', label: '执行中' }
                            : { color: 'orange', label: '孤儿' };
                    return {
                      color: meta.color,
                      children: (
                        <div>
                          <Space size={8} wrap>
                            <Text strong>{ex.actionCode}</Text>
                            <Tag color={meta.color}>{meta.label}</Tag>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {ex.createdAt ? formatDate(ex.createdAt) : '—'}
                            </Text>
                            {ex.operatorId && (
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                操作人 {ex.operatorId}
                              </Text>
                            )}
                          </Space>
                          {ex.resultJson && (
                            <Typography.Paragraph
                              type="secondary"
                              style={{ marginBottom: 0, fontSize: 12, wordBreak: 'break-all' }}
                            >
                              {ex.resultJson}
                            </Typography.Paragraph>
                          )}
                        </div>
                      ),
                    };
                  })}
                />
              )}
            </div>
          </Space>
        )}
      </Drawer>

      {/* G10：子表明细（CHILD_TABLE）整组替换编辑器 —— 键驱动通用行编辑，
          必填/类型校验由后端 RuntimeWriteValidator 兜底（422 明细错误信息）。 */}
      <Modal
        title={`明细：${childModal.field?.displayName ?? ''}`}
        open={childModal.open}
        onCancel={() => setChildModal({ open: false, record: null, field: null })}
        onOk={handleChildrenSave}
        width={760}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          /* 同一件事，两种说法：业务用户得到「保存会覆盖全部明细行」的结果提示；
             实施者额外看到事务实现（同事务先删后插）与记录标识（含雪花主键）——
             后者是排障线索，前者只是噪音，甚至会引发"什么叫事务"的支持工单。 */
          message={implMode ? '保存时整组替换明细行（同事务先删后插）' : '保存后用本次填写的内容覆盖全部明细行'}
          description={recordIdentityOf(childModal.record, writableFields, viewerMode)}
        />
        <Space style={{ marginBottom: 12 }} wrap>
          <Button
            size="small"
            type="dashed"
            icon={<PlusOutlined />}
            /* 新增行带稳定临时行键 __rowKey（保存前剔除），替代 rowKey 的废弃 index 参数 */
            onClick={() => setChildRows((r) => [...r, { __rowKey: `new-${++childRowSeq.current}` }])}
          >
            添加明细行
          </Button>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {childRows.length} 行
          </Text>
          <Text style={{ fontSize: 12, marginLeft: 16 }}>
            合计：
            <Text strong style={{ fontFamily: 'ui-monospace, monospace' }}>
              ¥{formatNumber(childSumOf(childRows), { minimumFractionDigits: 2 })}
              {/* C3：保存后由后端 ReimbursementChildAmountAggregator 事务内回填头表 amount，前端实时预览 */}
            </Text>
            <Text type="secondary" style={{ fontSize: 12, marginLeft: 6 }}>（保存后自动回填头表金额）</Text>
          </Text>
        </Space>
        <Table<RuntimeChildRow>
          /* 行键：既有行用后端 id，新增行用临时 __rowKey —— 不再取用已废弃的 index 参数 */
          rowKey={(row) => String(row.__rowKey ?? row.id ?? '')}
          size="small"
          pagination={false}
          scroll={{ x: true }}
          dataSource={childRows}
          columns={
            [
              ...childColumnsOf(childRows).map((c) => ({
                title: c,
                dataIndex: c,
                key: c,
                ellipsis: true,
                render: (v: unknown) => {
                  /* 明细金额类列（amount/price/数量）按 2 位标度渲染，与头表合计
                     （childSumOf 已用 formatNumber）保持一致；非数值原样展示。 */
                  if (isNumericColumn(c)) {
                    const n = typeof v === 'string' ? Number(v) : v;
                    if (typeof n === 'number' && Number.isFinite(n)) {
                      return (
                        <span style={{ fontFamily: 'ui-monospace, monospace' }}>
                          {formatNumber(n, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                        </span>
                      );
                    }
                  }
                  return formatCell(v);
                },
              })),
              {
                title: '操作',
                key: 'ops',
                width: 140,
                render: (_: unknown, row: RuntimeChildRow, idx: number) => (
                  <Space>
                    <Button size="small" type="link" onClick={() => openChildRowEditor(idx, row)}>
                      编辑
                    </Button>
                    <Popconfirm
                      title="删除该明细行？"
                      onConfirm={() => setChildRows((r) => r.filter((_, i) => i !== idx))}
                    >
                      <Button size="small" type="link" danger>
                        删除
                      </Button>
                    </Popconfirm>
                  </Space>
                ),
              },
            ] as ColumnsType<RuntimeChildRow>
          }
        />
      </Modal>

      {/* 明细行编辑小 Form：键驱动（union 行键），必填/类型由后端兜底 */}
      <ChildRowEditorModal
        open={childRowEditor.open}
        fields={childColumnsOf(childRows)}
        initial={childRowEditor.row}
        onCancel={() => setChildRowEditor({ open: false, idx: -1, row: null })}
        onSave={(next) => {
          const idx = childRowEditor.idx;
          if (idx >= 0) {
            // 替换时保留该行的 __rowKey（新增行编辑一次后仍需稳定行键）
            setChildRows((r) =>
              r.map((row, i) => (i === idx ? { ...next, __rowKey: row.__rowKey ?? next.__rowKey } : row)),
            );
          } else {
            // 走编辑器直接新增的行同样补一个临时行键（与「添加明细行」同款）
            setChildRows((r) => [...r, { ...next, __rowKey: next.__rowKey ?? `new-${++childRowSeq.current}` }]);
          }
          setChildRowEditor({ open: false, idx: -1, row: null });
        }}
      />
    </div>
  );
};

// ----- 内部工具 -----

/**
 * 把 `GET /actions` 的后端领域对象适配为 Describe 视图形状。
 *
 * 为什么需要适配而不是两套渲染：page 级 `meta.allowedActionsByStatus` 是唯一 Describe 形态（§4.3.1），
 * `GET /actions` 只是它的降级通道。两者在前端必须收敛成同一种视图对象，
 * 否则「按钮渲染」会出现两条分支、两次权限判断 —— 那是双真源的另一种写法。
 */
function toActionView(def: RuntimeActionDef): RuntimeActionView {
  return {
    code: def.actionCode,
    label: def.label,
    kind: def.escape ? 'escape' : 'state_transition',
    fromStatus: def.fromStatus,
    toStatus: def.toStatus,
    confirm: def.escape || def.requiresIdempotency,
    requiresIdempotencyKey: def.requiresIdempotency,
    permission: def.permissionCode,
    params: (def.params ?? []).map((p) => ({
      code: p.name,
      label: p.label ?? null,
      type: p.type,
      required: p.required,
      enumValues: p.values ?? null,
    })),
  };
}

function normalizeRuntimePublished(list: MetaEntity[]): MetaEntity[] {
  return list.filter(
    (e) => e.deliveryMode === META_DELIVERY_RUNTIME && e.status === META_ENTITY_PUBLISHED,
  );
}

function normalizePageRecords(page: { records?: MetaEntity[] | MetaField[] }): MetaField[] {
  return (page.records as MetaField[]) || [];
}

function normalizePage<T>(raw: { records?: T[]; total?: string | number; page?: number; size?: number }) {
  return {
    records: raw.records ?? [],
    total: typeof raw.total === 'string' ? Number(raw.total) : (raw.total ?? 0),
    page: raw.page ?? 1,
    size: raw.size ?? 20,
  };
}

// ----- G10 关系工具（REFERENCE / CHILD_TABLE / ATTACHMENT） -----

/** 从 CHILD_TABLE 字段的 validation_rules 解析子表声明。 */
export interface ChildTableSpec {
  childEntityCode: string;
  foreignKey: string;
  orderColumn?: string;
  cascadeDelete?: boolean;
  minRows?: number;
  maxRows?: number;
}

export function childSpecOf(field: MetaField): ChildTableSpec | null {
  if ((field.type || '').toUpperCase() !== 'CHILD_TABLE') return null;
  const raw = field.validationRules;
  if (!raw) return null;
  try {
    const rules = typeof raw === 'string' ? JSON.parse(raw) : raw;
    const ct = (rules as { childTable?: Partial<ChildTableSpec> })?.childTable;
    if (!ct?.childEntityCode || !ct?.foreignKey) return null;
    return {
      childEntityCode: ct.childEntityCode,
      foreignKey: ct.foreignKey,
      orderColumn: ct.orderColumn,
      cascadeDelete: ct.cascadeDelete ?? true,
      minRows: ct.minRows ?? 0,
      maxRows: ct.maxRows,
    };
  } catch {
    return null;
  }
}

/** 剔除 enrich 注入的 code__label 键（仅供回显，提交会被后端过滤但前端先清，防脏载荷） */
export function stripLabelKeys(row: Record<string, unknown>): RuntimeChildRow {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(row)) {
    if (!k.endsWith('__label')) out[k] = v;
  }
  return out;
}

/**
 * 剔除新增明细行的前端临时行键 `__rowKey`。
 *
 * <p>`__rowKey` 是 UI 记账（antd 5 已废弃 rowKey 的 index 参数，新增行尚无后端 id
 * 故需稳定行键），不是子实体业务列，提交前必须清掉。
 */
export function stripChildRowKey(row: RuntimeChildRow): RuntimeChildRow {
  const { __rowKey, ...rest } = row;
  void __rowKey; // 显式消费，避免 no-unused-vars 误报（解构剔除语义）
  return rest;
}

/**
 * 明细行可编辑列 = union 行键，排除外键/主键/审计列/标签键。
 *
 * <p>`__rowKey` 是新增行的前端临时行键（antd rowKey 去 index 参数后的替代方案），
 * 属于 UI 记账而非业务字段 —— 必须在此排除，否则会作为「可编辑列」渲染并进保存载荷。
 */
const CHILD_SKIP_KEYS = new Set([
  '__rowKey',
  'id',
  'tenant_id',
  'claim_id',
  'created_by',
  'updated_by',
  'created_at',
  'updated_at',
  'deleted',
  'version',
]);

export function childColumnsOf(rows: RuntimeChildRow[]): string[] {
  const seen = new Set<string>();
  for (const r of rows) {
    for (const k of Object.keys(r)) {
      if (k.endsWith('__label')) continue;
      if (CHILD_SKIP_KEYS.has(k)) continue;
      seen.add(k);
    }
  }
  return [...seen];
}

/** 明细行字段控件启发式：数值列（amount/price/数量/*_count/*_id）→ InputNumber，其余 Input */
export function isNumericColumn(code: string): boolean {
  const c = code.toLowerCase();
  return (
    c === 'amount' ||
    c === 'price' ||
    c === 'sort_order' ||
    c.endsWith('_id') ||
    c.endsWith('_count') ||
    c.endsWith('_qty') ||
    c.endsWith('_num')
  );
}

/** G10 附件清单编辑器（受控组件：Form.Item value/onChange）。 */
interface AttachmentItem {
  name?: string;
  url?: string;
  size?: number | null;
}

const AttachmentEditor: React.FC<{
  value?: AttachmentItem[];
  onChange?: (v: AttachmentItem[]) => void;
}> = ({ value = [], onChange }) => {
  const items = Array.isArray(value) ? value : [];
  const patch = (idx: number, key: keyof AttachmentItem, v: unknown) => {
    const next = items.map((it, i) => (i === idx ? { ...it, [key]: v } : it));
    onChange?.(next);
  };
  return (
    <Space direction="vertical" style={{ width: '100%' }} size={4}>
      {items.map((it, idx) => (
        <Space key={idx} style={{ width: '100%' }} size={4} wrap>
          <Input
            placeholder="附件名称"
            style={{ width: 160 }}
            value={it.name}
            onChange={(e) => patch(idx, 'name', e.target.value)}
          />
          <Input
            placeholder="URL（http/https）"
            style={{ width: 240 }}
            value={it.url}
            onChange={(e) => patch(idx, 'url', e.target.value)}
          />
          <InputNumber
            placeholder="大小(KB)"
            style={{ width: 100 }}
            min={0}
            value={it.size ?? null}
            onChange={(v) => patch(idx, 'size', v)}
          />
          <Button
            size="small"
            type="link"
            danger
            onClick={() => onChange?.(items.filter((_, i) => i !== idx))}
          >
            删除
          </Button>
        </Space>
      ))}
      <Button
        size="small"
        type="dashed"
        icon={<PlusOutlined />}
        onClick={() => onChange?.([...items, { name: '', url: '', size: null }])}
      >
        添加附件
      </Button>
      <Text type="secondary" style={{ fontSize: 12 }}>
        当前为附件元数据清单（名称/URL/大小）；真实文件上传通道属 v1.1 路线，后端 TEXT 列存 JSON 数组串。
      </Text>
    </Space>
  );
};

/** 明细行编辑小 Form（键驱动：union 行键渲染控件，必填/类型由后端校验兜底）。 */
const ChildRowEditorModal: React.FC<{
  open: boolean;
  fields: string[];
  initial: RuntimeChildRow | null;
  onCancel: () => void;
  onSave: (row: RuntimeChildRow) => void;
}> = ({ open, fields, initial, onCancel, onSave }) => {
  const [form] = Form.useForm();
  useEffect(() => {
    if (open) {
      form.resetFields();
      if (initial) form.setFieldsValue(initial);
    }
  }, [open, initial, form]);
  return (
    <Modal
      title={initial ? '编辑明细行' : '新增明细行'}
      open={open}
      onCancel={onCancel}
      width={520}
      destroyOnHidden
      onOk={() => {
        form
          .validateFields()
          .then((values) => {
            const row: RuntimeChildRow = {};
            for (const k of Object.keys(values)) {
              if (values[k] === undefined) continue;
              row[k] = values[k] as never;
            }
            // 新增行由后端补主键/外键；若 initial 存在则保留其 id（替换更新）
            if (initial && initial.id != null) row.id = initial.id;
            // 保留新增行的前端临时行键：否则编辑一次后行键退化为空，编辑态行会与
            // 新增行撞 React key（antd rowKey 已无 index 兜底）。
            if (initial?.__rowKey != null) row.__rowKey = initial.__rowKey;
            onSave(row);
          })
          .catch(() => {
            /* 校验未过，停留弹窗 */
          });
      }}
    >
      {fields.length === 0 ? (
        <Alert type="warning" showIcon message="明细行尚无字段，请先添加一行后再编辑" />
      ) : (
        <Form form={form} layout="vertical">
          {fields.map((c) => (
            <Form.Item key={c} name={c} label={c}>
              {isNumericColumn(c) ? (
                <InputNumber style={{ width: '100%' }} />
              ) : (
                <Input />
              )}
            </Form.Item>
          ))}
        </Form>
      )}
    </Modal>
  );
};

export default RuntimeDataManagement;
