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
   * 前往实体详情「数据」页签（两层闭环反向入口：设计器工具栏「运行时列表」按钮）。
   * 个人覆盖（列显隐/密度/个人视图）的唯一编辑器在运行时数据页工具栏——
   * 编辑入口跟着被编辑对象的生命周期走（业界双轨：组织级契约在 maker 工具配，
   * 个人视图由 end user 在看数据的页面自助）。由 EntityDetail 注入
   * setOuterTab('data')；缺省时设计器不渲染该按钮。
   */
  onGoRuntime?: () => void;
  /**
   * 实体行策略声明的 rule name 集合（entity.rowPolicies.rules[].name）。
   * 列表视图 dataScope.policyCode 下拉真源（协议 §3.2：只引用 row_policies，前端不拼 WHERE）。
   * 注意：policy 指行级数据策略（RLS），与保险业务域的「保单」无关。
   * 宿主可预置以省一次实体详情请求；未传时设计器自行按 entityId 拉取兜底。
   */
  rowPolicyCodes?: string[];
  /**
   * 字典快照刷新后重取字段（v1.1 联调实证修复）：字典面板的「刷新快照」只更新
   * 漂移徽标，选项列表与物化时间来自 EntityDetail 的 fields——不重取则面板
   * 停留在旧快照。由 EntityDetail 注入 loadFields。
   */
  onFieldsRefetch?: () => void;
}

const UiSchemaTab: React.FC<Props> = ({ entityId, entityCode, fields, onGoPublish, onGoRuntime, rowPolicyCodes, onFieldsRefetch }) => (
  <UiSchemaDesigner
    entityId={entityId}
    entityCode={entityCode}
    fields={fields ?? []}
    onGoPublish={onGoPublish}
    onGoRuntime={onGoRuntime}
    rowPolicyCodes={rowPolicyCodes}
    onFieldsRefetch={onFieldsRefetch}
  />
);

export default UiSchemaTab;
