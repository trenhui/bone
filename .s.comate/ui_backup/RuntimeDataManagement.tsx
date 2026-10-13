import { useCallback, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  Alert,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  errorMessage,
  metadataEntityApi,
  metadataFieldApi,
  runtimeRecordApi,
} from '../services/metadataApi';
import type {
  MetaEntity,
  MetaField,
  RuntimeRecord,
  RuntimeActionDef,
  RuntimeActionView,
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
import { Auth, AuthButton } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import {
  enumOptionsOf,
  isEnumField,
  isRenderSupported,
  loadOverlay,
  projectColumns,
  tableSizeOf,
} from '../utils/columnProjection';

const { Text } = Typography;

function recordId(row: RuntimeRecord): string {
  const id = row.id;
  return id != null ? String(id) : '';
}

function formatCell(value: unknown): string {
  if (value == null) return '—';
  if (typeof value === 'object') return JSON.stringify(value);
  return String(value);
}

/** ENUM 列表 Tag 色板（§5.5：MetaList ENUM 列 → Tag 徽标，可选色板） */
const ENUM_TAG_COLORS = ['blue', 'green', 'orange', 'purple', 'cyan', 'magenta', 'gold', 'geekblue', 'volcano', 'lime'];

/** ENUM 单元格：码值 → Tag 徽标（选项按定义序取色，稳定不闪烁；未命中码值兜底灰 Tag） */
function renderEnumCell(value: unknown, field: MetaField): ReactNode {
  if (value == null || value === '') return '—';
  const opts = enumOptionsOf(field);
  const idx = opts.findIndex((o) => o.code === String(value));
  const opt = idx >= 0 ? opts[idx] : null;
  if (!opt) return <Tag>{String(value)}</Tag>;
  return <Tag color={ENUM_TAG_COLORS[idx % ENUM_TAG_COLORS.length]}>{opt.label}</Tag>;
}

export interface RuntimeDataManagementProps {
  /** 嵌入实体详情「数据」Tab 时传入实体 id；为空则走独立全局「运行时数据」路由（带实体选择器） */
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
  /** 数据 Tab 空态 CTA：由 /…/data 回溯到实体详情并定位「结构」Tab（B1） */
  const structurePath = `${location.pathname.replace(/\/data\/?$/, '')}?tab=structure`;

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
  /** W0a2：q/sort 接线 —— 单条件过滤（field:value）*/
  const [searchField, setSearchField] = useState<string | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  /** sort 契约：逗号分隔列名，`-` 前缀降序（RuntimePageQuery.parseSort） */
  const [sortField, setSortField] = useState<string | undefined>(undefined);
  const [sortDesc, setSortDesc] = useState(false);

  /** 运行时状态机动作定义（按实体加载一次，按行 status 渲染可用按钮） */
  const [actionDefs, setActionDefs] = useState<RuntimeActionDef[]>([]);
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

  /** 子表（CHILD_TABLE）编辑弹窗状态：当前主记录 + 声明字段 */
  const [childModal, setChildModal] = useState<{
    open: boolean;
    record: RuntimeRecord | null;
    field: MetaField | null;
  }>({ open: false, record: null, field: null });
  /** 明细编辑中的行（键驱动渲染，不依赖子实体 schema） */
  const [childRows, setChildRows] = useState<RuntimeChildRow[]>([]);
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

  const writableFields = useMemo(
    () => fields.filter((f) => !RUNTIME_READONLY_FIELDS.has(f.code)),
    [fields],
  );

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
      writableFields.filter(
        (f) => f.code !== 'status' && (f.type || '').toUpperCase() !== 'CHILD_TABLE',
      ),
    [writableFields],
  );

  /** 列投影（预览 ≡ 数据 单一真源）：始终应用本地覆盖 */
  const overlay = useMemo(
    () => (entityCode ? loadOverlay(entityCode) : null),
    // overlay 随选中实体变化重新读取（localStorage 变更由界面 Tab 写入后重载）
    [entityCode, fields],
  );
  const projectedFields = useMemo(
    () => projectColumns(writableFields, overlay),
    [writableFields, overlay],
  );

  // 独立全局路由（/runtime、无 id）：加载 RUNTIME 已发布实体供选择器（W0-2：size 50→500，避免第 51 个后不可选）
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
        const fromQuery = searchParams.get('entity');
        // W0-1 / PRD D12：无 :id 且无 ?entity= 时**不替用户决策**——保持空态由用户显式选择。
        // 旧实现会 setEntityCode(runtimePublished[0].code)，即「/runtime 静默选中第一个实体」：
        // 用户以为在看自己选的实体，实际看到的是列表首项的数据（静默串实体）。
        if (fromQuery && runtimePublished.some((e) => e.code === fromQuery)) {
          setEntityCode(fromQuery);
        }
      });
    // entityCode 仅用于首次为空时初始化，不应作为依赖
  }, [searchParams, resolvedId]);

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
   */
  const load = useCallback(async () => {
    if (!entityCode) return;
    setLoading(true);
    try {
      const fieldsParam = projectedFields.length
        ? projectedFields.map((f) => f.code).join(',')
        : undefined;
      const query = {
        page,
        size: pageSize,
        fields: fieldsParam,
        // q 契约：field:value（后端 RuntimePageQuery.parseFilter）
        q: searchField && keyword ? `${searchField}:${keyword}` : undefined,
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
    searchField,
    keyword,
    sortField,
    sortDesc,
    trashMode,
  ]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setEditingEtag(undefined);
    setConflict(false);
    setConflictDiff([]);
    form.resetFields();
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

  /** 保存明细：整组替换（先删后插同一事务），空数组即清空 */
  const handleChildrenSave = async () => {
    const { record, field } = childModal;
    if (!record || !field || !entityCode) return;
    const rid = recordId(record);
    if (!rid) return;
    const spec = childSpecOf(field);
    if (!spec) return;
    try {
      const res = await runtimeRecordApi.replaceChildren(
        entityCode,
        rid,
        spec.childEntityCode,
        childRows,
      );
      if (res.code === 200) {
        message.success(`明细已保存（${res.data?.count ?? childRows.length} 行）`);
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
   * 某行当前可用的动作。
   *
   * 真源优先级：① page 级 `meta.allowedActionsByStatus`（后端已按权限裁剪）——
   * 拿到即用，零额外请求；② 降级到 `GET /actions` 全量 + 前端按 fromStatus 过滤。
   */
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
        const flow = res.data?.fromStatus ? `${res.data.fromStatus} → ${res.data.toStatus}` : '';
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
    const values = await form.validateFields();
    const body = buildBody(values);
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
    const cols: ColumnsType<RuntimeRecord> = projectedFields.map((f) => ({
      title: f.displayName || f.code,
      dataIndex: f.code,
      key: f.code,
      ellipsis: true,
      sorter: true,
      sortOrder: sortField === f.code ? (sortDesc ? 'descend' : 'ascend') : null,
      render: (_: unknown, row) => {
        if (isEnumField(f)) return renderEnumCell(row[f.code], f);
        const ftype = (f.type || '').toUpperCase();
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
        return formatCell(row[f.code]);
      },
    }));
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
      width: 200,
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
            {actionsOf(row).map((def) => (
              <AuthButton
                key={def.code}
                code={def.permission ?? BonePermissionCodes.METADATA_RUNTIME_WRITE}
                type="link"
                size="small"
                onClick={() => openAction(row, def)}
              >
                {def.label}
              </AuthButton>
            ))}
            {/* G10：CHILD_TABLE 声明字段 → 「明细」入口（整组替换编辑器） */}
            {writableFields
              .filter((f) => (f.type || '').toUpperCase() === 'CHILD_TABLE')
              .map((f) => (
                <AuthButton
                  key={`child-${f.code}`}
                  code={BonePermissionCodes.METADATA_RUNTIME_WRITE}
                  type="link"
                  size="small"
                  onClick={() => openChildren(row, f)}
                >
                  {f.displayName || f.code}
                </AuthButton>
              ))}
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
            这里是<Text strong>跨实体</Text>的数据入口，适合你不确定数据在哪个模型、或者要跨多个模型巡检记录时使用。
            若你已知要操作哪个实体，请从「实体」列表点行内「数据」按钮，或在实体工作区打开「数据」页签——
            那两条路径会自动锁定当前实体，不会看错数据。
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
          <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate} disabled={!entityCode}>
            新建记录
          </AuthButton>
        </Space>
      )}

      {selectedEntity && (
        <Text type="secondary" style={{ display: 'block', marginBottom: 12 }}>
          物理表：<Text code>{selectedEntity.tableName}</Text> · 交付模式：
          {DELIVERY_MODE[selectedEntity.deliveryMode ?? 0]} · 状态：
          {ENTITY_STATUS[selectedEntity.status]}
        </Text>
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
                    {!trashMode && (
                      <AuthButton code={BonePermissionCodes.METADATA_RUNTIME_WRITE} type="primary" icon={<PlusOutlined />} onClick={openCreate}>
                      新建记录
                      </AuthButton>
                    )}
                    <Button icon={<ReloadOutlined />} onClick={load}>刷新</Button>
                  </>
                )}
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
                {/* W0a2：q 接线（契约 = field:value） */}
                <Select
                  allowClear
                  style={{ minWidth: 160 }}
                  placeholder="搜索字段"
                  value={searchField}
                  onChange={(v) => { setSearchField(v); setPage(1); }}
                  options={projectedFields.map((f) => ({ value: f.code, label: f.displayName || f.code }))}
                />
                <Input.Search
                  allowClear
                  style={{ width: 240 }}
                  placeholder="搜索值（精确/模糊取决于后端规则）"
                  value={keyword}
                  disabled={!searchField}
                  onChange={(e) => setKeyword(e.target.value)}
                  onSearch={() => setPage(1)}
                />
              </Space>

              <Table<RuntimeRecord>
                rowKey={(row) => recordId(row) || JSON.stringify(row)}
                loading={loading}
                columns={tableColumns}
                dataSource={data}
                size={tableSizeOf(overlay?.density)}
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
              />
            </>
          )}
        </>
      )}

      <Modal
        title={editing ? '编辑记录' : '新建记录'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={handleSubmit}
        width={560}
        destroyOnHidden
      >
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
          {/* 入参按 Describe 声明的 params[] 动态渲染 —— 前端禁止硬编码参数名 */}
          {actionModal.def?.params?.map((p) => (
            <Form.Item
              key={p.code}
              name={p.code}
              label={p.label || p.code}
              rules={p.required ? [{ required: true, message: `请填写${p.label || p.code}` }] : []}
            >
              {(p.type || '').toUpperCase() === 'ENUM' && p.enumValues && p.enumValues.length ? (
                <Select
                  placeholder="请选择"
                  options={p.enumValues.map((v) => ({ value: v, label: v }))}
                />
              ) : (
                <Input placeholder={p.label || p.code} />
              )}
            </Form.Item>
          ))}
          {actionModal.def && (!actionModal.def.params || actionModal.def.params.length === 0) && (
            <Alert type="info" showIcon message="该动作无需额外参数，确认即执行" />
          )}
        </Form>
      </Modal>
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
          message="保存时整组替换明细行（同事务先删后插）"
          description={
            childModal.record
              ? `主记录：${formatCell(childModal.record.id)}`
              : '未选中主记录'
          }
        />
        <Space style={{ marginBottom: 12 }} wrap>
          <Button size="small" type="dashed" icon={<PlusOutlined />} onClick={() => setChildRows((r) => [...r,])}>
            添加明细行
          </Button>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {childRows.length} 行
          </Text>
        </Space>
        <Table<RuntimeChildRow>
          rowKey={(row, i) => String(row.id ?? `new-${i}`)}
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
                render: (v: unknown) => formatCell(v),
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
            setChildRows((r) => r.map((row, i) => (i === idx ? next : row)));
          } else {
            setChildRows((r) => [...r, next]);
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

/** 明细行可编辑列 = union 行键，排除外键/主键/审计列/标签键 */
const CHILD_SKIP_KEYS = new Set([
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
