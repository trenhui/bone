import { metadataUiOverlayApi } from '../services/metadataApi';
import { loadOverlay, saveOverlay, clearOverlay, toBundle, type UiOverlayBundle } from './columnProjection';

/**
 * 界面列投影覆盖的「服务端优先」同步层（0042 · P2-④ 服务端化；v3 多视图 bundle）。
 *
 * <p><b>为什么不是直接替换 localStorage</b>：PRD v0.32 §12.2 D2 的 localStorage 键仍有价值——
 * ① 服务端未配置时它就是唯一真源（升级过渡期）；② 服务端请求失败（弱网/未登录直连）时它是兜底，
 * 保证「界面 Tab 打不开也不白屏」。hydrate 三级降级：
 *
 * <pre>
 *   服务端覆盖（toBundle 归一通过） → 本地缓存 → null（调用方再 defaultBundle）
 * </pre>
 *
 * <p>写路径对称：先写本地缓存（UI 不等待网络），再异步推服务端（失败静默 —— 偏好丢失的代价
 * 远小于阻塞一次列设置交互；下次 hydrate 时服务端旧值会覆盖本地新值，属可接受最终一致）。
 */

/**
 * 把服务端返回的 JSON 字符串解析为 bundle。
 * v2 扁平存量（{version:2, overrides, density}）由 toBundle 无损迁移为单视图 bundle——
 * 升级到多视图不丢任何人已有的偏好；v1/未知结构按「无覆盖」处理。
 */
function parseServerOverlay(json: string | null | undefined): UiOverlayBundle | null {
  if (!json) return null;
  try {
    return toBundle(JSON.parse(json));
  } catch {
    return null;
  }
}

/**
 * 读覆盖：服务端优先 → 本地缓存兜底 → null。
 *
 * @param entityCode 实体编码
 * @returns 生效 bundle；无任何配置时 null（调用方按 defaultBundle 渲染）
 */
export async function fetchOverlay(entityCode: string): Promise<UiOverlayBundle | null> {
  try {
    const res = await metadataUiOverlayApi.get(entityCode);
    if (res.code === 200) {
      const remote = parseServerOverlay(res.data);
      if (remote) {
        saveOverlay(entityCode, remote); // 回写本地缓存：下次切换 Tab 秒开
        return remote;
      }
    }
  } catch {
    /* 服务端不可用（未登录/E2E 直连/弱网）→ 走本地缓存 */
  }
  return loadOverlay(entityCode);
}

/**
 * 个人覆盖变更广播（同一文档内多 surface 同步用）。
 *
 * <p><b>为什么需要</b>：个人覆盖有两个 surface——建模台「交付预览」Tab（只读）与
 * 运行时数据页工具栏（唯一编辑器）。antd Tabs 默认 {@code destroyInactiveTabPane=false}，
 * 两个 Tab 会**同时挂在同一棵 React 树**上；而 {@code useOverlayBundle} 的 hydrate effect
 * 只依赖 entityCode，实体不变就不重拉 ⇒ 数据页改完个人列显隐后，交付预览仍持有
 * 页面加载时的过期快照：「我的所见」列集与运行时漂移、自伤遮蔽告警也不触发。
 *
 * <p><b>为什么读本地缓存而不是重新请求服务端</b>：{@link persistOverlay} 已先写本地
 * 缓存再异步推服务端，此刻本地就是最新真源——直接重放可让切换即时同步，且不被
 * 网络往返阻塞（PUT 尚未落库时重新 GET 会读回旧值）。跨浏览器标签由 storage 事件兜底。
 */
export const OVERLAY_CHANGED_EVENT = 'bone:ui-overlay-changed';

function emitOverlayChanged(entityCode: string): void {
  if (typeof window === 'undefined') return;
  window.dispatchEvent(new CustomEvent(OVERLAY_CHANGED_EVENT, { detail: { entityCode } }));
}

/**
 * 保存覆盖：本地缓存立即写（乐观 UI），服务端异步推。
 *
 * <p>不 await 服务端结果：列设置是高频微交互（切换可见性/改名/排序/切视图），
 * 每次等一轮往返会让 UI 发涩。失败时本地缓存仍在，用户无感知；
 * 极端情况（服务端持久化失败且用户换设备）才丢偏好，可接受。
 */
export function persistOverlay(entityCode: string, bundle: UiOverlayBundle): void {
  saveOverlay(entityCode, bundle);
  emitOverlayChanged(entityCode);
  void metadataUiOverlayApi.save(entityCode, bundle).catch(() => {
    /* 离线容错：本地缓存已写，下次 hydrate 时以服务端为准 */
  });
}

/** 重置为默认投影：服务端软删 + 本地缓存清除（两端必须同时清，否则本地缓存会复活旧覆盖） */
export async function resetOverlay(entityCode: string): Promise<void> {
  clearOverlay(entityCode);
  try {
    await metadataUiOverlayApi.reset(entityCode);
  } catch {
    /* 服务端不可用时仅清本地：用户本机已恢复默认，服务端旧值不影响其渲染 */
  }
  emitOverlayChanged(entityCode);
}
