package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum EventType {
    /**
     * tpa 赔案，签收后下发到saas 录入
     * 返回数据
     *
     * {
     *     "claimStatus":"13",  //10 初审呆分配 ， 13 自动化初审中
     *     "claimStatusCn":"自动化初审中" //状态描述
     *     "bizIdentityCode":"code1" //业务类型
     *     "tenantId":"1"
     * }
     *
     *
     */
    tpa签收赔案下发到saas初审("1",1,"saas","tpa签收赔案下发到saas初审"),

    /**
     * 通知tpa自动化初审完成
     * 传入赔案claimNumber，不用传赔案数据
     * 返回的map里要有以下数据
     *         String autoDealer 0 手工分配 1 自动分配
     *         如果是手工分配，则状态变成 10-等待初审-未分配
     *         如果是自动分配，则状态变成 11-初审中，并且要求有以下字段
     *         String operatorId = (String)rsData.get("operatorId");; autoDealer=1时候，必须返回
     *         String operatorName = (String)rsData.get("operatorName");; autoDealer=1时候，必须返回
     *         String operatorGroupId = (String)rsData.get("operatorGroupId");; autoDealer=1时候，必须返回
     *         String operatorGroupName = (String)rsData.get("operatorGroupName");; autoDealer=1时候，必须返回

     */
    saa通知tpa自动化初审完成("20",1,"tpa","saa通知tpa自动化初审完成"),


    /**
     * tpa 对不需要初审的赔案，跳过初审，下发到saas 录入
     * {
     *     "claimStatus":"13",  //10 初审呆分配 ， 13 自动化初审中
     *     "claimStatusCn":"自动化初审中" //状态描述
     *     "bizIdentityCode":"code1" //业务类型
     *     "tenantId":"1"
     * }
     */
    tpa赔案跳过初审下发到saas录入("2",1,"saas","tpa赔案跳过初审下发到saas录入"),

    /**
     * 通知tpa初审完成-初审完成(不用自动分配人员，但是要分配组),
     *    // waibao 外包录入  pukang 普康录入
     *    String operatorSource = (String)rsData.get("operatorSource"); 必须返回
     *    String operatorId = (String)rsData.get("operatorId");  operatorSource = waibao 时候返回
     *    String operatorName = (String)rsData.get("operatorName"); operatorSource = waibao 时候返回
     *    String operatorGroupId = (String)rsData.get("operatorGroupId"); operatorSource = waibao 时候返回
     *    String operatorGroupName = (String)rsData.get("operatorGroupName"); operatorSource = waibao 时候返回
     */
    saas通知tpa初审完成不自动分配("10",0,"tpa","saas通知tpa初审完成不自动分配"),

    /**
     *         // waibao 外包录入  pukang 普康录入
     *         String operatorSource = (String)rsData.get("operatorSource");; 必须返回
     *         String operatorId = (String)rsData.get("operatorId");; 必须返回
     *         String operatorName = (String)rsData.get("operatorName");; 必须返回
     *         String operatorGroupId = (String)rsData.get("operatorGroupId");; 必须返回
     *         String operatorGroupName = (String)rsData.get("operatorGroupName");; 必须返回
     * 通知tpa初审完成-初审完成(自动分配)
     */
    saas通知tpa初审完成并且自动分配("11",0,"tpa","saas通知tpa初审完成并且自动分配"),

    /**
     * saas通知tpa案件自动化录入中
     */
    saas通知tpa案件自动化录入中("12",0,"tpa","saas通知tpa案件自动化录入中"),
    /**
     * saas通知tpa录入完成
     *
     * 返回的map里要有以下数据
     *         String autoDealer 0 手工分配 1 自动分配
     *         String operatorId = (String)rsData.get("operatorId");; autoDealer=1时候，必须返回
     *         String operatorName = (String)rsData.get("operatorName");; autoDealer=1时候，必须返回
     *         String operatorGroupId = (String)rsData.get("operatorGroupId");; autoDealer=1时候，必须返回
     *         String operatorGroupName = (String)rsData.get("operatorGroupName");; autoDealer=1时候，必须返回
     */
    saas通知tpa录入完成("13",0,"tpa","saas通知tpa人工完成"),

    /**
     * saas通知tpa质检完成
     */
    saas通知tpa质检完成("14",0,"tpa","saas通知tpa质检完成"),
    /**
     * 外包完成后，tpa 回调用saas，同步赔案数据
     */
    外包录入完成tpa回调saas("17",1,"saas", "外包录入完成tpa回调saas"),

    /**
     * saas提交发票记录
     */
    saas发票查重("19",1,"tpa", "saas提交发票，用于查重"),


    /**
     * 1218里程碑新增
     */
    saas通知tpa同步数据状态和日志("30",0,"tpa","saas通知tpa同步数据状态和日志"),


    /**
     * 复制赔案
     */
    saas通知tpa复制赔案("31",0,"tpa","saas通知tpa复制赔案"),

    ;
    private String code ;
    /**
     * 因为三个接口共用一个eventType枚举，所以同type做区分
     * 0 待定，tpa自己去梳理
     * 1 同步赔案
     * 2 退回赔案
     * 3 tpa分配赔案和组
     *
     */
    private Integer type ;
    /**
     * 接收事件系统名
     */
    private String system;
    private String desc;

    EventType(String code,Integer type,String system, String desc) {
        this.code = code;
        this.type = type;
        this.system= system;
        this.desc = desc;
    }

    public static EventType getByCode(String code){
        for (EventType eventType : EventType.values()) {
            if(eventType.getCode().equals(code)){
                return eventType;
            }
        }
        return null;

    }
}
