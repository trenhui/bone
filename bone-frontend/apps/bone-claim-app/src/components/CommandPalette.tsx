import { useMemo, useState, type ReactElement } from 'react';
import { useNavigate } from 'react-router-dom';
import { Empty, Input, List, Modal, Tag, Typography } from 'antd';
import { usePermission } from '@bone/ui';
import { useCommandPalette } from '../hooks/useCommandPalette';
import {
  filterCommands,
  runCommand,
  WORKBENCH_COMMANDS,
  type WorkbenchCommand,
} from '../workbench/commands';

/**
 * 全局命令面板（⌘/Ctrl+K）。
 *
 * <p>只在根布局挂载一次（见 App.tsx），因此必须位于 HashRouter 内（useNavigate）与 AntdApp 内。
 * 注册表与权限裁剪在 workbench/commands.ts，键盘约定在 useCommandPalette.ts；本组件只负责渲染。
 */
export function CommandPalette(): ReactElement {
  const navigate = useNavigate();
  const { hasAny } = usePermission();
  const [query, setQuery] = useState('');
  const [selected, setSelected] = useState(0);

  const commands = useMemo(
    () => filterCommands(WORKBENCH_COMMANDS, query, (codes) => hasAny(codes)),
    [query, hasAny],
  );

  // 函数声明提升到本作用域顶部，供下方 hook 的 onEnter 闭包引用；setOpen 在调用时已初始化。
  function execute(command: WorkbenchCommand | undefined) {
    if (!command) return;
    setOpen(false);
    setQuery('');
    setSelected(0);
    runCommand(command, navigate);
  }

  // 回调在每次渲染由 hook 的 ref 同步；此处只依赖当次渲染的 commands/selected 闭包。
  const { open, setOpen } = useCommandPalette({
    onMove: (delta) => {
      setSelected((prev) => {
        if (commands.length === 0) return 0;
        return (prev + delta + commands.length) % commands.length;
      });
    },
    onEnter: () => execute(commands[selected]),
  });

  const changeQuery = (value: string) => {
    setQuery(value);
    setSelected(0);
  };

  const close = () => {
    setOpen(false);
    setQuery('');
    setSelected(0);
  };

  return (
    <Modal
      title="命令面板"
      open={open}
      onCancel={close}
      footer={null}
      centered
      width={560}
      // Esc 由 useCommandPalette 统一处理，避免 antd 自带键盘处理与全局监听重复触发。
      keyboard={false}
    >
      <Input
        autoFocus
        aria-label="命令检索"
        placeholder="输入命令名或关键词（如：核赔、analytics、搜索）"
        value={query}
        onChange={(e) => changeQuery(e.target.value)}
      />
      <List
        style={{ marginTop: 12, maxHeight: 360, overflow: 'auto' }}
        dataSource={commands}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="无匹配命令" /> }}
        renderItem={(command, index) => (
          <List.Item
            key={command.id}
            onClick={() => execute(command)}
            style={{
              cursor: 'pointer',
              padding: '8px 12px',
              background: index === selected ? '#f0f5ff' : undefined,
            }}
          >
            <List.Item.Meta title={command.label} description={command.description} />
            <Tag color={index === selected ? 'blue' : 'default'} style={{ marginInlineEnd: 0 }}>
              {command.path ?? '动作'}
            </Tag>
          </List.Item>
        )}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>
        ↑↓ 选择 · Enter 执行 · Esc 关闭 · 权限不足的命令不展示（后端仍是最终门禁）
      </Typography.Text>
    </Modal>
  );
}
