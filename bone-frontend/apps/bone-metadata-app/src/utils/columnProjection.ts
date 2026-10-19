import type { MetaField } from '../types';
import { parseEnumValues, type EnumOption } from '@bone/shared-types';
import type { ListView } from './uiSchemaEngine';

/**
 * 界面列投影（预览 ≡ 数据 的单一真源）。
 *
 * 为什么存在：PRD v0.32 §0.6.3 ——「界面预览列 ≡ 发布后数据列」必须由**同一个纯函数**
 * 投影得到，而不是靠两边各自写一遍后靠测试保证。界面 Tab 的预览与运行时数据 Tab 都调用
 * `projectColumns(fields, overlay)`，结构上杜绝漂移。
 *
 * MVP 覆盖存储：localStorage（PRD D2）。v1.1 服务端化（团队级共享）时只需替换
 * `loadOverlay`/`saveOverlay` 的底层实现，投影逻辑不变。
 */

export interface ColumnOverride {
  /** 是否在界面/数据列表中可见 */
  visible: boolean;
  /** 覆盖显示名（缺省回退到字段 displayName） */
  displayName?: string;
  /** 列序（升序；缺省按 sortOrder → 定义顺序） */
  order?: number;
}

export interface UiOverlay {
  /** 覆盖格式版本，结构变更时 +1；版本不符的本地覆盖将被丢弃 */
  version: number;
  /** 以字段 code 为键的覆盖表 */
  overrides: Record<string, ColumnOverride>;
  /** 密度偏好（可选） */
  density?: 'comfortable' | 'compact';
}

/* ===================== 个人视图（多视图 bundle） ===================== */

/**
 * 视图筛选条件（与运行时 q 串同构：`{field}:{value}` 或 `{field}:{op}:{value}`）。
 *
 * <p>刻意用**可序列化标量**而非 React 状态对象：个人视图要落服务端 blob
 * （不透明 JSON），复杂引用无法 JSON 化/跨版本比对。
 */
export interface ViewFilter {
  /** 字段 code；空表示"无字段限定"（与运行时 appliedConditions 同口径） */
  field?: string;
  /** 运算符（eq/ne/gt/lt/ge/le/like/in…，空串按 eq 处理） */
  op: string;
  value: string;
}

/**
 * 命名个人视图（对标 Airtable / Notion personal views）：一套独立的
 * 显隐/列序/改名/密度偏好。id 由前端生成（`v_` 前缀），name 用户可改。
 *
 * <p><b>为什么视图还要存筛选/排序</b>：业界把「视图 = 列 + 筛选 + 排序」的完整快照
 * 当作保存视图的单位（Airtable view / Notion database view / Salesforce List View
 * 的 saved filter）。此前本仓视图只存显隐+密度，导致切视图后筛选与排序被静默清空——
 * 用户配好「我的待审：只看待我审批 + 按金额倒序」，切去别的视图再切回来，条件消失，
 * 表现为"视图不好用"。补齐后视图才是可复用的工作台面。
 *
 * <p>这两个字段是**可选的**（老 blob 无此键 ⇒undefined ⇒ 页面不应用筛选），
 * 因此扩展不需要版本迁移，也不会让存量用户的视图突然多出条件。
 */
export interface PersonalView {
  id: string;
  name: string;
  overrides: Record<string, ColumnOverride>;
  density?: 'comfortable' | 'compact';
  /** 该视图保存的筛选条件（空/缺省 = 不应用筛选） */
  filters?: ViewFilter[];
  /** 排序字段 code（缺省 = 不排序） */
  sortField?: string;
  /** 是否降序（仅 sortField 存在时有意义） */
  sortDesc?: boolean;
}

/**
 * 个人偏好存储包（blob 级真源，v3）。
 *
 * <p>演进决策（无需后端迁移）：ui-overlay 后端存的是**不透明 JSON blob**
 * （PUT 收 JsonNode、键由 JWT 推导 tenant/user），因此「单套偏好 → 多套命名视图」
 * 完全在 blob 内完成：v2 扁平存量 `{version:2, overrides, density}` 由
 * {@link toBundle} 无损包一层为单视图 bundle，用户偏好不丢。
 *
 * <p>投影层（projectColumns/projectRuntimeColumns）仍只认扁平 {@link UiOverlay}——
 * 由 {@link activeOverlay} 解析生效视图，两处消费方（界面 Tab 预览 / 运行时数据表）
 * 各自解析，避免把多视图复杂度渗进投影纯函数。
 */
export interface UiOverlayBundle {
  /** 存储格式版本（bundle 结构）；与扁平 UiOverlay.version 是两代口径 */
  version: number;
  /** 生效视图 id；缺失/非法时按首个视图兜底（不抛异常） */
  activeViewId: string;
  views: PersonalView[];
}

/** 密度 → antd Table size（界面预览与数据表共用，避免两处口径漂移） */
export function tableSizeOf(density?: 'comfortable' | 'compact'): 'middle' | 'small' {
  return density === 'compact' ? 'small' : 'middle';
}

/** 扁平覆盖结构版本（v2 存量口径；bundle 内的视图不再重复携带） */
export const UI_OVERLAY_VERSION = 2;

/** bundle 存储结构版本（v3：多视图） */
export const UI_OVERLAY_BUNDLE_VERSION = 3;

/** 默认视图 id / 名称（v2 存量迁移与新建实体的占位视图） */
export const DEFAULT_VIEW_ID = 'default';
export const DEFAULT_VIEW_NAME = '默认视图';

/** UI 渲染契约（PRD §5.5）支持的字段类型；其余一律显式降级，禁静默渲染空控件 */
export const SUPPORTED_RENDER_TYPES = new Set([
  'STRING',
  'TEXT',
  'INTEGER',
  'LONG',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'ENUM',
  // G10（PRD v0.46）：关系字段受支持 —— REFERENCE 渲染远程下拉（含 __label），
  // CHILD_TABLE/ATTACHMENT 由弹窗内专用编辑器（明细行 / 附件清单）处理，不再降级 Alert。
  'REFERENCE',
  'CHILD_TABLE',
  'ATTACHMENT',
]);

/**
 * 该字段是否有可用控件/列渲染。
 * 未支持类型必须在表单与预览里显式提示字段名 + 类型，而不是渲染成裸 Input 或空白
 * （PRD §5.5「禁止静默渲染空」）。
 */
export function isRenderSupported(field: MetaField): boolean {
  return SUPPORTED_RENDER_TYPES.has((field.type || '').toUpperCase());
}

/** 是否为 ENUM 字段（控件渲染/列投影需特殊处理） */
export function isEnumField(field: MetaField): boolean {
  return (field.type || '').toUpperCase() === 'ENUM';
}

/** 取 ENUM 字段的选项（优先 field.enumValues，兜底空） */
export function enumOptionsOf(field: MetaField): EnumOption[] {
  return parseEnumValues(field.enumValues);
}

export function defaultOverlay(fields: MetaField[]): UiOverlay {
  // 初始顺序与结构 Tab 的字段排序一致（sortOrder → 定义顺序），保证「默认 = 结构顺序」
  const ordered = [...fields].sort(
    (a, b) => (a.sortOrder ?? 9999) - (b.sortOrder ?? 9999),
  );
  return {
    version: UI_OVERLAY_VERSION,
    overrides: Object.fromEntries(
      ordered.map((f, i) => [f.code, { visible: true, displayName: f.displayName, order: i }]),
    ),
  };
}

/**
 * 列投影：过滤不可见列 → 覆盖显示名 → 按覆盖列序稳定排序。
 * 纯函数 —— 界面预览与运行时数据共用，保证两处列定义一致。
 */
export function projectColumns(fields: MetaField[], overlay?: UiOverlay | null): MetaField[] {
  if (!overlay) return fields;
  const visible: MetaField[] = [];
  for (const f of fields) {
    const ov = overlay.overrides[f.code];
    if (ov && ov.visible === false) continue;
    const displayName = ov?.displayName ?? f.displayName;
    visible.push(displayName === f.displayName ? f : { ...f, displayName });
  }
  // 稳定排序：有 order 的按 order，无 order（新增字段未进覆盖表）排在末尾并保持相对序
  const withIdx = visible.map((f, i) => ({ f, i, order: overlay.overrides[f.code]?.order }));
  withIdx.sort((a, b) => {
    if (a.order != null && b.order != null) return a.order - b.order;
    if (a.order != null) return -1;
    if (b.order != null) return 1;
    return a.i - b.i;
  });
  return withIdx.map((x) => x.f);
}

/** 运行时投影列：字段 + 契约列宽/固定列（个人 overlay 不含这两个维度） */
export interface ProjectedColumn {
  field: MetaField;
  /** 契约列宽（px）；无契约列时 undefined，由表格自适应 */
  width?: number;
  /** 契约固定列方向（横向滚动时钉住；left=主键侧，right=操作列侧） */
  pinned?: 'left' | 'right';
}

/**
 * 运行时列表列投影（P1 · 真源收敛）：
 * 契约（执行期 ui-schema `views.list.columns`）定义 列集合/顺序/列宽/固定列/列标题，
 * 个人 overlay 仅叠加 显隐/密度/个人改名 —— 偏好层不颠覆契约层（对标
 * Salesforce Search Layouts 组织级 + List View 个人偏好双轨）。
 *
 * 语义（strict 集合，对齐 Salesforce Search Layouts / Notion 视图属性）：
 * - 列集合：**契约即完整列集合**——未列入契约的字段不出现在运行时列表
 *   （策划 6 列的实体不该被 20 个字段淹没；新字段由建模者在设计器显式加入，
 *   数据页「交付口径对照」以「未在契约」提示 + 跳转设计器引导，不静默追加）；
 * - 可见性：契约 `visible===false` 或个人 `visible===false` 任一为真即隐藏；
 * - 列标题：个人改名（仅确实改过名时）> 契约 `format.title` > 字段 displayName；
 * - 契约引用已删字段：跳过（防幽灵列）。
 *
 * 无契约列（含从未配 ui-schema / 只配了 searchFields 等）时整体回落
 * {@link projectColumns} 旧行为，保证零回归。
 */
export function projectRuntimeColumns(
  fields: MetaField[],
  overlay?: UiOverlay | null,
  listView?: ListView | null,
): ProjectedColumn[] {
  const contractCols = (listView?.columns ?? []).filter((c) => !!c.fieldCode);
  if (contractCols.length === 0) {
    return projectColumns(fields, overlay).map((field) => ({ field }));
  }
  const byCode = new Map(fields.map((f) => [f.code, f]));
  const out: ProjectedColumn[] = [];
  for (const c of contractCols) {
    const f = byCode.get(c.fieldCode);
    if (!f) continue; // 契约引用已删字段：跳过
    const ov = overlay?.overrides[c.fieldCode];
    if (c.visible === false || ov?.visible === false) continue; // 任一维度隐藏即隐藏
    const contractTitle =
      typeof c.format?.title === 'string' && c.format.title ? c.format.title : undefined;
    // 个人改名仅在「确实改过」（与字段名不同）时生效：defaultOverlay 会为每个字段生成
    // displayName=字段名的占位条目，那不是用户意图，不能拿来压契约标题
    const personalRename =
      ov?.displayName && ov.displayName !== f.displayName ? ov.displayName : undefined;
    const displayName = personalRename ?? contractTitle ?? f.displayName;
    out.push({
      field: displayName === f.displayName ? f : { ...f, displayName },
      width: c.width,
      pinned: c.pinned,
    });
  }
  return out;
}

/**
 * 未列入契约的字段（strict 集合下运行时不可见）。
 * 供数据页「交付口径对照」与「列设置」显式提示 + 跳转设计器加入——把「新字段不见了」
 * 从静默事故变为可见引导。无契约列时返回空（整页回落纯偏好模式）。
 */
export function fieldsOutsideContract(fields: MetaField[], listView?: ListView | null): MetaField[] {
  const contractCols = (listView?.columns ?? []).filter((c) => !!c.fieldCode);
  if (contractCols.length === 0) return [];
  const inContract = new Set(contractCols.map((c) => c.fieldCode));
  return fields.filter((f) => !inContract.has(f.code));
}

/** 新建实体的默认 bundle：单个「默认视图」，覆盖表 = 默认投影 */
export function defaultBundle(fields: MetaField[]): UiOverlayBundle {
  return {
    version: UI_OVERLAY_BUNDLE_VERSION,
    activeViewId: DEFAULT_VIEW_ID,
    views: [{ id: DEFAULT_VIEW_ID, name: DEFAULT_VIEW_NAME, overrides: defaultOverlay(fields).overrides }],
  };
}

/**
 * 生效视图 → 扁平覆盖（投影层单一入口）。
 * activeViewId 缺失/悬空/views 为空时按首个视图兜底——偏好数据损坏不应白屏。
 */
export function activeOverlay(bundle?: UiOverlayBundle | null): UiOverlay {
  if (!bundle?.views?.length) return { version: UI_OVERLAY_VERSION, overrides: {} };
  const v = bundle.views.find((x) => x.id === bundle.activeViewId) ?? bundle.views[0];
  return { version: UI_OVERLAY_VERSION, overrides: v.overrides ?? {}, density: v.density };
}

/** 生效视图对象（UI 层管理当前视图用；无 views 时返回 null） */
export function activeViewOf(bundle?: UiOverlayBundle | null): PersonalView | null {
  if (!bundle?.views?.length) return null;
  return bundle.views.find((x) => x.id === bundle.activeViewId) ?? bundle.views[0];
}

/**
 * 存储形态归一（服务端 blob / 本地缓存共用）：
 * - bundle v3：校验后原样采用（views 至少 1 个合法视图，activeViewId 悬空则纠偏）
 * - v2 扁平存量 `{version:2, overrides, density}`：包一层为单视图 bundle（偏好无损迁移）
 * - 其余（v1/未知结构/垃圾）：null（调用方按默认投影渲染）
 */
export function toBundle(parsed: unknown): UiOverlayBundle | null {
  if (!parsed || typeof parsed !== 'object') return null;
  const p = parsed as Record<string, unknown>;
  if (Array.isArray(p.views)) {
    const views = p.views.filter(
      (v): v is PersonalView =>
        !!v && typeof v === 'object'
        && typeof (v as PersonalView).id === 'string'
        && typeof (v as PersonalView).name === 'string'
        && !!(v as PersonalView).overrides && typeof (v as PersonalView).overrides === 'object',
    );
    if (!views.length) return null;
    const activeId =
      typeof p.activeViewId === 'string' && views.some((v) => v.id === p.activeViewId)
        ? (p.activeViewId as string)
        : views[0].id;
    return { version: UI_OVERLAY_BUNDLE_VERSION, activeViewId: activeId, views };
  }
  if (p.version === UI_OVERLAY_VERSION && p.overrides && typeof p.overrides === 'object') {
    return {
      version: UI_OVERLAY_BUNDLE_VERSION,
      activeViewId: DEFAULT_VIEW_ID,
      views: [
        {
          id: DEFAULT_VIEW_ID,
          name: DEFAULT_VIEW_NAME,
          overrides: p.overrides as Record<string, ColumnOverride>,
          density: p.density as PersonalView['density'],
        },
      ],
    };
  }
  return null;
}

/** 在生效视图上应用补丁（显隐/改名/密度/列序的唯一写口，避免多视图状态漂移） */
export function updateActiveView(
  bundle: UiOverlayBundle,
  patch: (v: PersonalView) => PersonalView,
): UiOverlayBundle {
  const idx = bundle.views.findIndex((v) => v.id === bundle.activeViewId);
  const at = idx < 0 ? 0 : idx;
  const views = [...bundle.views];
  views[at] = patch(views[at]);
  return { ...bundle, views };
}

/** 切换生效视图（id 不存在时原样返回，不静默跳走） */
export function switchView(bundle: UiOverlayBundle, id: string): UiOverlayBundle {
  return bundle.views.some((v) => v.id === id) ? { ...bundle, activeViewId: id } : bundle;
}

/**
 * 新建个人视图并设为生效。
 * @param mode blank=默认投影（干净起点）；copy=深拷贝当前视图偏好（改差异）
 */
export function createView(
  bundle: UiOverlayBundle,
  name: string,
  fields: MetaField[],
  mode: 'blank' | 'copy',
): UiOverlayBundle {
  const cur = activeViewOf(bundle);
  const id = `v_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
  const view: PersonalView =
    mode === 'copy' && cur
      ? { id, name: name.trim() || '未命名视图', overrides: JSON.parse(JSON.stringify(cur.overrides)), density: cur.density }
      : { id, name: name.trim() || '未命名视图', overrides: defaultOverlay(fields).overrides };
  return { ...bundle, activeViewId: id, views: [...bundle.views, view] };
}

/** 重命名视图（空名拒绝：无名视图在切换器里不可识别） */
export function renameView(bundle: UiOverlayBundle, id: string, name: string): UiOverlayBundle {
  const n = name.trim();
  if (!n) return bundle;
  return { ...bundle, views: bundle.views.map((v) => (v.id === id ? { ...v, name: n } : v)) };
}

/** 删除视图；最后一个是底线（无视图 = 无偏好载体，切换器会空转），原样返回 */
export function deleteView(bundle: UiOverlayBundle, id: string): UiOverlayBundle {
  if (bundle.views.length <= 1) return bundle;
  const views = bundle.views.filter((v) => v.id !== id);
  if (views.length === bundle.views.length) return bundle;
  return {
    ...bundle,
    views,
    activeViewId: bundle.activeViewId === id ? views[0].id : bundle.activeViewId,
  };
}

/** 把生效视图恢复为默认投影（保留视图本身与其他视图；全量重置走 resetOverlay 删 blob） */
export function resetActiveViewToDefault(bundle: UiOverlayBundle, fields: MetaField[]): UiOverlayBundle {
  return updateActiveView(bundle, (v) => ({ ...v, overrides: defaultOverlay(fields).overrides, density: undefined }));
}

/**
 * 被个人偏好隐藏、但团队契约默认显示的契约列（自伤遮蔽判据 · 双轨收敛）。
 *
 * <p>为什么需要：投影规则「任一层隐藏即隐藏」是有意的双轨语义，但它带来一个
 * 不可观测状态——交付工程师在运行时数据页把自己某列隐藏后，再去建模台发布
 * 新契约（或契约本来就让该列可见），他的历史隐藏偏好会**盖住**契约，
 * 呈现为「配了不生效」。用户看不到「是我的偏好挡住了契约」，就无法自救。
 *
 * <p>本函数把该状态变成可计算的纯判据：**实时比对、无存储、无版本号依赖、
 * 无存量盲区**（契约发布时间戳不可得——BoneUiSchema 不带该字段——故不用
 * 「指纹快照」方案，那会对从未编辑过 overlay 的用户漏报）。
 * 判据：契约列 visible!==false 且个人 overlay visible===false ⇒ 命中。
 * 契约自己隐藏的列不算（那是契约语义，不是遮蔽）；无契约列时返回空（纯偏好模式）。
 */
export function overlayHiddenContractColumns(
  fields: MetaField[],
  overlay?: UiOverlay | null,
  listView?: ListView | null,
): MetaField[] {
  const contractCols = (listView?.columns ?? []).filter((c) => !!c.fieldCode && c.visible !== false);
  if (contractCols.length === 0) return [];
  const byCode = new Map(fields.map((f) => [f.code, f]));
  const out: MetaField[] = [];
  for (const c of contractCols) {
    if (overlay?.overrides[c.fieldCode]?.visible !== false) continue;
    const f = byCode.get(c.fieldCode);
    if (f) out.push(f);
  }
  return out;
}

/**
 * 生效覆盖是否偏离默认投影（dirty 判据）：「恢复默认显示」的启用条件。
 *
 * <p><b>判脏口径 = 「重置能否改变用户看得见的投影」</b>：逐字段比对显隐/改名/列序
 * + 密度；但**够不着投影的条目不算脏**——
 * ① 字段已删（不在 fields 里）：两条投影路径都不遍历它（契约路径按
 *    {@link projectRuntimeColumns} 的契约列 ∩ fields，无契约路径按 fields 全量）；
 * ② 契约外字段（strict 集合下运行时不可见）：同理 inert。
 * 否则契约发布/字段删除后，用户够不着的陈旧 `visible:false` 条目会把
 * 「恢复默认显示」永久置亮、徽标常亮，误导「还有未调整完的偏好」。
 *
 * <p>空覆盖表不脏（hydrate 时 fields 晚到的占位态与默认等价）。
 *
 * @param listView 执行期契约列表视图；缺省/无契约列时按全量字段口径（纯个人模式）
 */
export function isOverlayDirty(
  overlay: UiOverlay,
  fields: MetaField[],
  listView?: ListView | null,
): boolean {
  const def = defaultOverlay(fields).overrides;
  // 「能影响投影的字段」：无契约 ⇒ 全部字段；有契约 ⇒ 仅契约列（strict 集合）。
  const contractCols = (listView?.columns ?? []).filter((c) => !!c.fieldCode);
  const inPlay = contractCols.length > 0 ? new Set(contractCols.map((c) => c.fieldCode)) : null;
  for (const [code, ov] of Object.entries(overlay.overrides ?? {})) {
    const d = def[code];
    if (!d) continue; // 字段已删：投影不遍历它，偏好 inert
    if (inPlay && !inPlay.has(code)) continue; // 契约外字段：strict 集合下运行时不可见，inert
    if (!!ov.visible !== !!d.visible) return true;
    if (ov.displayName != null && ov.displayName !== d.displayName) return true;
    if ((ov.order ?? d.order) !== (d.order ?? ov.order)) return true;
  }
  return overlay.density != null && overlay.density !== 'comfortable';
}

/**
 * 个人列改名（S1 双轨之「个人改名」维度的唯一写口）。
 *
 * <p>为什么投影层早就能读 {@link ColumnOverride.displayName}（个人改名优先于契约标题）
 * 却没有编辑器：v1 双轨收敛把 overlay 编辑器搬到运行时工具栏时只接了显隐/密度，
 * 改名维度留在模型里没有入口——「契约标题团队统一、个人叫法自助」正是复杂企业场景的
 * 高频诉求（财务叫「客户」、销售叫「甲方」；巡检组叫「工单」、机组叫「单号」），
 * 本函数补上该写口，与 {@link reorderOverlay} 同族（纯函数、返回新表不改入参）。
 *
 * <p>语义：
 * - name 为空 / 与字段 displayName 相同 ⇒ **删除**个人改名条目（回落契约标题/字段名），
 *   绝不写一个「等于默认值的改名」——那会把 {@link isOverlayDirty} 永久置亮；
 * - 其余 ⇒ 写 displayName，保留该字段既有覆盖条目的其他键（visible/order 不丢）；
 * - 字段无覆盖条目时新建（visible: true，与「缺省即可见」同口径）。
 */
export function renameColumnOverride(
  overrides: Record<string, ColumnOverride>,
  code: string,
  name: string | null,
  fields: MetaField[],
): Record<string, ColumnOverride> {
  const fieldName = fields.find((f) => f.code === code)?.displayName;
  const next = name?.trim();
  const cur = overrides[code];
  if (!next || next === fieldName) {
    if (!cur || cur.displayName === undefined) return overrides;
    const rest = { ...cur };
    delete rest.displayName;
    return { ...overrides, [code]: rest };
  }
  return { ...overrides, [code]: { ...(cur ?? { visible: true }), displayName: next } };
}

/**
 * 在覆盖表内交换两个相邻可见列的顺序（↑ / ↓）。
 * 泛型：扁平 {@link UiOverlay} 与 {@link PersonalView} 都可直接入参，
 * 返回值携带入参的全部字段（多视图写口经 updateActiveView 拼回 bundle）。
 */
export function reorderOverlay<T extends { overrides: Record<string, ColumnOverride> }>(
  overlay: T,
  orderedCodes: string[],
  code: string,
  direction: 'up' | 'down',
): T {
  const idx = orderedCodes.indexOf(code);
  const swapWith = direction === 'up' ? idx - 1 : idx + 1;
  if (idx < 0 || swapWith < 0 || swapWith >= orderedCodes.length) return overlay;
  const next = [...orderedCodes];
  [next[idx], next[swapWith]] = [next[swapWith], next[idx]];
  const overrides: Record<string, ColumnOverride> = { ...overlay.overrides };
  next.forEach((c, i) => {
    overrides[c] = { ...(overrides[c] ?? { visible: true }), order: i };
  });
  return { ...overlay, overrides };
}

/**
 * 会话上下文（租户/用户）—— D2 存储隔离定案（PRD §12.2）：
 * 键 = `bone.meta.overlay.{tenantId}.{userId}.{entityCode}`。
 * 来源：bone-shell 登录后写入 `localStorage['bone-user']`（{id, tenantId}），
 * 微应用（qiankun）同源共享该 localStorage。
 * 兜底策略：无会话（开发直连/未登录预览）→ 'anon'，键退化为两段前缀，
 * 与登录态天然隔离，不会串写真实用户的覆盖配置。
 */
function sessionScope(): string {
  try {
    const raw = localStorage.getItem('bone-user');
    if (raw) {
      const u = JSON.parse(raw) as { id?: string | number; tenantId?: string | number };
      const tid = u.tenantId ?? '0';
      const uid = u.id ?? 'anon';
      return `${tid}.${uid}`;
    }
  } catch {
    /* 解析失败按无会话处理 */
  }
  return '0.anon';
}

export function overlayKey(entityCode: string): string {
  return `bone.meta.overlay.${sessionScope()}.${entityCode}`;
}

export function loadOverlay(entityCode: string): UiOverlayBundle | null {
  try {
    const raw = localStorage.getItem(overlayKey(entityCode));
    if (!raw) return null;
    // toBundle 兼容两类存量：v3 bundle 原样采用；v2 扁平包一层迁移（偏好不丢）
    return toBundle(JSON.parse(raw));
  } catch {
    return null;
  }
}

export function saveOverlay(entityCode: string, bundle: UiOverlayBundle): void {
  localStorage.setItem(overlayKey(entityCode), JSON.stringify(bundle));
}

export function clearOverlay(entityCode: string): void {
  localStorage.removeItem(overlayKey(entityCode));
}
