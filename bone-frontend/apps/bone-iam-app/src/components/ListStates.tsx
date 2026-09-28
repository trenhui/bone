import type { ReactElement, ReactNode } from 'react';
import { Button, Empty, Result } from 'antd';

/**
 * 列表页「四态」共享呈现组件（详设 §2.11 第 3 条：加载中 / 空 / 错误 / 无权限）。
 *
 * <p>加载中由 Table 的 {@code loading} 承担（骨架行），本文件只覆盖其余三态：
 * 错误态、无权限态、空态。各列表页在渲染区按「错误 → 403 → 空 → 正常表格」顺序分流。
 */

/** 加载失败态：展示错误文案 + 重试。 */
export function ListErrorState({
  error,
  onRetry,
}: {
  error?: string;
  onRetry: () => void;
}): ReactElement {
  return (
    <Result
      status="error"
      title="加载失败"
      subTitle={error ?? '数据加载出错，请稍后重试'}
      extra={
        <Button type="primary" onClick={onRetry}>
          重试
        </Button>
      }
    />
  );
}

/** 无访问权限态（HTTP 403）：不与「加载失败」混为一谈，引导联系管理员。 */
export function ListForbiddenState({ onRetry }: { onRetry: () => void }): ReactElement {
  return (
    <Result
      status="403"
      title="无访问权限"
      subTitle="当前账号没有查看该列表的权限，请联系管理员开通后重试。"
      extra={
        <Button onClick={onRetry}>重试</Button>
      }
    />
  );
}

/** 空态：友好提示 + 可选行动按钮（如「新建」）。 */
export function ListEmptyState({
  text,
  action,
}: {
  text: string;
  action?: ReactNode;
}): ReactElement {
  return (
    <div style={{ padding: '48px 0', textAlign: 'center' }}>
      <Empty description={text} />
      {action ? <div style={{ marginTop: 16 }}>{action}</div> : null}
    </div>
  );
}
