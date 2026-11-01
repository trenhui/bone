import { App as AntApp } from 'antd';

export interface ConfirmOptions {
  title: string;
  content?: string;
  okText?: string;
  /**
   * 是否为「破坏性」动作（默认 true，确认按钮渲染为危险色）。
   *
   * <p>发布/启用这类<b>正向</b>状态迁移不是破坏性的，用危险色会误导用户以为不可逆；
   * 只有下线/停用/减人才应保留红色。</p>
   */
  danger?: boolean;
  /** onOk 返回 Promise 时，确认框自动进入 loading 直至完成/失败。 */
  onOk: () => Promise<void> | void;
}

/**
 * 破坏性配置操作的二次确认封装。
 *
 * <p>保险配置（启用/停用保单、发布/下线计划与险种、减人/恢复额度）一旦误操作即改变承保/额度口径，
 * 且部分不可逆。统一走 antd 的 {@code modal.confirm}，避免各页重复实现、口径不一致。</p>
 */
export function useConfirmAction(): (opts: ConfirmOptions) => void {
  const { modal } = AntApp.useApp();
  return ({ title, content, okText, danger = true, onOk }: ConfirmOptions) => {
    modal.confirm({
      title,
      content,
      okText: okText ?? '确定',
      okButtonProps: danger ? { danger: true } : undefined,
      cancelText: '取消',
      onOk,
    });
  };
}
