import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactElement, ReactNode } from 'react';

/**
 * 本应用的 antd Provider：中文语言包 + 中文表单校验默认文案。
 *
 * 与 bone-commerce-app 同口径：antd locale 不含 rc-field-form 的校验文案表，
 * 必须显式给 ConfigProvider form.validateMessages，否则 required 提示为英文。
 */
export function AppAntdProvider({ children }: { children: ReactNode }): ReactElement {
  return (
    <ConfigProvider
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
