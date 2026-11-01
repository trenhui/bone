/**
 * 表单提交失败的错误归类（配置面三个页面共用）。
 *
 * <p><b>为什么需要这个文件</b>：antd 的 {@code validateFields()} 抛出的是「校验错误对象」
 * （带 {@code errorFields}，**没有** {@code errorCode}——那是后端业务码的位置）。
 * 而此前各页面的 catch 形如
 * {@code if (code === 'SC_CLAIM_CONCURRENT_CONFLICT') {...} else if (code) { message.error(...) }}，
 * 一旦是**前端校验失败**（code 为 undefined），就一个分支都不进 ⇒
 * <b>静默失败</b>：用户点「保存」后页面毫无反应，既没有提示也没有字段高亮，
 * 只能靠猜是不是系统坏了。UI 探针实测到的「点保存没反应 + qiankun 报 PromiseRejectionEvent」
 * 就是这个缺陷的外部表现。
 *
 * <p>正确做法：校验失败由 antd 自己高亮/聚焦首个错误字段（前提是**不要把 rejection 吞掉**），
 * 这里只额外补一条人话提示；后端业务错误继续走 errorCode 分支；两者之外的未知异常必须兜底提示，
 * 绝不静默。
 */

/** antd `validateFields()` 的校验失败对象结构（只取我们用到的部分）。 */
interface FormValidationError {
  errorFields?: unknown[];
  outOfDate?: boolean;
}

/** 判断一个 rejection 是否来自 antd 表单校验失败。 */
export function isFormValidationError(error: unknown): boolean {
  if (typeof error !== 'object' || error === null) return false;
  const candidate = error as FormValidationError;
  return Array.isArray(candidate.errorFields) && candidate.errorFields.length > 0;
}

/** 校验失败时的统一提示（字段级高亮由 antd 负责，这里只给人话结论）。 */
export const FORM_VALIDATION_MESSAGE = '表单校验未通过，请检查标红的必填项后重试';
