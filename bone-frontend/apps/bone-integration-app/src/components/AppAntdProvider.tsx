import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';

/**
 * 本应用的 antd Provider：中文语言包 + **中文表单校验默认文案**。
 *
 * <p><b>为什么需要（2026-10-05 UI 巡检实测）</b>：本应用有若干
 * {@code rules={[{ required: true }]}} 没写 {@code message}，提交时页面上直接出现英文
 * {@code 'displayName' is required} 这类中英混排。
 *
 * <p><b>为什么必须显式给 {@code form.validateMessages}（而不是只给 {@code locale}）</b>：
 * 英文文案来自 rc-field-form 的 {@code defaultValidateMessages}
 * （{@code node_modules/rc-field-form/lib/utils/messages.js}，其中
 * {@code required: "'${name}' is required"}），而 **antd 的 {@code locale/zh_CN} 里并不含这张表**
 * （实测其 {@code Form} 键为空对象）⇒ 只设 {@code locale} 改不掉它。
 * 只有 {@code ConfigProvider form.validateMessages} 才是覆盖这张表的正确入口。
 */
export function AppAntdProvider({ children }: { children: ReactNode }) {
  return (
    <ConfigProvider
      locale={zhCN}
      form={{
        validateMessages: {
          default: '校验失败：${name}',
          required: '${name}为必填项',
          enum: '${name}必须是下列值之一：${enum}',
          whitespace: '${name}不能为空',
          date: {
            format: '${name}日期格式不正确',
            parse: '${name}无法解析为日期',
            invalid: '${name}不是合法日期',
          },
          types: {
            string: '${name}不是合法的字符串',
            method: '${name}不是合法的方法',
            array: '${name}不是合法的数组',
            object: '${name}不是合法的对象',
            number: '${name}不是合法的数字',
            date: '${name}不是合法的日期',
            boolean: '${name}不是合法的布尔值',
            integer: '${name}不是合法的整数',
            float: '${name}不是合法的浮点数',
            regexp: '${name}不是合法的正则表达式',
            email: '${name}不是合法的邮箱地址',
            url: '${name}不是合法的网址',
            hex: '${name}不是合法的十六进制数',
          },
          array: {
            len: '${name}长度必须是 ${len}',
            max: '${name}最多 ${max} 个',
            min: '${name}最少 ${min} 个',
          },
          string: {
            len: '${name}长度必须是 ${len}',
            max: '${name}最多 ${max} 个字符',
            min: '${name}最少 ${min} 个字符',
          },
        },
      }}
    >
      {children}
    </ConfigProvider>
  );
}
