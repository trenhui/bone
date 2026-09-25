import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  AppstoreOutlined,
  DeleteOutlined,
  EditOutlined,
  MenuOutlined,
  PlusSquareOutlined,
  ReloadOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  App as AntApp,
  Breadcrumb,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Flex,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  Tag,
  Tree,
  TreeSelect,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import type { Key } from 'react';
import {
  createMenu,
  deleteMenu,
  getMenuTree,
  updateMenu,
  type MenuNodeItem,
  type MenuReq,
} from '../services/api';
import ModulePage from '../components/ModulePage';
import { ProCard, StatisticCard } from '@ant-design/pro-components';

const typeMap: Record<number, { label: string; color: string }> = {
  1: { label: '菜单', color: 'blue' },
  2: { label: '按钮', color: 'purple' },
};

const typeTag = (type?: number) => {
  const t = typeMap[type ?? 1] ?? { label: `类型${type}`, color: 'default' };
  return <Tag color={t.color}>{t.label}</Tag>;
};

function summarize(list: MenuNodeItem[]) {
  let total = 0;
  let menus = 0;
  let buttons = 0;
  let roots = 0;
  let maxDepth = 0;
  const walk = (nodes: MenuNodeItem[], depth: number) => {
    maxDepth = Math.max(maxDepth, depth);
    for (const n of nodes) {
      total += 1;
      if ((n.type ?? 1) === 2) buttons += 1;
      else menus += 1;
      if (depth === 1) roots += 1;
      if (n.children?.length) walk(n.children, depth + 1);
    }
  };
  walk(list, 1);
  return { total, menus, buttons, roots, maxDepth };
}

function collectDescendantIds(node: MenuNodeItem | null): string[] {
  const ids: string[] = [];
  const walk = (n: MenuNodeItem) => {
    ids.push(n.id);
    n.children?.forEach(walk);
  };
  node?.children?.forEach(walk);
  return ids;
}

/** 递归收集全部节点 id（展开全部用） */
function collectAllKeys(list: MenuNodeItem[]): Key[] {
  const keys: Key[] = [];
  const walk = (nodes: MenuNodeItem[]) => {
    nodes.forEach((n) => {
      keys.push(n.id);
      if (n.children?.length) walk(n.children);
    });
  };
  walk(list);
  return keys;
}

function findPath(list: MenuNodeItem[], id: string, trail: MenuNodeItem[] = []): MenuNodeItem[] | null {
  for (const n of list) {
    const next = [...trail, n];
    if (n.id === id) return next;
    if (n.children) {
      const r = findPath(n.children, id, next);
      if (r) return r;
    }
  }
  return null;
}

const MenuManagement: React.FC = () => {
  const { message: msg, modal } = AntApp.useApp();
  const [treeData, setTreeData] = useState<MenuNodeItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [expandedKeys, setExpandedKeys] = useState<Key[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editing, setEditing] = useState<MenuNodeItem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<MenuReq>();
  // 类型联动：按钮型无路由路径、权限码必填
  const menuType = Form.useWatch('type', form);

  const load = useCallback(
    async (kw?: string) => {
      setLoading(true);
      try {
        const res = await getMenuTree(kw || undefined);
        const data = res.data ?? [];
        setTreeData(data);
        const expand: Key[] = [];
        const walk = (nodes: MenuNodeItem[], depth: number) => {
          if (depth <= 2) nodes.forEach((n) => (expand.push(n.id), n.children && walk(n.children, depth + 1)));
        };
        walk(data, 1);
        setExpandedKeys(expand);
      } catch {
        msg.error('加载菜单失败');
      } finally {
        setLoading(false);
      }
    },
    [msg],
  );

  useEffect(() => {
    void load();
  }, []);

  useEffect(() => {
    const t = setTimeout(() => void load(keyword), 300);
    return () => clearTimeout(t);
  }, [keyword, load]);

  const renderTree = useMemo<DataNode[]>(() => {
    const map = (list: MenuNodeItem[]): DataNode[] =>
      list.map((m) => ({
        key: m.id,
        title: (
          <Flex justify="space-between" align="center" style={{ paddingInlineEnd: 8 }}>
            <Space size={6} style={{ overflow: 'hidden' }}>
              {(m.type ?? 1) === 2 ? (
                <ThunderboltOutlined style={{ color: '#722ed1' }} />
              ) : (
                <MenuOutlined style={{ color: '#1677ff' }} />
              )}
              <span>{m.name}</span>
              {typeTag(m.type)}
              <span style={{ color: '#999', fontSize: 12 }}>
                {m.path || m.permission || ''}
              </span>
            </Space>
            <Space size={0} className="menu-node-actions">
              <Button
                type="link"
                size="small"
                icon={<PlusSquareOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  openCreate(m.id);
                }}
                title="添加子菜单"
              />
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  openEdit(m);
                }}
                title="编辑"
              />
              <Button
                type="link"
                size="small"
                danger
                icon={<DeleteOutlined />}
                onClick={(e) => {
                  e.stopPropagation();
                  remove(m);
                }}
                title="删除"
              />
            </Space>
          </Flex>
        ),
        children: m.children && m.children.length ? map(m.children) : undefined,
      }));
    return map(treeData);
  }, [treeData]);

  const selectedNode = useMemo(
    () => (selectedId == null ? null : findPath(treeData, selectedId)?.pop() ?? null),
    [treeData, selectedId],
  );
  const selectedPath = useMemo(
    () => (selectedId == null ? [] : findPath(treeData, selectedId) ?? []),
    [treeData, selectedId],
  );

  const stats = useMemo(() => summarize(treeData), [treeData]);

  const openCreate = (pid: string | null) => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ parentId: pid && pid !== '0' ? pid : '0', type: 1, orderNo: 1 });
    setDrawerOpen(true);
  };

  const openEdit = (node: MenuNodeItem) => {
    setEditing(node);
    form.setFieldsValue({
      name: node.name,
      parentId: node.parentId && node.parentId !== '0' ? node.parentId : '0',
      path: node.path,
      icon: node.icon,
      permission: node.permission,
      orderNo: node.orderNo ?? 1,
      type: node.type ?? 1,
    });
    setDrawerOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const parentId = values.parentId && values.parentId !== '0' ? values.parentId : null;
      const payload = { ...values, parentId };
      if (editing) {
        await updateMenu(editing.id, payload);
        msg.success('菜单已更新');
      } else {
        await createMenu(payload);
        msg.success('菜单已创建');
      }
      setDrawerOpen(false);
      await load();
    } catch {
      msg.error('保存失败');
    } finally {
      setSubmitting(false);
    }
  };

  const remove = (node: MenuNodeItem) => {
    const childCount = collectDescendantIds(node).length;
    // 后端仅删除单节点不级联：有子项时前端拦截，避免产生孤儿数据
    if (childCount > 0) {
      modal.warning({
        title: `无法删除「${node.name}」`,
        content: `该菜单下还有 ${childCount} 个子项，请先删除子项后再操作。`,
        okText: '知道了',
      });
      return;
    }
    modal.confirm({
      title: `确认删除「${node.name}」？`,
      content: '删除后将不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        await deleteMenu(node.id);
        msg.success('菜单已删除');
        if (selectedId === node.id) setSelectedId(null);
        await load();
      },
    });
  };

  const parentTreeData = useMemo<DataNode[]>(() => {
    const disabledIds = editing ? new Set([editing.id, ...collectDescendantIds(editing)]) : new Set<string>();
    const map = (list: MenuNodeItem[]): DataNode[] =>
      list.map(
        (m) =>
          ({
            key: m.id,
            value: m.id,
            title: m.name,
            disabled: disabledIds.has(m.id),
            children: m.children && m.children.length ? map(m.children) : undefined,
          }) as DataNode,
      );
    return [{ key: '0', value: '0', title: '顶级菜单（无上级）' } as DataNode, ...map(treeData)];
  }, [treeData, editing]);

  const toolbar = (
    <Space>
      <Input
        allowClear
        prefix={<SearchOutlined />}
        placeholder="搜索菜单名称 / 路径 / 权限码"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        style={{ width: 260 }}
      />
      <Button icon={<ReloadOutlined />} onClick={() => load(keyword)} title="刷新" />
      <Button onClick={() => setExpandedKeys(collectAllKeys(treeData))} title="展开全部">
        展开
      </Button>
      <Button onClick={() => setExpandedKeys([])} title="折叠全部">
        折叠
      </Button>
      <Button type="primary" icon={<PlusSquareOutlined />} onClick={() => openCreate(null)}>
        新建顶级菜单
      </Button>
    </Space>
  );

  const statistics = (
    <StatisticCard.Group direction="row" style={{ width: '100%' }}>
      <StatisticCard
        statistic={{ title: '菜单总数', value: stats.total, prefix: <AppstoreOutlined /> }}
      />
      <StatisticCard
        statistic={{ title: '菜单项', value: stats.menus, valueStyle: { color: '#1677ff' } }}
      />
      <StatisticCard
        statistic={{ title: '按钮权限', value: stats.buttons, valueStyle: { color: '#722ed1' } }}
      />
      <StatisticCard statistic={{ title: '顶级菜单', value: stats.roots }} />
      <StatisticCard statistic={{ title: '最大层级', value: stats.maxDepth, suffix: '级' }} />
    </StatisticCard.Group>
  );

  return (
    <ModulePage title="菜单管理" description="维护平台导航菜单与按钮级权限，支持搜索、层级浏览与影响范围确认删除。" extra={toolbar} statistics={statistics}>
      <Spin spinning={loading}>
        <Flex gap={16} align="flex-start" style={{ padding: 16 }}>
        <ProCard
          title="菜单结构"
          style={{ flex: '0 0 460px', minWidth: 380 }}
          bordered
          bodyStyle={{ padding: 0, maxHeight: 'calc(100vh - 320px)', overflow: 'auto' }}
        >
          <Tree<DataNode>
            treeData={renderTree}
            expandedKeys={expandedKeys}
            onExpand={(keys) => setExpandedKeys(keys)}
            selectedKeys={selectedId == null ? [] : [selectedId]}
            onSelect={(keys) => setSelectedId(keys.length ? String(keys[0]) : null)}
            blockNode
            style={{ padding: 12 }}
          />
        </ProCard>

        <ProCard
          title={selectedNode ? selectedNode.name : '菜单详情'}
          style={{ flex: 1, minWidth: 0 }}
          bordered
          headerBordered
          bodyStyle={{ padding: 16 }}
          extra={
            selectedNode && (
              <Space>
                <Button size="small" icon={<PlusSquareOutlined />} onClick={() => openCreate(selectedNode.id)}>
                  子菜单
                </Button>
                <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(selectedNode)}>
                  编辑
                </Button>
                <Button size="small" danger icon={<DeleteOutlined />} onClick={() => remove(selectedNode)}>
                  删除
                </Button>
              </Space>
            )
          }
        >
          {selectedNode ? (
            <>
              <Breadcrumb
                items={selectedPath.map((n) => ({ title: n.name }))}
                style={{ marginBlockEnd: 16 }}
              />
              <Descriptions column={2} size="small" bordered>
                <Descriptions.Item label="菜单 ID">{selectedNode.id}</Descriptions.Item>
                <Descriptions.Item label="类型">{typeTag(selectedNode.type)}</Descriptions.Item>
                <Descriptions.Item label="路由路径">{selectedNode.path || '-'}</Descriptions.Item>
                <Descriptions.Item label="图标">{selectedNode.icon || '-'}</Descriptions.Item>
                <Descriptions.Item label="权限码">{selectedNode.permission || '-'}</Descriptions.Item>
                <Descriptions.Item label="排序号">{selectedNode.orderNo ?? '-'}</Descriptions.Item>
                <Descriptions.Item label="子项数">{selectedNode.children?.length ?? 0}</Descriptions.Item>
                <Descriptions.Item label="层级">第 {(selectedPath.findIndex((n) => n.id === selectedNode.id) + 1)} 级</Descriptions.Item>
              </Descriptions>
            </>
          ) : (
            <Empty description="从左侧选择一个菜单查看详情，或点击「新建顶级菜单」" style={{ padding: '48px 0' }} />
          )}
        </ProCard>
      </Flex>
      </Spin>

      <Drawer
        title={editing ? `编辑菜单 · ${editing.name}` : '新建菜单'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={460}
        destroyOnClose
        footer={
          <Flex justify="end" gap={8}>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={submitting} onClick={submit}>
              保存
            </Button>
          </Flex>
        }
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input placeholder="如：用户管理" />
          </Form.Item>
          <Form.Item name="parentId" label="上级菜单" tooltip="选择所属上级；选「顶级菜单」即为一级菜单">
            <TreeSelect
              treeData={parentTreeData}
              treeDefaultExpandAll
              placeholder="请选择上级菜单"
              allowClear
              onChange={(v) => form.setFieldsValue({ parentId: v ?? undefined })}
            />
          </Form.Item>
          <Form.Item name="type" label="类型" tooltip="菜单=页面导航项；按钮=操作级权限点">
            <Select
              options={[
                { value: 1, label: '菜单（页面导航）' },
                { value: 2, label: '按钮（操作权限）' },
              ]}
            />
          </Form.Item>
          {menuType !== 2 && (
            <Form.Item name="path" label="路由路径" tooltip="前端路由，如 /iam/users">
              <Input placeholder="如：/iam/users" />
            </Form.Item>
          )}
          <Form.Item name="icon" label="图标" tooltip="Ant Design 图标名，如 UserOutlined">
            <Input placeholder="如：UserOutlined" />
          </Form.Item>
          <Form.Item
            name="permission"
            label="权限码"
            rules={menuType === 2 ? [{ required: true, message: '按钮型菜单必须绑定权限码!' }] : undefined}
            tooltip="按钮/菜单绑定的权限标识，如 iam:users:read；按钮型必填，与 JWT authorities 对齐"
          >
            <Input placeholder="如：iam:users:read" />
          </Form.Item>
          <Form.Item name="orderNo" label="排序号" tooltip="同层级下的展示顺序，数字越小越靠前">
            <InputNumber style={{ width: '100%' }} min={0} />
          </Form.Item>
        </Form>
      </Drawer>
    </ModulePage>
  );
};

export default MenuManagement;
