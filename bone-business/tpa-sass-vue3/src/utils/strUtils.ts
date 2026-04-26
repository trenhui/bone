/**
 * 首字母大写
 * @param str
 * @returns
 */
export const capitalizeFirstLetter = (str: string) => {
  if (!str || !str.length) {
    return str;
  }
  return str.charAt(0).toUpperCase() + str.slice(1);
};

/**
 * 正则校验
 * @param str
 * @param reg
 * @returns
 */
export const validateRegex = (str: string, reg: RegExp) => {
  return reg.test(str);
};

/**
 * 校验中文字母数字下划线
 * @param str
 * @param minLen
 * @param maxLen
 * @returns
 */
export const validateChineseLetterNumberUnderline = (
  str: string,
  minLen: number,
  maxLen: number
) => {
  return validateRegex(
    str,
    new RegExp(`^[\u4e00-\u9fa5A-Za-z0-9_]{${minLen},${maxLen}}$`)
  );
};

/**
 * 校验字母数字下划线
 * @param str
 * @param minLen
 * @param maxLen
 * @returns
 */
export const validateLetterNumberUnderline = (
  str: string,
  minLen: number,
  maxLen: number
) => {
  return validateRegex(str, new RegExp(`^[A-Za-z0-9_]{${minLen},${maxLen}}$`));
};

/**
 * 校验身份证号
 * 身份证号：
    基本校验：15位或18位。^\d{17}[\dXx]$  or   ^\d{15}$
    校验码校验：
      15位不校验。
      18位校验：
      将身份证号的前17位分别乘以对应的权重系数，权重系数为：[7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]。
      将乘积结果相加。
      将相加的结果除以11，取余数。
      根据余数查找对应的校验码，对应关系为：[1, 0, X, 9, 8, 7, 6, 5, 4, 3, 2]。
      如果计算出的校验码与身份证号的最后一位一致，则身份证号有效。

      例：余数=10，则取对应关系的「10」即2，若最后一位为2，则身份证有效。
 * @param str
 * @returns
 */
export const validateIdCard = (str: string) => {
  // 基本格式校验
  if (!/^\d{15}$/.test(str) && !/^\d{17}[\dXx]$/.test(str)) {
    return false;
  }
  // 15位身份证不校验
  if (str.length === 15) {
    return true;
  }
  // 18位身份证校验
  // 身份证号码前17位权重系数
  const weightFactors = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  // 身份证校验码对应值
  const validationCodes = [
    "1",
    "0",
    "X",
    "9",
    "8",
    "7",
    "6",
    "5",
    "4",
    "3",
    "2",
  ];
  // 获取前17位
  const idCard17 = str.substring(0, 17);
  // 获取最后一位校验码
  const idCardLast = str.substring(17).toUpperCase();
  // 计算校验和
  let sum = 0;
  for (let i = 0; i < 17; i++) {
    sum += parseInt(idCard17.charAt(i)) * weightFactors[i];
  }
  // 计算校验码
  const remainder = sum % 11;
  const expectedCode = validationCodes[remainder];
  // 比较计算出的校验码与身份证最后一位是否一致
  return expectedCode === idCardLast;
};

/**
 * 校验手机号
 * @param str
 * @returns
 */
export const validatePhone = (str: string) => {
  return validateRegex(str, new RegExp(/^1[3-9]\d{9}$/));
};

/**
 * 校验联系方式
 * 需满足其一：^1[3-9]\d{9}$；^(0\d{2,3}-)?\d{7,8}$；^(0\d{2,3}-)?\d{7,8}(-\d{1,5})?$
 * @param str
 * @returns
 */
export const validateContact = (str: string) => {
  return (
    validateRegex(str, /^1[3-9]\d{9}$/) ||
    validateRegex(str, /^(0\d{2,3}-)?\d{7,8}(-\d{1,5})?$/)
  );
};
