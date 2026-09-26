/**
 * 微应用的 locale 订阅入口（i18n 方案 §5.5.1）。
 *
 * 8 个应用各自打包、各自持有一份 i18next 实例，所以**每个应用都必须订阅一次**——
 * 只靠 Shell init 不会让子应用的文案跟着变。
 *
 * 这里刻意**不 import `@bone/core-event-bus`**：总线实例是 Shell 挂到 `window.__BONE_EVENT_BUS__` 上的
 * 那一份（`SimpleEventBus` 是模块级 `new` 出来的，子应用 import 到的是另一份实例，收不到广播）。
 * 从 window 取还能避免给 shared-utils 增加一条包依赖边。
 */
import { i18n } from './index';

type LocaleChangePayload = { theme?: string; locale?: string };

interface MinimalBus {
  on: (event: string, handler: (data: unknown) => void) => unknown;
  off: (event: string, handler: (data: unknown) => void) => unknown;
}

const LOCALE_EVENT = 'bone:theme:change';

function resolveBus(): MinimalBus | null {
  const bus = (window as unknown as { __BONE_EVENT_BUS__?: MinimalBus }).__BONE_EVENT_BUS__;
  if (!bus || typeof bus.on !== 'function') {
    // 独立运行（脱离 Shell）时没有共享总线：语言由 LanguageDetector 决定，无需订阅
    return null;
  }
  return bus;
}

/**
 * 订阅 Shell 广播的语言变更，并同步本应用的 i18next 实例。
 *
 * @returns 取消订阅函数（微应用 unmount 时调用，避免重复挂载累积 handler）
 */
export function subscribeLocaleChange(): () => void {
  const bus = resolveBus();
  if (!bus) {
    return () => undefined;
  }

  const handler = (data: unknown) => {
    const locale = (data as LocaleChangePayload | undefined)?.locale;
    if (locale && i18n.language !== locale) {
      void i18n.changeLanguage(locale);
    }
  };

  bus.on(LOCALE_EVENT, handler);
  return () => {
    bus.off(LOCALE_EVENT, handler);
  };
}
