package com.bone.procurement.fulfillment.domain;

/** 采购履约错误码（LLD 05 卷 §6，315xx 段）。 */
public final class FulfillmentErrorCodes {

  public static final String ASN_GR_MISMATCH = "31501";
  public static final String INVOICE_DUPLICATE = "31502";
  public static final String PO_NOT_ACKNOWLEDGED = "31503";
  public static final String INVOICE_OVER_BALANCE = "31504";
  public static final String PROCUREMENT_RELEASE_FORBIDDEN = "31505";
  public static final String SYNTAX_ACK_AS_BUSINESS = "31506";
  public static final String VENDOR_SCOPE_FORBIDDEN = "403";
  public static final String INVALID_TRANSITION = "409";

  private FulfillmentErrorCodes() {}
}
