package com.bone.tpa.claim.application.dto;

import com.bone.core.domain.extension.ExtensibleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Schema(name = "invoiceProjectItem", description = "费用项目详情")
public class InvoiceProjectItemDTO extends ExtensibleObject<InvoiceProjectItemDTO,Long> {

//    @Schema(description = "关联发票id")
    private Long relatedId;

    @Schema(description = "明细uuid")
    private String itemUuid;

    @Schema(description = "药品/诊疗名称")
    private String itemName;
    @Schema(description = "药品/诊疗代码")
    private String itemCode;

    @Schema(description = "关联票据号码")
    private String relatedInvoiceNo;

    @Schema(description = "费用项目名称", format = "SelectDrop")
    private String relatedProjectCode;
    @Schema(description = "费用项目名称_中文")
    private String relatedProjectName;

    @Schema(description = "医保费用类型", format = "SelectDrop")
    private String medicalType;

    @Schema(description = "金额")
    private BigDecimal itemTotalAmount;
    @Schema(description = "自付比例")
    private BigDecimal chargingPercentage;
    @Schema(description = "扣费金额")
    private BigDecimal chargingAmount;
    @Schema(description = "剂型")
    private String dosageForm;
    @Schema(description = "剂型_中文")
    private String dosageFormCn;
    @Schema(description = "单价")
    private BigDecimal price;
    @Schema(description = "数量")
    private Integer count;

    private Map<String,Object> extraStore;


    /**
     * 同步提示信息
     */
    private Map<String, String> syncHintMsg = new HashMap<>();
}
