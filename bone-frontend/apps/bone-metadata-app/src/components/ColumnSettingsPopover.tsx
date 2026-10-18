import { useEffect, useRef, useState } from 'react';
import { Alert, Badge, Button, Divider, Empty, Input, Popconfirm, Popover, Segmented, Space, Switch, Tag, Tooltip, Typography, message } from 'antd';
import type { InputRef } from 'antd';
import { EditOutlined, ReloadOutlined, SettingOutlined, UndoOutlined } from '@ant-design/icons';
import type { MetaField } from '../types';
import type { ListView } from '../utils/uiSchemaEngine';
import type { UseOverlayBundle } from '../hooks/useOverlayBundle';

const { Text } = Typography;

/**
 * 运行时「列设置」弹层（个人覆盖的唯一编辑器）。
 *
 * <p>为什么在运行时而不在建模台：被编辑的对象（个人覆盖）是运行期状态，
 * 编辑入口跟着生命周期走（对标 Salesforce personal lists 在 record home 自助，
 * 不在 Setup）。建模台侧只核对团队契约口径，个人效果对照在同页「交付口径对照」卡片。
 *
 * <p>弹层内容与「界面设计器 → 列表」的分界：列集合/列序/列宽/固定列归团队契约
 * （此处只读展示，契约隐藏的列置灰并明示真源）；本弹层只编辑个人维度——
 * 显隐 / 表格密度 / 个人改名，以及自伤遮蔽的一键自救。
 */
interface Props {
  overlay: UseOverlayBundle;
  fields: MetaField[];
  listView: ListView | null;
}

const ColumnSettingsPopover: React.FC<Props> = ({ overlay, fields, listView }) => {
  // 受控开关：antd Popover(trigger=click) 在「内容含可交互状态」时，点击内部按钮触发 setState
  // → 内容重渲染，rc-trigger 的外点监听在重渲染时序上会误判为「点外部」而关闭弹层。
  // 这里接管 open，自己用 mousedown 外点监听关闭——弹层内任意交互（含改名重渲染）都不误关。
  const [open, setOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement | null>(null);
  const popupRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!open) return;
    const onDocMouseDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (triggerRef.current?.contains(t) || popupRef.current?.contains(t)) return;
      setOpen(false);
    };
    document.addEventListener('mousedown', onDocMouseDown);
    return () => document.removeEventListener('mousedown', onDocMouseDown);
  }, [open]);

  const contractCols = (listView?.columns ?? []).filter((c) => !!c.fieldCode);
  const byCode = new Map(fields.map((f) => [f.code, f]));
  // 有契约：行 = 契约列（契约序，含契约隐藏列——置灰展示，让用户看见「契约不给显示」）；
  // 无契约：行 = 全部可写字段（纯个人模式）。
  const rows: { field: MetaField; contractHidden: boolean }[] =
    contractCols.length > 0
      ? contractCols
        .map((c) => ({ field: byCode.get(c.fieldCode), contractHidden: c.visible === false }))
        .filter((r): r is { field: MetaField; contractHidden: boolean } => !!r.field)
      : fields.map((f) => ({ field: f, contractHidden: false }));
  const kanban = listView?.viewKind === 'kanban';
  const shadowedCount = overlay.shadowedColumns.length;

  /* 个人列改名（行内编辑）：editingCode 非空时该行渲染 Input。
     Enter / 失焦提交（微交互不弹二级确认）；Esc 取消。 */
  const [editingCode, setEditingCode] = useState<string | null>(null);
  const [draftName, setDraftName] = useState('');
  const renameInputRef = useRef<InputRef | null>(null);

  // 进入编辑态后在下一拍聚焦输入框（避开 Popover 开启动画把焦点抢回内容容器，
  // 否则 autoFocus 同步聚焦会立刻被 Popover 抢走 → onBlur 触发 commit → Input 闪退）。
  useEffect(() => {
    if (!editingCode) return;
    const t = setTimeout(() => renameInputRef.current?.focus(), 0);
    return () => clearTimeout(t);
  }, [editingCode]);

  const startRename = (field: MetaField) => {
    const cur = overlay.activeOv.overrides[field.code];
    // 初始值 = 当前个人名（已改名时）否则字段名——从「现状」起改，不从空起改
    setDraftName(cur?.displayName && cur.displayName !== field.displayName ? cur.displayName : (field.displayName || field.code));
    setEditingCode(field.code);
  };
  const commitRename = () => {
    if (!editingCode) return;
    const field = fields.find((f) => f.code === editingCode);
    const next = draftName.trim();
    const restored = !next || (field != null && next === field.displayName);
    overlay.renameColumn(editingCode, next || null);
    message.success(restored ? '已恢复默认列名（回落团队契约标题）' : `已个人改名「${next}」（仅你本人可见）`);
    setEditingCode(null);
  };
  const cancelRename = () => setEditingCode(null);

  const content = (
    <div style={{ width: 380 }}>
      {shadowedCount > 0 && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 10 }}
          message={`${shadowedCount} 列被你的个人偏好隐藏（团队契约默认显示）`}
          description="这就是「配了不生效」：设计器契约要显示的列，被你之前的隐藏偏好盖住。恢复显示只动你本人，不影响团队契约与其他成员。"
          action={<Button size="small" onClick={overlay.unhideShadowed}>恢复显示</Button>}
        />
      )}
      <Text strong style={{ fontSize: 13 }}>列显示</Text>
      <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
        {contractCols.length > 0 ? '列集合由团队契约定义（界面设计器）' : '未配置团队契约，当前为个人模式'}
      </Text>
      <div style={{ maxHeight: 300, overflowY: 'auto', margin: '6px 0 10px' }}>
        {rows.length === 0 ? (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无字段" />
        ) : (
          rows.map(({ field, contractHidden }) => {
            const ov = overlay.activeOv.overrides[field.code];
            const personalHidden = ov?.visible === false;
            const renamed = !!ov?.displayName && ov.displayName !== field.displayName;
            const editing = editingCode === field.code;
            return (
              <div
                key={field.code}
                className="col-settings-row"
                data-field-code={field.code}
                // 关键：行内任意点击（改名/开关/恢复）都不冒泡到 document，避免 antd
                // Popover(trigger=click) 的「外点关闭」监听在内容重渲染后误判为点外部而关闭弹层
                // （这是内容含可交互状态时的经典竞态：改名按钮点击 → setState → 内容重渲染 →
                // rc-trigger 的 document 点击监听才看到该事件，误关）。stopPropagation 精准切断。
                onClick={(e) => e.nativeEvent.stopPropagation()}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '5px 0', gap: 8 }}
              >
                {editing ? (
                  <Input
                    ref={renameInputRef}
                    size="small"
                    value={draftName}
                    maxLength={30}
                    showCount
                    placeholder={field.displayName || field.code}
                    onChange={(e) => setDraftName(e.target.value)}
                    onPressEnter={commitRename}
                    onBlur={commitRename}
                    onKeyDown={(e) => {
                      if (e.key === 'Escape') cancelRename();
                    }}
                    style={{ fontSize: 13, flex: 1, marginRight: 8 }}
                  />
                ) : (
                  <div style={{ display: 'flex', alignItems: 'center', minWidth: 0, flex: 1, gap: 6 }}>
                    <Tooltip
                      title={
                        renamed
                          ? `个人改名：${ov!.displayName}（默认：${field.displayName || field.code}）`
                          : (field.displayName || field.code)
                      }
                    >
                      {/* .col-settings-name 保持「纯列名」：探针与测试按它取行名，
                          「个人」徽标必须放在 span 外，否则 innerText 混入徽标文字 */}
                      <span
                        className="col-settings-name"
                        style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
                      >
                        {renamed ? ov!.displayName : (field.displayName || field.code)}
                      </span>
                    </Tooltip>
                    {renamed && <Tag style={{ fontSize: 10, margin: 0, lineHeight: '15px' }}>个人</Tag>}
                  </div>
                )}
                {!editing && !contractHidden && (
                  <Space size={0}>
                    {renamed && (
                      <Button
                        size="small"
                        type="text"
                        title="恢复默认列名（回落团队契约标题）"
                        aria-label="恢复默认列名"
                        icon={<UndoOutlined />}
                        onClick={() => {
                          overlay.renameColumn(field.code, null);
                          message.success('已恢复默认列名（回落团队契约标题）');
                        }}
                      />
                    )}
                    <Button
                      size="small"
                      type="text"
                      title="个人改名：仅你本人可见，团队契约标题与其他成员不变"
                      aria-label="个人改名"
                      icon={<EditOutlined />}
                      onClick={() => startRename(field)}
                    />
                  </Space>
                )}
                {contractHidden ? (
                  <Tooltip title="该列被「界面设计器」契约隐藏；如需显示请去设计器修改并发布">
                    <Switch size="small" checked={false} disabled />
                  </Tooltip>
                ) : (
                  <Tooltip title={personalHidden ? '你已个人隐藏该列（契约默认显示）' : '仅隐藏你本人的视图，不影响其他成员'}>
                    <Switch
                      size="small"
                      className="col-settings-switch"
                      checked={!personalHidden}
                      onChange={(v) => overlay.toggleVisible(field.code, v)}
                    />
                  </Tooltip>
                )}
              </div>
            );
          })
        )}
      </div>
      <Divider style={{ margin: '4px 0 10px' }} />
      <Space>
        <Text type="secondary" style={{ fontSize: 12 }}>密度</Text>
        <Segmented
          size="small"
          disabled={kanban || !overlay.activeView}
          value={overlay.activeOv.density ?? 'comfortable'}
          onChange={(v) => overlay.setDensity(v as 'comfortable' | 'compact')}
          options={[
            { value: 'comfortable', label: '标准' },
            { value: 'compact', label: '紧凑' },
          ]}
        />
        {kanban && <Text type="secondary" style={{ fontSize: 12 }}>看板视图不使用表格密度</Text>}
      </Space>
      <Divider style={{ margin: '10px 0' }} />
      <Space>
        {/* 恢复默认显示 = 清空全部个人视图回单视图默认态（resetAll）。
            爆炸半径随视图数增长：>1 个命名视图时一键清完且不可撤销 ⇒ 按业界
            破坏性操作惯例（GitHub/Airtable/Salesforce）先确认，把范围说清；
            单视图时与业界 per-view 即时重置一致（ServiceNow/Airtable），不打扰。 */}
        {(overlay.bundle?.views.length ?? 0) > 1 ? (
          <Popconfirm
            title="清空全部个人视图？"
            description={`将删除你的 ${overlay.bundle?.views.length} 个个人视图及各自保存的显隐/密度，恢复为默认投影；团队契约不受影响。`}
            okText="清空并恢复"
            cancelText="取消"
            onConfirm={() => overlay.resetAll()}
          >
            <Button size="small" icon={<ReloadOutlined />} disabled={!overlay.dirty}>
              恢复默认显示
            </Button>
          </Popconfirm>
        ) : (
          <Button size="small" icon={<ReloadOutlined />} disabled={!overlay.dirty} onClick={overlay.resetAll}>
            恢复默认显示
          </Button>
        )}
        <Text type="secondary" style={{ fontSize: 12 }}>仅清你本人的偏好，不动团队契约</Text>
      </Space>
    </div>
  );

  return (
    <Badge dot={overlay.dirty || shadowedCount > 0} offset={[-4, 4]}>
      <Popover
        open={open}
        trigger={[]}
        placement="bottomLeft"
        title="列设置"
        content={<div ref={popupRef}>{content}</div>}
      >
        <Button
          ref={triggerRef}
          size="small"
          icon={<SettingOutlined />}
          onClick={() => setOpen((v) => !v)}
        >
          列设置
        </Button>
      </Popover>
    </Badge>
  );
};

export default ColumnSettingsPopover;
