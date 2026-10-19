import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MetaField } from '../types';
import {
  DEFAULT_VIEW_ID,
  DEFAULT_VIEW_NAME,
  UI_OVERLAY_BUNDLE_VERSION,
  UI_OVERLAY_VERSION,
  activeOverlay,
  activeViewOf,
  clearOverlay,
  createView,
  defaultBundle,
  defaultOverlay,
  deleteView,
  enumOptionsOf,
  fieldsOutsideContract,
  isEnumField,
  isOverlayDirty,
  isRenderSupported,
  loadOverlay,
  overlayHiddenContractColumns,
  overlayKey,
  projectColumns,
  projectRuntimeColumns,
  renameColumnOverride,
  renameView,
  reorderOverlay,
  resetActiveViewToDefault,
  saveOverlay,
  switchView,
  tableSizeOf,
  toBundle,
  updateActiveView,
  type UiOverlayBundle,
} from './columnProjection';
import type { ListView } from './uiSchemaEngine';

/** 构造测试字段 */
function field(code: string, extra: Partial<MetaField> = {}): MetaField {
  return { id: code, entityId: 'e1', code, displayName: code, name: code, type: 'STRING', required: false, sortOrder: 0, ...extra };
}

/** node 环境无 localStorage：stub 一个内存实现 */
function mockLocalStorage(store: Record<string, string> = {}) {
  const storage = {
    getItem: (k: string) => (k in store ? store[k] : null),
    setItem: (k: string, v: string) => { store[k] = String(v); },
    removeItem: (k: string) => { delete store[k]; },
    clear: () => { Object.keys(store).forEach((k) => delete store[k]); },
  };
  vi.stubGlobal('localStorage', storage);
  return storage;
}

const FIELDS: MetaField[] = [
  field('code', { displayName: '编码', sortOrder: 10 }),
  field('name', { displayName: '名称', sortOrder: 20 }),
  field('qty', { displayName: '数量', type: 'INTEGER', sortOrder: 30 }),
];

beforeEach(() => {
  mockLocalStorage();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('defaultOverlay', () => {
  it('按 sortOrder 升序生成初始覆盖，版本=当前版本', () => {
    const ov = defaultOverlay(FIELDS);
    expect(ov.version).toBe(UI_OVERLAY_VERSION);
    expect(Object.keys(ov.overrides)).toEqual(['code', 'name', 'qty']);
    expect(ov.overrides.code.order).toBe(0);
    expect(ov.overrides.name.order).toBe(1);
    expect(ov.overrides.qty.order).toBe(2);
    expect(ov.overrides.code.visible).toBe(true);
    expect(ov.overrides.code.displayName).toBe('编码');
  });
});

describe('projectColumns', () => {
  it('无 overlay 时原样返回', () => {
    expect(projectColumns(FIELDS, null)).toBe(FIELDS);
    expect(projectColumns(FIELDS, undefined)).toBe(FIELDS);
  });

  it('隐藏列被过滤（visible=false）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    const out = projectColumns(FIELDS, ov);
    expect(out.map((f) => f.code)).toEqual(['code', 'name']);
  });

  it('显示名覆盖生效，未覆盖字段保持原名', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name = { ...ov.overrides.name, displayName: '商品名称' };
    const out = projectColumns(FIELDS, ov);
    expect(out.find((f) => f.code === 'name')?.displayName).toBe('商品名称');
    expect(out.find((f) => f.code === 'code')?.displayName).toBe('编码');
  });

  it('列序按 order 稳定排序', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name.order = 5;
    ov.overrides.code.order = 10;
    ov.overrides.qty.order = 0;
    const out = projectColumns(FIELDS, ov);
    expect(out.map((f) => f.code)).toEqual(['qty', 'name', 'code']);
  });

  it('新增字段（无覆盖记录）排在末尾且不被隐藏', () => {
    const ov = defaultOverlay([FIELDS[0], FIELDS[1]]);
    const out = projectColumns(FIELDS, ov); // 传入含新增字段 qty 的完整列表
    expect(out.map((f) => f.code)).toEqual(['code', 'name', 'qty']);
  });
});

describe('projectRuntimeColumns（P1 · 契约优先 + 个人偏好叠加）', () => {
  const listView = (columns: ListView['columns']): ListView => ({ columns });

  it('无契约列：整体回落 projectColumns 旧行为（零回归）', () => {
    const ov = defaultOverlay(FIELDS);
    const viaNew = projectRuntimeColumns(FIELDS, ov, null).map((c) => c.field);
    const viaOld = projectColumns(FIELDS, ov);
    expect(viaNew).toEqual(viaOld);
    expect(projectRuntimeColumns(FIELDS, ov, listView([])).map((c) => c.field)).toEqual(viaOld);
    expect(projectRuntimeColumns(FIELDS, ov, listView(undefined)).map((c) => c.field)).toEqual(viaOld);
  });

  it('契约列序/列宽/固定列/列标题生效（设计器配的终于有人消费）', () => {
    const out = projectRuntimeColumns(FIELDS, defaultOverlay(FIELDS), listView([
      { fieldCode: 'qty', width: 220, pinned: 'left' },
      { fieldCode: 'name', format: { title: '商品名称' } },
      { fieldCode: 'code', width: 80 },
    ]));
    expect(out.map((c) => c.field.code)).toEqual(['qty', 'name', 'code']);
    expect(out[0]).toMatchObject({ width: 220, pinned: 'left' });
    expect(out[2]).toMatchObject({ width: 80, pinned: undefined });
    // 契约列标题覆盖字段 displayName
    expect(out[1].field.displayName).toBe('商品名称');
  });

  it('契约固定列方向区分：left 钉主键侧、right 钉操作列侧（协议 enum）', () => {
    const out = projectRuntimeColumns(FIELDS, defaultOverlay(FIELDS), listView([
      { fieldCode: 'code', pinned: 'left' },
      { fieldCode: 'name', pinned: 'right' },
    ]));
    expect(out[0].pinned).toBe('left');
    expect(out[1].pinned).toBe('right');
  });

  it('个人改名优先于契约列标题', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name = { ...ov.overrides.name, displayName: '我起的名字' };
    const out = projectRuntimeColumns(FIELDS, ov, listView([
      { fieldCode: 'name', format: { title: '契约标题' } },
    ]));
    expect(out[0].field.displayName).toBe('我起的名字');
  });

  it('可见性：契约隐藏或个人隐藏任一为真即隐藏', () => {
    const ov = defaultOverlay(FIELDS);
    const out = projectRuntimeColumns(FIELDS, ov, listView([
      { fieldCode: 'code' },
      { fieldCode: 'name', visible: false },
      { fieldCode: 'qty' },
    ]));
    expect(out.map((c) => c.field.code)).toEqual(['code', 'qty']); // 契约隐藏 name
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    const out2 = projectRuntimeColumns(FIELDS, ov, listView([
      { fieldCode: 'code' },
      { fieldCode: 'name', visible: false },
      { fieldCode: 'qty' },
    ]));
    expect(out2.map((c) => c.field.code)).toEqual(['code']); // 个人再隐藏 qty
  });

  it('契约引用已删字段：跳过；契约未列出的字段不进运行时列（strict 集合）', () => {
    const out = projectRuntimeColumns(FIELDS, defaultOverlay(FIELDS), listView([
      { fieldCode: 'code' },
      { fieldCode: 'ghost_deleted' },
      { fieldCode: 'name' },
    ]));
    // ghost 被跳过；qty 不在契约中 → 不出现在运行时列（策划清单不被字段洪流淹没）
    expect(out.map((c) => c.field.code)).toEqual(['code', 'name']);
  });

  it('fieldsOutsideContract：报告未列入契约的字段；无契约时为空', () => {
    const lv = listView([{ fieldCode: 'code' }, { fieldCode: 'name' }]);
    expect(fieldsOutsideContract(FIELDS, lv).map((f) => f.code)).toEqual(['qty']);
    expect(fieldsOutsideContract(FIELDS, null)).toEqual([]);
    expect(fieldsOutsideContract(FIELDS, listView([]))).toEqual([]);
  });

  it('契约后新增字段不进入运行时列（strict）；由 fieldsOutsideContract 提示去设计器加入', () => {
    const lv = listView([
      { fieldCode: 'name', width: 100 },
      { fieldCode: 'code' },
    ]);
    const out = projectRuntimeColumns(FIELDS, defaultOverlay(FIELDS), lv);
    expect(out.map((c) => c.field.code)).toEqual(['name', 'code']);
    expect(fieldsOutsideContract(FIELDS, lv).map((f) => f.code)).toEqual(['qty']);
  });

  it('负向探针：无 width 的契约列不得凭空产出 width（断言有牙）', () => {
    const out = projectRuntimeColumns(FIELDS, defaultOverlay(FIELDS), listView([{ fieldCode: 'code' }]));
    expect(out).toHaveLength(1); // strict：仅契约列
    expect(out[0]).toMatchObject({ width: undefined, pinned: undefined });
  });

  it('负向探针：契约存在时 overlay 的列序不得颠覆契约顺序', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.code.order = 99; // 个人把 code 排到最后
    const out = projectRuntimeColumns(FIELDS, ov, listView([
      { fieldCode: 'code' },
      { fieldCode: 'name' },
      { fieldCode: 'qty' },
    ]));
    expect(out.map((c) => c.field.code)).toEqual(['code', 'name', 'qty']);
  });
});

describe('reorderOverlay', () => {
  it('上移：与前一列交换，顺序号紧凑化', () => {
    const ov = defaultOverlay(FIELDS);
    const next = reorderOverlay(ov, ['code', 'name', 'qty'], 'name', 'up');
    expect(Object.keys(next.overrides).map((c) => next.overrides[c].order)).toEqual([1, 0, 2]);
  });

  it('下移：与后一列交换', () => {
    const ov = defaultOverlay(FIELDS);
    const next = reorderOverlay(ov, ['code', 'name', 'qty'], 'code', 'down');
    expect(next.overrides.code.order).toBe(1);
    expect(next.overrides.name.order).toBe(0);
  });

  it('首列上移 / 末列下移：返回原 overlay（不改动）', () => {
    const ov = defaultOverlay(FIELDS);
    expect(reorderOverlay(ov, ['code', 'name', 'qty'], 'code', 'up')).toBe(ov);
    expect(reorderOverlay(ov, ['code', 'name', 'qty'], 'qty', 'down')).toBe(ov);
  });

  it('不修改入参（纯函数）', () => {
    const ov = defaultOverlay(FIELDS);
    const snapshot = JSON.stringify(ov);
    reorderOverlay(ov, ['code', 'name', 'qty'], 'name', 'up');
    expect(JSON.stringify(ov)).toBe(snapshot);
  });
});

describe('renameColumnOverride（个人列改名写口）', () => {
  it('写个人改名：保留该字段既有覆盖条目的其他键（visible/order 不丢）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { visible: false, displayName: '数量', order: 2 };
    const next = renameColumnOverride(ov.overrides, 'qty', '出货数', FIELDS);
    expect(next.qty).toEqual({ visible: false, displayName: '出货数', order: 2 });
  });

  it('无覆盖条目时新建条目（visible: true，与「缺省即可见」同口径）', () => {
    const next = renameColumnOverride({}, 'name', '商品名', FIELDS);
    expect(next.name).toEqual({ visible: true, displayName: '商品名' });
  });

  it('空名 ⇒ 恢复默认：删除 displayName 键而非写空串', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name = { ...ov.overrides.name, displayName: '旧改名' };
    const next = renameColumnOverride(ov.overrides, 'name', '   ', FIELDS);
    expect(next.name.displayName).toBeUndefined();
    expect('displayName' in next.name).toBe(false);
  });

  it('与字段 displayName 相同 ⇒ 恢复默认（负向探针：不写「等于默认值的改名」）', () => {
    // 为什么必须是负向探针：写一个等值改名会把 isOverlayDirty 永久置亮——
    // 「恢复默认显示」按钮从此再也不会灭，用户以为还有未调整完的偏好。
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name = { ...ov.overrides.name, displayName: '旧改名' };
    const next = renameColumnOverride(ov.overrides, 'name', '名称', FIELDS); // '名称' = 字段 displayName
    expect('displayName' in next.name).toBe(false);
  });

  it('恢复默认不清掉同字段的其他键（隐藏偏好不与改名共存亡）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { visible: false, displayName: '出货数', order: 2 };
    const next = renameColumnOverride(ov.overrides, 'qty', null, FIELDS);
    expect(next.qty).toEqual({ visible: false, order: 2 });
  });

  it('不修改入参（纯函数）', () => {
    const ov = defaultOverlay(FIELDS);
    const snapshot = JSON.stringify(ov);
    renameColumnOverride(ov.overrides, 'name', '新名', FIELDS);
    renameColumnOverride(ov.overrides, 'code', null, FIELDS);
    expect(JSON.stringify(ov)).toBe(snapshot);
  });

  it('闭环：改名后投影用个人名且 dirty；恢复默认后回落字段名且不脏', () => {
    const lv: ListView = { columns: [{ fieldCode: 'name', format: { title: '契约标题' } }] };
    const ov = defaultOverlay(FIELDS);
    const renamed = renameColumnOverride(ov.overrides, 'name', '甲方', FIELDS);
    const base = defaultBundle(FIELDS);
    const bundle: UiOverlayBundle = { ...base, views: [{ ...base.views[0], overrides: renamed }] };
    const active = activeOverlay(bundle);
    // 投影：个人改名优先于契约标题（projectRuntimeColumns 既有语义，此处闭环验证）
    expect(projectRuntimeColumns(FIELDS, active, lv)[0].field.displayName).toBe('甲方');
    // dirty：改名 = 偏离默认投影 ⇒「恢复默认显示」可用
    expect(isOverlayDirty(active, FIELDS, lv)).toBe(true);
    // 恢复默认：displayName 键删除 ⇒ 投影回落契约标题、dirty 熄灭
    const restored = renameColumnOverride(renamed, 'name', '名称', FIELDS);
    const active2 = activeOverlay({ ...bundle, views: [{ ...bundle.views[0], overrides: restored }] });
    expect(projectRuntimeColumns(FIELDS, active2, lv)[0].field.displayName).toBe('契约标题');
    expect(isOverlayDirty(active2, FIELDS, lv)).toBe(false);
  });
});

describe('overlayKey / 存储（D2 键格式）', () => {
  it('无会话时键退化为 0.anon 前缀（bone.meta.overlay.{tenant}.{user}.{entity}）', () => {
    expect(overlayKey('bp_order')).toBe('bone.meta.overlay.0.anon.bp_order');
  });

  it('有会话（bone-user）时键带租户与用户', () => {
    mockLocalStorage({ 'bone-user': JSON.stringify({ id: 10086, tenantId: 3 }) });
    expect(overlayKey('bp_order')).toBe('bone.meta.overlay.3.10086.bp_order');
  });

  it('loadOverlay：无存储返回 null', () => {
    expect(loadOverlay('bp_order')).toBeNull();
  });

  it('loadOverlay：版本不符的旧覆盖被丢弃（返回 null）', () => {
    const store: Record<string, string> = {};
    mockLocalStorage(store);
    const key = overlayKey('bp_order');
    store[key] = JSON.stringify({ version: UI_OVERLAY_VERSION - 1, overrides: {} });
    expect(loadOverlay('bp_order')).toBeNull();
  });

  it('save/load/clear 往返一致（v3 bundle 口径）', () => {
    const b = defaultBundle(FIELDS);
    saveOverlay('bp_order', b);
    expect(loadOverlay('bp_order')).toEqual(b);
    clearOverlay('bp_order');
    expect(loadOverlay('bp_order')).toBeNull();
  });

  it('loadOverlay：v2 扁平存量缓存迁移为单视图 bundle（升级多视图不丢偏好）', () => {
    const store: Record<string, string> = {};
    mockLocalStorage(store);
    const legacy = { version: UI_OVERLAY_VERSION, overrides: { name: { visible: false, displayName: '旧名' } }, density: 'compact' };
    store[overlayKey('bp_order')] = JSON.stringify(legacy);

    const b = loadOverlay('bp_order');

    expect(b).not.toBeNull();
    expect(b!.views).toHaveLength(1);
    expect(b!.views[0].name).toBe(DEFAULT_VIEW_NAME);
    expect(b!.views[0].overrides).toEqual(legacy.overrides);
    expect(b!.views[0].density).toBe('compact');
    // 迁移不是走过场：投影层必须仍能消费存量偏好
    expect(activeOverlay(b!).overrides.name.displayName).toBe('旧名');
  });
});

describe('渲染支持与类型判定', () => {
  it('isRenderSupported：支持类型 true，未知类型 false', () => {
    expect(isRenderSupported(field('a', { type: 'STRING' }))).toBe(true);
    expect(isRenderSupported(field('b', { type: 'DATE' }))).toBe(true);
    expect(isRenderSupported(field('c', { type: 'ENUM' }))).toBe(true);
    expect(isRenderSupported(field('d', { type: 'BLOB' }))).toBe(false);
    expect(isRenderSupported(field('e', { type: '' }))).toBe(false);
  });

  it('isEnumField：仅 ENUM（大小写不敏感）', () => {
    expect(isEnumField(field('a', { type: 'enum' }))).toBe(true);
    expect(isEnumField(field('b', { type: 'STRING' }))).toBe(false);
  });

  it('enumOptionsOf：解析 enumValues 兜底空数组', () => {
    const f = field('s', { type: 'ENUM', enumValues: '[{"code":"a","label":"A"},{"code":"b","label":"B"}]' });
    expect(enumOptionsOf(f)).toEqual([
      { code: 'a', label: 'A' },
      { code: 'b', label: 'B' },
    ]);
    expect(enumOptionsOf(field('x', { type: 'ENUM' }))).toEqual([]);
  });

  it('tableSizeOf：compact→small，其余→middle', () => {
    expect(tableSizeOf('compact')).toBe('small');
    expect(tableSizeOf('comfortable')).toBe('middle');
    expect(tableSizeOf(undefined)).toBe('middle');
  });
});

/* ===================== 个人视图 bundle（v3 多视图） ===================== */

describe('defaultBundle / activeOverlay', () => {
  it('默认 bundle：单视图「默认视图」，覆盖表 = 默认投影', () => {
    const b = defaultBundle(FIELDS);
    expect(b.version).toBe(UI_OVERLAY_BUNDLE_VERSION);
    expect(b.activeViewId).toBe(DEFAULT_VIEW_ID);
    expect(b.views).toHaveLength(1);
    expect(b.views[0]).toMatchObject({ id: DEFAULT_VIEW_ID, name: DEFAULT_VIEW_NAME });
    expect(b.views[0].overrides).toEqual(defaultOverlay(FIELDS).overrides);
  });

  it('activeOverlay：解析生效视图为扁平覆盖（投影层口径）', () => {
    const b = defaultBundle(FIELDS);
    const ov = activeOverlay(b);
    expect(ov.version).toBe(UI_OVERLAY_VERSION);
    expect(Object.keys(ov.overrides)).toEqual(['code', 'name', 'qty']);
  });

  it('activeOverlay：null/空 views 不抛异常，回落空覆盖', () => {
    expect(activeOverlay(null)).toEqual({ version: UI_OVERLAY_VERSION, overrides: {} });
    expect(activeOverlay({ version: 3, activeViewId: 'x', views: [] })).toEqual({ version: UI_OVERLAY_VERSION, overrides: {} });
  });

  it('负向探针：activeViewId 悬空时按首个视图兜底，不静默跳走也不崩', () => {
    const b = defaultBundle(FIELDS);
    const dangling: UiOverlayBundle = { ...b, activeViewId: 'not-exist' };
    expect(activeViewOf(dangling)?.id).toBe(DEFAULT_VIEW_ID);
    expect(Object.keys(activeOverlay(dangling).overrides)).toEqual(['code', 'name', 'qty']);
  });
});

describe('toBundle：存储形态归一（v3 原样 / v2 迁移 / 垃圾拒绝）', () => {
  it('v3 bundle 原样采用（views 合法）', () => {
    const b = defaultBundle(FIELDS);
    expect(toBundle(JSON.parse(JSON.stringify(b)))).toEqual(b);
  });

  it('v2 扁平存量：包一层为单视图 bundle，overrides/density 无损', () => {
    const legacy = { version: 2, overrides: { qty: { visible: false } }, density: 'compact' };
    const b = toBundle(legacy);
    expect(b?.views).toHaveLength(1);
    expect(b?.views[0].overrides).toEqual(legacy.overrides);
    expect(b?.views[0].density).toBe('compact');
    expect(b?.activeViewId).toBe(DEFAULT_VIEW_ID);
  });

  it('v3 bundle 的 activeViewId 悬空：纠偏到首个视图', () => {
    const b = defaultBundle(FIELDS);
    const fixed = toBundle({ ...b, activeViewId: 'ghost' });
    expect(fixed?.activeViewId).toBe(DEFAULT_VIEW_ID);
  });

  it('负向探针：v1/未知结构/views 为空/非对象一律 null（坏数据不蒙混过关）', () => {
    expect(toBundle({ version: 1, overrides: {} })).toBeNull();
    expect(toBundle({ version: 99, overrides: {} })).toBeNull();
    expect(toBundle({ version: 3, activeViewId: 'a', views: [] })).toBeNull();
    expect(toBundle({ version: 3, activeViewId: 'a', views: [{ id: 'a' }] })).toBeNull(); // 缺 name/overrides
    expect(toBundle(null)).toBeNull();
    expect(toBundle('garbage')).toBeNull();
    expect(toBundle(42)).toBeNull();
  });
});

describe('视图增删改与切换', () => {
  it('switchView：切换生效视图；未知 id 原样返回（负向）', () => {
    let b = defaultBundle(FIELDS);
    b = createView(b, '我的待审', FIELDS, 'blank');
    const newId = b.activeViewId;
    expect(switchView(b, DEFAULT_VIEW_ID).activeViewId).toBe(DEFAULT_VIEW_ID);
    expect(switchView(b, newId).activeViewId).toBe(newId);
    expect(switchView(b, 'nope')).toBe(b); // 未知 id：不改状态
  });

  it('createView(blank)：新视图带默认投影；createView(copy)：深拷贝当前偏好', () => {
    let b = defaultBundle(FIELDS);
    b = updateActiveView(b, (v) => ({ ...v, overrides: { ...v.overrides, qty: { ...v.overrides.qty, visible: false } }, density: 'compact' }));

    const blank = createView(b, '空白视图', FIELDS, 'blank');
    expect(blank.views).toHaveLength(2);
    expect(blank.activeViewId).not.toBe(DEFAULT_VIEW_ID);
    expect(activeOverlay(blank).overrides.qty.visible).toBe(true); // blank = 默认投影，不受当前视图隐藏影响

    const copied = createView(b, '复制视图', FIELDS, 'copy');
    const copiedActive = activeViewOf(copied)!;
    expect(copiedActive.overrides.qty.visible).toBe(false); // copy 带上当前隐藏
    expect(copiedActive.density).toBe('compact');

    // 深拷贝探针：改新视图不得污染源视图（JSON 往返失败的浅拷贝会在这里现形）
    const mutated = updateActiveView(copied, (v) => ({ ...v, overrides: { ...v.overrides, qty: { visible: true } } }));
    expect(activeViewOf(mutated)!.overrides.qty.visible).toBe(true);
    expect(b.views[0].overrides.qty.visible).toBe(false); // 源视图仍隐藏
  });

  it('renameView：重命名生效；空名拒绝（无名视图在切换器里不可识别）', () => {
    const b = defaultBundle(FIELDS);
    expect(renameView(b, DEFAULT_VIEW_ID, '大额专项').views[0].name).toBe('大额专项');
    expect(renameView(b, DEFAULT_VIEW_ID, '   ')).toBe(b); // 负向：纯空白拒绝
  });

  it('deleteView：删非生效视图不动 activeViewId；删生效视图落到首个', () => {
    let b = defaultBundle(FIELDS);
    b = createView(b, 'V2', FIELDS, 'blank'); // active=V2
    const kept = deleteView(b, DEFAULT_VIEW_ID);
    expect(kept.views.map((v) => v.id)).toEqual([b.activeViewId]);
    expect(kept.activeViewId).toBe(b.activeViewId);
    const dropped = deleteView(b, b.activeViewId);
    expect(dropped.views).toHaveLength(1);
    expect(dropped.activeViewId).toBe(DEFAULT_VIEW_ID);
  });

  it('负向探针：最后一个是底线——单视图 bundle 拒绝删除', () => {
    const b = defaultBundle(FIELDS);
    expect(deleteView(b, DEFAULT_VIEW_ID)).toBe(b);
    expect(deleteView(b, 'whatever')).toBe(b);
  });

  it('updateActiveView：只改生效视图，其他视图原样（隔离性）', () => {
    let b = defaultBundle(FIELDS);
    b = createView(b, 'V2', FIELDS, 'blank');
    const patched = updateActiveView(b, (v) => ({ ...v, overrides: { ...v.overrides, name: { ...v.overrides.name, visible: false } } }));
    expect(activeViewOf(patched)!.overrides.name.visible).toBe(false);
    expect(patched.views.find((v) => v.id === DEFAULT_VIEW_ID)!.overrides.name.visible).toBe(true);
  });

  it('resetActiveViewToDefault：生效视图回默认投影并清密度；其他视图不动', () => {
    let b = defaultBundle(FIELDS);
    b = updateActiveView(b, (v) => ({ ...v, density: 'compact', overrides: { ...v.overrides, qty: { ...v.overrides.qty, visible: false } } }));
    b = createView(b, 'V2', FIELDS, 'copy'); // active=V2（compact + qty 隐藏）
    const reset = resetActiveViewToDefault(b, FIELDS);
    const active = activeViewOf(reset)!;
    expect(active.overrides).toEqual(defaultOverlay(FIELDS).overrides);
    expect(active.density).toBeUndefined();
    expect(reset.views.find((v) => v.id === DEFAULT_VIEW_ID)!.overrides.qty.visible).toBe(false); // 旧视图保留
  });
});

describe('reorderOverlay 泛型化（PersonalView 直接入参）', () => {
  it(' PersonalView 入参：返回带 id/name 的完整视图（多视图写口拼回 bundle）', () => {
    const b = defaultBundle(FIELDS);
    const v = b.views[0];
    const next = reorderOverlay(v, ['code', 'name', 'qty'], 'qty', 'up');
    expect(next.id).toBe(DEFAULT_VIEW_ID);
    expect(next.name).toBe(DEFAULT_VIEW_NAME);
    expect(Object.keys(next.overrides).map((c) => next.overrides[c].order)).toEqual([0, 2, 1]);
    // 纯函数：入参不被修改
    expect(Object.keys(v.overrides).map((c) => v.overrides[c].order)).toEqual([0, 1, 2]);
  });

  it('边界：首列上移/末列下移返回同一引用（不制造新对象）', () => {
    const v = defaultBundle(FIELDS).views[0];
    expect(reorderOverlay(v, ['code', 'name', 'qty'], 'code', 'up')).toBe(v);
    expect(reorderOverlay(v, ['code', 'name', 'qty'], 'qty', 'down')).toBe(v);
  });
});

describe('端到端：bundle → activeOverlay → projectRuntimeColumns（视图隔离）', () => {
  const listView = (columns: ListView['columns']): ListView => ({ columns });

  it('切换视图改变运行时投影（A 视图隐藏 qty，B 视图显示）', () => {
    let b = defaultBundle(FIELDS);
    b = updateActiveView(b, (v) => ({ ...v, overrides: { ...v.overrides, qty: { ...v.overrides.qty, visible: false } } }));
    b = createView(b, '全列视图', FIELDS, 'blank');

    const colsA = projectRuntimeColumns(FIELDS, activeOverlay(switchView(b, DEFAULT_VIEW_ID)), listView([{ fieldCode: 'code' }, { fieldCode: 'qty' }]));
    expect(colsA.map((c) => c.field.code)).toEqual(['code']); // A：个人隐藏 qty

    const colsB = projectRuntimeColumns(FIELDS, activeOverlay(switchView(b, b.activeViewId)), listView([{ fieldCode: 'code' }, { fieldCode: 'qty' }]));
    expect(colsB.map((c) => c.field.code)).toEqual(['code', 'qty']); // B：默认全显
  });

  it('负向探针：A 视图的个人隐藏不得漏进 B 视图（偏好串台是多视图最常见 bug）', () => {
    let b = defaultBundle(FIELDS);
    b = updateActiveView(b, (v) => ({ ...v, overrides: { ...v.overrides, name: { ...v.overrides.name, visible: false } } }));
    b = createView(b, 'B', FIELDS, 'blank');
    const ovB = activeOverlay(switchView(b, b.activeViewId));
    expect(ovB.overrides.name.visible).toBe(true);
    // 回到 A：隐藏仍在（两个方向都验证，防「切换即丢失」）
    const ovA = activeOverlay(switchView(b, DEFAULT_VIEW_ID));
    expect(ovA.overrides.name.visible).toBe(false);
  });

  it('密度按视图独立保存（A 紧凑 / B 标准）', () => {
    let b = defaultBundle(FIELDS);
    b = updateActiveView(b, (v) => ({ ...v, density: 'compact' }));
    b = createView(b, 'B', FIELDS, 'blank');
    expect(activeOverlay(b).density).toBeUndefined();
    expect(activeOverlay(switchView(b, DEFAULT_VIEW_ID)).density).toBe('compact');
  });
});

describe('overlayHiddenContractColumns（自伤遮蔽判据 · 双轨收敛）', () => {
  const listView = (columns: ListView['columns']): ListView => ({ columns });

  it('个人隐藏 + 契约显示 ⇒ 命中（「配了不生效」的精确状态）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    const hit = overlayHiddenContractColumns(FIELDS, ov, listView([{ fieldCode: 'code' }, { fieldCode: 'qty' }]));
    expect(hit.map((f) => f.code)).toEqual(['qty']);
  });

  it('契约自己隐藏的列不算遮蔽（那是契约语义，不是偏好盖契约）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    const hit = overlayHiddenContractColumns(FIELDS, ov, listView([{ fieldCode: 'qty', visible: false }]));
    expect(hit).toEqual([]);
  });

  it('无契约列 ⇒ 空（纯偏好模式，没有契约可遮蔽）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    expect(overlayHiddenContractColumns(FIELDS, ov, null)).toEqual([]);
    expect(overlayHiddenContractColumns(FIELDS, ov, listView([]))).toEqual([]);
    expect(overlayHiddenContractColumns(FIELDS, ov, listView(undefined))).toEqual([]);
  });

  it('overlay 为 null/缺项 ⇒ 空（hydrate 前、新字段未入覆盖表都不误报）', () => {
    expect(overlayHiddenContractColumns(FIELDS, null, listView([{ fieldCode: 'qty' }]))).toEqual([]);
    expect(overlayHiddenContractColumns(FIELDS, undefined, listView([{ fieldCode: 'qty' }]))).toEqual([]);
    expect(overlayHiddenContractColumns(FIELDS, { version: 2, overrides: {} }, listView([{ fieldCode: 'qty' }]))).toEqual([]);
  });

  it('负向探针：只隐藏一个契约列时不得把其他列一并报出', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.name = { ...ov.overrides.name, visible: false };
    const hit = overlayHiddenContractColumns(FIELDS, ov, listView([{ fieldCode: 'code' }, { fieldCode: 'name' }, { fieldCode: 'qty' }]));
    expect(hit.map((f) => f.code)).toEqual(['name']);
  });

  it('契约引用已删字段 ⇒ 跳过（防幽灵列误报）', () => {
    const ov = defaultOverlay(FIELDS);
    const hit = overlayHiddenContractColumns([FIELDS[0]], ov, listView([{ fieldCode: 'gone' }, { fieldCode: 'code' }]));
    expect(hit).toEqual([]);
  });
});

describe('isOverlayDirty（恢复默认显示的启用判据 = 重置能否改变可见投影）', () => {
  const listView = (columns: ListView['columns']): ListView => ({ columns });

  it('默认投影 ⇒ 不脏', () => {
    expect(isOverlayDirty(defaultOverlay(FIELDS), FIELDS)).toBe(false);
  });

  it('隐藏/改名/密度任一偏离 ⇒ 脏', () => {
    const hidden = defaultOverlay(FIELDS);
    hidden.overrides.qty = { ...hidden.overrides.qty, visible: false };
    expect(isOverlayDirty(hidden, FIELDS)).toBe(true);

    const renamed = defaultOverlay(FIELDS);
    renamed.overrides.name = { ...renamed.overrides.name, displayName: '商品名称' };
    expect(isOverlayDirty(renamed, FIELDS)).toBe(true);

    expect(isOverlayDirty({ ...defaultOverlay(FIELDS), density: 'compact' }, FIELDS)).toBe(true);
  });

  it('空覆盖表 ⇒ 不脏（hydrate 时 fields 晚到的占位态，不是用户调整）', () => {
    expect(isOverlayDirty({ version: UI_OVERLAY_VERSION, overrides: {} }, FIELDS)).toBe(false);
  });

  it('负向探针：缺条目 ≠ 偏离；显隐条目才算', () => {
    const ov = defaultOverlay(FIELDS);
    delete ov.overrides.qty;
    expect(isOverlayDirty(ov, FIELDS)).toBe(false);
    ov.overrides.qty = { visible: false };
    expect(isOverlayDirty(ov, FIELDS)).toBe(true);
  });

  it('字段已删的陈旧条目 ⇒ inert（投影不遍历它，不得把重置键永久置亮）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.gone_field = { visible: false, displayName: '幽灵列', order: 99 };
    expect(isOverlayDirty(ov, FIELDS)).toBe(false);
    // 与真实偏离并存时仍要报脏（inert 跳过不得掩盖真差异）
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    expect(isOverlayDirty(ov, FIELDS)).toBe(true);
  });

  it('有契约时契约外字段的偏好 ⇒ inert（strict 集合下运行时不可见）', () => {
    const lv = listView([{ fieldCode: 'code' }, { fieldCode: 'name' }]);
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false }; // qty 不在契约里
    expect(isOverlayDirty(ov, FIELDS, lv)).toBe(false);
    ov.overrides.name = { ...ov.overrides.name, visible: false }; // name 在契约里
    expect(isOverlayDirty(ov, FIELDS, lv)).toBe(true);
  });

  it('自洽性：同一偏好随契约有无切换脏态（契约撤下后旧隐藏重新算数）', () => {
    const ov = defaultOverlay(FIELDS);
    ov.overrides.qty = { ...ov.overrides.qty, visible: false };
    // 无契约（纯个人模式）：qty 参与投影 ⇒ 脏
    expect(isOverlayDirty(ov, FIELDS, null)).toBe(true);
    // 契约只含 code/name：qty 不可见 ⇒ 不脏
    expect(isOverlayDirty(ov, FIELDS, listView([{ fieldCode: 'code' }, { fieldCode: 'name' }]))).toBe(false);
    // 契约重新包含 qty：又脏了
    expect(isOverlayDirty(ov, FIELDS, listView([{ fieldCode: 'qty' }]))).toBe(true);
  });
});
