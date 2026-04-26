package com.bone.tpa.test.claim.sync;

import java.math.BigDecimal;

public class ProjectTestConstatns {

    static public String invoiceUuid = InvoiceTestConstants.invoiceUuid;

    static public String projectName = "诊疗费";


    static public BigDecimal amount = new BigDecimal("100.00");

    static public BigDecimal allSelfPayAmount = new BigDecimal(200);


    static public BigDecimal partSelfPayAmount = new BigDecimal(100);

    static public BigDecimal thirdPayAmount = new BigDecimal(200);

    static public BigDecimal reasonableAmount = new BigDecimal(300);

    static public BigDecimal unReasonableAmount = new BigDecimal(400);
}
