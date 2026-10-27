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

/** 已发布版本（{@code Published} 镜像，含定义原文，供版本抽屉「载入」还原画布）。 */
export interface WorkflowPublished {
  defKey: string;
  defName: string;
  version: number;
  definitionJson: string;
  enabled: boolean;
}

/**
 * 定义总览行（{@code Summary} 镜像，<b>不含定义原文</b>）。
 *
 * <p>后端刻意不让列表带 definitionJson：几十个流程 × 每份原文 KB 级 = 几百 KB 首屏无用载荷。
 * 这里保持同口径——类型上就没有 definitionJson，用的时候只能去查版本详情，避免「列表顺手当详情用」。
 */
export interface WorkflowDefinitionSummary {
  defKey: string;
  defName: string;
  version: number;
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
  /**
   * 定义总览。
   *
   * <p>设计器固定要 {@code includeOffline=true}：缺省口径只含「已上线」的 key，一旦某流程被下线，它会从列表里
   * 整体消失——左侧选不到、也就没有任何入口把它重新上线（联调实测到的死路）。{@code enabled} 字段用于区分状态。
   */
  listLatest: (includeOffline = true) =>
    api.get<never, ApiResponse<WorkflowDefinitionSummary[]>>('/workflow/definitions', {
      params: { includeOffline },
    }),

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

  /** 某 key 的全部版本（按版本号<b>降序</b>：首元素即当前最新版，设计器据此在无草稿时还原画布）。 */
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

/**
 * 失败语义档位。
 *
 * <p>页面按它分流，**不要**在组件里比 HTTP 数字码：响应拦截器已把 `response` 解包成信封，
 * 组件的 `res.status` 恒 undefined（仓库门禁 `check-frontend-response-contract` 的 R1 规则就是拦这个）。
 * 把「HTTP 码 → 语义」的映射收在服务层，页面只表达交互意图。
 */
export type WorkflowApiFailureKind =
  | 'unauthenticated'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'invalid'
  | 'network'
  | 'unknown';

/** 把后端错误信封解析成可判定的形状（见 {@link toWorkflowFailure}）。 */
export interface WorkflowApiFailure {
  /** 语义档位，页面按此分流。 */
  kind: WorkflowApiFailureKind;
  /** HTTP 状态码，仅供日志与断言；网络层失败时为 0。 */
  status: number;
  message: string;
  /** 信封里的业务码（缺省与 HTTP 状态一致）。 */
  code?: string;
  /** 422 时后端回传的结构化违规清单，可直接喂给画布高亮。 */
  violations: Violation[];
}

/**
 * 统一解析失败原因。
 *
 * <p><b>为什么需要这一层</b>：{@code createApiClient} 的响应拦截器在非 2xx 时 {@code reject}，
 * 而不是把信封当成功值返回。于是「先写 {@code if (res.code === 200)} 再 else 提示」的写法在错误路径上
 * 根本不会执行——Promise 直接未处理地 reject，界面毫无反应：409 冲突像没点、403 像卡住、422 只看到
 * 前端自己的预检提示。联调时这三个分支全部踩到，故把解包集中在这里，页面只负责呈现。
 */
export function toWorkflowFailure(error: unknown): WorkflowApiFailure {
  const e = error as {
    response?: {
      status?: number;
      data?: { code?: number | string; message?: string; data?: { violations?: Violation[] } };
    };
    displayMessage?: string;
    message?: string;
  };
  const status = e?.response?.status ?? 0;
  const body = e?.response?.data;
  const kind: WorkflowApiFailureKind =
    status === 401
      ? 'unauthenticated'
      : status === 403
        ? 'forbidden'
        : status === 404
          ? 'notFound'
          : status === 409
            ? 'conflict'
            : status === 422
              ? 'invalid'
              : status === 0
                ? 'network'
                : 'unknown';
  return {
    kind,
    status,
    message: body?.message || e?.displayMessage || e?.message || '请求失败',
    code: body?.code == null ? undefined : String(body.code),
    violations: body?.data?.violations ?? [],
  };
}

/** 把后端定义 JSON 里的 ui.flowDoc 解析回文档树（fast path，无损）。解析失败回退空文档。 */
export function docFromDefinitionJson(definitionJson: string): FlowDoc | null {
  try {
    const def = JSON.parse(definitionJson) as { ui?: { flowDoc?: FlowDoc } };
    return def.ui?.flowDoc ?? null;
  } catch {
    return null;
  }
}
