/**
 * ${className} 页面类型定义
 * 遵循单一职责原则，集中管理页面相关类型
 */
import type { ${className} } from '../../../services/${moduleName}/${lowerClassName}Api';

/**
 * ${className} 表单弹窗组件 Props
 */
export interface ${className}FormModalProps {
  /** 弹窗是否打开 */
  open: boolean;
  /** 当前编辑记录，null 表示新增 */
  currentRecord: ${className} | null;
  /** 取消回调 */
  onCancel: () => void;
  /** 提交完成回调 */
  onSubmit: (values: Partial<${className}>) => Promise<void>;
}
