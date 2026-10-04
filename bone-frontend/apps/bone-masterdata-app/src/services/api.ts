import { createApiClient, setQiankunToken } from '@bone/shared-services';
import { normalizeTotal } from '@bone/shared-utils';
import type {
  ApiResponse,
  PageResult,
  NormalizedPageResult,
  MasterDataEntity,
  MasterDataField,
  DataQualityRule,
  MasterDataRecord,
  QualityCheck,
  QualityReport,
  DataQualityResult,
  CreateMasterDataEntityReq,
  UpdateMasterDataEntityReq,
  CreateMasterDataFieldReq,
  UpdateMasterDataFieldReq,
  CreateDataQualityRuleReq,
  UpdateDataQualityRuleReq,
  CreateMasterDataRecordReq,
  UpdateMasterDataRecordReq,
  MasterDataEntityPageQry,
  MasterDataRecordListQry,
  DataQualityRuleListQry,
  ImportDuplicateStrategy,
  ImportResult
} from '../types';

export { setQiankunToken };

const MD = '/api/v1/masterdata';

// 创建 axios 实例（开发走 Vite 代理 → 网关 :8888）
const apiClient = createApiClient('');

/**
 * 分页结果归一化：后端 PageResult 真源字段为 records/total/page/size（bone-core PageResult），
 * 前端 `PageResult<T>` 亦声明为 `records`。此处在 api 层统一适配，
 * 调用方一律读 `data.records`。
 *
 * `total` 必须 `normalizeTotal()`：后端字段是 `java.lang.Long`，运行期被骨核全局
 * Long→String 序列化器输出为字符串（见 doc/architecture/Bone-API-规范.md §5.3）。
 * 不归一则调用方 `Math.ceil(total / pageSize)` 会抛 TypeError。此处 Number() 安全：
 * total 是行数计数而非雪花 ID。
 */
function normalizePage<T>(resp: ApiResponse<PageResult<T>>): ApiResponse<NormalizedPageResult<T>> {
  // 判据用records（权威字段）：原先判的是 `list`，而后端 `list` 是 @Deprecated 兼容
  // getter 的产物，`@JsonIgnore` 收敛后会恒为 undefined → Array.isArray 永远 false →
  // 误判为「原始引擎分页」而丢弃当前页数据。判据必须与权威字段同名。
  if (resp.code === 200 && resp.data && Array.isArray((resp.data as unknown as { records?: T[] }).records)) {
    const raw = resp.data as unknown as {
      records?: T[];
      total?: string | number;
      page?: number;
      size?: number;
    };
    return {
      ...resp,
      data: {
        records: raw.records ?? [],
        total: normalizeTotal(raw.total),
        pageNum: raw.page ?? 1,
        pageSize: raw.size ?? 10,
      },
    };
  }
  return { ...resp, data: resp.data ? { ...resp.data, total: normalizeTotal(resp.data.total) } : resp.data };
}

// 主数据模型相关API
export const masterDataEntityApi = {
  page: async (params: MasterDataEntityPageQry): Promise<ApiResponse<NormalizedPageResult<MasterDataEntity>>> => {
    return normalizePage(await apiClient.get(`${MD}/entities`, { params }));
  },
  detail: (id: string): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.get(`${MD}/entities/${id}`);
  },
  create: (data: CreateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.post(`${MD}/entities`, data);
  },
  update: (id: string, data: UpdateMasterDataEntityReq): Promise<ApiResponse<MasterDataEntity>> => {
    return apiClient.put(`${MD}/entities/${id}`, data);
  },
  delete: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/entities/${id}`);
  },
  publish: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/entities/${id}/publish`);
  },
  /** 停用实体：后端 DisableMasterDataEntityCommand 默认 disabled=true，须带 JSON body（空对象即可），
   *  否则 Spring @RequestBody(required=false) 缺 Content-Type 会 400。 */
  disable: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/entities/${id}/disable`, {});
  },
};

// 主数据字段相关API
export const masterDataFieldApi = {
  listByEntityId: (masterDataEntityId: string): Promise<ApiResponse<MasterDataField[]>> => {
    return apiClient.get(`${MD}/entities/${masterDataEntityId}/fields`);
  },
  detail: (id: string): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.get(`${MD}/fields/${id}`);
  },
  create: (data: CreateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    const entityId = data.masterDataEntityId;
    return apiClient.post(`${MD}/entities/${entityId}/fields`, data);
  },
  update: (id: string, data: UpdateMasterDataFieldReq): Promise<ApiResponse<MasterDataField>> => {
    return apiClient.put(`${MD}/fields/${id}`, data);
  },
  delete: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/fields/${id}`);
  },
};

// 数据质量规则相关API
// 契约注意：后端 `GET /quality/rules` 返回 `ApiResponse<List<DataQualityRuleDTO>>`，**不是** PageResult
// （DataQualityController#listRules）。此前经 normalizePage 消费，因 Array 无 records 字段而恒得空列表。
export const dataQualityRuleApi = {
  list: async (params?: DataQualityRuleListQry): Promise<ApiResponse<DataQualityRule[]>> => {
    return apiClient.get(`${MD}/quality/rules`, { params });
  },
  detail: (id: string): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.get(`${MD}/quality/rules/${id}`);
  },
  create: (data: CreateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.post(`${MD}/quality/rules`, data);
  },
  update: (id: string, data: UpdateDataQualityRuleReq): Promise<ApiResponse<DataQualityRule>> => {
    return apiClient.put(`${MD}/quality/rules/${id}`, data);
  },
  delete: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/quality/rules/${id}`);
  },
  /** 检查为同步执行，返回的是检查任务 ID（不是任务对象）。 */
  executeCheck: (masterDataEntityId: string): Promise<ApiResponse<number>> => {
    return apiClient.post(`${MD}/quality/check`, null, { params: { masterDataEntityId } });
  },
};

// 主数据记录相关API
export const masterDataRecordApi = {
  page: async (params: MasterDataRecordListQry): Promise<ApiResponse<NormalizedPageResult<MasterDataRecord>>> => {
    return normalizePage(await apiClient.get(`${MD}/records`, { params }));
  },
  detail: (id: string): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.get(`${MD}/records/${id}`);
  },
  /**
   * 创建记录：body 是 `{ data, recordCode, displayName, effectiveFrom, effectiveTo }`。
   *
   * 此前只发 data，导致 record_code 恒为 NULL；下游只能靠 data JSON 里的业务字段定位记录。
   * 后端 `MasterDataRecordTopController#create` 现在按此契约接收业务主键与生效期。
   */
  create: (masterDataEntityId: string, payload: CreateMasterDataRecordReq): Promise<ApiResponse<number>> => {
    return apiClient.post(`${MD}/records/entity/${masterDataEntityId}`, payload);
  },
  update: (id: string, data: UpdateMasterDataRecordReq): Promise<ApiResponse<MasterDataRecord>> => {
    return apiClient.put(`${MD}/records/${id}`, data);
  },
  delete: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.delete(`${MD}/records/${id}`);
  },
  publish: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/records/${id}/publish`);
  },
  archive: (id: string): Promise<ApiResponse<void>> => {
    return apiClient.post(`${MD}/records/${id}/archive`);
  },
  /**
   * 导入：返回部分成功语义的 {@link ImportResult}（总数/成功数/更新数/失败行明细）。
   *
   * @param duplicateStrategy FAIL（默认）重码行计入失败清单；UPDATE 按业务编码幂等更新（ERP 周期全量同步场景）。
   */
  import: (
    masterDataEntityId: string,
    file: File,
    duplicateStrategy?: ImportDuplicateStrategy,
  ): Promise<ApiResponse<ImportResult>> => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('masterDataEntityId', String(masterDataEntityId));
    return apiClient.post(`${MD}/records`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      params: {
        masterDataEntityId,
        ...(duplicateStrategy ? { duplicateStrategy } : {}),
      },
    });
  },
  /**
   * 后端 `GET /records/export` 返回 `ApiResponse<String>`（纯文本 `id=.., data=..`），**不是文件流**
   * （MasterDataRecordTopController#export）。此前按 `responseType: 'blob'` 接收，实为把整个 JSON 信封
   * 写成文件、扩展名却是 .xlsx，用户拿到打不开的伪 Excel。这里取信封 data 文本，由调用方包装 Blob 并定扩展名。
   */
  export: async (masterDataEntityId?: string): Promise<string> => {
    const resp: ApiResponse<string> = await apiClient.get(`${MD}/records/export`, {
      params: { masterDataEntityId },
    });
    return resp?.data ?? '';
  },
};

// 质量检查相关API
export const qualityCheckApi = {
  /** 后端 GET /quality/checks 返回 List（非分页），故用数组接收后自行分页展示。 */
  list: (params: { masterDataEntityId?: string }): Promise<ApiResponse<QualityCheck[]>> => {
    return apiClient.get(`${MD}/quality/checks`, { params });
  },
  detail: (id: string): Promise<ApiResponse<QualityCheck>> => {
    return apiClient.get(`${MD}/quality/checks/${id}`);
  },
};

// 质量报告API：报告按 checkId 唯一（uk_mdm_qrpt_check），报告 ID ≠ 检查 ID
export const qualityReportApi = {
  listByCheckId: (qualityCheckId: string): Promise<ApiResponse<QualityReport[]>> => {
    return apiClient.get(`${MD}/quality/reports`, { params: { qualityCheckId } });
  },
  /** 按检查任务 ID 取单份报告：/reports/{id} 的 id 是报告主键，不能拿 checkId 去打。 */
  detailByCheckId: (qualityCheckId: string): Promise<ApiResponse<QualityReport>> => {
    return apiClient.get(`${MD}/quality/checks/${qualityCheckId}/report`);
  },
};

export const qualityResultApi = {
  /**
   * 后端 `GET /quality-results` 接受 recordId 与 masterDataEntityId 两个可选参数
   * （QualityResultController#list），**返回粒度由是否传 recordId 决定**：
   *
   * - 不传 recordId → `level=SUMMARY`，每行是一次质检任务的整体结论，
   *   `masterDataRecordId` 与 `dataQualityRuleId` 均为 null；
   * - 传 recordId  → `level=DETAIL`，每行是「某规则 × 该记录」的判定，数据来自
   *   `mdm_qcheck_detail`（每次 performCheck 写入）。
   *
   * 两者同时传时，recordId 只在该实体的检查任务范围内匹配，不会跨实体串数据。
   *
   * 历史背景：recordId 曾经不参与筛选（只被原样回填 DTO），"按记录查"语义不成立；
   * 明细链路补齐后该参数才真正生效。
   */
  list: (params: {
    masterDataEntityId?: string;
    recordId?: string;
  }): Promise<ApiResponse<DataQualityResult[]>> => apiClient.get(`${MD}/quality-results`, { params }),
};

// ============================================================
// 治理域 API（G4/G5/G9/G10/G11/G15/G16/G17 · 3a 设计方案落地）
// ============================================================

// 域模板（G9 / UC-P1 P2 T1）
export const domainTemplateApi = {
  page: (params?: { pageNum?: number; pageSize?: number; status?: string }): Promise<ApiResponse<any>> =>
    apiClient.get(`${MD}/templates`, { params }),
  detail: (id: string): Promise<ApiResponse<any>> => apiClient.get(`${MD}/templates/${id}`),
  create: (data: Record<string, unknown>): Promise<ApiResponse<number>> => apiClient.post(`${MD}/templates`, data),
  update: (id: string, data: Record<string, unknown>): Promise<ApiResponse<void>> =>
    apiClient.put(`${MD}/templates/${id}`, data),
  publishVersion: (id: string, data: { versionNumber: string; changeLog?: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/templates/${id}/versions`, data),
  archive: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/templates/${id}/archive`),
  instantiate: (data: {
    templateId: string;
    entityCode?: string;
    name: string;
    description?: string;
    governanceTier?: string;
    owningAppId?: string;
    withFields?: boolean;
  }): Promise<ApiResponse<number>> => apiClient.post(`${MD}/templates/instantiate`, data),
  versions: (id: string): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/templates/${id}/versions`),
};

// 分类体系（G4 / UC-T3）
export const categoryApi = {
  tree: (masterDataEntityId: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/categories`, { params: { masterDataEntityId } }),
  create: (data: Record<string, unknown>): Promise<ApiResponse<string>> =>
    apiClient.post(`${MD}/categories`, data),
  // 分类 id 是雪花 ID，后端序列化为字符串，此处按 string 传递避免精度丢失
  update: (id: string, data: Record<string, unknown>): Promise<ApiResponse<void>> =>
    apiClient.put(`${MD}/categories/${id}`, data),
  delete: (id: string): Promise<ApiResponse<void>> => apiClient.delete(`${MD}/categories/${id}`),
  assignRecord: (recordId: string, categoryId: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/categories/assign`, { recordId, categoryId }),
  unassignRecord: (recordId: string, categoryId: string): Promise<ApiResponse<void>> =>
    apiClient.delete(`${MD}/categories/assign`, { params: { recordId, categoryId } }),
  recordCategories: (recordId: string): Promise<ApiResponse<number[]>> =>
    apiClient.get(`${MD}/categories/record/${recordId}`),
};

// 治理角色（G3 / UC-T2）
export const governanceRoleApi = {
  byEntity: (masterDataEntityId: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/governance-roles`, { params: { masterDataEntityId } }),
  assign: (data: { masterDataEntityId: string; accountId: string; roleType: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/governance-roles`, data),
  unassign: (id: string): Promise<ApiResponse<void>> => apiClient.delete(`${MD}/governance-roles/${id}`),
};

// 消费订阅（G10 / UC-C1 C3）
export const subscriptionApi = {
  byEntity: (masterDataEntityId: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/subscriptions`, { params: { masterDataEntityId } }),
  request: (data: { masterDataEntityId: string; appId: string; subscribeMode?: string }): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/subscriptions`, data),
  approve: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/subscriptions/${id}/approve`),
  revoke: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/subscriptions/${id}/revoke`),
};

// 质量整改工单（G11 / UC-T9）
export const qualityIssueApi = {
  byEntity: (masterDataEntityId: string, status?: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/quality-issues`, { params: { masterDataEntityId, status } }),
  openCount: (masterDataEntityId: string): Promise<ApiResponse<number>> =>
    apiClient.get(`${MD}/quality-issues/open-count`, { params: { masterDataEntityId } }),
  create: (data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/quality-issues`, data),
  fix: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/fix`),
  close: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/close`),
  ignore: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/quality-issues/${id}/ignore`),
};

// 参考数据（G15 / §2.3）
export const referenceApi = {
  sets: (): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/reference-sets`),
  createSet: (data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/reference-sets`, data),
  archiveSet: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/reference-sets/${id}/archive`),
  values: (setId: string): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/reference-sets/${setId}/values`),
  createValue: (setId: string, data: Record<string, unknown>): Promise<ApiResponse<number>> =>
    apiClient.post(`${MD}/reference-sets/${setId}/values`, data),
  disableValue: (valueId: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/reference-sets/values/${valueId}/disable`),
};

// 模型漂移（G16 / UC-T10）
export const driftApi = {
  byEntity: (masterDataEntityId: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/model-drifts`, { params: { masterDataEntityId } }),
  reconcile: (masterDataEntityId: string): Promise<ApiResponse<any[]>> =>
    apiClient.post(`${MD}/model-drifts/reconcile`, null, { params: { masterDataEntityId } }),
  handle: (id: string, status: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/model-drifts/${id}/handle`, { status }),
};

// 下游反馈（G17 / UC-C4）
export const feedbackApi = {
  byEntity: (masterDataEntityId: string, status?: string): Promise<ApiResponse<any[]>> =>
    apiClient.get(`${MD}/feedbacks`, { params: { masterDataEntityId, status } }),
  submit: (data: Record<string, unknown>): Promise<ApiResponse<number>> => apiClient.post(`${MD}/feedbacks`, data),
  accept: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/feedbacks/${id}/accept`),
  reject: (id: string, result?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/feedbacks/${id}/reject`, { result }),
  complete: (id: string, result?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/feedbacks/${id}/complete`, { result }),
};

// 记录审批（G5 / UC-T7）与模型治理配置（G3）
export const approvalApi = {
  submit: (id: string): Promise<ApiResponse<void>> => apiClient.post(`${MD}/records/${id}/submit`),
  approve: (id: string, comment?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/records/${id}/approve`, { comment }),
  reject: (id: string, comment?: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/records/${id}/reject`, { comment }),
  versions: (id: string): Promise<ApiResponse<any[]>> => apiClient.get(`${MD}/records/${id}/versions`),
  changeGovernanceTier: (id: string, tier: string): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/entities/${id}/governance-tier`, { tier }),
  bindOwningApp: (id: string, owningAppId: string | null): Promise<ApiResponse<void>> =>
    apiClient.post(`${MD}/entities/${id}/owning-app`, { owningAppId }),
};

export default {
  masterDataEntity: masterDataEntityApi,
  masterDataField: masterDataFieldApi,
  dataQualityRule: dataQualityRuleApi,
  masterDataRecord: masterDataRecordApi,
  qualityCheck: qualityCheckApi,
  qualityResult: qualityResultApi
};
