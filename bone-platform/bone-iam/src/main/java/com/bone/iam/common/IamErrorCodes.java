package com.bone.iam.common;

public final class IamErrorCodes {

  public static final String SSO_NOT_CONFIGURED = "IAM_SSO_NOT_CONFIGURED";
  public static final String MFA_NOT_AVAILABLE = "IAM_MFA_NOT_AVAILABLE";
  public static final String WEAK_PASSWORD = "IAM_WEAK_PASSWORD";
  public static final String PASSWORD_MUST_CHANGE = "IAM_PASSWORD_MUST_CHANGE";
  public static final String REFRESH_TOKEN_REUSE = "IAM_REFRESH_TOKEN_REUSE";
  public static final String TENANT_ACCESS_DENIED = "IAM_TENANT_ACCESS_DENIED";
  public static final String TENANT_DELETE_FORBIDDEN = "IAM_TENANT_DELETE_FORBIDDEN";
  public static final String TENANT_QUOTA_EXCEEDED = "IAM_TENANT_QUOTA_EXCEEDED";
  public static final String LOGIN_FAILED = "IAM_LOGIN_FAILED";
  public static final String ACCOUNT_LOCKED = "IAM_ACCOUNT_LOCKED";
  public static final String ACCOUNT_DISABLED = "IAM_ACCOUNT_DISABLED";
  public static final String SESSION_NOT_FOUND = "IAM_SESSION_NOT_FOUND";
  public static final String PROFILE_OWNERSHIP_DENIED = "IAM_PROFILE_OWNERSHIP_DENIED";
  public static final String OLD_PASSWORD_MISMATCH = "IAM_OLD_PASSWORD_MISMATCH";

  private IamErrorCodes() {}
}
