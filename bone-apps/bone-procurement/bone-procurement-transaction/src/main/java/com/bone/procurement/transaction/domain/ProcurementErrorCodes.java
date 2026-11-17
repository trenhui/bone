package com.bone.procurement.transaction.domain;

/** 采购交易错误码（LLD 04 卷 §6）。 */
public final class ProcurementErrorCodes {

  public static final String VENDOR_NOT_ACTIVE = "31003";
  public static final String LEGAL_ENTITY_MISMATCH = "31004";
  public static final String PRICE_VALIDITY_OVERLAP = "31401";
  public static final String PO_AMOUNT_MISMATCH = "31402";
  public static final String TERMINAL_WRITE_BACK = "31403";
  public static final String GR_OVER_RECEIPT = "31404";
  public static final String PRICE_SNAPSHOT_MISSING = "31405";
  public static final String INVALID_TRANSITION = "409";

  private ProcurementErrorCodes() {}
}
