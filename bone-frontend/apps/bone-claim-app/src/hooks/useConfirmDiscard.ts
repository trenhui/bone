import { App as AntApp } from 'antd';

export interface DiscardOptions {
  title?: string;
  content?: string;
  /** 留在编辑态的按钮文案（取消语义），默认「继续编辑」。 */
  stayText?: string;
  /** 确认丢弃的按钮文案，默认「放弃修改」。 */
  discardText?: string;
}

/**
 * 关闭前脏检查的命令式确认：`await confirmDiscard()` → true 表示可以继续丢弃。
 *
 * <p>与 `useConfirmAction` 的分工：后者确认的是**写动作**（执行 onOk），
 * 本 hook 确认的是**离开编辑态**（没有写请求）。返回 Promise 而非 void，
 * 才能写出 `if (dirty && !(await confirmDiscard())) return;` 这种不会误关弹窗的控制流。
 *
 * <p>权限与数据真源不在此处：按钮可见性仍走 Auth/AuthButton，服务端仍是最终门禁。
 */
export function useConfirmDiscard(): (opts?: DiscardOptions) => Promise<boolean> {
  const { modal } = AntApp.useApp();
  return ({
    title = '有未保存的修改',
    content = '关闭后本次修改将丢失，且无法恢复。确定放弃吗？',
    stayText = '继续编辑',
    discardText = '放弃修改',
  }: DiscardOptions = {}) =>
    new Promise<boolean>((resolve) => {
      modal.confirm({
        title,
        content,
        okText: discardText,
        okButtonProps: { danger: true },
        cancelText: stayText,
        onOk: () => resolve(true),
        onCancel: () => resolve(false),
      });
    });
}
