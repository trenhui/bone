import type { ReactElement, ReactNode } from 'react';
import { Alert, Empty, Spin } from 'antd';

/**
 * 异步区块的三态边界：loading / error / empty / content。
 *
 * <p>与路由级 `ErrorBoundary` 分工：后者只兜渲染异常；本组件管**读取数据的正常三态**。
 * 此前每个页面手写一遍，常见偏差是失败时被渲染成空列表——用户看到"暂无数据"而不会重试，
 * 把系统故障伪装成业务空态。
 *
 * <p>约束：`error` 必须展示可操作的重试提示；调用方传 `onRetry` 时给出重试按钮。
 */
export interface AsyncBoundaryProps {
  loading?: boolean;
  error?: string | null;
  empty?: boolean;
  emptyText?: string;
  onRetry?: () => void;
  children: ReactNode;
}

export function AsyncBoundary({
  loading,
  error,
  empty,
  emptyText = '暂无数据',
  onRetry,
  children,
}: AsyncBoundaryProps): ReactElement {
  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        message={error}
        description={onRetry ? '请确认网络/权限后重试' : undefined}
        action={
          onRetry ? (
            <button type="button" onClick={onRetry} data-testid="async-retry">
              重试
            </button>
          ) : undefined
        }
      />
    );
  }
  if (loading) return <Spin data-testid="async-loading" tip="加载中" />;
  if (empty) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  return <>{children}</>;
}
