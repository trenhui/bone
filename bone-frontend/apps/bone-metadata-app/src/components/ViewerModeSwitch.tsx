import { Segmented, Tooltip } from 'antd';
import { ToolOutlined } from '@ant-design/icons';
import { usePermission } from '@bone/ui';
import { BonePermissionCodes } from '@bone/shared-types';
import type { ViewerMode } from '../utils/persona';

/**
 * 「观看者模式」开关（消费面 ⇄ 实施面）—— 业界通行的 persona 切换的最小形态。
 *
 * <p><b>为什么是开关而不是两个页面</b>：Odoo 的开发者模式、Airtable 的 Designer/Interface、
 * Salesforce 的 Setup/App 都证明——同一份元数据若维护两套列表页，列定义、投影、权限、
 * 行内动作会立刻漂移；开关只切换**信息密度**，渲染引擎、投影函数、写路径完全共用，
 * 结构性杜绝漂移。
 *
 * <p><b>为什么按权限隐藏而不是按人写死</b>：真正的门禁始终在后端（RuntimeAccessGuard /
 * 建模写接口），这里的 `has()` 只是避免给没有建模权限的用户一个「点了也没用」的入口
 * （渐进披露：不可达的能力不占视觉位）。本组件**不做**任何写操作鉴权，切模式也只是本机偏好。
 */
interface Props {
  value: ViewerMode;
  onChange: (mode: ViewerMode) => void;
}

const ViewerModeSwitch: React.FC<Props> = ({ value, onChange }) => {
  const { has } = usePermission();
  if (!has(BonePermissionCodes.METADATA_MODEL_WRITE)) return null;
  return (
    <Tooltip title="业务用户看到的是「消费面」；实施/建模工程师切到「实施面」查看物理表、列契约与代理键">
      <Segmented
        size="small"
        value={value}
        onChange={(v) => onChange(v as ViewerMode)}
        options={[
          { value: 'consume', label: '业务视图' },
          { value: 'implement', label: <span><ToolOutlined /> 实施模式</span> },
        ]}
      />
    </Tooltip>
  );
};

export default ViewerModeSwitch;
