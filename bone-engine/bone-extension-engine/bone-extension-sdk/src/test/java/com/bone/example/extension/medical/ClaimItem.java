package com.bone.example.extension.medical;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 医疗理赔项目明细
 * 封装单个医疗费用项目的详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClaimItem {
    /**
     * 项目ID，唯一标识理赔明细项目
     */
    @NonNull
    private String itemId;
    
    /**
     * 医疗项目类型，如药品、检查、治疗等
     */
    @NonNull
    private String itemType;
    
    /**
     * 项目名称，详细描述医疗服务或药品
     */
    @NonNull
    private String itemName;
    
    /**
     * 项目金额，单项费用
     */
    @NonNull
    private BigDecimal amount;
    
    /**
     * 项目日期，医疗服务发生的日期
     */
    private Date itemDate;
    
    /**
     * 数量，药品或服务的数量
     */
    private int quantity;
    
    /**
     * 单位，数量的计量单位
     */
    private String unit;
    
    /**
     * 医生姓名，提供服务的医生
     */
    private String doctorName;
    
    /**
     * 是否为处方药
     */
    private boolean prescriptionRequired;
    
    /**
     * 药品代码，如适用
     */
    private String drugCode;
}