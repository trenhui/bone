/**
 * i18next 单例初始化（每个应用各自 init 一次）。
 *
 * ⚠️ 微前端边界：8 个应用各自独立打包，本模块在运行时是「**每应用一实例**」，不是跨应用单例
 * （与 event-bus 不同——后者靠 `window.__BONE_EVENT_BUS__` 共享）。因此：
 *   1. **每个应用都必须 import 本模块**（只靠 Shell init，子应用不会有语言包）；
 *   2. 语言切换由 Shell 经 `bone:theme:change` 广播，各应用自行 `i18n.changeLanguage(payload.locale)`；
 *   3. 各应用不得自决语言。
 *
 * 语言包用**静态 import**（非异步 fetch），避免首屏语言闪烁（FOUC）。
 * 详见：doc/design/国际化设计方案.md §5.2
 */
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';

import enUS from './locales/en-US.json';
import zhCN from './locales/zh-CN.json';

/** 支持的语言列表（权威源，语言包文件按此命名） */
export const SUPPORTED_LANGUAGES = ['zh-CN', 'en-US'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/** locale 持久化 key：唯一写入方是 Shell 的语言切换组件 */
export const LOCALE_STORAGE_KEY = 'bone.locale';

if (!i18n.isInitialized) {
  // ★ 显式解析初始语言，绕开 LanguageDetector 的异步链：
  //   detector + initImmediate(默认 true) 组合下，languages 回退链可能停留空数组，
  //   t() 全量 miss 返回裸 key（实测 E2E 联调复现）。语言由 Shell 单源下发并持久化，
  //   这里按同一 key 读取即可与 detector 的 localStorage 通道保持等价语义。
  let initialLng: string = 'zh-CN';
  try {
    const persisted = typeof localStorage !== 'undefined' ? localStorage.getItem(LOCALE_STORAGE_KEY) : null;
    if (persisted && (SUPPORTED_LANGUAGES as readonly string[]).includes(persisted)) {
      initialLng = persisted;
    }
  } catch {
    // localStorage 不可用（隐私模式等）→ 用默认 zh-CN
  }
  i18n
    .use(initReactI18next)
    .init({
      lng: initialLng,
      fallbackLng: 'zh-CN',
      supportedLngs: SUPPORTED_LANGUAGES as unknown as string[],

      // ★ 同步完成 init（语言包是静态 import，无需异步；同步后首个渲染帧即可翻译）
      initImmediate: false,

      // ⚠️ 禁开 nonExplicitSupportedLngs：与显式 supportedLngs（'zh-CN'/'en-US'）组合时，
      //    isSupportedCode 会把候选码剥成语言主码（'zh-CN'→'zh'）再比对，全部被拒 →
      //    i18n.languages 变空数组 → 所有 t() 原样返回裸 key（2026-10-01 系统管理页面
      //    国际化全量失效根因，Node 受控实验复现：开关该选项即复现/修复）。
      //    若需归一化裸语言码（'en'/'zh'），应加回 LanguageDetector 并用
      //    detection.convertDetectedLanguage 在检测层转换，而非此选项。

      resources: {
        'zh-CN': { translation: zhCN },
        'en-US': { translation: enUS },
      },

      interpolation: { escapeValue: false },
      saveMissing: false,
    });

  // ★ init 后显式重建语言链（实测：dev 下 init 自身组装的 languages 可能停留空数组，
  //   t() 全量 miss 返回裸 key；changeLanguage 幂等且能正确组装语言链，
  //   languageChanged 事件会触发 react-i18next 重渲染，最坏闪一帧裸 key）。
  void i18n.changeLanguage(initialLng);
}

/** 归一化到受支持的语言（AntD locale / dayjs locale 判定都以此为准） */
export function normalizeLocale(lng?: string | null): SupportedLanguage {
  return lng === 'en-US' ? 'en-US' : 'zh-CN';
}

/** 当前语言：必须用 resolvedLanguage（已按 supportedLngs 归一化），而非 i18n.language */
export function currentLocale(): SupportedLanguage {
  return normalizeLocale(i18n.resolvedLanguage ?? i18n.language);
}

export { default as i18n } from 'i18next';
export default i18n;
