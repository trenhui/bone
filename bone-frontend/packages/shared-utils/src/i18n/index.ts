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
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import enUS from './locales/en-US.json';
import zhCN from './locales/zh-CN.json';

/** 支持的语言列表（权威源，语言包文件按此命名） */
export const SUPPORTED_LANGUAGES = ['zh-CN', 'en-US'] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];

/** locale 持久化 key：唯一写入方是 Shell 的语言切换组件 */
export const LOCALE_STORAGE_KEY = 'bone.locale';

if (!i18n.isInitialized) {
  i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      fallbackLng: 'zh-CN',
      supportedLngs: SUPPORTED_LANGUAGES as unknown as string[],

      // 浏览器普遍上报 'en' / 'zh' 而非 'en-US' / 'zh-CN'；不开此项会直接落到 fallbackLng
      // → 英文浏览器用户默认看到中文。开启后 'en' 归一化到 'en-US'。
      nonExplicitSupportedLngs: true,

      resources: {
        'zh-CN': { translation: zhCN },
        'en-US': { translation: enUS },
      },

      detection: {
        order: ['querystring', 'cookie', 'localStorage', 'navigator'],
        lookupQuerystring: 'lang',
        lookupCookie: 'bone_i18n',
        lookupLocalStorage: LOCALE_STORAGE_KEY,
        // ★ 单一写入方：持久化只由 Shell 的语言切换组件写 localStorage。
        //   这里若也 caches，LanguageDetector 会在 init 时回写同一 key，形成两个写入方。
        caches: [],
      },

      interpolation: { escapeValue: false },
      saveMissing: false,
    });
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
