package com.bone.example.extension.medical.exception;

import com.bone.engine.extension.api.exception.ExtensionBizException;

/**
 * 医疗理赔异常类
 *
 * <p>提供医疗理赔领域特有的异常功能和业务方法 包含理赔ID作为核心业务字段
 */
public class MedicalClaimException extends ExtensionBizException {
  private static final long serialVersionUID = 1L;

  // 理赔ID，医疗理赔业务的核心标识
  private final String claimId;

  /**
   * 构建医疗理赔异常
   *
   * @param errorCode 错误码
   * @param message 错误消息
   * @param claimId 理赔ID
   */
  public MedicalClaimException(String errorCode, String message, String claimId) {
    super("MEDICAL_CLAIM", errorCode, message);
    this.claimId = claimId;
  }

  /**
   * 构建医疗理赔异常
   *
   * @param errorCode 错误码
   * @param message 错误消息
   * @param claimId 理赔ID
   * @param cause 异常原因
   */
  public MedicalClaimException(String errorCode, String message, String claimId, Throwable cause) {
    super("MEDICAL_CLAIM", errorCode, message, cause);
    this.claimId = claimId;
  }

  /**
   * 获取理赔ID
   *
   * @return 理赔ID
   */
  public String getClaimId() {
    return claimId;
  }

  @Override
  public String toString() {
    return "MedicalClaimException{"
        + "errorCode='"
        + getErrorCode()
        + "'"
        + ", module='"
        + getModule()
        + "'"
        + ", message='"
        + getMessage()
        + "'"
        + ", claimId='"
        + claimId
        + "'"
        + '}';
  }

  /**
   * 创建理赔验证异常
   *
   * @param claimId 理赔ID
   * @param message 验证失败消息
   * @return 医疗理赔异常实例
   */
  public static MedicalClaimException validationError(String claimId, String message) {
    return new MedicalClaimException("VALIDATION_ERROR", message, claimId);
  }

  /**
   * 创建理赔材料不足异常
   *
   * @param claimId 理赔ID
   * @param missingMaterials 缺失材料列表
   * @return 医疗理赔异常实例
   */
  public static MedicalClaimException insufficientMaterials(
      String claimId, String missingMaterials) {
    return new MedicalClaimException(
        "INSUFFICIENT_MATERIALS", "理赔材料不足: " + missingMaterials, claimId);
  }

  /**
   * 创建理赔审核拒绝异常
   *
   * @param claimId 理赔ID
   * @param reason 拒绝原因
   * @return 医疗理赔异常实例
   */
  public static MedicalClaimException reviewRejected(String claimId, String reason) {
    return new MedicalClaimException("REVIEW_REJECTED", "理赔审核拒绝: " + reason, claimId);
  }
}
