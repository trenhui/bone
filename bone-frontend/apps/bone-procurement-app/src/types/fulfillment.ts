/** 采购履约前端类型（白名单投影，不含 PII；金额/数量以字符串承载避免精度丢失）。 */

export interface AckLine {
  lineNo: number;
  acceptedQty: number;
  reason?: string;
}

export interface AckCommandPayload {
  tenantId: number;
  poVersion: number;
  ackType: 'FULL' | 'PARTIAL' | 'REJECT';
  lines: AckLine[];
}

export interface AsnLinePayload {
  lineNo: number;
  poLineNo: number;
  itemId: string;
  qty: string;
  uom?: string;
  packRef?: string;
}

export interface AsnCommandPayload {
  tenantId: number;
  asnNo: string;
  poNo: string;
  shipDate?: string;
  carrier?: string;
  trackingNo?: string;
  sscc?: string[];
  lines: AsnLinePayload[];
}

export interface InvoiceLinePayload {
  itemNo: string;
  qty: string;
  price: string;
  amount: string;
}

export interface InvoiceCommandPayload {
  tenantId: number;
  invoiceNo: string;
  poNo?: string;
  amount: string;
  tax?: string;
  currency?: string;
  category: string;
  lines: InvoiceLinePayload[];
}

export interface SupplementPayload {
  tenantId: number;
  supplementRef: string;
}

export interface PoAckDTO {
  id?: string;
  poNo?: string;
  poVersion?: number;
  vendorPartyId?: string;
  ackType?: string;
  ackLinesJson?: string;
  ackAt?: string;
  version?: number;
}

export interface AsnDTO {
  id?: string;
  asnNo?: string;
  poNo?: string;
  vendorPartyId?: string;
  shipDate?: string;
  carrier?: string;
  trackingNo?: string;
  ssccJson?: string;
  status?: string;
  version?: number;
}

export interface InvoiceDTO {
  id?: string;
  invoiceNo?: string;
  poNo?: string;
  vendorPartyId?: string;
  amount?: string;
  tax?: string;
  currency?: string;
  linesJson?: string;
  status?: string;
  submittedAt?: string;
  financeEventId?: string;
  version?: number;
}

export interface IrExceptionDTO {
  id?: string;
  exceptionNo?: string;
  invoiceNo?: string;
  exceptionCode?: string;
  matchStatus?: string;
  status?: string;
  supplementRef?: string;
  financeEventId?: string;
  version?: number;
}

export interface FulfillmentViewDTO {
  poNo?: string;
  acknowledged?: boolean;
  acks?: PoAckDTO[];
  asns?: AsnDTO[];
  invoices?: InvoiceDTO[];
  exceptions?: IrExceptionDTO[];
}

export interface NormalizedPage<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}
