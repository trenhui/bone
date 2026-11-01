import { App as AntApp } from 'antd';

export interface ConfirmOptions {
  title: string;
  content?: string;
  okText?: string;
  /** onOk 返回 Promise 时，确认框自动进入 loading 直至完成/失败。 */
  onOk: () => Promise<void> | void;
}

/**
 * 破坏性配置操作的二次确认封装。
 *
 * <p>保险配置（启用/停用保单、发布/下线计划与险种、减人/恢复额度）一旦误操作即改变承保/额度口径，
 * 且部分不可逆。统一走 antd 的 {@code modal.confirm}（危险按钮样式），避免各页重复实现、口径不一致。
 */
export function useConfirmAction(): (opts: ConfirmOptions) => void {
  const { modal } = AntApp.useApp();
  return ({ title, content, okText, onOk }: ConfirmOptions) => {
    modal.confirm({
      title,
      content,
      okText: okText ?? '确定',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk,
    });
  };
}
