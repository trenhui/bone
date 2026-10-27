/* eslint-disable @typescript-eslint/explicit-module-boundary-types */
import { createApiClient } from '@bone/shared-services';
import type { ApiResponse } from '@bone/shared-types';
import type { FlowDoc, Violation } from '@bone/workflow-dsl';

const api = createApiClient('/api/v1', { headers: { 'Content-Type': 'application/json' } });

/**
 * 当前操作人：写路径（保存 / 发布 / 上下线）的审计列必须回答「谁改的」，故从登录态 token 解出 userId 注入
 * {@code X-Operator-Id} 头。token 取不到时回退 {@code anonymous}（后端会因缺操作人直接 400，而非静默匿名写入）。
 */
function operatorId(): string {
  try {
    const token = localStorage.getItem('token');
    if (token && token.split('.').length === 3) {
      const payload = JSON.parse(
        atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')),
      );
      return payload.sub || payload.userId || payload.username || 'anonymous';
    }
  } catch {
    /* 解析失败按 anonymous 处理，由后端拦截 */
  }
  return 'anonymous';
}

const withOperator = () => ({ headers: { 'X-Operator-Id': operatorId() } });

/** 校验结论（{@code WorkflowDefinitionCommandService.ValidationReport} 的镜像）。 */
export interface WorkflowValidationReport {
  valid: boolean;
  violations: Violation[];
}

/** 草稿（{@code WorkflowDefinitionCommandService.Draft} 镜像）。 */
export interface WorkflowDraft {
  defKey: string;
  defName: string;
  definitionJson: string;
  revision: number;
  updatedBy: string;
}

/** 保存草稿结果（{@code SaveDraftResult} 镜像）。 */
export interface WorkflowSaveDraftResult {
  draft: WorkflowDraft;
  violations: Violation[];
}

/** 已发布版本（{@code Published} 镜像）。 */
export interface WorkflowPublished {
  defKey: string;
  defName: string;
  version: number;
  definitionJson: string;
  enabled: boolean;
}

/** 试运行结果（{@code WorkflowSimulator.Simulation} 镜像）。 */
export interface WorkflowSimulationReport {
  path: string[];
  conditionTraces: Array<{
    nodeCode: string;
    label: string;
    branches: Array<{ to: string; label: string; defaulted: boolean; matched: boolean | null; problems: string[] }>;
  }>;
  warnings: string[];
  error: string | null;
}

export const workflowDesignerApi = {
  /** 定义总览：每个 key 的当前生效版本。 */
  listLatest: () => api.get<never, ApiResponse<WorkflowPublished[]>>('/workflow/definitions'),

  /** 取草稿（无草稿 data=null）。 */
  findDraft: (key: string) =>
    api.get<never, ApiResponse<WorkflowDraft | null>>(`/workflow/definitions/${key}/draft`),

  /** 保存草稿（expectedRevision 为 null 表示新建）。 */
  saveDraft: (key: string, definitionJson: string, expectedRevision: number | null) =>
    api.post<never, ApiResponse<WorkflowSaveDraftResult>>(
      `/workflow/definitions/${key}/draft`,
      { definitionJson, expectedRevision },
      withOperator(),
    ),

  /** 丢弃草稿。 */
  discardDraft: (key: string) =>
    api.delete<never, ApiResponse<void>>(`/workflow/definitions/${key}/draft`, withOperator()),

  /** 发布草稿为不可变版本。 */
  publish: (key: string, expectedRevision: number | null) =>
    api.post<never, ApiResponse<WorkflowPublished>>(
      `/workflow/definitions/${key}/publish`,
      { expectedRevision },
      withOperator(),
    ),

  /** 上线 / 下线某版本。 */
  setEnabled: (key: string, version: number, enabled: boolean) =>
    api.post<never, ApiResponse<WorkflowPublished>>(
      `/workflow/definitions/${key}/enabled`,
      { version, enabled },
      withOperator(),
    ),

  /** 某 key 的全部版本。 */
  listVersions: (key: string) =>
    api.get<never, ApiResponse<WorkflowPublished[]>>(`/workflow/definitions/${key}/versions`),

  /** 指定版本。 */
  findVersion: (key: string, version: number) =>
    api.get<never, ApiResponse<WorkflowPublished>>(`/workflow/definitions/${key}/versions/${version}`),

  /** 只校验不落库。 */
  validate: (definitionJson: string) =>
    api.post<never, ApiResponse<WorkflowValidationReport>>(
      '/workflow/definitions/validate',
      definitionJson,
      withOperator(),
    ),

  /** 发布前试运行。 */
  simulate: (payload: {
    definitionJson: string;
    sampleData: Record<string, unknown>;
    initiatorId: string;
    entityCode?: string | null;
    recordId?: string | null;
  }) => api.post<never, ApiResponse<WorkflowSimulationReport>>('/workflow/definitions/simulate', payload, withOperator()),
};

/** 把后端定义 JSON 里的 ui.flowDoc 解析回文档树（fast path，无损）。解析失败回退空文档。 */
export function docFromDefinitionJson(definitionJson: string): FlowDoc | null {
  try {
    const def = JSON.parse(definitionJson) as { ui?: { flowDoc?: FlowDoc } };
    return def.ui?.flowDoc ?? null;
  } catch {
    return null;
  }
}
