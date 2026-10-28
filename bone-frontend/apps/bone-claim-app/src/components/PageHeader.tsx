import type { ReactElement, ReactNode } from 'react';
import { Typography } from 'antd';

export interface PageHeaderProps {
  /** 页面标题。**必须与 IAM 菜单名逐字一致**（shell 顶栏标题同样取自菜单名）。 */
  title: string;
  /** 可选副标题：一句话说明本页能做什么（不写实现细节、不写能力编号）。 */
  description?: ReactNode;
  /** 可选右侧操作区（避免与 Card 头部抢位置时使用）。 */
  extra?: ReactNode;
}

/**
 * 统一页面标题（Ant Design Pro「面包屑 + 页面标题」范式的内容区一半）。
 *
 * 为什么要收敛成一个组件
 * ----------------------
 * 改造前 9 个页面的「标题」有三种写法：`Typography.Title level={3}`（3 个页面）、
 * Card `title` 里塞「材料上传（A2）」这类**能力编号**（6 个页面），还有页面干脆没有标题。
 * 同一个微应用里标题位置、字号、术语三处不一致，用户每换一页都要重新找「我现在在哪」。
 *
 * 约定：
 *   1. 一页只有一个标题（页面级用本组件；Card 只放「区块名」，不重复页面名）；
 *   2. 标题文案 = IAM 菜单名 = shell 顶栏标题，三处同一字符串，改一处即三处一致；
 *   3. 能力编号（A2/A6/A7/A8/A9）属于实现映射，留在代码注释里，不上屏。
 */
export function PageHeader({ title, description, extra }: PageHeaderProps): ReactElement {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 16,
        marginBottom: 16,
      }}
    >
      <div>
        <Typography.Title level={4} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        {description ? (
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 4 }}>
            {description}
          </Typography.Text>
        ) : null}
      </div>
      {extra ? <div>{extra}</div> : null}
    </div>
  );
}
