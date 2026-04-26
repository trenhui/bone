package com.bone.lowcode.infra.application.vo.processPage;

import com.bone.lowcode.infra.domain.model.SelectDatasource;
import lombok.Data;

@Data
public class SearchFieldVO {

    private String fieldId;

    private String bizCode;

    private String bizName;

    private Integer sequence;

    //展示标题名称
    private String title;

    //页面组件类型，Input：文本单行输入框，SelectDrop：下拉框，DateTime：日期时间，DateRange：日期区间，SelectCtrl：级联下拉框，InputNum：数字单行输入框
    private String componentType;

    //是否多选，0：单选，1：多选
    private Byte selectType;

    //是否选项筛选，0：否，1：是
    private Byte filterType;

    //选项数据源名称
    private SelectDatasource selectDatasource;

    //选择层级，0：二级，1：三级
    private Byte selectLevel;

    //是否必填，0：不必填，1：必填
    private Byte required;

    //搜索方式
    private Byte searchMode;
}
