/**
 * 采购供应商治理前端类型（对齐后端 supplier-mdm DTO）。
 *
 * <p>安全边界：这些类型是后端只读视图 DTO 的镜像，只含状态、版本、裁决与迁移摘要，
 * **不含**统一社会信用代码 / 税号 / 银行账号 / 电话 / 法人名等身份主键与 PII。
 * 页面只消费白名单字段；登记表单是唯一允许用户输入身份数据的入口，提交后立即丢弃本地副本。
 *
 * <p>响应包装沿用后端 `ApiResponse<T>` 顶层 `success/code/message/data`；
 * 分页权威字段为 `records`（`getList()` 已 @JsonIgnore），`total` 运行期是字符串（Long→String）。
 */

/** 后端统一响应（补齐 shared-types ApiResponse 未声明的 success 字段）。 */
export interface SupplierApiResponse<T> {
  success: boolean;
  code: number;
  message: string;
  data: T;
}

/** 供应商当前双角色状态视图（对齐 SupplierStateDTO）。 */
export interface SupplierStateDTO {
  partyId: string;
  supplierState: string;
  supplierVersion: number;
  vendorState: string;
  vendorVersion: number;
  /** 迁移摘要：`from->to (event)`，最新在后。 */
  supplierHistory: string[];
  vendorHistory: string[];
}

/** 登记结果（对齐 SupplierRegistrationResultDTO，无字段原值）。 */
export interface SupplierRegistrationResultDTO {
  outcome: string;
  partyId: string;
  supplierState: string;
  vendorState: string;
  supplierVersion: number;
  vendorVersion: number;
  fieldCount: number;
  reason?: string;
}

/** 状态迁移结果（对齐 SupplierTransitionResultDTO）。 */
export interface SupplierTransitionResultDTO {
  partyId: string;
  role: 'SUPPLIER' | 'VENDOR' | string;
  fromState: string;
  toState: string;
  version: number;
  basis?: string;
}

/** 分页查询（对齐 SupplierPageQuery；不含 tenantId，租户取登录态）。 */
export interface SupplierPageQuery {
  page?: number;
  size?: number;
}

/** 登记命令的候选源记录（对齐 RegisterSupplierCommand.SourceRecordSpec）。 */
export interface SupplierSourceRecordSpec {
  recordId: string;
  sourceSystem: string;
  /** 可空；一旦声明须与命令租户一致，否则后端 fail-closed。 */
  tenantId?: number;
  trustRank?: number;
  observedAt?: string;
  /** 字段原值（可能含身份数据）——仅提交，不在 UI 回显。 */
  values?: Record<string, string>;
}

/** 登记命令（对齐 RegisterSupplierCommand）。 */
export interface RegisterSupplierCommand {
  mergeId: string;
  tenantId: number;
  partyId?: string;
  sourceSystem?: string;
  localId?: string;
  sources: SupplierSourceRecordSpec[];
  strategies?: Record<string, string>;
  stewardOverrides?: Record<string, string>;
  stewardAccountId?: string;
  mergedAt?: string;
}

/** 归一化分页结果（records 权威，total 归一为 number）。 */
export interface NormalizedSupplierPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
