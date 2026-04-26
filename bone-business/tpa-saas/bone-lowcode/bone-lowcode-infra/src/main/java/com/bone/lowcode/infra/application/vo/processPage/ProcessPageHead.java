package com.bone.lowcode.infra.application.vo.processPage;

import com.bone.lowcode.infra.application.vo.processPage.PageHeadFieldVO;
import lombok.Data;

import java.util.List;

@Data
public class ProcessPageHead {

    /**
     * 是否开启页头,0:否,1:是
     */
    private byte enablePageHead;

    /**
     * 页头字段信息
     */
    private List<PageHeadFieldVO> pageHeadField;

    /**
     * 页头所用的model的id
     */
    private String pageHeadModelId;

    /**
     * 页头所用的model的code
     */
    private String modelCode;
}
