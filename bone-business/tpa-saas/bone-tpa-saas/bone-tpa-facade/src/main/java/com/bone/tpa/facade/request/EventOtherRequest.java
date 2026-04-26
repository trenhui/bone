package com.bone.tpa.facade.request;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class EventOtherRequest {

    /**
     * 日志列表
     * 如果传入的日志是空，则不更新日志
     */
    private List<TpaAddLogRequest> operationLogList=new ArrayList<>();

    /**
     * 赔案状态，如果传入null，则不更新赔案状态
     */
    private Integer claimStatus ;

    /**
     * 时效
     * 审核通过的时候会传入
     */
    private BigDecimal limitHour;

    /**
     * 是否需要分配人员
     * false 不需要
     * true 需要
     */
    private boolean assignTag = false;
    /**
     * 分配的阶段
     * PRE_EXAM  初审
     * SUBMITTING  录入
     * INSPECTION  质检
     * AUDITING   审核
     * REVIEWING   复核
     *
     */
    private String assignStage;
    /**
     * 处理人分配策略
     * 0 手工分配
     * 1 随机分配
     * 2 指定分配
     */
    private String assignStrategy;
    /**
     * assignStrategy = 2 时候，必填
     */
    private String assignOperatorName ;
    /**
     * assignStrategy = 2 时候，必填
     */
    private String assignOperatorId ;
    /**
     * assignStrategy = 2 时候，必填
     */
    private String assignOperatorGroupName;
    /**
     * assignStrategy = 2 时候，必填
     */
    private String assignOperatorGroupId;

    /**
     * 有的话更新，没有则忽略
     */
    private List<ReportLogRequest> reportLogRequest = new ArrayList();

}
