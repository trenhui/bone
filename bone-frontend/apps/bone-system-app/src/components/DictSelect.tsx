import React from 'react';
import { Cascader, Select } from 'antd';
import { useDict, useDictTree } from '@/hooks/useDict';
import type { DictItem } from '@/types';

interface DictSelectProps {
  /** 值域编码 */
  type: string;
  value?: string;
  onChange?: (value?: string) => void;
  placeholder?: string;
  allowClear?: boolean;
  disabled?: boolean;
  style?: React.CSSProperties;
}

/** 扁平 / 枚举值域的选择器（LIST、ENUM）。 */
export const DictSelect: React.FC<DictSelectProps> = ({
  type,
  value,
  onChange,
  placeholder,
  allowClear = true,
  disabled,
  style,
}) => {
  const { options, loading } = useDict(type);
  return (
    <Select
      style={style}
      loading={loading}
      value={value}
      onChange={onChange}
      allowClear={allowClear}
      disabled={disabled}
      placeholder={placeholder ?? '请选择'}
      options={options.map((o) => ({ value: o.code, label: o.label }))}
    />
  );
};

/** antd Cascader 的递归选项形状（自引用类型，避免退化成 unknown[]）。 */
interface CascaderOption {
  value: string;
  label: string;
  children?: CascaderOption[];
}

interface DictCascaderProps extends Omit<DictSelectProps, 'value' | 'onChange'> {
  value?: string[];
  onChange?: (value?: string[]) => void;
}

/** 级联值域的选择器（CASCADE）：一次加载整棵树，前端按路径回传。 */
export const DictCascader: React.FC<DictCascaderProps> = ({
  type,
  value,
  onChange,
  placeholder,
  allowClear = true,
  disabled,
  style,
}) => {
  const { tree, loading } = useDictTree(type);
  const toOptions = (nodes: DictItem[]): CascaderOption[] =>
    nodes.map((n) => ({
      value: n.code,
      label: n.label,
      children: n.children?.length ? toOptions(n.children) : undefined,
    }));
  return (
    <Cascader
      style={style}
      loading={loading}
      value={value}
      onChange={(v) => onChange?.((v as string[] | undefined) ?? undefined)}
      allowClear={allowClear}
      disabled={disabled}
      placeholder={placeholder ?? '请选择'}
      options={toOptions(tree)}
      changeOnSelect
    />
  );
};
