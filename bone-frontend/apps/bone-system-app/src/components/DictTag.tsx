import React from 'react';
import { Tag } from 'antd';
import { useDict } from '@/hooks/useDict';
import type { DictTagType } from '@/types';

/** 展示语义色 → antd Tag 色板（与后端 DictTagType 一一对应）。 */
const COLOR: Record<DictTagType, string> = {
  default: 'default',
  info: 'blue',
  success: 'green',
  warning: 'orange',
  error: 'red',
};

interface DictTagProps {
  /** 值域编码 */
  type: string;
  /** 字典项编码 */
  code?: string | null;
  /** 未命中字典时的兜底文案（默认直接显示编码） */
  fallback?: string;
}

/**
 * 按字典渲染带色标签：色值来自字典项自身的 {@code tagType}，不由页面写死——
 * 同一状态在不同页面的口径应一致，写死在页面里就是把一份值域知识复制 N 份。
 */
const DictTag: React.FC<DictTagProps> = ({ type, code, fallback }) => {
  const { options } = useDict(type);
  const hit = options.find((o) => o.code === code);
  const label = hit?.label ?? fallback ?? code ?? '-';
  const color = COLOR[(hit?.tagType ?? 'default') as DictTagType];
  return <Tag color={color}>{label}</Tag>;
};

export default DictTag;
