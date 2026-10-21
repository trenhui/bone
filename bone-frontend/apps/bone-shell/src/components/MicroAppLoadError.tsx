import { Button } from 'antd';
import { WarningOutlined, ReloadOutlined } from '@ant-design/icons';

export interface MicroAppLoadErrorInfo {
  /** qiankun 应用名，如 bone-iam-app */
  app: string;
  /** 应用入口地址，如 //localhost:3003 */
  entry: string;
  /** 归因后的简短原因 */
  reason: string;
}

/**
 * 微应用加载失败的**归因**面板。
 *
 * 背景：qiankun 的加载失败是全局 uncaught error（不抛到 React 树），
 * 而容器 div 此时仍是空的 ⇒ 表现为「点菜单后白屏」，只有控制台有线索。
 * 本组件把「哪个应用 / 哪个入口 / 为什么 / 怎么修」摆在用户面前。
 */
export default function MicroAppLoadError({
  info,
  onRetry,
}: {
  info: MicroAppLoadErrorInfo;
  onRetry: () => void;
}): JSX.Element {
  const appDir = info.app ? `bone-frontend/apps/${info.app}` : 'bone-frontend/apps/<微应用目录>';
  return (
    <div className="subapp-error" role="alert">
      <div className="subapp-error-card">
        <div className="subapp-error-icon">
          <WarningOutlined />
        </div>
        <h3 className="subapp-error-title">微应用加载失败</h3>
        <p className="subapp-error-desc">
          <code className="subapp-error-app">{info.app || '未知微应用'}</code>
          <span className="subapp-error-entry">（入口 {info.entry || '未知'}）</span>
          的入口脚本无法访问，因此页面无法渲染。
        </p>
        <div className="subapp-error-reason">
          <span className="subapp-error-label">原因</span>
          {info.reason}
        </div>
        <div className="subapp-error-fix">
          <span className="subapp-error-label">修复</span>
          <span>通常是该微应用尚未启动。在仓库根目录执行：</span>
          <pre className="subapp-error-cmd">cd {appDir} &amp;&amp; npm run dev</pre>
        </div>
        <Button type="primary" icon={<ReloadOutlined />} onClick={onRetry}>
          重新加载
        </Button>
      </div>
    </div>
  );
}
