package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 字段信息
 * 专属字段的页面隔离属性: displayed、required、rowNo、columnNo
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_field")
@Schema(description = "CfgFieldDO对象")
public class CfgFieldDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "所属页面ID")
    private Long pageId;

    @Schema(description = "数据模型ID")
    private Long modelId;

    @Schema(description = "业务字段类型，1：系统，2：专属")
    private Byte fieldType;

    @Schema(description = "业务字段名称")
    private String bizName;

    @Schema(description = "业务字段编码")
    private String bizCode;

    @Schema(description = "字段取值表达式")
    private String dataBinding;

    @Schema(description = "页面组件类型，Input：文本单行输入框，SelectDrop：下拉框，DateTime：日期时间，DateRange：日期区间，SelectCtrl：级联下拉框，InputNum：数字单行输入框")
    private String componentType;

    @Schema(description = "展示标题名称")
    private String title;

    @Schema(description = "对齐方式，0：左对齐，1：居中，2：右对齐")
    private Byte alignment;

    @Schema(description = "提示信息")
    private String prompt;

    @Schema(description = "占位文案")
    private String placeholder;

    @Schema(description = "宽度")
    private Integer width;

    @Schema(description = "限制字数")
    private Integer characterLimit;

    @Schema(description = "输入状态，0：可编辑，1：只读")
    private Byte inputState;

    @Schema(description = "显示隐藏，0：隐藏，1：展示")
    private Byte displayed;

    @Schema(description = "是否必填，0：不必填，1：必填")
    private Byte required;

    @Schema(description = "默认值")
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String defaultValue;

    @Schema(description = "值类型，0：通用文本，1：手机号码，2：身份证号，3:联系方式")
    private Byte valueType;

//================================================

    @Schema(description = "数据格式,0:数值,1:百分百,2:金额")
    private Byte dataFormat;

    @Schema(description = "最小值")
    private String min;

    @Schema(description = "最大值")
    private String max;

    @Schema(description = "小数位数")
    private Byte decimalDigit;

    @Schema(description = "单位倍数")
    private String multiples;

//================================================

    @Schema(description = "选项方式，0：单选，1：多选")
    private Byte selectType;

    @Schema(description = "是否选项筛选，0：否，1：是")
    private Byte filterType;

    @Schema(description = "下拉框选项数据源类型,1:选项集,2:主数据")
    private Byte datasourceType;

    @Schema(description = "下拉框选项数据源code")
    private String datasourceCode;

//================================================

    @Schema(description = "选择层级，0：二级，1：三级")
    private Byte selectLevel;

//================================================

    @Schema(description = "日期显示模式")
    private Byte dateFormatType;

    @Schema(description = "最早显示时间")
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Date earliestDatetime;

    @Schema(description = "最早显示时间取值类型，0：固定值，1：操作日")
    private Byte earliestDatetimeType;

    @Schema(description = "最晚显示时间")
//    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Date latestDatetime;

    @Schema(description = "最晚显示时间取值类型，0：固定值，1：操作日")
    private Byte latestDatetimeType;

//================================================

    @Schema(description = "开始时间占位文案")
    private String placeholderTwo;

    @Schema(description = "结束时间占位文案")
    private String placeholderThree;

    @Schema(description = "日期应用场景，0：通用，1：身份证有效期")
    private Byte dateApplyScene;

//================================================

    @Schema(description = "行号")
    private Integer rowNo;

    @Schema(description = "列号")
    private Integer columnNo;

    @Schema(description = "序号(表格中的字段使用)")
    private Integer sequence;

    /**
     * 额外配置
     * 比如选项集其他的设置
     */
    private String extraConfig;


    @Schema(description = "是否删除，0：正常，1：已删除")
    private Byte deleted;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "修改人")
    private String updateBy;

    @Schema(description = "修改时间")
    private Date updateTime;
}
