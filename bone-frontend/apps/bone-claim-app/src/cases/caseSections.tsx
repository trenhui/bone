import type { ComponentType, ReactElement } from 'react';
import type { CaseTab } from '../workbench/caseDeepLinks';
import { EvidenceUploadPage } from '../pages/EvidenceUploadPage';
import { AdjustmentAttributionPage } from '../pages/AdjustmentAttributionPage';
import { FundWorkbenchPage } from '../pages/FundWorkbenchPage';
import { AppealPage } from '../pages/AppealPage';
import { SuspendPage } from '../pages/SuspendPage';
import { InspectionWorkbenchPage } from '../pages/InspectionWorkbenchPage';

/**
 * 案件 360「对象内聚」的视图注册表 —— 六个单案作业视图的**唯一映射源**。
 *
 * <p>为什么不是把整页塞进 Tab：六个作业页原本自带 PageHeader + CaseIdBar + 未选案件空态，
 * 直接嵌入会出现双标题与双 ID 源。这里登记的是各页的「无壳内容形态」：同一个页面组件
 * 以 `embeddedClaimId` 渲染时**不输出页面标题、ID 输入条与外层留白**，业务逻辑与取数
 * 仍是同一份实现（不复制页面、不引入第二套界面，避免「同一功能两套界面必然漂移」）。
 *
 * <p>旧路由页面壳继续从 `?claimId=` 读取 ID 并渲染同一个组件；案件 360 的 Tab 通过本表
 * 把 `claimId` 直接传进去。URL `?claimId=&tab=` 仍是唯一真源，`trace`（队列型核赔工作台）
 * 不在本表内，保持既有深链桥。
 */
export interface CaseSectionProps {
  /** 案件 ID 原文（18 位雪花号按字符串传递，绝不转 Number）。 */
  claimId: string;
}

export type EmbeddedCaseTab = 'material' | 'adjust' | 'fund' | 'appeal' | 'suspend' | 'inspection';

export const EMBEDDED_CASE_TABS: readonly EmbeddedCaseTab[] = [
  'material', 'adjust', 'fund', 'appeal', 'suspend', 'inspection',
] as const;

/** 每个视图都是「无壳内容组件」：只接收 claimId，不接收 URL / 输入框状态。 */
export const CASE_SECTIONS: Record<EmbeddedCaseTab, ComponentType<CaseSectionProps>> = {
  material: ({ claimId }) => <EvidenceUploadPage embeddedClaimId={claimId} />,
  adjust: ({ claimId }) => <AdjustmentAttributionPage embeddedClaimId={claimId} />,
  fund: ({ claimId }) => <FundWorkbenchPage embeddedClaimId={claimId} />,
  appeal: ({ claimId }) => <AppealPage embeddedClaimId={claimId} />,
  suspend: ({ claimId }) => <SuspendPage embeddedClaimId={claimId} />,
  inspection: ({ claimId }) => <InspectionWorkbenchPage embeddedClaimId={claimId} />,
};

/** `trace` 是队列型工作台、`overview` 是概览，两者都不内嵌。 */
export function isEmbeddedCaseTab(tab: CaseTab): tab is EmbeddedCaseTab {
  return (EMBEDDED_CASE_TABS as readonly string[]).includes(tab);
}

/** 统一渲染入口：保证所有内嵌视图都通过同一组 props 拿到案件上下文。 */
export function renderCaseSection(tab: EmbeddedCaseTab, claimId: string): ReactElement {
  const Section = CASE_SECTIONS[tab];
  return <Section claimId={claimId} />;
}
