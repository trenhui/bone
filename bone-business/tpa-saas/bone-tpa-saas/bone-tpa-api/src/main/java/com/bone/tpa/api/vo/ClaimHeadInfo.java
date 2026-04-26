package com.bone.tpa.api.vo;

import lombok.Data;

import java.util.Date;

/**
 * 签收信息
 */
@Data
public class ClaimHeadInfo {
    /**
     * 赔案标识（主键）
     *
     * @mbg.generated
     */

    private String claimid;

    /**
     * 城市编号
     *
     * @mbg.generated
     */
    private Integer cityid;

    /**
     * 保险公司ID
     *
     * @mbg.generated
     */
    private String insurancecompanyid;

    /**
     * 总赔号
     *
     * @mbg.generated
     */
    private Long claimheadnumber;

    /**
     * 案件数量
     *
     * @mbg.generated
     */
    private Integer claimcount;

    /**
     * 标签打印
     *
     * @mbg.generated
     */
    private Integer printlabelflag;

    /**
     * 案件分配区分 2;//全部已分配 1;//部分已分配 0;//未分配
     *
     * @mbg.generated
     */
    private Integer claimassignflag;

    /**
     * 赔案签收类型
     *
     * @mbg.generated
     */
    private Integer claimsigntype;

    /**
     * 投保单位ID
     *
     * @mbg.generated
     */
    private String insuredcompanyid;

    /**
     * 医院ID
     *
     * @mbg.generated
     */
    private String hospitalid;

    /**
     * 归档日期
     *
     * @mbg.generated
     */
    private Date claimarchivedate;

    /**
     * 归属日期
     *
     * @mbg.generated
     */
    private Date claimdate;

    /**
     * 紧急程度（分配）
     *
     * @mbg.generated
     */
    private Integer signurgencytype;

    /**
     * 紧急程度（录入）
     *
     * @mbg.generated
     */
    private Integer assignurgencytype;

    /**
     * 1-影像签收
     *
     * @mbg.generated
     */
    private Integer claimdatatype;

    /**
     * 签收人
     *
     * @mbg.generated
     */
    private String claimsigncreator;

    /**
     * 签收日期
     *
     * @mbg.generated
     */
    private Date claimsigndate;

    /**
     * 赔案分配人
     *
     * @mbg.generated
     */
    private String claimassigncreator;

    /**
     * 赔案分配时间
     *
     * @mbg.generated
     */
    private Date claimassigndate;

    /**
     * 赔案分配类型
     *
     * @mbg.generated
     */
    private Integer claimassigntype;

    /**
     * 保留字段1
     *
     * @mbg.generated
     */
    private String reserved1;

    /**
     * 保留字段2
     *
     * @mbg.generated
     */
    private String reserved2;

    /**
     * 创建时间
     *
     * @mbg.generated
     */
    private Date createtime;

    /**
     * 创建人
     *
     * @mbg.generated
     */
    private String createuser;

    /**
     * 更新时间
     *
     * @mbg.generated
     */
    private Date updatetime;

    /**
     * 更新人
     *
     * @mbg.generated
     */
    private String updateuser;

    /**
     * 是否已删除
     *
     * @mbg.generated
     */
    private Byte isdelete;

    /**
     * 案件所属地
     *
     * @mbg.generated
     */
    private String locationguid;

    /**
     * 总赔状态(1:已结案;其它:未结案)
     *
     * @mbg.generated
     */
    private Integer claimstatus;

    /**
     * 结案时间
     *
     * @mbg.generated
     */
    private Date closetime;

    /**
     * 直付/事后标识(0:直付;1:事后)
     *
     * @mbg.generated
     */
    private Integer directorafterflag;

    /**
     * 发送日期
     *
     * @mbg.generated
     */
    private Date metlifesenddate;

    /**
     * 分支机构
     *
     * @mbg.generated
     */
    private String insurancecompanychild;

    /**
     * 签收人
     *
     * @mbg.generated
     */
    private String claimsigncreatorname;

    /**
     * 分配人
     *
     * @mbg.generated
     */
    private String claimassigncreatorname;

    /**
     * 最晚转账日
     *
     * @mbg.generated
     */
    private Date transferenddate;

    /**
     * 原件状态：IsOriginal:1已归还，0未归还
     *
     * @mbg.generated
     */
    private Byte isoriginal;

    /**
     * 旧的理赔次数
     *
     * @mbg.generated
     */
    private Long oldclaimnumber;

    /**
     * 首次结案时间
     *
     * @mbg.generated
     */
    private Date fclosetime;

    /**
     * 0-全流程 1-半流程
     *
     * @mbg.generated
     */
    private String workflow;

    private Date metlifesenddatew;

    /**
     * 是否人工推送
     *
     * @mbg.generated
     */
    private Byte isartificialpush;

    /**
     * 投保公司
     *
     * @mbg.generated
     */
    private String insurename;

    /**
     * 保险公司
     *
     * @mbg.generated
     */
    private String insurancename;

    /**
     * 分支机构
     *
     * @mbg.generated
     */
    private String branchname;

    /**
     * 归属地
     *
     * @mbg.generated
     */
    private String location;

    /**
     * 紧急程度
     *
     * @mbg.generated
     */
    private String urgencystate;

    /**
     * 是否自动回传
     *
     * @mbg.generated
     */
    private Integer isautoback;

    /**
     * 特殊操作
     *
     * @mbg.generated
     */
    private String specialopr;

    /**
     * 补赔赔案号
     *
     * @mbg.generated
     */
    private String reclaimnumber;

    /**
     * 补赔审核员
     *
     * @mbg.generated
     */
    private String reclaimuser;

    /**
     * 资料类型(原件,影像件)
     *
     * @mbg.generated
     */
    private String filetype;

    /**
     * 工银批次号
     *
     * @mbg.generated
     */
    private String gybatchno;

    /**
     * 签收名单
     *
     * @mbg.generated
     */
    private String claimlist;

    /**
     * 备注
     *
     * @mbg.generated
     */
    private String remark;

    /**
     * 分配备注
     *
     * @mbg.generated
     */
    private String remarkassign;

    /**
     * 线上/线下
     *
     * @mbg.generated
     */
    private String onlinetype;

    /**
     * 是否完成上传 （是/否）
     *
     * @mbg.generated
     */
    private Integer onuploadcomplete;

    /**
     * 线下件来源(0-上门签收，1-快递签收)
     *
     * @mbg.generated
     */
    private Integer offlinesource;

    /**
     * 确认签收完成 （0否/1是）
     *
     * @mbg.generated
     */
    private Integer onsigncomplete;

    private Integer allDetail;

    private String acquiringSerialNumber;

    /**
     * 机构ID
     *
     * @mbg.generated
     */
    private String agencycode;


    /**
     * 幸福确认 0否/1是
     *
     * @mbg.generated
     */
    private Integer xfFlag;

    /**
     * 幸福确认时间
     *
     * @mbg.generated
     */
    private Date xfConfirmDate;

    /**
     * 幸福确认人
     *
     * @mbg.generated
     */

    /**
     * 幸福备注
     *
     * @mbg.generated
     */
    private String xfRemark;





}
