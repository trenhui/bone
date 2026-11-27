import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, Result, Typography } from 'antd';

/** 错误上报载荷：与具体监控厂商无关（Sentry / APM 各自适配，不在本组件内绑定）。 */
export interface ErrorReport {
  message: string;
  stack?: string;
  componentStack?: string;
  /** 捕获时刻（ISO 8601），便于与后端日志对齐。 */
  at: string;
}

/** 可注入的上报函数；返回 void，上报失败不得影响兜底 UI 的展示。 */
export type ErrorReporter = (report: ErrorReport) => void;

interface ErrorBoundaryProps {
  children: ReactNode;
  /** 生产环境由入口注入真实监控上报；未注入时仅 console.error（不伪造"已上报"）。 */
  reporter?: ErrorReporter;
}

interface ErrorBoundaryState {
  error: Error | null;
}

/**
 * 路由级错误边界。
 *
 * <p>捕获页面<b>渲染期</b>异常，展示可重试的兜底 UI，而不是让 React 卸载整棵树导致整页白屏且无痕。
 * 任何页面抛错都被兜在这里（错误详情可一键复制，便于反馈）。组件内不吞异常——仍会
 * {@code console.error} 打印。
 *
 * <p>可观测性（2026-11-24 补齐）：新增可注入的 {@link ErrorReporter}。此前只有 console.error，
 * 生产环境线上故障不可见；现在入口可注入真实 APM 上报。刻意不内置 Sentry SDK——仓库没有该依赖与
 * DSN，硬接会造出"配了不生效"的假监控；上报失败也吞掉，绝不让监控故障把兜底 UI 变成二次白屏。
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error('[ErrorBoundary] 页面渲染异常:', error, info);
    const report: ErrorReport = {
      message: error.message,
      stack: error.stack,
      componentStack: info.componentStack ?? undefined,
      at: new Date().toISOString(),
    };
    try {
      this.props.reporter?.(report);
    } catch (reportErr) {
      // 监控上报失败不得影响兜底 UI（避免监控故障引发二次白屏）。
      console.error('[ErrorBoundary] 错误上报失败:', reportErr);
    }
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
