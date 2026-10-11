import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { MetaField } from '../types';
import {
  UI_OVERLAY_VERSION,
  clearOverlay,
  defaultOverlay,
  enumOptionsOf,
  isEnumField,
  isRenderSupported,
  loadOverlay,
  overlayKey,
  projectColumns,
  reorderOverlay,
  saveOverlay,
  tableSizeOf,
} from './columnProjection';

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

  it('save/load/clear 往返一致', () => {
    const ov = defaultOverlay(FIELDS);
    saveOverlay('bp_order', ov);
    expect(loadOverlay('bp_order')).toEqual(ov);
    clearOverlay('bp_order');
    expect(loadOverlay('bp_order')).toBeNull();
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
