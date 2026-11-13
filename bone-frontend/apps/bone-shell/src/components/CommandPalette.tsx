import {
  useCallback, useEffect, useMemo, useState,
  type KeyboardEvent as ReactKeyboardEvent, type ReactElement,
} from 'react';
import {
  Button, Empty, Input, List, Modal, Space, Tag, Typography,
} from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import type { ShellMenuItem } from '../shellContext';
import { buildCommandItems, type CommandItem } from '../utils/commandItems';

/**
 * Shell 顶栏常驻的全局检索 / 命令面板（⌘K、Ctrl+K 或 `/` 唤起）。
 *
 * 业界依据：Fiori shell bar 把 enterprise search 与 notifications 并列常驻；Nielsen #7
 * 把键盘快捷键定义为「叠加给熟练用户的 accelerator」，因此侧栏与可见菜单**完全不变**，
 * 这里只多一条更快路径；#3 要求随时 Esc 退出；#9 要求无结果给可执行的下一步。
 *
 * 结果只来自 IAM 已下发的可见菜单（见 utils/commandItems.ts 的安全边界），
 * 案件 ID 直达只做 URL 编码，合法性由目标页 fail-closed 复核。
 */
export interface CommandPaletteProps {
  menuConfig: readonly ShellMenuItem[];
  onNavigate: (target: string) => void;
}

export default function CommandPalette({ menuConfig, onNavigate }: CommandPaletteProps): ReactElement {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [active, setActive] = useState(0);

  const items = useMemo(() => buildCommandItems(menuConfig, keyword), [menuConfig, keyword]);
  const close = useCallback(() => {
    setOpen(false);
    setKeyword('');
    setActive(0);
  }, []);
  const select = useCallback((item: CommandItem) => {
    onNavigate(item.target);
    close();
  }, [onNavigate, close]);

  useEffect(() => { setActive(0); }, [keyword]);

  useEffect(() => {
    const onKey = (e: KeyboardEvent): void => {
      const el = e.target as HTMLElement | null;
      const typing = Boolean(el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable));
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOpen((prev) => !prev);
        return;
      }
      // `/` 只在非输入态触发，避免把所有输入框里的斜杠都吃掉。
      if (e.key === '/' && !typing && !e.metaKey && !e.ctrlKey && !e.altKey) {
        e.preventDefault();
        setOpen(true);
        return;
      }
      if (e.key === 'Escape' && open) {
        e.preventDefault();
        close();
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, close]);

  const onInputKeyDown = (e: ReactKeyboardEvent<HTMLInputElement>): void => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((a) => Math.max(0, Math.min(a + 1, items.length - 1)));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(0, a - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const item = items[active];
      if (item) select(item);
    }
  };

  return (
    <>
      <Button
        type="text"
        className="header-button command-palette-trigger"
        icon={<SearchOutlined />}
        onClick={() => setOpen(true)}
        title="全局检索（⌘K / Ctrl+K / /）"
      >
        <span className="command-palette-trigger-label">搜索 <kbd>⌘K</kbd></span>
      </Button>
      <Modal
        open={open}
        onCancel={close}
        footer={null}
        width={620}
        title="全局检索 · 找功能或找案件"
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Input
            autoFocus
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={onInputKeyDown}
            prefix={<SearchOutlined />}
            placeholder="输入菜单名，或输入纯数字案件 ID 直达案件 360"
            allowClear
            size="large"
          />
          <Typography.Text type="secondary">
            ↑↓ 选择 · ↵ 打开 · Esc 关闭 · 只显示当前账号有权访问的菜单
          </Typography.Text>
          {items.length === 0 ? (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <Space direction="vertical" size={2}>
                  <Typography.Text>没有匹配的功能或案件</Typography.Text>
                  <Typography.Text type="secondary">
                    可试试菜单名（如「核赔工作台」）或纯数字案件 ID；若确认功能应存在，
                    请检查当前账号的角色与数据权限，或切换租户视角。
                  </Typography.Text>
                  <Typography.Text type="secondary">
                    按保单号 / 姓名 / 发票号的跨键检索已由工作台顶栏搜索框接入后端 /api/v1/claims/search（网关已白名单）；命令面板保持只做菜单 + 案件 ID 直达，避免越权检索入口外溢。
                  </Typography.Text>
                </Space>
              }
            />
          ) : (
            <List
              size="small"
              dataSource={items}
              renderItem={(item, index) => (
                <List.Item
                  key={item.id}
                  role="option"
                  aria-selected={index === active}
                  onClick={() => select(item)}
                  onMouseEnter={() => setActive(index)}
                  style={{
                    cursor: 'pointer',
                    padding: '8px 10px',
                    borderRadius: 6,
                    ...(index === active ? { background: 'rgba(22, 119, 255, 0.08)' } : {}),
                  }}
                  extra={index === active ? <Tag color="blue">↵ 打开</Tag> : null}
                >
                  <List.Item.Meta
                    title={
                      <Space size={6}>
                        <Tag color={item.group === 'case' ? 'blue' : 'default'}>
                          {item.group === 'case' ? '案件' : '功能'}
                        </Tag>
                        <span>{item.label}</span>
                      </Space>
                    }
                    description={<Typography.Text type="secondary" style={{ fontSize: 12 }}>{item.description}</Typography.Text>}
                  />
                </List.Item>
              )}
            />
          )}
        </Space>
      </Modal>
    </>
  );
}
