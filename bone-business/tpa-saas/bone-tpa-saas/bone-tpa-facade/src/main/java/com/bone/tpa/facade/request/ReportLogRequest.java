package com.bone.tpa.facade.request;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class ReportLogRequest {

    /**
     * 阶段，需要根据阶段定义更新什么字段
     */
    private String stage ;
    /**
     * 状态中文
     */
    private String status;
    /**
     * 有的话则更新，没有则不更新
     */
    private String operatorName ;
    /**
     * 有的话则更新，没有则不更新
     * 如果传入""字符串，则清空该字段
     */
    private String startTime ;
    /**
     * 有的话则更新，没有则不更新
     *  如果传入""字符串，则清空该字段
     */
    private String endTime ;

}
