package com.bone.lowcode.infra.application.dto.field;

import com.bone.lowcode.infra.domain.model.SelectDatasource;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

//用于FieldDO的数据更新
@Data
public class UpdateFieldDTO {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "展示标题名称")
    private String showName;

    @Schema(description = "对齐方式")
    private Byte alignment;

    @Schema(description = "提示信息")
    private String prompt;

    @Schema(description = "占位符信息")
    private String placeholder;

    @Schema(description = "字段宽度")
    private Integer width;

    @Schema(description = "限制字数")
    private Integer limitedLength;

    @Schema(description = "输入状态，0：可编辑，1：只读")
    private Byte inputStatus;

    @Schema(description = "是否展示，0：隐藏，1：展示")
    private Byte displayed;

    @Schema(description = "是否必填，0：不必填，1：必填")
    private Byte required;

    @Schema(description = "默认值")
    private String defaultValue;

    @Schema(description = "值类型，0：通用文本，1：手机号码，2：身份证号")
    private Byte valueType;

    @Schema(description = "数据格式")
    private Byte dataFormat;

    @Schema(description = "最小值")
    private String min;

    @Schema(description = "最大值")
    private String max;

    @Schema(description = "小数位数")
    private Byte decimalDigit;

    @Schema(description = "单位倍数")
    private String multiples;

    @Schema(description = "是否多选，0：单选，1：多选")
    private Byte selectType;

    @Schema(description = "是否选项筛选，0：否，1：是")
    private Byte filterType;

    @Schema(description = "下拉框数据源")
    private SelectDatasource selectDatasource;

    @Schema(description = "二级占位信息")
    private String placeholderTwo;

    @Schema(description = "三级占位信息")
    private String placeholderThree;

    @Schema(description = "选择层级，0：二级，1：三级")
    private Byte selectLevel;

    @Schema(description = "日期显示模式")
    private Byte dateFormatType;

    @Schema(description = "最早显示时间")
    private String earliestDatetime;

    @Schema(description = "最早显示时间取值类型，0：固定值，1：操作日")
    private Byte earliestDatetimeType;

    @Schema(description = "最晚显示时间")
    private String latestDatetime;

    @Schema(description = "最晚显示时间取值类型，0：固定值，1：操作日")
    private Byte latestDatetimeType;

    @Schema(description = "日期应用场景，0：通用，1：身份证有效期")
    private Byte dateApplyScene;
}
