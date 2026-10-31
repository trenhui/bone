import React from 'react';
import { Tag, Tooltip } from 'antd';
import type { ApprovalSummaryRow } from '../services/metadataApi';
import { approvalCellOf } from '../utils/approvalSummary';

/**
 * 列表「当前审批人」单元格：引擎台账派生，不读单据字段。
 *
 * <p>取色与文案全部下沉到 {@link approvalCellOf}（纯函数 + 单测），此处只负责画。
 * 无审批流的记录渲染中性破折号——`已提交/草稿` 的单据在列表里大面积存在，
 * 给它们人人一个灰色标签会把真正「卡住等人」的几行淹没掉。
 */
const ApproverCell: React.FC<{
  row?: ApprovalSummaryRow | null;
  names?: Record<string, string>;
}> = ({ row, names }) => {
  const view = approvalCellOf(row, names ?? {});
  if (view.tone === null) {
    return <span style={{ color: 'rgba(0,0,0,.35)' }}>{view.label}</span>;
  }
  return (
    <Tooltip title={view.detail}>
      <Tag color={view.tone} style={{ marginInlineEnd: 0 }}>
        {view.label}
      </Tag>
    </Tooltip>
  );
};

export default ApproverCell;
