import React from 'react';
import { Select } from 'antd';
import { useEntityScope } from '../context/EntityScopeContext';

interface Props {
  width?: number;
  /** 选项上是否附带发布状态后缀（工作台默认展示，表单内可关掉）。 */
  showStatus?: boolean;
}

/**
 * 全局主数据模型选择器：选择结果写入 EntityScopeContext，全站共享。
 * 各治理页面统一使用本组件，避免再出现「手输模型 ID + 加载按钮」的死路交互。
 */
const EntityScopeSelect: React.FC<Props> = ({ width = 300, showStatus = true }) => {
  const { entities, entityId, setEntityId, entitiesLoading } = useEntityScope();

  return (
    <Select
      showSearch
      allowClear
      optionFilterProp="label"
      loading={entitiesLoading}
      placeholder="请选择主数据模型"
      notFoundContent={entitiesLoading ? '加载中…' : '暂无主数据模型'}
      style={{ width }}
      value={entityId}
      onChange={(v) => setEntityId(v)}
      options={entities.map((e) => ({
        value: e.id,
        label: showStatus ? `${e.name}${e.status === 'PUBLISHED' ? '（已发布）' : '（草稿）'}` : e.name
      }))}
    />
  );
};

export default EntityScopeSelect;
