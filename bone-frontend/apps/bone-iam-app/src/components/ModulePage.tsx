import React from 'react';
import { PageContainer, ProCard } from '@ant-design/pro-components';
import type { ReactNode } from 'react';

export interface ModulePageProps {
  /** 页面主标题，如「组织机构管理」 */
  title: string;
  /** 副标题 / 功能描述 */
  description?: ReactNode;
  /** 标题右侧操作区（如「新建」按钮、批量操作） */
  extra?: ReactNode;
  /** 顶部统计区（StatisticCard 等），可选 */
  statistics?: ReactNode;
  /** 是否给正文包一层 ProCard 容器（默认 true） */
  card?: boolean;
  children?: ReactNode;
  /** 正文容器样式微调 */
  bodyStyle?: React.CSSProperties;
}

/**
 * IAM 模块统一页面外框。
 * 采用 Ant Design Pro 的 PageContainer + ProCard，使账号/角色/权限/组织/菜单等
 * 全部页面拥有统一的企业级观感：标题 + 描述 + 右上操作区 + 可选统计行 + 卡片正文。
 */
const ModulePage: React.FC<ModulePageProps> = ({
  title,
  description,
  extra,
  statistics,
  card = true,
  children,
  bodyStyle,
}) => {
  return (
    <PageContainer
      header={{ title }}
      content={description}
      extra={extra}
      style={{ paddingBlockEnd: 0 }}
    >
      {statistics && <div style={{ marginBlockEnd: 16 }}>{statistics}</div>}
      {card ? (
        <ProCard style={{ padding: 0, ...bodyStyle }}>{children}</ProCard>
      ) : (
        children
      )}
    </PageContainer>
  );
};

export default ModulePage;
