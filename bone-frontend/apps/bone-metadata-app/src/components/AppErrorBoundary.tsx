import React from 'react';
import { Result, Button } from 'antd';

interface AppErrorBoundaryProps {
  children: React.ReactNode;
}

interface AppErrorBoundaryState {
  hasError: boolean;
}

/**
 * 应用级错误边界（元数据工作区融合方案 §0.6.4 B4）：捕获 bone-metadata-app
 * 渲染期异常，避免单 Tab 崩溃导致整页白屏。
 *
 * <p>对标 bone-system-app 的 {@code AppErrorBoundary} 与 Shell 的
 * {@code MicroAppErrorBoundary}——Shell 只兜住了「微应用挂载」这一层，子应用内部
 * 的渲染崩溃仍会冒泡成白屏，这里在子应用内部再兜一层。
 *
 * <p>异步请求错误（含 401/403）已由各页 {@code catch + errorMessage} 处理，
 * 后端 {@code @PreAuthorize} 是真正的授权边界；本边界仅兜底「渲染期」崩溃。
 */
export default class AppErrorBoundary extends React.Component<
  AppErrorBoundaryProps,
  AppErrorBoundaryState
> {
  constructor(props: AppErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo): void {
    // 渲染期异常通常意味着前端代码缺陷，打点供排查（后端链路已带 MDC traceId）。
    console.error('[bone-metadata] 渲染期异常:', error, info.componentStack);
  }

  render(): React.ReactNode {
    if (this.state.hasError) {
      return (
        <Result
          status="error"
          title="页面出现未预期错误"
          subTitle="部分功能可能暂时不可用，请重试或刷新页面。"
          extra={
            <Button type="primary" onClick={() => this.setState({ hasError: false })}>
              重试
            </Button>
          }
        />
      );
    }
    return this.props.children;
  }
}
