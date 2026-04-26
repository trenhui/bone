package com.bone.lowcode.infra.infrastructure.persistence.dataobject;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bone.lowcode.infra.domain.model.DataSummaryRule;
import com.bone.lowcode.infra.domain.model.EditableColumn;
import com.bone.lowcode.infra.domain.model.TableFieldSortType;
import com.bone.lowcode.infra.domain.model.TableSearchField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@Data
@EqualsAndHashCode(callSuper = false)
@TableName("cfg_table")
@Schema(description = "CfgTableDO对象")
public class CfgTableDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "所属页面ID")
    private Long pageId;

    @Schema(description = "所属block的id")
    private Long blockId;

    @Schema(description = "名称")
    private String name;

    @Schema(description = "提示语")
    private String prompt;

    @Schema(description = "空数据提示语")
    private String emptyPrompt;
    @Schema(description = "数据模型列表")
    private String modelIdList;

    @Schema(description = "序号")
    private Integer sequence;

    //    @TableField(updateStrategy = FieldStrategy.IGNORED)
    @Schema(description = "排序方式列表")
    private String orderByList;

    @Schema(description = "是否开启序号列,1：开启，0：不开启")
    private Byte enableOrderColumn;

    @Schema(description = "可编辑列列表")
    private String editableColumnList;

    @Schema(description = "左固定方式，0：不固定，1：前一列固定，2：前二列固定，3：前三列固定")
    private Byte leftFixed;

    @Schema(description = "右固定方式，0：不固定，1：最后一列固定，2：最后二列固定")
    private Byte rightFixed;

    @Schema(description = "是否占满剩余屏幕，0：否，1：是")
    private Byte fillScreen;

    @Schema(description = "是否开启操作列，1：开启，0：不开启")
    private Byte operationColumnEnabled;

    @Schema(description = "是否固定操作列，1：固定，0：不固定")
    private Byte operationColumnFixed;

    @Schema(description = "表格显示还是隐藏")
    private String display;

    @Schema(description = "提交时是否要求有数据,1:必须有,0:可以没有")
    private Byte requiredData;

    @Schema(description = "显示分页，1：显示，0：不显示")
    private Byte paginationEnabled;

    @Schema(description = "默认页行数")
    private Integer defaultPageSize;

    @Schema(description = "显示总页数，1：显示，0：不显示")
    private Byte displayTotalPage;

    @Schema(description = "显示总条数，1：显示，0：不显示")
    private Byte displayTotalSize;

    @Schema(description = "父表格id")
    private Long parentTableId;

    @Schema(description = "initApi的入参名和值，取自父表格被选中的数据")
    private String initApiParam;

    @Schema(description = "获取数据的接口")
    private String initApi;

    @Schema(description = "提交数据的接口")
    private String submitApi;

    @Schema(description = "删除数据的接口")
    private String deleteApi;

    @Schema(description = "是否开启数据汇总，0：否，1：是")
    private Byte enableDataSummary;

    @Schema(description = "数据汇总规则")
    private String dataSummaryRule;

    @Schema(description = "是否开启数据分组聚合，0：否，1：是")
    private Byte enableDataAggregate;

    @Schema(description = "是否开启搜索栏,0:否,1:是")
    private Byte enableSearch;

    @Schema(description = "搜索字段")
    private String searchFieldList;

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

    public List<Long> getModelIds() {
        List<Long> list = new ArrayList<>();
        if (StringUtils.hasText(modelIdList)) {
            list = Arrays.stream(modelIdList.split(",")).map(Long::parseLong).toList();
        }
        return list;
    }

    public void setModelIdListFromList(List<Long> modelIdListNew) {
        this.modelIdList = modelIdListNew.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public List<TableFieldSortType> getSortTypeList() {
        List<TableFieldSortType> sortTypeList = new ArrayList<>();
        if (StringUtils.hasText(orderByList)) {
            sortTypeList = JSON.parseArray(orderByList, TableFieldSortType.class);
        }
        return sortTypeList;
    }

    public void setSortTypeList(List<TableFieldSortType> list) {
        if (CollectionUtils.isEmpty(list)) {
            this.orderByList = null;
        } else {
            this.orderByList = JSON.toJSONString(list);
        }
    }

    public List<EditableColumn> getEditableColumns() {
        List<EditableColumn> list = new ArrayList<>();
        if (StringUtils.hasText(editableColumnList)) {
            list = JSON.parseArray(editableColumnList, EditableColumn.class);
        }
        return list;
    }

    public void setEditableColumns(List<EditableColumn> editableColumnList) {
        if (CollectionUtils.isEmpty(editableColumnList)) {
            this.editableColumnList = null;
        } else {
            this.editableColumnList = JSON.toJSONString(editableColumnList);
        }
    }

    public List<DataSummaryRule> getDataSummaryRules() {
        List<DataSummaryRule> list = new ArrayList<>();
        if (StringUtils.hasText(dataSummaryRule)) {
            list = JSON.parseArray(dataSummaryRule, DataSummaryRule.class);
        }
        return list;
    }

    public void setDataSummaryRules(List<DataSummaryRule> list) {
        if (CollectionUtils.isEmpty(list)) {
            this.dataSummaryRule = "";
        } else {
            this.dataSummaryRule = JSON.toJSONString(list);
        }
    }

    public List<TableSearchField> getSearchFields() {
        List<TableSearchField> list = new ArrayList<>();
        if (StringUtils.hasText(searchFieldList)) {
            list = JSON.parseArray(searchFieldList, TableSearchField.class);
        }
        return list;
    }

    public void setSearchFields(List<TableSearchField> list) {
        if (CollectionUtils.isEmpty(list)) {
            this.searchFieldList = null;
        } else {
            this.searchFieldList = JSON.toJSONString(list);
        }
    }
}
