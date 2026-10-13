import { metadataUiOverlayApi } from '../services/metadataApi';
import { UI_OVERLAY_VERSION, loadOverlay, saveOverlay, clearOverlay, type UiOverlay } from './columnProjection';

/**
 * 界面列投影覆盖的「服务端优先」同步层（0042 · P2-④ 服务端化）。
 *
 * <p><b>为什么不是直接替换 localStorage</b>：PRD v0.32 §12.2 D2 的 localStorage 键仍有价值——
 * ① 服务端未配置时它就是唯一真源（升级过渡期）；② 服务端请求失败（弱网/未登录直连）时它是兜底，
 * 保证「界面 Tab 打不开也不白屏」。hydrate 三级降级：
 *
 * <pre>
 *   服务端覆盖（version 校验通过） → 本地缓存 → null（调用方再 defaultOverlay）
 * </pre>
 *
 * <p>写路径对称：先写本地缓存（UI 不等待网络），再异步推服务端（失败静默 —— 偏好丢失的代价
 * 远小于阻塞一次列设置交互；下次 hydrate 时服务端旧值会覆盖本地新值，属可接受最终一致）。
 */

/** 把服务端返回的 JSON 字符串解析为 UiOverlay；版本不符/解析失败按「无覆盖」处理 */
function parseServerOverlay(json: string | null | undefined): UiOverlay | null {
  if (!json) return null;
  try {
    const parsed = JSON.parse(json) as UiOverlay;
    if (parsed.version !== UI_OVERLAY_VERSION) return null;
    return parsed;
  } catch {
    return null;
  }
}

/**
 * 读覆盖：服务端优先 → 本地缓存兜底 → null。
 *
 * @param entityCode 实体编码
 * @returns 生效覆盖；无任何配置时 null（调用方按 defaultOverlay 渲染）
 */
export async function fetchOverlay(entityCode: string): Promise<UiOverlay | null> {
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
 * 保存覆盖：本地缓存立即写（乐观 UI），服务端异步推。
 *
 * <p>不 await 服务端结果：列设置是高频微交互（切换可见性/改名/排序），
 * 每次等一轮往返会让 UI 发涩。失败时本地缓存仍在，用户无感知；
 * 极端情况（服务端持久化失败且用户换设备）才丢偏好，可接受。
 */
export function persistOverlay(entityCode: string, overlay: UiOverlay): void {
  saveOverlay(entityCode, overlay);
  void metadataUiOverlayApi.save(entityCode, overlay).catch(() => {
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
}
