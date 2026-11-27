import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useEffect, useState, type ReactElement, type ReactNode } from 'react';
import { readStoredTheme } from '@bone/ui';
import { claimAntdTheme, resolveBroadcastTheme } from './claimTheme';

/**
 * 本应用的 antd Provider：中文语言包 + 中文表单校验默认文案 + 跟随全局主题。
 *
 * <p>与 bone-commerce-app 同口径：antd locale 不含 rc-field-form 的校验文案表，
 * 必须显式给 ConfigProvider form.validateMessages，否则 required 提示为英文。
 *
 * <p>主题（2026-11-24 补齐）：此前固定 antd 默认亮色，Shell 切暗色时本应用不跟随。
 * 现在订阅 `bone:theme:change` 总线（Shell 广播已解析的 light/dark），用平台设计令牌
 * `toAntdTheme` 生成 ConfigProvider.theme。独立运行（无 Shell 总线）时读本地偏好回退 system。
 */
export function AppAntdProvider({ children }: { children: ReactNode }): ReactElement {
  // 初值取本地偏好；嵌入 Shell 后由总线事件覆盖为 Shell 的解析值（单一真源在 Shell）。
  const [themeRaw, setThemeRaw] = useState<unknown>(() => readStoredTheme());

  useEffect(() => {
    const bus = (window as unknown as {
      __BONE_EVENT_BUS__?: {
        on: (event: string, handler: (data: unknown) => void) => unknown;
        off: (event: string, handler: (data: unknown) => void) => unknown;
      };
    }).__BONE_EVENT_BUS__;
    if (!bus || typeof bus.on !== 'function') return;
    const onTheme = (data: unknown): void => {
      const t = (data as { theme?: unknown } | undefined)?.theme;
      if (t !== undefined) setThemeRaw(t);
    };
    bus.on('bone:theme:change', onTheme);
    return () => {
      // SimpleEventBus.off 必须带 handler：不带会清空该事件全部监听者。
      bus.off('bone:theme:change', onTheme);
    };
  }, []);

  return (
    <ConfigProvider
      theme={claimAntdTheme(themeRaw)}
      locale={zhCN}
      form={{
        validateMessages: {
          default: '校验失败：${name}',
          required: '${name}为必填项',
          enum: '${name}必须是下列值之一：${enum}',
          whitespace: '${name}不能为空',
          types: {
            string: '${name}不是合法的字符串',
            number: '${name}不是合法的数字',
            array: '${name}不是合法的数组',
            object: '${name}不是合法的对象',
            date: '${name}不是合法的日期',
          },
          string: {
            max: '${name}最多 ${max} 个字符',
            min: '${name}最少 ${min} 个字符',
          },
          array: {
            max: '${name}最多 ${max} 个',
            min: '${name}最少 ${min} 个',
          },
        },
      }}
    >
      {children}
    </ConfigProvider>
  );
}

export { resolveBroadcastTheme };
