import { useState } from 'react';
import { Button, Checkbox, Dropdown, Form, Input, Modal, Segmented, Space, Tag, Tooltip, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, QuestionCircleOutlined, ReloadOutlined, SaveOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { MetaField } from '../types';
import type { ListView } from '@bone/ui-schema';
import type { UseOverlayBundle } from '../hooks/useOverlayBundle';
import ColumnSettingsPopover from './ColumnSettingsPopover';

/**
 * 运行时工具栏「个人显示」组（个人覆盖编辑器的入口集合）。
 *
 * <p>构成：身份徽标（仅自己可见）→ 视图切换 Segmented → 管理（新建/重命名/删除/
 * 恢复该视图默认）→ 列设置弹层 → 自伤遮蔽一键自救。
 *
 * <p>与建模台「界面设计器 → 列表」的关系：那边配团队契约（发布后全员生效），
 * 这边配个人覆盖（即时生效仅自己）；本组只出现在**运行时数据页**——编辑入口
 * 跟着被编辑对象的生命周期走（业界双轨：Salesforce/ServiceNow/Power Apps/Airtable
 * 的个人视图编辑器都在最终用户看数据的页面，不在 maker 工具）。
 */
interface Props {
  overlay: UseOverlayBundle;
  fields: MetaField[];
  listView: ListView | null;
  /**
   * 「把当前筛选/排序保存到本视图」（由数据页注入 —— 筛选态住在页面里，
   * 只有页面知道当前值）。缺省时该菜单项不出现（不提供假按钮）。
   */
  onSaveFilterToView?: () => void;
}

const PersonalViewBar: React.FC<Props> = ({ overlay, fields, listView, onSaveFilterToView }) => {
  const [viewModal, setViewModal] = useState<{ mode: 'create' | 'rename' | 'delete'; id?: string } | null>(null);
  const [viewName, setViewName] = useState('');
  const [viewCopy, setViewCopy] = useState(true);

  const openCreate = () => { setViewModal({ mode: 'create' }); setViewName(''); setViewCopy(true); };
  const openRename = () => {
    if (!overlay.activeView) return;
    setViewModal({ mode: 'rename', id: overlay.activeView.id });
    setViewName(overlay.activeView.name);
  };
  const openDelete = () => {
    if (!overlay.activeView) return;
    setViewModal({ mode: 'delete', id: overlay.activeView.id });
  };

  const confirmViewModal = () => {
    if (!viewModal) return;
    if (viewModal.mode === 'create') {
      overlay.createView(viewName, viewCopy ? 'copy' : 'blank');
      message.success(`已新建并切换到个人视图「${viewName.trim() || '未命名视图'}」`);
    } else if (viewModal.mode === 'rename' && viewModal.id) {
      overlay.renameView(viewModal.id, viewName);
    } else if (viewModal.mode === 'delete' && viewModal.id) {
      overlay.deleteView(viewModal.id);
      message.success('已删除个人视图');
    }
    setViewModal(null);
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'save-filter',
      icon: <SaveOutlined />,
      label: '把当前筛选/排序保存到本视图…',
      title: '把此刻的筛选条件与排序沉淀进当前个人视图（切视图时随视图恢复）',
      onClick: () => {
        onSaveFilterToView?.();
        message.success(`已把当前筛选/排序保存到视图「${overlay.activeView?.name ?? ''}」`);
      },
    },
    { key: 'new', icon: <PlusOutlined />, label: '新建视图…', onClick: openCreate },
    { key: 'rename', icon: <EditOutlined />, label: '重命名当前视图…', onClick: openRename },
    {
      key: 'reset',
      icon: <ReloadOutlined />,
      label: '恢复该视图默认显示',
      title: '重置当前个人视图的显隐/密度偏好为默认；不影响团队契约，也不影响你的其他个人视图',
      onClick: () => {
        overlay.resetActive();
        message.success('已恢复该视图默认显示');
      },
    },
    { type: 'divider' },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: '删除当前视图',
      danger: true,
      disabled: (overlay.bundle?.views.length ?? 0) <= 1,
      onClick: openDelete,
    },
  ];

  return (
    <Space wrap>
      <Tooltip title="个人偏好层：仅调整你本人的列显隐/密度与个人视图，按用户存于服务端（仅自己可见）；团队级列集合/列序/列宽由「界面设计器」契约定义，发布后生效">
        <Tag color="blue" icon={<UserOutlined />}>个人偏好 · 仅自己可见</Tag>
      </Tooltip>
      <Segmented
        size="small"
        value={overlay.activeView?.id}
        onChange={(v) => overlay.switchView(String(v))}
        options={(overlay.bundle?.views ?? []).map((v) => ({ value: v.id, label: v.name }))}
      />
      <Dropdown menu={{ items: menuItems }} trigger={['click']}>
        <Button size="small" icon={<SettingOutlined />}>管理</Button>
      </Dropdown>
      <ColumnSettingsPopover overlay={overlay} fields={fields} listView={listView} />
      {overlay.shadowedColumns.length > 0 && (
        <Tooltip title={`${overlay.shadowedColumns.length} 列被你的个人偏好隐藏，但团队契约默认显示——这就是「配了不生效」的原因`}>
          <Button size="small" type="link" onClick={overlay.unhideShadowed}>
            {overlay.shadowedColumns.length} 列被个人偏好隐藏 · 恢复显示
          </Button>
        </Tooltip>
      )}
      <Tooltip title="个人视图按用户存于服务端，仅自己可见；与「界面设计器」契约叠加生效——契约定列集合，视图定你的显隐/密度">
        <QuestionCircleOutlined style={{ color: '#bfbfbf' }} />
      </Tooltip>
      {/* 视图管理弹窗：新建（可复制当前偏好）/ 重命名 / 删除（最后一个是底线） */}
      <Modal
        title={viewModal?.mode === 'create' ? '新建个人视图' : viewModal?.mode === 'rename' ? '重命名个人视图' : '删除个人视图'}
        open={!!viewModal}
        onOk={confirmViewModal}
        onCancel={() => setViewModal(null)}
        okText={viewModal?.mode === 'delete' ? '删除' : '确定'}
        okButtonProps={{
          // 重命名空名 = 静默无操作（renameView 拒绝空名且无反馈）⇒ 直接禁用确定；
          // 新建空名有意的「未命名视图」兜底 + 成功提示，故不禁用。
          disabled: viewModal?.mode === 'rename' && !viewName.trim(),
          ...(viewModal?.mode === 'delete' ? { danger: true } : {}),
        }}
        destroyOnHidden
      >
        {viewModal?.mode === 'delete' ? (
          <div style={{ lineHeight: 1.8 }}>
            确定删除个人视图「{overlay.bundle?.views.find((v) => v.id === viewModal.id)?.name}」？
            <br />
            <span style={{ fontSize: 12, color: '#86909c' }}>
              删除后该视图保存的显隐/密度偏好一并清除，其他视图不受影响；个人视图至少保留一个。
            </span>
          </div>
        ) : (
          <Form layout="vertical">
            <Form.Item label="视图名称" required extra="切换器里显示的名字，建议按使用场景命名">
              <Input
                value={viewName}
                maxLength={20}
                showCount
                placeholder="如：我的待审、大额专项"
                onChange={(e) => setViewName(e.target.value)}
              />
            </Form.Item>
            {viewModal?.mode === 'create' && (
              <Form.Item style={{ marginBottom: 0 }}>
                <Checkbox checked={viewCopy} onChange={(e) => setViewCopy(e.target.checked)}>
                  复制当前视图的列偏好（显隐 / 密度）
                </Checkbox>
              </Form.Item>
            )}
          </Form>
        )}
      </Modal>
    </Space>
  );
};

export default PersonalViewBar;
