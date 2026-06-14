package com.bone.example.extension.medical;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.*;

/**
 * 医疗保险理赔结果对象
 *
 * <p>封装医疗保险理赔处理的完整结果信息，包括理赔状态、金额明细、项目处理结果等。 作为理赔流程的输出数据，用于返回给调用方和记录系统。
 */
@Getter
@ToString
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MedicalClaimResult {
  /** 理赔申请唯一标识，与理赔请求中的claimId对应 */
  private String claimId;

  /** 理赔处理状态，决定后续流程和展示给用户的信息 */
  private ClaimStatus status;

  /** 申请理赔总金额，与理赔请求中的totalAmount对应 */
  private BigDecimal totalClaimAmount;

  /** 批准赔付的金额 */
  private BigDecimal approvedAmount;

  /** 拒绝赔付的金额 */
  private BigDecimal rejectedAmount;

  /** 理赔项目结果列表，包含每个理赔项目的详细处理结果 */
  private List<ClaimItemResult> itemResults;

  /** 拒绝原因，当理赔被拒绝或部分拒绝时提供 */
  private String rejectionReason;

  /** 处理日期，记录理赔处理完成的时间 */
  private LocalDateTime processingDate;

  /** 处理人员ID，记录执行理赔处理的人员标识 */
  private String processorId;

  /** 支付状态，反映理赔款项的支付情况 */
  private String paymentStatus;

  /** 支付日期，记录理赔款项支付的时间 */
  private LocalDateTime paymentDate;

  /** 交易ID，支付系统生成的交易标识 */
  private String transactionId;

  /** 备注信息，提供额外说明 */
  private String remarks;

  /**
   * 获取理赔申请唯一标识
   *
   * @return 理赔申请唯一标识
   */
  public String getClaimId() {
    return claimId;
  }

  /**
   * 获取理赔处理状态
   *
   * @return 理赔处理状态
   */
  public ClaimStatus getStatus() {
    return status;
  }

  /**
   * 获取申请理赔总金额
   *
   * @return 申请理赔总金额
   */
  public BigDecimal getTotalClaimAmount() {
    return totalClaimAmount;
  }

  /**
   * 获取批准赔付的金额
   *
   * @return 批准赔付的金额
   */
  public BigDecimal getApprovedAmount() {
    return approvedAmount;
  }

  /**
   * 获取拒绝赔付的金额
   *
   * @return 拒绝赔付的金额
   */
  public BigDecimal getRejectedAmount() {
    return rejectedAmount;
  }

  /**
   * 获取拒绝原因
   *
   * @return 拒绝原因
   */
  public String getRejectionReason() {
    return rejectionReason;
  }

  /**
   * 获取理赔项目处理结果列表
   *
   * @return 理赔项目处理结果列表
   */
  public List<ClaimItemResult> getItemResults() {
    return itemResults;
  }

  /**
   * 获取处理日期
   *
   * @return 处理日期
   */
  public LocalDateTime getProcessingDate() {
    return processingDate;
  }

  /**
   * 设置处理日期
   *
   * @param processingDate 处理日期
   */
  public void setProcessingDate(LocalDateTime processingDate) {
    this.processingDate = processingDate;
  }

  /**
   * 设置处理日期（兼容Date类型）
   *
   * @param processingDate 处理日期
   */
  public void setProcessingDate(Date processingDate) {
    this.processingDate =
        processingDate != null
            ? processingDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            : null;
  }

  /**
   * 设置理赔申请唯一标识
   *
   * @param claimId 理赔申请唯一标识
   */
  public void setClaimId(String claimId) {
    this.claimId = claimId;
  }

  /**
   * 设置理赔处理状态
   *
   * @param status 理赔处理状态
   */
  public void setStatus(ClaimStatus status) {
    this.status = status;
  }

  /**
   * 设置申请理赔总金额
   *
   * @param totalClaimAmount 申请理赔总金额
   */
  public void setTotalClaimAmount(BigDecimal totalClaimAmount) {
    this.totalClaimAmount = totalClaimAmount;
  }

  /**
   * 理赔状态枚举
   *
   * <p>定义理赔处理的各个状态阶段，反映理赔在整个生命周期中的位置。
   */
  public enum ClaimStatus {
    /** 已提交，理赔申请已提交但尚未开始处理 */
    SUBMITTED("已提交", "理赔申请已提交但尚未开始处理"),

    /** 处理中，理赔申请正在处理中 */
    PROCESSING("处理中", "理赔申请正在处理中"),

    /** 已批准，理赔申请已完全批准 */
    APPROVED("已批准", "理赔申请已完全批准"),

    /** 部分批准，理赔申请部分批准部分拒绝 */
    PARTIALLY_APPROVED("部分批准", "理赔申请部分批准部分拒绝"),

    /** 已拒绝，理赔申请被完全拒绝 */
    REJECTED("已拒绝", "理赔申请被完全拒绝"),

    /** 已支付，理赔款项已支付 */
    PAID("已支付", "理赔款项已支付"),

    /** 已取消，理赔申请被取消 */
    CANCELLED("已取消", "理赔申请被取消");

    private final String name;
    private final String description;

    ClaimStatus(final String name, final String description) {
      this.name = name;
      this.description = description;
    }

    /**
     * 获取理赔状态的中文名称
     *
     * @return 理赔状态的中文名称
     */
    public String getName() {
      return name;
    }

    /**
     * 获取理赔状态的详细描述
     *
     * @return 理赔状态的详细描述
     */
    public String getDescription() {
      return description;
    }
  }

  // 显式添加builder()方法以确保编译通过
  public static MedicalClaimResultBuilder builder() {
    return new MedicalClaimResultBuilder();
  }

  /**
   * 理赔项目结果
   *
   * <p>表示单个理赔项目的处理结果，包含项目的批准金额、拒绝原因等详细信息。
   */
  public static class ClaimItemResult {
    /** 项目名称，与理赔请求中的itemName对应 */
    private String itemName;

    /** 项目代码，与理赔请求中的itemCode对应 */
    private String itemCode;

    /** 申请理赔金额，与理赔请求中的项目总金额对应 */
    private BigDecimal claimedAmount;

    /** 批准赔付金额，该项目实际批准的赔付金额 */
    private BigDecimal approvedAmount;

    /** 拒绝赔付金额，该项目被拒绝赔付的金额 */
    private BigDecimal rejectionAmount;

    /** 是否已批准，该项目是否被批准赔付 */
    private boolean approved;

    /** 拒绝原因，该项目被拒绝的具体原因 */
    private String rejectionReason;

    /** 报销比例，该项目的实际报销比例 */
    private BigDecimal reimbursementRate;

    /**
     * 检查项目是否已批准
     *
     * @return 是否已批准
     */
    public boolean isApproved() {
      return this.approved;
    }

    /**
     * 获取批准赔付金额
     *
     * @return 批准赔付金额
     */
    public BigDecimal getApprovedAmount() {
      return this.approvedAmount;
    }

    /**
     * 获取拒绝赔付金额
     *
     * @return 拒绝赔付金额
     */
    public BigDecimal getRejectionAmount() {
      return this.rejectionAmount;
    }

    // 手动实现builder方法
    public static ClaimItemResultBuilder builder() {
      return new ClaimItemResultBuilder();
    }

    public static class ClaimItemResultBuilder {
      private String itemName;
      private String itemCode;
      private BigDecimal claimedAmount;
      private BigDecimal approvedAmount;
      private BigDecimal rejectionAmount;
      private boolean approved;
      private String rejectionReason;
      private BigDecimal reimbursementRate;

      public ClaimItemResultBuilder itemName(String itemName) {
        this.itemName = itemName;
        return this;
      }

      public ClaimItemResultBuilder itemCode(String itemCode) {
        this.itemCode = itemCode;
        return this;
      }

      public ClaimItemResultBuilder claimedAmount(BigDecimal claimedAmount) {
        this.claimedAmount = claimedAmount;
        return this;
      }

      public ClaimItemResultBuilder approvedAmount(BigDecimal approvedAmount) {
        this.approvedAmount = approvedAmount;
        return this;
      }

      public ClaimItemResultBuilder rejectionAmount(BigDecimal rejectionAmount) {
        this.rejectionAmount = rejectionAmount;
        return this;
      }

      public ClaimItemResultBuilder approved(boolean approved) {
        this.approved = approved;
        return this;
      }

      public ClaimItemResultBuilder rejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
        return this;
      }

      public ClaimItemResultBuilder reimbursementRate(BigDecimal reimbursementRate) {
        this.reimbursementRate = reimbursementRate;
        return this;
      }

      public ClaimItemResult build() {
        ClaimItemResult result = new ClaimItemResult();
        result.itemName = this.itemName;
        result.itemCode = this.itemCode;
        result.claimedAmount = this.claimedAmount;
        result.approvedAmount = this.approvedAmount;
        result.rejectionAmount = this.rejectionAmount;
        result.approved = this.approved;
        result.rejectionReason = this.rejectionReason;
        result.reimbursementRate = this.reimbursementRate;
        return result;
      }
    }
  }

  // 保留一个builder()方法定义

  public static class MedicalClaimResultBuilder {
    private String claimId;
    private ClaimStatus status;
    private BigDecimal totalClaimAmount;
    private BigDecimal approvedAmount;
    private BigDecimal rejectedAmount;
    private String rejectionReason;
    private List<ClaimItemResult> itemResults = new ArrayList<>();
    private LocalDateTime processingDate;
    private String processorId;
    private String paymentStatus;
    private LocalDateTime paymentDate;
    private String transactionId;
    private String remarks;

    public MedicalClaimResultBuilder claimId(String claimId) {
      this.claimId = claimId;
      return this;
    }

    public MedicalClaimResultBuilder status(ClaimStatus status) {
      this.status = status;
      return this;
    }

    public MedicalClaimResultBuilder totalClaimAmount(BigDecimal totalClaimAmount) {
      this.totalClaimAmount = totalClaimAmount;
      return this;
    }

    public MedicalClaimResultBuilder approvedAmount(BigDecimal approvedAmount) {
      this.approvedAmount = approvedAmount;
      return this;
    }

    public MedicalClaimResultBuilder rejectedAmount(BigDecimal rejectedAmount) {
      this.rejectedAmount = rejectedAmount;
      return this;
    }

    public MedicalClaimResultBuilder rejectionReason(String rejectionReason) {
      this.rejectionReason = rejectionReason;
      return this;
    }

    public MedicalClaimResultBuilder itemResults(List<ClaimItemResult> itemResults) {
      this.itemResults = itemResults;
      return this;
    }

    public MedicalClaimResultBuilder addItemResult(ClaimItemResult itemResult) {
      this.itemResults.add(itemResult);
      return this;
    }

    public MedicalClaimResultBuilder processingDate(LocalDateTime processingDate) {
      this.processingDate = processingDate;
      return this;
    }

    /** 兼容旧版API，接收Date类型参数 */
    public MedicalClaimResultBuilder processingDate(Date processingDate) {
      this.processingDate =
          processingDate != null
              ? processingDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
              : null;
      return this;
    }

    public MedicalClaimResultBuilder processorId(String processorId) {
      this.processorId = processorId;
      return this;
    }

    public MedicalClaimResultBuilder paymentStatus(String paymentStatus) {
      this.paymentStatus = paymentStatus;
      return this;
    }

    public MedicalClaimResultBuilder paymentDate(LocalDateTime paymentDate) {
      this.paymentDate = paymentDate;
      return this;
    }

    /** 兼容旧版API，接收Date类型参数 */
    public MedicalClaimResultBuilder paymentDate(Date paymentDate) {
      this.paymentDate =
          paymentDate != null
              ? paymentDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
              : null;
      return this;
    }

    public MedicalClaimResultBuilder transactionId(String transactionId) {
      this.transactionId = transactionId;
      return this;
    }

    public MedicalClaimResultBuilder remarks(String remarks) {
      this.remarks = remarks;
      return this;
    }

    // 兼容方法
    public MedicalClaimResultBuilder claimStatus(String claimStatus) {
      // 尝试从字符串转换为ClaimStatus
      try {
        this.status = ClaimStatus.valueOf(claimStatus);
      } catch (Exception e) {
        // 忽略转换错误
      }
      return this;
    }

    // 兼容方法
    public MedicalClaimResultBuilder totalApprovedAmount(BigDecimal totalApprovedAmount) {
      this.approvedAmount = totalApprovedAmount;
      return this;
    }

    // 兼容方法
    public MedicalClaimResultBuilder totalRejectedAmount(BigDecimal totalRejectedAmount) {
      this.rejectedAmount = totalRejectedAmount;
      return this;
    }

    // 兼容方法
    public MedicalClaimResultBuilder processor(String processor) {
      this.processorId = processor;
      return this;
    }

    public MedicalClaimResult build() {
      MedicalClaimResult result = new MedicalClaimResult();
      result.claimId = this.claimId;
      result.status = this.status;
      result.totalClaimAmount = this.totalClaimAmount;
      result.approvedAmount = this.approvedAmount;
      result.rejectedAmount = this.rejectedAmount;
      result.rejectionReason = this.rejectionReason;
      result.itemResults = this.itemResults;
      result.processingDate = this.processingDate;
      result.processorId = this.processorId;
      result.paymentStatus = this.paymentStatus;
      result.paymentDate = this.paymentDate;
      result.transactionId = this.transactionId;
      result.remarks = this.remarks;

      return result;
    }
  }

  /**
   * 获取理赔项目数量
   *
   * <p>安全地获取理赔项目结果列表的大小。
   *
   * @return 理赔项目数量
   */
  public int getItemResultCount() {
    return Optional.ofNullable(itemResults).map(List::size).orElse(0);
  }

  /**
   * 获取批准的项目数量
   *
   * <p>统计所有已批准的理赔项目数量。
   *
   * @return 批准的项目数量
   */
  public long getApprovedItemCount() {
    return Optional.ofNullable(itemResults)
        .map(results -> results.stream().filter(ClaimItemResult::isApproved).count())
        .orElse(0L);
  }

  /**
   * 获取拒绝的项目数量
   *
   * <p>统计所有被拒绝的理赔项目数量。
   *
   * @return 拒绝的项目数量
   */
  public long getRejectedItemCount() {
    return Optional.ofNullable(itemResults)
        .map(results -> results.stream().filter(item -> !item.isApproved()).count())
        .orElse(0L);
  }

  /**
   * 验证理赔结果的完整性
   *
   * <p>检查理赔结果的关键字段是否已设置，并验证金额计算的一致性。
   *
   * @return 验证是否通过
   */
  public boolean isResultComplete() {
    // 检查必要字段
    if (claimId == null || status == null) {
      return false;
    }

    // 验证金额计算一致性（如果有项目结果）
    if (itemResults != null && !itemResults.isEmpty()) {
      BigDecimal calculatedApprovedAmount =
          itemResults.stream()
              .map(ClaimItemResult::getApprovedAmount)
              .filter(amount -> amount != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal calculatedRejectedAmount =
          itemResults.stream()
              .map(ClaimItemResult::getRejectionAmount)
              .filter(amount -> amount != null)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      // 验证批准金额和拒绝金额的计算是否与项目明细一致
      if (approvedAmount != null && !approvedAmount.equals(calculatedApprovedAmount)) {
        return false;
      }

      if (rejectedAmount != null && !rejectedAmount.equals(calculatedRejectedAmount)) {
        return false;
      }

      // 验证申请总金额是否等于批准金额加上拒绝金额
      if (totalClaimAmount != null
          && approvedAmount != null
          && rejectedAmount != null
          && !totalClaimAmount.equals(approvedAmount.add(rejectedAmount))) {
        return false;
      }
    }

    return true;
  }
}
