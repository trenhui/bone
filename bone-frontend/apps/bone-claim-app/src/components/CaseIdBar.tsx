import type { ReactElement, ReactNode } from 'react';
import { Button, Empty, Input, Space, Typography } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

/**
 * 案件 ID 查询条 —— 6 个单案页共用的同一段 UI。
 *
 * <p>改造前每个页面各写一遍 `<Input placeholder="案件ID" style={{width:140}}/> + <Button>查询</Button>`，
 * 宽度在 140/160/260 三种值之间漂移，按钮文案在「查询」与图标+文字之间漂移。收敛成一个组件后，
 * 位置、宽度、回车提交行为只有一处。
 */
export interface CaseIdBarProps {
  value: string;
  onChange: (value: string) => void;
  /** 回车或点查询时触发 */
  onSubmit: () => void;
  /** 有在途请求时禁用查询按钮，避免连点 */
  loading?: boolean;
  /** 右侧附加操作（如「刷新」）。 */
  extra?: ReactNode;
}

export function CaseIdBar({ value, onChange, onSubmit, loading, extra }: CaseIdBarProps): ReactElement {
  return (
    <Space>
      <Input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        onPressEnter={onSubmit}
        placeholder="案件ID"
        allowClear
        style={{ width: 160 }}
      />
      <Button icon={<SearchOutlined />} loading={loading} onClick={onSubmit}>
        查询
      </Button>
      {extra}
    </Space>
  );
}

/**
 * 未选择案件时的统一空态。
 *
 * <p>与「查无此案」区分开：未选择是**操作前置缺失**（用 info/引导语），查无此案是**服务端答复**
 * （用 warning/错误）。两者混用会让用户分不清是自己没填还是系统没数据。
 */
export function CaseNotSelected({ hint = '输入案件ID后查询' }: { hint?: string }): ReactElement {
  return (
    <Empty
      image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={
        <Typography.Text type="secondary">
          尚未选择案件 · {hint}
        </Typography.Text>
      }
    />
  );
}
