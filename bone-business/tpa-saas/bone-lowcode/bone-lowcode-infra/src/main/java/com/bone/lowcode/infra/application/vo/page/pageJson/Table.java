package com.bone.lowcode.infra.application.vo.page.pageJson;

import com.bone.lowcode.infra.application.vo.Sequence;
import com.bone.lowcode.infra.application.vo.page.EditableColumnVO;
import com.bone.lowcode.infra.application.vo.processPage.SearchFieldVO;
import com.bone.lowcode.infra.domain.model.TableFieldSortType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class Table implements Sequence {

    private String id;

    private String type;

    private String name;

    private Integer sequence;

    private List<Field> body;

    private List<EventTrigger> eventTriggerList;

    @Schema(description = "提示语")
    private String prompt;

    @Schema(description = "空数据提示语")
    private String emptyPrompt;

    private List<TableFieldSortType> fieldSortTypeList;

    @Schema(description = "是否开启序号列,1：开启，0：不开启")
    private Byte enableOrderColumn;

    @Schema(description = "可编辑列列表")
    private List<EditableColumnVO> editableColumnList;

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

    private String display;//表格显示还是隐藏
    private Byte requiredData;//提交时是否要求有数据,1:必须有,0:可以没有

    @Schema(description = "显示分页，1：显示，0：不显示")
    private Byte paginationEnabled;

    @Schema(description = "默认页行数")
    private Integer defaultPageSize;

    @Schema(description = "显示总条数，1：显示，0：不显示")
    private Byte displayTotalSize;

    //    父表格id
    private String parentTableId;

    //    initApi的入参名和值，取自父表格被选中的数据
    private String initApiParam;

    //    获取数据的接口
    private String initApi;

    //    提交数据的接口
    private String submitApi;

    //    删除数据的接口
    private String deleteApi;

    // 是否开启数据汇总，0：否，1：是
    private Byte enableDataSummary;

    // 是否是否开启数据分组聚合，0：否，1：是
    private Byte enableDataAggregate;

    // 是否开启搜索栏,0:否,1:是
    private Byte enableSearch;

    // 搜索字段
    private List<SearchFieldVO> searchFieldList;

    private List<String> modelCodeList;

    @Override
    public Integer returnSequence() {
        return sequence;
    }
}
