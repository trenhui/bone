package com.bone.tpa.test.claim.sync;

import java.math.BigDecimal;

public class InvoiceTestConstants{
    public static final String invoiceUuid = "invoiceUuid12132324";
    public static final String invoiceNo = "invoiceNo3344555";
    public static final String inputModeCn = "录入组";
    public static final String paperType = "00";
    public static final String eInvoiceNo = "eInvoiceNo1111";
    public static final String verificationCode = "verificationCode32234";
    /**
     * 1:医疗门诊,
     * 2:医疗特殊病,
     * 3:医疗住院,
     * 4:医保审批单,
     * 5:生育门诊,
     * 6生育住院,
     * 7:新农合-住院,
     * 8:普通门诊,
     * 9:普通住院,
     * 10:分割单
     */
    public static final String billTypeCn = "医疗门诊";
    /**
     *  2-真票；
     * 3-无法验真；
     * 4-红冲；
     * 5-换开；
     * 6-假票；
     * 7-未验真；
     * 8-查无此票
     */
    public static final String verifyValidCn = "真票";
    public static final String invoiceDate = "2022-01-01";
    public static final String liveStartDate =  "2022-01-01";


    public static final String liveEndDate =  "2022-02-01";

    public static final String invoiceTypeCn = "门诊";

    public static  final  String treatmentTypeCn = "急诊";

    public static final  String invoiceName = "testinvoiceName";

    public static final  String medicalTypeCn = "普通";

    public static final Integer ybFlag = 0;


    public static final  Integer severeFlag = 0;

    public static  final  Integer chronicFlag = 1;

    public static  final  String ybTypeCn = "杭州医保";


    public static  final  String chronicName = "高血压";

    public static final String hospitalName ="同德";


    public static final String hospitalLevelCn = "甲等";


    public  static  final String hospitalTypeCn="公立";


    public static  final  String hospitalDepartmentCn = "呼吸科";


    public static  final  String diseaseName = "感冒";

    public  static  final  String severeName = "肺癌";




    public  static  final Integer subsidyDays = 5;

    public static  final BigDecimal invoiceAmount = new BigDecimal(100);

    public static  final BigDecimal enteredAmount = new BigDecimal(200);

    public static  final BigDecimal totalMedicalFundPayment = new BigDecimal(300);


    public  static  final  BigDecimal basicPoolingAmount = new BigDecimal(400);

    public static final BigDecimal poolingReimbursementRate = new BigDecimal(500);

    public static  final  BigDecimal partSelfPayAmount = new BigDecimal(600);

    public static  final  BigDecimal totalSelfPayAmount = new BigDecimal(700);

    public  static  final  BigDecimal classCSelfPayAmount = new BigDecimal(800);

    public static  final  BigDecimal excessLimitSelfPayAmount = new BigDecimal(900);

    public  static  final  BigDecimal thirdPartyPaidAmount = new BigDecimal(1000);

    public  static  final  BigDecimal otherFundAmount = new BigDecimal(1100);

    public  static  final  BigDecimal validAmount = new BigDecimal(1200);

    public  static  final  BigDecimal invalidAmount = new BigDecimal(1300);

    public static  final  String remark = "remark33";


    public static final String accountNo = "3232";
}
