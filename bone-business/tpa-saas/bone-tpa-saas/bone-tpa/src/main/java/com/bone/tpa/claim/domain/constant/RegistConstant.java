package com.bone.tpa.claim.domain.constant;

import com.bone.tpa.claim.application.response.KindCode;

import java.util.List;

public class RegistConstant {

    public static List<KindCode> yc_kindCodeList = List.of(
            new KindCode("710022", "医疗保险附加扩展被保险人年龄保险", null),
            new KindCode("500025", "团体住院津贴医疗保险", null),
            new KindCode("500057", "团体生育医疗保险", null),
            new KindCode("500514", "团体补充医疗费用保险（A款）", null),
            new KindCode("500059", "团体门（急）诊住院医疗保险", null),
            new KindCode("500427", "团体门（急）诊住院医疗保险", null),
            new KindCode("710018", "团体门（急）诊住院综合医疗保险附加新型冠状病毒肺炎保险", null),
            new KindCode("600039", "附加医药用品费用补偿保险", null),
            new KindCode("600003", "附加团体意外伤害医疗保险", null),
            new KindCode("600201", "附加团体特定疾病保险（A款）", null),
            new KindCode("600172", "附加接种疫苗意外伤害保险", null),
            new KindCode("600146", "附加疾病身故保险", null),
            new KindCode("600135", "附加癌症特种药品费用医疗保险（线上会员专享）", null)
    );

    public static List<KindCode> yc_itemCodeList = List.of(
            new KindCode("10150", "住院津贴责任", "500025"),
            new KindCode("10157", "生育住院医疗", "500057"),
            new KindCode("10158", "生育门急诊医疗", "500057"),
            new KindCode("10152", "住院医疗保险责任", "500059"),
            new KindCode("10033", "公共保险金额责任（个人保额）", "500059"),
            new KindCode("10035", "公共保险金额责任（团体保额）", "500059"),
            new KindCode("10036", "共用保险金额", "500059"),
            new KindCode("10085", "门诊医疗保险责任", "500059"),
            new KindCode("10151", "住院医疗", "500427"),
            new KindCode("10386", "住院和门急诊医疗", "500427"),
            new KindCode("10387", "公共保险金额", "500427"),
            new KindCode("10385", "门（急）诊医疗", "500427"),
            new KindCode("10372", "住院医疗保险责任", "500514"),
            new KindCode("10454", "公共医疗保险责任", "500514"),
            new KindCode("10452", "门（急）诊医疗保险责任", "500514"),
            new KindCode("10453", "预防性医疗保险责任", "500514"),
            new KindCode("10126", "意外医疗", "600003"),
            new KindCode("10120", "医药用品费用补偿", "600039"),
            new KindCode("10325", "癌症特种药品费用医疗保险", "600135"),
            new KindCode("10330", "附加疾病身故保险", "600146"),
            new KindCode("10359", "接种疫苗意外伤害", "600172"),
            new KindCode("10391", "团体保险附加特定疾病保险金", "600201")
    );


    public static List<KindCode> yc_secondItemCodeList = List.of(
            new KindCode("200166", "意外医疗费用", "10033"),
            new KindCode("200058", "疾病医疗费用", "10033"),
            new KindCode("200166", "意外医疗费用", "10035"),
            new KindCode("200058", "疾病医疗费用", "10035"),
            new KindCode("200166", "意外医疗费用", "10036"),
            new KindCode("200058", "疾病医疗费用", "10036"),
            new KindCode("200156", "意外门诊医疗责任", "10085"),
            new KindCode("200052", "疾病门诊医疗责任", "10085"),
            new KindCode("200152", "医药用品费用补偿", "10120"),
            new KindCode("200163", "意外医疗_意外医疗", "10126"),
            new KindCode("200059", "住院津贴责任_疾病住院津贴", "10150"),
            new KindCode("200193", "住院医疗", "10151"),
            new KindCode("200171", "意外住院医疗责任", "10152"),
            new KindCode("200061", "疾病住院医疗责任", "10152"),
            new KindCode("200200", "生育住院医疗_生育住院医疗", "10157"),
            new KindCode("200199", "生育门急诊医疗_生育门急诊医疗", "10158"),
            new KindCode("200662", "癌症特种药品费用医疗保险", "10325"),
            new KindCode("200666", "附加疾病身故保险", "10330"),
            new KindCode("200714", "疫苗预防接种意外伤残保险金", "10359"),
            new KindCode("200715", "疫苗预防接种意外身故保险金", "10359"),
            new KindCode("200719", "住院医疗保险责任", "10372"),
            new KindCode("500529", "团体补充医疗费用保险（B款）", "10372"),
            new KindCode("200742", "门（急）诊医疗", "10385"),
            new KindCode("200743", "住院和门急诊医疗", "10386"),
            new KindCode("200744", "公共保险金额", "10387"),
            new KindCode("200748", "团体保险附加特定疾病保险金", "10391"),
            new KindCode("200825", "门（急）诊医疗保险责任", "10452"),
            new KindCode("200826", "预防性医疗保险责任", "10453"),
            new KindCode("200827", "公共医疗保险责任", "10454")
    );
}
