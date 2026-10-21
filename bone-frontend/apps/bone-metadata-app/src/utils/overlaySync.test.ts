import { beforeEach, describe, expect, it, vi } from 'vitest';
import { loadOverlay, UI_OVERLAY_VERSION, type UiOverlayBundle } from './columnProjection';
import { fetchOverlay, persistOverlay, resetOverlay } from './overlaySync';

/**
 * overlaySync 单测（0042 · P2-④；v3 多视图 bundle）：hydrate 三级降级 + 写路径两端一致。
 *
 * 覆盖的契约（改实现时必须仍为真）：
 *   ① 服务端覆盖（toBundle 归一通过）优先，且回写本地缓存
 *   ② 服务端无覆盖/版本不符/请求失败 → 逐级降级本地缓存，不抛异常
 *   ③ persist：本地缓存立即写 + 服务端异步推（不等结果）
 *   ④ reset：本地与服务端同时清（只清一端会「复活」旧覆盖）
 *   ⑤ v2 扁平存量 blob 无损迁移为单视图 bundle（升级多视图不丢偏好）
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  save: vi.fn(),
  reset: vi.fn(),
}));

vi.mock('../services/metadataApi', () => ({
  metadataUiOverlayApi: {
    get: mocks.get,
    save: mocks.save,
    reset: mocks.reset,
  },
}));

// vitest environment=jsdom（jsdom ≥ 26 起 window.localStorage 为只读 getter，直接赋值抛
// TypeError）。用 defineProperty 覆盖：node 环境原本无此属性、jsdom 环境替换原生实现，
// 两种环境下本测试都可执行（与 tests/setup.ts 的 window.localStorage mock 同思路）。
const store = new Map<string, string>();
const localStorageMock = {
  get length() {
    return store.size;
  },
  clear: () => store.clear(),
  getItem: (k: string) => (store.has(k) ? store.get(k)! : null),
  key: (i: number) => Array.from(store.keys())[i] ?? null,
  removeItem: (k: string) => store.delete(k),
  setItem: (k: string, v: string) => store.set(k, String(v)),
} as Storage;
Object.defineProperty(globalThis, 'localStorage', {
  value: localStorageMock,
  configurable: true,
  writable: true,
});

const ENTITY = 'expense_reimbursement';

/** 单视图 bundle（等价于旧扁平口径的一套装法） */
function bundleOf(extra: Partial<UiOverlayBundle> = {}): UiOverlayBundle {
  return {
    version: 3,
    activeViewId: 'default',
    views: [{ id: 'default', name: '默认视图', overrides: { amount: { visible: false } } }],
    ...extra,
  };
}

beforeEach(() => {
  localStorage.clear();
  mocks.get.mockReset();
  mocks.save.mockReset();
  mocks.reset.mockReset();
});

describe('fetchOverlay：服务端优先 → 本地兜底', () => {
  it('服务端有 bundle 覆盖：返回解析结果并回写本地缓存', async () => {
    const remote = bundleOf();
    mocks.get.mockResolvedValue({ code: 200, data: JSON.stringify(remote) });

    const got = await fetchOverlay(ENTITY);

    expect(got).toEqual(remote);
    // 回写本地：下次切换 Tab 走 localStorage 秒开（服务端不再往返）
    expect(loadOverlay(ENTITY)).toEqual(remote);
  });

  it('服务端无覆盖（data=null）：降级本地缓存', async () => {
    const local = bundleOf({ views: [{ id: 'default', name: '默认视图', overrides: {}, density: 'compact' }] });
    localStorage.setItem(`bone.meta.overlay.0.anon.${ENTITY}`, JSON.stringify(local));
    mocks.get.mockResolvedValue({ code: 200, data: null });

    expect(await fetchOverlay(ENTITY)).toEqual(local);
  });

  it('服务端版本不符：旧结构覆盖被丢弃，降级本地', async () => {
    localStorage.setItem(
      `bone.meta.overlay.0.anon.${ENTITY}`,
      JSON.stringify({ version: 999, overrides: {} }),
    );
    mocks.get.mockResolvedValue({ code: 200, data: JSON.stringify({ version: 1, overrides: {} }) });

    expect(await fetchOverlay(ENTITY)).toBeNull();
  });

  it('服务端 v2 扁平存量 blob：迁移为单视图 bundle（升级多视图不丢偏好）', async () => {
    const legacy = { version: UI_OVERLAY_VERSION, overrides: { amount: { visible: false } }, density: 'compact' };
    mocks.get.mockResolvedValue({ code: 200, data: JSON.stringify(legacy) });

    const got = await fetchOverlay(ENTITY);

    expect(got?.views).toHaveLength(1);
    expect(got?.views[0].overrides).toEqual(legacy.overrides);
    expect(got?.views[0].density).toBe('compact');
    // 迁移结果回写本地：下次 hydrate 直接走 bundle 口径
    expect(loadOverlay(ENTITY)).toEqual(got);
  });

  it('服务端请求失败（弱网/未登录）：不抛异常，返回本地覆盖', async () => {
    const local = bundleOf();
    localStorage.setItem(`bone.meta.overlay.0.anon.${ENTITY}`, JSON.stringify(local));
    mocks.get.mockRejectedValue(new Error('network down'));

    expect(await fetchOverlay(ENTITY)).toEqual(local);
  });

  it('服务端与本地都无：返回 null（调用方按 defaultBundle 渲染）', async () => {
    mocks.get.mockResolvedValue({ code: 200, data: null });
    expect(await fetchOverlay(ENTITY)).toBeNull();
  });
});

describe('persistOverlay / resetOverlay：写路径两端一致', () => {
  it('persist：本地缓存立即写，且异步推服务端', () => {
    const b = bundleOf();
    mocks.save.mockResolvedValue({ code: 200 });

    persistOverlay(ENTITY, b);

    // 同步断言：UI 不等待网络，本地必须已生效
    expect(loadOverlay(ENTITY)).toEqual(b);
    expect(mocks.save).toHaveBeenCalledWith(ENTITY, b);
  });

  it('persist：服务端推送失败不抛（离线容错）', () => {
    mocks.save.mockRejectedValue(new Error('offline'));
    expect(() => persistOverlay(ENTITY, bundleOf())).not.toThrow();
    expect(loadOverlay(ENTITY)).not.toBeNull();
  });

  it('reset：本地与服务端同时清（只清一端会复活旧覆盖）', async () => {
    mocks.save.mockResolvedValue({ code: 200 });
    persistOverlay(ENTITY, bundleOf());
    expect(loadOverlay(ENTITY)).not.toBeNull();
    mocks.reset.mockResolvedValue({ code: 200 });

    await resetOverlay(ENTITY);

    expect(loadOverlay(ENTITY)).toBeNull();
    expect(mocks.reset).toHaveBeenCalledWith(ENTITY);
  });
});
