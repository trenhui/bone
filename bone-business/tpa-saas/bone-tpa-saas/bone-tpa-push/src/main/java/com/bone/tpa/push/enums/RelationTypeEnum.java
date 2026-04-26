package com.bone.tpa.push.enums;

/***
 * @describe: 关系类型映射
 * @author: shy
 * @version: 1.0
 * @date 2023/6/13 18:15
 **/

public enum RelationTypeEnum {

    BR("401", "本人"),
    PO("402", "配偶"),
    FM("403", "父母"),
    ZN("404", "子女"),
    QT("405", "其他"),
    JQS("406", "近亲属"),
    RBS_BR("401", "4"),
    RBS_PO("402", "1"),
    RBS_PARENT("403", "3"),
    RBS_CHILD("404", "2"),
    RBS_QT("405", "5"),
    //新增瑞泰枚举值
    RT_BR("401", "01"),//被保险人本人
    RT_FATHER("407", "12"),//父亲
    RT_MOTHER("408", "13"),//母亲
    RT_FDSYR("409", "02"),//法定受益人
    RT_FLYDSF("410", "03"),//非利益第三方（含单位和个人）
    RT_YLJGJSB("411", "04"),//医疗机构及社保
    RT_GBDW("412", "05"),//共保单位
    RT_SFXZBM("413", "06"),//司法行政部门及社会组织
    RT_YFLWS("414", "07"),//依法律文书支付第三方
    RT_DSFHZ("415", "08"),//第三方合作单位
    RT_ZFFY("416", "09"),//支付费用给本公司员工
    RT_WS("417", "10"),//我司
    RT_JHR("418", "11"),
    MZ("419", "母子"),
    MN("420", "母女"),
    FZ("421", "父子"),
    FN("422", "父女");//监护人


    private String key;
    private String value;

    private RelationTypeEnum(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public static String getKey(String value) {
        for (RelationTypeEnum b : RelationTypeEnum.values()) {
            if (b.getValue().equals(value)) {
                return b.getKey();
            }
        }
        return "";
    }

    public static String getValue(String key) {
        for (RelationTypeEnum b : RelationTypeEnum.values()) {
            if (b.getKey().equals(key)) {
                return b.getValue();
            }
        }
        return "";
    }
}
