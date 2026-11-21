/**
 * 命令面板的注册表单一真源（纯数据 + 纯函数，可单测）。
 *
 * 权限口径对齐 `workbench/profiles.ts`：`requiresAny` 只做**展示层裁剪**，空 = 登录用户可见；
 * 真正的门禁仍是后端 @PreAuthorize 与路由级 <Auth>（App.tsx 的 HIDDEN_ROUTE_REQUIRED），
 * 前端隐藏命令不等于接口安全，也不能据此放宽任何操作。
 */

import type { NavigateFunction } from 'react-router-dom';
import { PERMISSIONS } from '../constants/permissions';

export interface WorkbenchCommand {
  id: string;
  label: string;
  description: string;
  /** 检索关键词（命令名之外的同义词，避免用户必须记住菜单原名）。 */
  keywords: string[];
  /** 导航目标（微应用内 hash 路由，可带 query）；与 action 二选一。 */
  path?: string;
  /** 非导航动作；当前仅「聚焦队列搜索」——复用 WorkbenchPage 既有的 ?panel=search 面板。 */
  action?: 'focus-queue-search';
  /** OR 语义：命中任一码才在命令面板中展示。 */
  requiresAny?: string[];
}

export const WORKBENCH_COMMANDS: readonly WorkbenchCommand[] = [
  {
    id: 'focus-queue-search',
    label: '聚焦队列搜索',
    description: '打开全局检索面板（按案件 ID / 保单号 / 发票号直达）',
    keywords: ['搜索', '检索', 'search', '全局', '队列', '⌘k'],
    action: 'focus-queue-search',
  },
  {
    id: 'review',
    label: '核赔工作台',
    description: '领取并按队列逐案核赔',
    keywords: ['核赔', '审核', 'review', '待办'],
    path: '/review',
  },
  {
    id: 'workbench',
    label: '我的工作台',
    description: '四空间能力画像首页',
    keywords: ['首页', '工作台', 'home', 'workbench'],
    path: '/workbench',
  },
  {
    id: 'case-360',
    label: '案件 360',
    description: '一个 claimId 看全材料 / 理算 / 资金 / 决策轨迹',
    keywords: ['案件', '详情', 'case', '360'],
    path: '/case',
  },
  {
    id: 'evidence',
    label: '材料上传',
    description: '单证列表、OCR 状态与补件',
    keywords: ['材料', '影像', '单证', 'evidence'],
    path: '/evidence',
  },
  {
    id: 'progress',
    label: '理赔进度',
    description: '按案件 ID 查询进度与决策解释',
    keywords: ['进度', '状态', 'progress'],
    path: '/progress',
  },
  {
    id: 'analytics',
    label: '运营分析看板',
    description: '决策终态分布 / 复核率 / 推送成功率 / 影子自动化率（≠出金率）',
    keywords: ['看板', '指标', '分析', 'analytics', 'dashboard'],
    path: '/analytics',
    requiresAny: [PERMISSIONS.CLAIMS_READ],
  },
  {
    id: 'ai-governance',
    label: 'AI 治理',
    description: '能力就绪 / 逐案守门预演 / 盲标校准 / 放量档位',
    keywords: ['治理', '守门', 'ai', 'governance'],
    path: '/ai-governance',
    requiresAny: [PERMISSIONS.CLAIMS_READ],
  },
  {
    id: 'inspection',
    label: '后督抽检',
    description: '人工抽检与 AI 质检 DIFF 队列',
    keywords: ['质检', '抽检', '后督', 'inspection'],
    path: '/inspection',
    requiresAny: [PERMISSIONS.INSPECTION_WRITE],
  },
  {
    id: 'blind-labels',
    label: '盲标工作台',
    description: '金标准标注（禁区：不展示 aiSuggestion）',
    keywords: ['盲标', '标注', 'blind', 'label'],
    path: '/blind-labels',
    requiresAny: [PERMISSIONS.INSPECTION_WRITE],
  },
];

/** 输入过滤 + 权限裁剪；can 由 usePermission().hasAny 提供，保持本文件无 React 依赖。 */
export function filterCommands(
  commands: readonly WorkbenchCommand[],
  query: string,
  can: (codes: string[]) => boolean,
): WorkbenchCommand[] {
  const q = query.trim().toLowerCase();
  return commands.filter((cmd) => {
    if (cmd.requiresAny?.length && !can(cmd.requiresAny)) return false;
    if (!q) return true;
    return [cmd.label, cmd.description, ...cmd.keywords].some((term) =>
      term.toLowerCase().includes(q),
    );
  });
}

/** 执行命令：导航命令走 react-router，动作命令复用既有深链约定。 */
export function runCommand(command: WorkbenchCommand, navigate: NavigateFunction): void {
  if (command.path) {
    navigate(command.path);
    return;
  }
  if (command.action === 'focus-queue-search') {
    navigate('/workbench?panel=search');
  }
}
