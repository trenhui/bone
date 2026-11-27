import React from 'react';
import { Link } from 'react-router-dom';
import { Menu, Tag } from 'antd';

/** 入口降级文案：403 无权限、404 能力未启用，绝不谎报入口可用。 */
export const ENTRY_DEGRADED_TEXT: Record<number, string> = {
  403: '无权限访问该入口',
  404: '该能力未启用',
};

const ENTRIES = [
  { key: 'contract-hitl', label: '合同人工终决', path: '/contract-hitl' },
  { key: 'human-review', label: '人工复核', path: '/human-review' },
  { key: 'ai-readiness', label: 'AI 就绪度', path: '/ai-readiness' },
  { key: 'governance', label: '治理态势', path: '/governance' },
];

/**
 * 采购工作台共享导航：只链接现有页面，不缓存也不重复渲染其判词（避免态势口径分叉）。
 */
const ProcurementWorkbenchNav: React.FC = () => (
  <Menu
    mode="horizontal"
    items={ENTRIES.map((e) => ({
      key: e.key,
      label: (
        <Link to={e.path}>
          {e.label}
          <Tag>既有入口</Tag>
        </Link>
      ),
    }))}
  />
);

export default ProcurementWorkbenchNav;
