package com.bone.tpa.sdk.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BaseFlowNodeVO {

    /**
     * 0 必须进入
     * 1 跳过
     * 2 符合所有选中所有条件进入
     * 3 符合选择任一条件进入
     *
     * 默认是0
     */
    private List<JumpTypeVO> jumpTypeList = new ArrayList<>();


}
