/**
 * 实体「界面设计器」页签。
 *
 * 本文件只负责装配（取数 + 传参），具体配置能力全部在 `components/uiSchema/UiSchemaDesigner`。
 * 拆分理由：配置器本身已足够复杂（结构/规则/动作/列表/字典/多语言/模板/变体/自检/JSON + 预览），
 * 与实体详情的取数逻辑混在一个文件里会双双失控。
 */
import type { MetaField } from '../types';
import UiSchemaDesigner from './uiSchema/UiSchemaDesigner';

interface Props {
  entityId: string;
  entityCode: string;
  fields: MetaField[];
  /**
   * 前往实体详情「发布」页签（0-1 闭环：设计器内「去发布」按钮直达）。
   * 由 EntityDetail 注入 setOuterTab('publish')；缺省时设计器不渲染该按钮。
   */
  onGoPublish?: () => void;
  /**
   * 前往实体详情「列表显示」页签（两层闭环反向入口：设计器工具栏「个人偏好」按钮）。
   * 由 EntityDetail 注入 setOuterTab('ui')；缺省时设计器不渲染该按钮。
   */
  onGoPersonal?: () => void;
  /**
   * 实体行策略声明的 rule name 集合（entity.rowPolicies.rules[].name）。
   * 列表视图 dataScope.policyCode 下拉真源（协议 §3.2：只引用 row_policies，前端不拼 WHERE）。
   */
  policyCodes?: string[];
}

const UiSchemaTab: React.FC<Props> = ({ entityId, entityCode, fields, onGoPublish, onGoPersonal, policyCodes }) => (
  <UiSchemaDesigner
    entityId={entityId}
    entityCode={entityCode}
    fields={fields ?? []}
    onGoPublish={onGoPublish}
    onGoPersonal={onGoPersonal}
    policyCodes={policyCodes}
  />
);

export default UiSchemaTab;
