import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result, Typography } from 'antd';

interface ErrorBoundaryProps {
  children: ReactNode;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * 路由级错误边界。
 *
 * <p>捕获页面<b>渲染期</b>异常，展示可重试的兜底 UI，而不是让 React 卸载整棵树导致整页白屏且无痕。
 * 任何页面抛错都被兜在这里（错误详情可一键复制，便于反馈）。组件内不吞异常——仍会
 * {@code console.error} 打印，生产环境应再接监控上报（Sentry 等）。</p>
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // 生产环境应上报到监控（Sentry 等）；此处仅打印，避免白屏且无痕。
    console.error('[ErrorBoundary] 页面渲染异常:', error, info);
  }

  private handleRetry = (): void => {
    this.setState({ error: null });
  };

  render(): ReactNode {
    const { error } = this.state;
    if (!error) {
      return this.props.children;
    }
    return (
      <Result
        status="error"
        title="页面渲染出错"
        subTitle="组件在渲染时发生异常，已被错误边界捕获。可尝试重试，或重新加载页面。"
        extra={[
          <Button type="primary" key="retry" onClick={this.handleRetry}>
            重试
          </Button>,
          <Button key="reload" onClick={() => window.location.reload()}>
            重新加载
          </Button>,
        ]}
      >
        <Typography.Paragraph type="secondary" copyable>
          {error.message}
        </Typography.Paragraph>
      </Result>
    );
  }
}
