/** 采购交易前端类型（白名单投影，不含供应商 PII）。 */

export interface PurchaseOrderDTO {
  id?: string;
  poNo?: string;
  tenantId?: string;
  orgId?: string;
  vendorPartyId?: string;
  legalEntityId?: string;
  status?: string;
  poAmount?: string;
  currency?: string;
  taxAmount?: string;
  version?: number;
}

export interface RequisitionDTO {
  id?: string;
  prNo?: string;
  tenantId?: string;
  orgId?: string;
  status?: string;
  totalAmount?: string;
  currency?: string;
  version?: number;
}

export interface PageQuery {
  page?: number;
  size?: number;
  status?: string;
}

export interface NormalizedPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export interface ApiEnvelope<T> {
  success?: boolean;
  code?: number;
  message?: string;
  data?: T;
}

export interface CreatePurchaseOrderPayload {
  tenantId: number;
  orgId: number;
  vendorPartyId: string;
  legalEntityId: number;
  declaredAmount?: string;
  taxCodeVersion?: string;
  budgetRef?: string;
  lines: Array<{
    itemId: string;
    itemName?: string;
    quantity: string;
    unit?: string;
    netPrice: string;
    taxRate: string;
  }>;
}
