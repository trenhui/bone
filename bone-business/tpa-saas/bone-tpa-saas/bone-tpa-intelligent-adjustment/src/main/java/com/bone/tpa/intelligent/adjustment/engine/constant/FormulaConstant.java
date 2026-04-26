package com.bone.tpa.intelligent.adjustment.engine.constant;

public class FormulaConstant {

    public static final String HEAD = "%s = 责任理算金额";

    public static final String PLUS = " + ";

    public static final String LIABILITY_LIMIT = "责任可用额度";

    public static final String INVOICE_FEE_LIMIT = "对应费用可用额度";

    public static final String MIN_ONE = "Min(%s)";

    public static final String MIN_TWO = "Min(%s, %s)";

    public static final String MIN_THREE = "Min(%s, %s, %s)";

    public static final String DEDUCT_MONEY_DIVERSE_BY_FEE = "对应责任剩余免赔金额";

    public static final String DEDUCT_MONEY = "剩余免赔金额";

    public static final String FORMULA_DEDUCT_RATIO = "%s * (1 - 免赔比例) * 对应赔付比例";

    public static final String FORMULA_DEDUCT_INVOICE = "(%s - %s) * 对应赔付比例";

    public static final String FORMULA_DEDUCT_ADJUSTMENT = "%s * 对应赔付比例 - %s";

    public static final String FORMULA_BASIC = "%s * 对应赔付比例";


    public static final String FORMULA_FIXED = "给付额度 * 赔付比例 = 责任理算金额";

    public static final String FORMULA_FIXED_DEDUCT_INVOICE = "(给付额度 - 免赔金额) * 赔付比例 = 责任理算金额";

    public static final String FORMULA_FIXED_DEDUCT_ADJUSTMENT = "给付额度 * 赔付比例 - 免赔金额 = 责任理算金额";

    public static final String FORMULA_FIXED_DEDUCT_RATIO = "给付额度 * (1 - 免赔比例) * 赔付比例 = 责任理算金额";


    public static final String FORMULA_ALLOWANCE = "Min(日津贴金额 * 津贴天数 * 赔付比例, 津贴可用额度) = 责任理算金额";

    public static final String FORMULA_ALLOWANCE_DEDUCT_DAYS = "Min(日津贴金额 * (津贴天数 - 剩余免赔天数) * 赔付比例, 津贴可用额度) = 责任理算金额";

    public static final String FORMULA_ALLOWANCE_DEDUCT_RATIO = "Min(日津贴金额 * 津贴天数 * (1 - 免赔比例) * 赔付比例, 津贴可用额度) = 责任理算金额";

    public static final String FORMULA_ALLOWANCE_DEDUCT_MONEY = "Min(日津贴金额 * 津贴天数 * 赔付比例 - 剩余免赔金额, 津贴可用额度) = 责任理算金额";

    /**
     * 医疗报销型数字公式
     */
    public static final String MIN_TWO_WITH_NAME = "Min(%s, 责任可用:%s)";

    public static final String EMPTY_HEAD = "%s = 理算金额:%s";

    public static final String EMPTY_FORMULA_DEDUCT_BASIC = "%s:%s * 赔付比例:%s%%, %s可用:%s";

    public static final String EMPTY_FORMULA_DEDUCT_RATIO = "%s:%s * 赔付比例:%s%% * (1 - 免赔比例:%s%%), %s可用:%s";

    public static final String EMPTY_FORMULA_DEDUCT_INVOICE = "(%s:%s - 免赔额:%s) * 赔付比例:%s%%, %s可用:%s";

    public static final String EMPTY_FORMULA_DEDUCT_ADJUSTMENT = "%s:%s * 赔付比例:%s%% - 免赔额:%s, %s可用:%s";

    /**
     * 定额给付型，没有控额的公式
     */
    public static final String EMPTY_FORMULA_FIXED_BASIC = "%s:%s * 赔付比例:%s%%";

    public static final String EMPTY_FORMULA_FIXED_RATIO = "%s:%s * 赔付比例:%s%% * (1 - 免赔比例:%s%%)";

    public static final String EMPTY_FORMULA_FIXED_INVOICE = "(%s:%s - 免赔额:%s) * 赔付比例:%s%%";

    public static final String EMPTY_FORMULA_FIXED_ADJUSTMENT = "%s:%s * 赔付比例:%s%% - 免赔额:%s";

    /**
     * 津贴给付型数字公式
     */
    public static final String EMPTY_FORMULA_ALLOWANCE = "Min(%s * %s, 津贴可用金额:%s)";

    public static final String EMPTY_FORMULA_ALLOWANCE_PER_DAY = "津贴金额:%s * 赔付比例:%s%%";

    public static final String EMPTY_FORMULA_ALLOWANCE_DAYS = "津贴天数:%s";

    public static final String EMPTY_FORMULA_ALLOWANCE_DEDUCT_DAYS = "(津贴天数:%s - 免赔天数:%s)";

//    public static final String EMPTY_FORMULA_ALLOWANCE_DEDUCT_RATIO = "%s * (1 - 免赔比例:%s%%) * 赔付比例:%s%%";
//
//    public static final String EMPTY_FORMULA_ALLOWANCE_DEDUCT_INVOICE = "(%s - 免赔额:%s) * 赔付比例:%s%%";
//
//    public static final String EMPTY_FORMULA_ALLOWANCE_DEDUCT_ADJUSTMENT = "%s * 赔付比例:%s%% - 免赔额:%s";

    /**
     * 定额给付型数字公式
     */
}
