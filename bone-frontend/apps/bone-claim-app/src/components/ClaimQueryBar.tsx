import { useEffect, useState, type ReactElement, type ReactNode } from 'react';
import { Button, Dropdown, Input, Space, Tooltip, Typography } from 'antd';
import {
  DeleteOutlined,
  PushpinOutlined,
  ReloadOutlined,
  SaveOutlined,
  StarOutlined,
} from '@ant-design/icons';
import type { SavedView } from '../hooks/useSavedViews';

const { Text } = Typography;

/**
 * 统一查询条：检索框 + 保存视图 + 刷新 + 页面自定义动作，是各列表页筛选区的统一语义。
 *
 * <p><b>为什么抽成受控组件</b>：提案 §5.2 指出 5 个配置页各写一套筛选，同一语义多处实现必然漂移。
 * 本组件不持有查询状态（由 `useClaimQuery` 持有并同步到 URL），只负责呈现与回调，
 * 因此可被任意列表页复用而不产生第二份真源。
 */
export interface ClaimQueryBarProps {
  /** 当前关键字（受控）。 */
  keyword: string;
  /** 关键字变更回调（含回车 / 点搜索触发）。 */
  onKeyword: (keyword: string) => void;
  /** 刷新按钮回调。 */
  onRefresh?: () => void;
  /** 保存视图列表。 */
  views?: SavedView[];
  /** 应用一个已保存视图（把它的 queryString 写回 URL）。 */
  onApplyView?: (view: SavedView) => void;
  /** 把当前查询存为命名视图。 */
  onSaveView?: (name: string) => void;
  onRemoveView?: (id: string) => void;
  onTogglePin?: (id: string) => void;
  /** 检索框占位文案。 */
  placeholder?: string;
  /**
   * 是否渲染检索框，默认 true。
   *
   * <p>关闭用于「后端列表契约本身不支持 keyword」的页面（如核赔待办 `/review/tasks`
   * 只接 status/page/size、初审 `claimApi.page` 只接 statuses/page/size）。这类页面仍可复用
   * 保存视图、刷新与自定义动作；若强行显示搜索框，输入后要么打一个后端不认识的参数（被静默忽略），
   * 要么只在前端过滤当前页（翻页即失效）——两者都是「配了不生效」的假功能，故显式关掉。
   */
  searchable?: boolean;
  /** 页面私有动作（新建 / 跳转等），追加在刷新之后。 */
  actions?: ReactNode;
}

export function ClaimQueryBar({
  keyword,
  onKeyword,
  onRefresh,
  views = [],
  onApplyView,
  onSaveView,
  onRemoveView,
  onTogglePin,
  placeholder = '关键字检索',
  searchable = true,
  actions,
}: ClaimQueryBarProps): ReactElement {
  const [input, setInput] = useState(keyword);
  // 外部清空或回灌查询（换 Tab / 换保单 / 应用保存视图）后输入框必须同步，
  // 否则框里残留旧关键字，用户以为筛选还在、提交后却查不到——属静默误导。
  useEffect(() => setInput(keyword), [keyword]);
  const [saveOpen, setSaveOpen] = useState(false);
  const [draftName, setDraftName] = useState('');

  const submit = () => onKeyword(input.trim());

  const viewItems = views.map((v) => ({
    key: v.id,
    label: (
      <Space size={4}>
        {v.pinned ? <PushpinOutlined /> : <StarOutlined />}
        <span>{v.name}</span>
        <Text type="secondary" style={{ fontSize: 12 }}>{v.queryString || '全部'}</Text>
      </Space>
    ),
    onClick: () => onApplyView?.(v),
  }));

  const commitSave = () => {
    const name = draftName.trim();
    if (name) onSaveView?.(name);
    setDraftName('');
    setSaveOpen(false);
  };

  return (
    <Space wrap size={8}>
      {searchable && (
        <Input.Search
          allowClear
          value={input}
          // placeholder 不是可访问名称（屏幕阅读器不保证读出）；用同一文案显式赋名。
          aria-label={placeholder}
          placeholder={placeholder}
          style={{ width: 240 }}
          onChange={(e) => setInput(e.target.value)}
          onSearch={submit}
        />
      )}
      {onRefresh && (
        <Tooltip title="刷新">
          <Button icon={<ReloadOutlined />} onClick={onRefresh} />
        </Tooltip>
      )}
      {onSaveView && (
        <Dropdown
          open={saveOpen}
          onOpenChange={setSaveOpen}
          trigger={['click']}
          // antd 5.27 起 dropdownRender 废弃（POC 浏览器实测每次开视图菜单均 console.error 告警），
          // popupRender 是同一语义的正式入口，签名一致。
          popupRender={() => (
            <Space direction="vertical" size={8} style={{ padding: 8, background: '#fff' }}>
              <Input
                size="small"
                placeholder="视图名称"
                value={draftName}
                onChange={(e) => setDraftName(e.target.value)}
                onPressEnter={commitSave}
              />
              <Button size="small" type="primary" icon={<SaveOutlined />} onClick={commitSave}>
                保存当前查询
              </Button>
            </Space>
          )}
          menu={views.length ? { items: viewItems } : undefined}
        >
          <Button icon={<StarOutlined />}>视图{views.length ? ` (${views.length})` : ''}</Button>
        </Dropdown>
      )}
      {views.filter((v) => v.pinned).map((v) => (
        <Button
          key={v.id}
          size="small"
          type="link"
          onClick={() => onApplyView?.(v)}
          icon={<PushpinOutlined />}
        >
          {v.name}
        </Button>
      ))}
      {views.length > 0 && onRemoveView && (
        <Dropdown menu={{
          items: views.map((v) => ({
            key: v.id,
            label: (
              <Space size={4}>
                <span>{v.name}</span>
                <Tooltip title="取消置顶">
                  <Button
                    type="text"
                    size="small"
                    icon={<PushpinOutlined />}
                    onClick={(e) => { e.stopPropagation(); onTogglePin?.(v.id); }}
                  />
                </Tooltip>
                <Tooltip title="删除视图">
                  <Button
                    type="text"
                    size="small"
                    danger
                    icon={<DeleteOutlined />}
                    onClick={(e) => { e.stopPropagation(); onRemoveView(v.id); }}
                  />
                </Tooltip>
              </Space>
            ),
          })),
        }}>
          <Button size="small" type="text">管理视图</Button>
        </Dropdown>
      )}
      {actions}
    </Space>
  );
}
