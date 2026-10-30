import type { ReactElement } from 'react';
import {
  AuditOutlined,
  CalculatorOutlined,
  FileAddOutlined,
  BankOutlined,
  FileProtectOutlined,
  PauseCircleOutlined,
  SafetyCertificateOutlined,
  FormOutlined,
  HistoryOutlined,
  SolutionOutlined,
} from '@ant-design/icons';
import { ClaimSubmitPage } from './pages/ClaimSubmitPage';
import { ReviewWorkbenchPage } from './pages/ReviewWorkbenchPage';
import { PreExamWorkbenchPage } from './pages/PreExamWorkbenchPage';
import { ClaimProgressPage } from './pages/ClaimProgressPage';
import { AdjustmentAttributionPage } from './pages/AdjustmentAttributionPage';
import { EvidenceUploadPage } from './pages/EvidenceUploadPage';
import { FundWorkbenchPage } from './pages/FundWorkbenchPage';
import { AppealPage } from './pages/AppealPage';
import { SuspendPage } from './pages/SuspendPage';
import { InspectionWorkbenchPage } from './pages/InspectionWorkbenchPage';

/**
 * 智能理赔 · 路由与页面名的**唯一真源**（单导航架构的前端侧锚点）。
 *
 * 为什么需要这张表
 * ----------------
 * 2026-10-28 之前的形态是「两层菜单」：shell 侧边栏渲染 IAM 菜单（`/claim#/*` 8 条），
 * 而微应用内部又自绘了一条 Sider（9 条，名字还不一样：证据上传/材料上传、申诉处理/申诉复议、
 * 暂停案件/挂起·恢复，且 `后督抽检` 只在子应用侧存在）。同一份信息维护两处 ⇒ 必然漂移，
 * 用户看到的就是「两层菜单、子菜单还不一样」。
 *
 * 现在的架构约定（业界通行的微前端单一导航）：
 *   1. **导航只有一处**：登录后由 shell 依据 IAM 菜单渲染，微应用内不再出现侧边栏；
 *   2. 微应用只负责「hash 路由 → 页面」的映射，本表就是这份映射；
 *   3. `name` 必须与 IAM 菜单名逐字一致 —— shell 顶栏标题取的就是菜单名，
 *      这样「菜单名 / 顶栏标题 / 页面语义」天然同一，页面内不再重复写标题（一页一标题）；
 *   4. 本表与 IAM 菜单的一致性由 `scripts/e2e/ui/claim-single-nav-probe.mjs` 在真实环境里双向断言
 *      （菜单有页面无 ⇒ 幽灵菜单；页面有菜单无 ⇒ 功能不可达）。
 *
 * 顺序 = 作业链路顺序：初审 → 核赔 → 理算 → 材料 → 资金 → 申诉 → 挂起 → 后督，最后是员工自助两端。
 * 与迁移脚本 `scripts/migration/0078_seed_claim_preexam_menu.sql` 的 order_no 一一对应（1..10；
 * 该脚本在 0071 的 9 条基础上补齐「初审工作台」并整体重排）。
 *
 * 能力编号对照（synapClaim 工程落地版 A1–A9；**只做代码内追溯，不上屏**——页面上出现
 * 「材料上传（A2）」这类编号是给研发看的，业务用户看不懂）：
 *   /pre-exam 初审 A3 前段 · /review 核赔工作台 A3 · /adjust 理算归因 A6 · /fund 资金工作台 A7
 *   /evidence 材料上传 A2 · /progress 理赔进度 A5 · /appeal 申诉复议 A8
 *   /inspection 后督抽检 A9 · /suspend 挂起/恢复 正交机制 · /submit 理赔申请 A1
 */
export interface ClaimRoute {
  /** 微应用内 hash 路由（shell 侧菜单 path 形如 `/claim#/review`）。 */
  path: string;
  /** 菜单名 = 顶栏标题 = 本页唯一标题（与 IAM 菜单 `name` 逐字一致）。 */
  name: string;
  /** 菜单/页面图标（IAM 菜单存的是同名 antd 组件名字符串）。 */
  icon: ReactElement;
  /** 页面组件。 */
  element: ReactElement;
  /** 进入 `/claim` 时的落点（内勤主入口）。 */
  isDefault?: boolean;
}

export const CLAIM_ROUTES: ClaimRoute[] = [
  { path: '/pre-exam', name: '初审工作台', icon: <SolutionOutlined />, element: <PreExamWorkbenchPage /> },
  { path: '/review', name: '核赔工作台', icon: <AuditOutlined />, element: <ReviewWorkbenchPage />, isDefault: true },
  { path: '/adjust', name: '理算归因', icon: <CalculatorOutlined />, element: <AdjustmentAttributionPage /> },
  { path: '/evidence', name: '材料上传', icon: <FileAddOutlined />, element: <EvidenceUploadPage /> },
  { path: '/fund', name: '资金工作台', icon: <BankOutlined />, element: <FundWorkbenchPage /> },
  { path: '/appeal', name: '申诉复议', icon: <FileProtectOutlined />, element: <AppealPage /> },
  { path: '/suspend', name: '挂起/恢复', icon: <PauseCircleOutlined />, element: <SuspendPage /> },
  { path: '/inspection', name: '后督抽检', icon: <SafetyCertificateOutlined />, element: <InspectionWorkbenchPage /> },
  { path: '/submit', name: '理赔申请', icon: <FormOutlined />, element: <ClaimSubmitPage /> },
  { path: '/progress', name: '理赔进度', icon: <HistoryOutlined />, element: <ClaimProgressPage /> },
];

/** shell（qiankun activeRule）侧的路由前缀；菜单 path 形如 `/claim#/<hash>`。 */
export const CLAIM_MENU_PREFIX = '/claim';

/** 默认落点（无 hash 时重定向目标）。 */
export const CLAIM_DEFAULT_PATH = CLAIM_ROUTES.find((r) => r.isDefault)?.path ?? CLAIM_ROUTES[0].path;
